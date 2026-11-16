package com.adi.bitlifesaveeditor

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

internal data class BalanceSlot(
    val offset: Int,
    val value: Double,
    val markerOffset: Int
)

/**
 * Guarded fixed-width patcher for the currently observed BitLife NRBF save layout.
 *
 * This is intentionally NOT a general BinaryFormatter deserializer/serializer.
 * The candidate location is based on the existing controlled-save comparison:
 * 19 bytes after the unique "SimBankLoan" marker. The app validates the NRBF
 * stream header and known type markers, then changes exactly one little-endian
 * Double on a copy. Cross-version and in-game acceptance still require testing.
 */
internal object BalancePatchEngine {
    private const val MARKER = "SimBankLoan"
    private const val OFFSET_AFTER_MARKER = 19
    internal const val MAX_BALANCE = 1.0e18

    fun inspect(data: ByteArray): BalanceSlot? {
        if (!hasNrbfHeader(data)) return null
        if (!containsAscii(data, "Assembly-CSharp") || !containsAscii(data, "Life3")) return null

        val markerBytes = MARKER.toByteArray(Charsets.US_ASCII)
        val hits = findOccurrences(data, markerBytes)
        if (hits.size != 1) return null

        val markerOffset = hits.single()
        val valueOffset = markerOffset + OFFSET_AFTER_MARKER
        if (valueOffset < markerOffset + markerBytes.size || valueOffset + java.lang.Double.BYTES > data.size) {
            return null
        }

        val value = readDouble(data, valueOffset)
        if (!value.isFinite() || value < 0.0 || value > MAX_BALANCE) return null

        return BalanceSlot(valueOffset, value, markerOffset)
    }

    fun patch(source: ByteArray, expectedSlot: BalanceSlot, amount: Double): ByteArray? {
        if (!amount.isFinite() || amount < 0.0 || amount > MAX_BALANCE) return null

        val currentSlot = inspect(source) ?: return null
        if (currentSlot != expectedSlot) return null

        val patched = source.copyOf()
        ByteBuffer.wrap(patched, currentSlot.offset, java.lang.Double.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putDouble(amount)

        return if (verifyPatched(source, patched, expectedSlot, amount)) patched else null
    }

    fun verifyPatched(
        source: ByteArray,
        edited: ByteArray,
        expectedSlot: BalanceSlot,
        expectedAmount: Double
    ): Boolean {
        if (source.size != edited.size) return false
        if (!expectedAmount.isFinite() || expectedAmount < 0.0 || expectedAmount > MAX_BALANCE) return false

        val sourceSlot = inspect(source) ?: return false
        val editedSlot = inspect(edited) ?: return false
        if (sourceSlot != expectedSlot) return false
        if (editedSlot.offset != sourceSlot.offset || editedSlot.markerOffset != sourceSlot.markerOffset) return false
        if (editedSlot.value != expectedAmount) return false

        val changedRange = sourceSlot.offset until sourceSlot.offset + java.lang.Double.BYTES
        for (index in source.indices) {
            if (index !in changedRange && source[index] != edited[index]) return false
        }
        return true
    }

    fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun hasNrbfHeader(data: ByteArray): Boolean {
        // SerializationHeaderRecord: record=0, rootId, headerId=-1, major=1, minor=0.
        if (data.size < 17 || data[0] != 0.toByte()) return false
        val header = ByteBuffer.wrap(data, 1, 16).order(ByteOrder.LITTLE_ENDIAN)
        val rootId = header.int
        val headerId = header.int
        val majorVersion = header.int
        val minorVersion = header.int
        return rootId > 0 && headerId == -1 && majorVersion == 1 && minorVersion == 0
    }

    private fun containsAscii(data: ByteArray, value: String): Boolean =
        findOccurrences(data, value.toByteArray(Charsets.US_ASCII)).isNotEmpty()

    private fun findOccurrences(data: ByteArray, pattern: ByteArray): List<Int> {
        if (pattern.isEmpty() || pattern.size > data.size) return emptyList()
        val hits = mutableListOf<Int>()
        var index = 0
        while (index <= data.size - pattern.size) {
            var matches = true
            for (offset in pattern.indices) {
                if (data[index + offset] != pattern[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                hits += index
                index += pattern.size
            } else {
                index++
            }
        }
        return hits
    }

    private fun readDouble(data: ByteArray, offset: Int): Double =
        ByteBuffer.wrap(data, offset, java.lang.Double.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .double
}
