package com.adi.bitlifesaveeditor

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NrbfBalanceLocatorTest {
    private class Writer {
        private val output = ByteArrayOutputStream()

        fun byte(value: Int) {
            output.write(value and 0xff)
        }

        fun int(value: Int) {
            repeat(4) { shift -> byte(value ushr (shift * 8)) }
        }

        fun long(value: Long) {
            repeat(8) { shift -> byte((value ushr (shift * 8)).toInt()) }
        }

        fun string(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            var length = bytes.size
            while (length >= 0x80) {
                byte((length and 0x7f) or 0x80)
                length = length ushr 7
            }
            byte(length)
            output.write(bytes)
        }

        fun double(value: Double) = long(value.toBits())

        fun bytes(): ByteArray = output.toByteArray()
    }

    private fun fixture(
        rootName: String = "Life",
        financesClassName: String = "SimFinances",
        bankFieldName: String = "<BankBalance>k__BackingField",
        bankPrimitiveType: Int = 6,
        includeFinancesField: Boolean = true,
        includeBankBalanceField: Boolean = true,
        appendClassWithId: Boolean = false,
        includeArrayNoise: Boolean = false,
        withoutTypes: Boolean = false
    ): ByteArray {
        val writer = Writer()
        writer.byte(0) // SerializationHeaderRecord
        writer.int(1)  // root id
        writer.int(-1)
        writer.int(1)
        writer.int(0)

        writer.byte(12) // BinaryLibrary
        writer.int(1)
        writer.string("Assembly-CSharp")

        writer.byte(if (withoutTypes) 3 else 5) // ClassWithMembers[AndTypes]: Life
        writer.int(1)
        writer.string(rootName)
        val rootMemberCount = (if (includeFinancesField) 1 else 0) + (if (includeArrayNoise) 1 else 0)
        writer.int(rootMemberCount)
        if (includeFinancesField) writer.string("<Finances>k__BackingField")
        if (includeArrayNoise) writer.string("Noise")

        if (!withoutTypes) {
            if (includeFinancesField) writer.byte(4) // BinaryType.Class
            if (includeArrayNoise) writer.byte(5) // BinaryType.ObjectArray

            if (includeFinancesField) {
                writer.string(financesClassName)
                writer.int(1) // library id for member class type
            }
        }
        writer.int(1) // library id for Life class

        if (includeFinancesField) {
            writer.byte(if (withoutTypes) 3 else 5) // ClassWithMembers[AndTypes]: SimFinances
            writer.int(2)
            writer.string(financesClassName)
            writer.int(if (includeBankBalanceField) 1 else 0)
            if (includeBankBalanceField) {
                writer.string(bankFieldName)
                if (!withoutTypes) {
                    writer.byte(0) // BinaryType.Primitive
                    writer.byte(bankPrimitiveType)
                }
            }
            writer.int(1) // library id for SimFinances
            if (withoutTypes && includeBankBalanceField) {
                writer.byte(8) // MemberPrimitiveTyped
                writer.byte(bankPrimitiveType)
            }
            when (bankPrimitiveType) {
                6 -> writer.double(12345.5)
                9 -> writer.long(12345L)
                else -> writer.byte(0)
            }
        }

        if (includeArrayNoise) {
            writer.byte(16) // ArraySingleObject
            writer.int(3)
            writer.int(1)
            writer.byte(6) // BinaryObjectString
            writer.int(4)
            writer.string("unrelated")
        }

        if (appendClassWithId) {
            writer.byte(1) // ClassWithId reuses SimFinances metadata id 2
            writer.int(5)
            writer.int(2)
            writer.double(777.0)
        }

        writer.byte(11) // MessageEnd
        return writer.bytes()
    }

    @Test
    fun locatesDoubleByLifeFinancesObjectPath() {
        val data = fixture()
        val location = NrbfBalanceLocator.locate(data)

        assertNotNull(location)
        assertEquals(12345.5, location!!.value, 0.0)
        assertArrayEquals(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(12345.5).array(), data.copyOfRange(location.offset, location.offset + 8))
        assertEquals("Life", location.rootType)
        assertEquals("SimFinances", location.financesType)
        assertTrue(location.offset in 0 until data.size - 7)
    }

    @Test
    fun locatesDoubleInClassWithMembersRecordsWithoutTypeMetadata() {
        val data = fixture(withoutTypes = true)
        val location = NrbfBalanceLocator.locate(data)

        assertNotNull(location)
        assertEquals(12345.5, location!!.value, 0.0)
        assertEquals("Life", location.rootType)
        assertEquals("SimFinances", location.financesType)
        assertArrayEquals(
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(12345.5).array(),
            data.copyOfRange(location.offset, location.offset + 8)
        )
    }

    @Test
    fun rejectsWrongRootType() {
        assertNull(NrbfBalanceLocator.locate(fixture(rootName = "NotLife")))
    }

    @Test
    fun rejectsWrongFinancesClass() {
        assertNull(NrbfBalanceLocator.locate(fixture(financesClassName = "OtherClass")))
    }

    @Test
    fun rejectsMissingFinancesField() {
        assertNull(NrbfBalanceLocator.locate(fixture(includeFinancesField = false)))
    }

    @Test
    fun rejectsMissingBankBalanceField() {
        assertNull(NrbfBalanceLocator.locate(fixture(includeBankBalanceField = false)))
    }

    @Test
    fun rejectsBankBalanceWithWrongPrimitiveType() {
        assertNull(NrbfBalanceLocator.locate(fixture(bankPrimitiveType = 9)))
    }

    @Test
    fun followsClassWithIdMetadataAndSkipsObjectArrays() {
        val data = fixture(appendClassWithId = true, includeArrayNoise = true)
        val location = NrbfBalanceLocator.locate(data)

        assertNotNull(location)
        assertEquals(12345.5, location!!.value, 0.0)
        assertArrayEquals(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(12345.5).array(), data.copyOfRange(location.offset, location.offset + 8))
    }

    @Test
    fun rejectsTruncatedAndTrailingGarbageStreams() {
        val valid = fixture()
        assertNull(NrbfBalanceLocator.locate(valid.copyOf(valid.size - 2)))

        val trailing = valid + byteArrayOf(0)
        assertNull(NrbfBalanceLocator.locate(trailing))
    }

    @Test
    fun rejectsClassMemberCountAboveAllocationBudget() {
        val malformed = fixture()
        // Root class member count follows the 17-byte header, library record,
        // "Assembly-CSharp" string, and "Life" class name.
        malformed[48] = 0xA1.toByte()
        malformed[49] = 0x86.toByte()
        malformed[50] = 0x01.toByte()
        malformed[51] = 0x00.toByte()
        assertNull(NrbfBalanceLocator.locate(malformed))
    }

    @Test
    fun rejectsOverflowingFiveByteStringLength() {
        val malformed = fixture()
        // The first BinaryLibrary string length begins after the 17-byte header,
        // record type, and 4-byte library id.
        malformed[22] = 0x80.toByte()
        malformed[23] = 0x80.toByte()
        malformed[24] = 0x80.toByte()
        malformed[25] = 0x80.toByte()
        malformed[26] = 0x10.toByte()
        assertNull(NrbfBalanceLocator.locate(malformed))
    }

    @Test
    fun rejectsMalformedUtf8MetadataStrings() {
        val malformed = fixture()
        // The first BinaryLibrary string payload begins at byte 23.
        malformed[23] = 0xff.toByte()
        assertNull(NrbfBalanceLocator.locate(malformed))
    }

    @Test
    fun rejectsNonCanonicalSevenBitStringLength() {
        val valid = fixture()
        val malformed = ByteArray(valid.size + 1)
        valid.copyOfRange(0, 22).copyInto(malformed, 0)
        malformed[22] = 0x8f.toByte()
        malformed[23] = 0x00
        valid.copyOfRange(23, valid.size).copyInto(malformed, 24)
        assertNull(NrbfBalanceLocator.locate(malformed))
    }

    @Test
    fun rejectsInvalidHeaderAndOversizedInput() {
        val invalidHeader = fixture()
        invalidHeader[0] = 9
        assertNull(NrbfBalanceLocator.locate(invalidHeader))
        assertNull(NrbfBalanceLocator.locate(ByteArray(64 * 1024 * 1024 + 1)))
    }
}
