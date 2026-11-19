package com.adi.bitlifesaveeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BalancePatchEngineTest {
    private fun fixture(
        includeFinancesReference: Boolean = true,
        includeFinanceType: Boolean = true,
        includeBankBalanceField: Boolean = true,
        financesOffset: Int = 88,
        financeTypeOffset: Int = 160,
        bankBalanceOffset: Int = 192,
        markerOffset: Int = 320
    ): ByteArray {
        val data = ByteArray(640)
        data[0] = 0 // SerializedStreamHeader record
        ByteBuffer.wrap(data, 1, 16).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(1)   // root id
            .putInt(-1)  // no additional header
            .putInt(1)   // major version
            .putInt(0)   // minor version

        "Assembly-CSharp".toByteArray(Charsets.US_ASCII).copyInto(data, 24)
        "Life".toByteArray(Charsets.US_ASCII).copyInto(data, 64)
        if (includeFinancesReference) {
            "<Finances>k__BackingField".toByteArray(Charsets.US_ASCII).copyInto(data, financesOffset)
        }
        if (includeFinanceType) {
            "SimFinances".toByteArray(Charsets.US_ASCII).copyInto(data, financeTypeOffset)
        }
        if (includeBankBalanceField) {
            "<BankBalance>k__BackingField".toByteArray(Charsets.US_ASCII).copyInto(data, bankBalanceOffset)
        }

        val marker = "SimBankLoan".toByteArray(Charsets.US_ASCII)
        marker.copyInto(data, markerOffset)
        ByteBuffer.wrap(data, markerOffset + 19, java.lang.Double.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putDouble(1000.0)
        return data
    }

    @Test
    fun inspectsNrbfHeaderAndFinanceFieldSignature() {
        val slot = BalancePatchEngine.inspect(fixture())
        assertNotNull(slot)
        assertEquals(339, slot!!.offset)
        assertEquals(320, slot.markerOffset)
        assertEquals(1000.0, slot.value, 0.0)
    }

    @Test
    fun rejectsNonNrbfHeader() {
        val data = fixture()
        data[0] = 7
        assertNull(BalancePatchEngine.inspect(data))
    }

    @Test
    fun rejectsMissingFinancesReference() {
        assertNull(BalancePatchEngine.inspect(fixture(includeFinancesReference = false)))
    }

    @Test
    fun rejectsMissingSimFinancesTypeMetadata() {
        assertNull(BalancePatchEngine.inspect(fixture(includeFinanceType = false)))
    }

    @Test
    fun rejectsMissingBankBalanceFieldMetadata() {
        assertNull(BalancePatchEngine.inspect(fixture(includeBankBalanceField = false)))
    }

    @Test
    fun rejectsDuplicateLoanMetadataAnchor() {
        val data = fixture()
        "SimBankLoan".toByteArray(Charsets.US_ASCII).copyInto(data, 500)
        assertNull(BalancePatchEngine.inspect(data))
    }


    @Test
    fun rejectsFinanceMetadataInWrongOrder() {
        val data = fixture(
            financesOffset = 88,
            bankBalanceOffset = 192,
            financeTypeOffset = 240,
            markerOffset = 400
        )
        assertNull(BalancePatchEngine.inspect(data))
    }

    @Test
    fun patchChangesOnlyTheEightByteCandidateAndVerifiesIt() {
        val source = fixture()
        val slot = BalancePatchEngine.inspect(source)!!
        val patched = BalancePatchEngine.patch(source, slot, 2_000_000_000.0)
        assertNotNull(patched)
        assertTrue(BalancePatchEngine.verifyPatched(source, patched!!, slot, 2_000_000_000.0))
        assertEquals(1000.0, BalancePatchEngine.inspect(source)!!.value, 0.0)
        assertEquals(2_000_000_000.0, BalancePatchEngine.inspect(patched)!!.value, 0.0)

        val changed = source.indices.filter { source[it] != patched[it] }
        assertTrue(changed.isNotEmpty())
        assertTrue(changed.all { it in slot.offset until slot.offset + java.lang.Double.BYTES })
    }

    @Test
    fun rejectsStaleInspectionAndInvalidAmounts() {
        val source = fixture()
        val slot = BalancePatchEngine.inspect(source)!!
        val changedSource = source.copyOf()
        ByteBuffer.wrap(changedSource, slot.offset, java.lang.Double.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putDouble(1500.0)

        assertNull(BalancePatchEngine.patch(changedSource, slot, 2_000_000_000.0))
        assertNull(BalancePatchEngine.patch(source, slot, -1.0))
        assertNull(BalancePatchEngine.patch(source, slot, Double.NaN))
        assertNull(BalancePatchEngine.patch(source, slot, BalancePatchEngine.MAX_BALANCE * 2))
        assertFalse(BalancePatchEngine.verifyPatched(source, source, slot, 2_000_000_000.0))
    }
}
