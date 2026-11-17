package com.adi.bitlifesaveeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression check against two real save revisions decoded with Microsoft's
 * read-only System.Formats.Nrbf decoder in the companion probe workflow.
 */
class RealSaveBalanceTest {
    @Test
    fun realSaveRevisionsMapToFinancesBankBalance() {
        val oldPath = System.getenv("BITLIFE_OLD_SAVE") ?: return
        val newPath = System.getenv("BITLIFE_NEW_SAVE") ?: return

        val cases = listOf(
            Triple(oldPath, 1_000_000_000.0, 0xF2A3),
            Triple(newPath, 853_244.6095520258, 0xF29F)
        )

        for ((path, expectedBalance, expectedOffset) in cases) {
            val source = File(path).readBytes()
            val sourceHash = BalancePatchEngine.sha256(source)
            val slot = BalancePatchEngine.inspect(source)

            assertNotNull("Money Editor must recognize real save: $path", slot)
            assertEquals("BankBalance candidate offset", expectedOffset, slot!!.offset)
            assertEquals("Decoded Finances.BankBalance value", expectedBalance, slot.value, 1e-6)

            val edited = BalancePatchEngine.patch(source, slot, 2_000_000_000.0)
            assertNotNull("A copied save should be patchable", edited)
            assertTrue(
                "Only the eight-byte BankBalance value may change",
                BalancePatchEngine.verifyPatched(source, edited!!, slot, 2_000_000_000.0)
            )
            assertEquals("Source bytes must remain unchanged", sourceHash, BalancePatchEngine.sha256(source))
            assertEquals(expectedBalance, BalancePatchEngine.inspect(source)!!.value, 1e-6)
        }
    }
}
