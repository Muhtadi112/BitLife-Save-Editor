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
 * Conservative fixed-width patcher for the BitLife NRBF layout validated so far.
 *
 * The safe .NET NRBF reader identified the actual field as:
 * Life.<Finances>k__BackingField -> SimFinances.<BankBalance>k__BackingField.
 * In two real revisions, its value bytes were 19 bytes after the unique
 * "SimBankLoan" type-metadata marker (not a live SimBankLoan object):
 *  - old save: 0xF2A3 = 1,000,000,000.0
 *  - new save: 0xF29F = 853,244.6095520258
 *
 * This is NOT a general BinaryFormatter parser/serializer. The engine fails
 * closed unless the known NRBF + finance metadata signature is present, and
 * only patches a copied file. Other BitLife layouts still need validation.
 */
internal object BalancePatchEngine {
    private const val MARKER = "SimBankLoan"
    private const val OFFSET_AFTER_MARKER = 19
    private const val FINANCES_FIELD = "<Finances>k__BackingField"
    private const val FINANCE_TYPE = "SimFinances"
    private const val BANK_BALANCE_FIELD = "<BankBalance>k__BackingField"
    internal const val MAX_BALANCE = 1.0e18

    fun inspect(data: ByteArray): BalanceSlot? {
        if (!hasNrbfHeader(data)) return null
        if (findOccurrences(data, "Assembly-CSharp".toByteArray(Charsets.US_ASCII)).isEmpty()) return null

        val markerBytes = MARKER.toByteArray(Charsets.US_ASCII)
        val markerHits = findOccurrences(data, markerBytes)
        if (markerHits.size != 1) return null

        val markerOffset = markerHits.single()
        val financesHits = findOccurrences(data, FINANCES_FIELD.toByteArray(Charsets.US_ASCII))
        val financeTypeHits = findOccurrences(data, FINANCE_TYPE.toByteArray(Charsets.US_ASCII))
        val bankBalanceHits = findOccurrences(data, BANK_BALANCE_FIELD.toByteArray(Charsets.US_ASCII))

        // Require the root Finances reference, SimFinances type metadata and
        // BankBalance member metadata to precede the candidate's metadata anchor.
        if (financesHits.none { it < markerOffset }) return null
        if (financeTypeHits.none { it < markerOffset }) return null
        if (bankBalanceHits.none { it < markerOffset }) return null

        val valueOffset = markerOffset + OFFSET_AFTER_MARKER
        if (valueOffset < markerOffset + markerBytes.size ||
            valueOffset + java.lang.Double.BYTES > data.size
        ) {
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
