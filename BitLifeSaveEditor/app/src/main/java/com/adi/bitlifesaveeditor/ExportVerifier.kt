package com.adi.bitlifesaveeditor

internal object ExportVerifier {
    fun verifyBackup(expectedSource: ByteArray, readBack: ByteArray): Boolean =
        expectedSource.contentEquals(readBack)

    fun verifyEdited(
        original: ByteArray,
        expectedEdited: ByteArray,
        readBack: ByteArray,
        slot: BalanceSlot,
        expectedAmount: Double
    ): Boolean =
        expectedEdited.contentEquals(readBack) &&
            BalancePatchEngine.verifyPatched(original, readBack, slot, expectedAmount)
}
