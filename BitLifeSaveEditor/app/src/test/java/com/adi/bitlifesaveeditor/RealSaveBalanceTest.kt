package com.adi.bitlifesaveeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Optional regression check against two real save revisions previously decoded
 * with Microsoft's read-only System.Formats.Nrbf decoder.
 */
class RealSaveBalanceTest {
    @Test
    fun realSaveRevisionsMapToFinancesBankBalance() {
        val oldPath = System.getenv("BITLIFE_OLD_SAVE")
        val newPath = System.getenv("BITLIFE_NEW_SAVE")
        assumeTrue(
            "Set BITLIFE_OLD_SAVE and BITLIFE_NEW_SAVE to run the opt-in real-save regression test",
            !oldPath.isNullOrBlank() && !newPath.isNullOrBlank()
        )

        val cases = listOf(
            Triple(oldPath!!, 1_000_000_000.0, 0xF2A3),
            Triple(newPath!!, 853_244.6095520258, 0xF29F)
        )

        for ((path, expectedBalance, expectedOffset) in cases) {
            val source = File(path).readBytes()
            val sourceHash = BalancePatchEngine.sha256(source)
            val slot = BalancePatchEngine.inspect(source)

            assertNotNull("Money Editor must recognize real save: $path", slot)
            assertEquals("BankBalance candidate offset", expectedOffset, slot!!.offset)
            assertEquals("Decoded Finances.BankBalance value", expectedBalance, slot.value, 1e-6)

            // Explicitly exercise the new Kotlin NRBF locator on each real save.
            // This test remains opt-in so personal save bytes never enter CI.
            val located = NrbfBalanceLocator.locate(source)
            assertNotNull("Kotlin NRBF locator must recognize real save: $path", located)
            assertEquals("Kotlin NRBF locator offset", expectedOffset, located!!.offset)
            assertEquals("Kotlin NRBF locator balance", expectedBalance, located.value, 1e-6)
            assertEquals("Locator and guarded patcher must agree", slot.offset, located.offset)

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
