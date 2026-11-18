package com.adi.bitlifesaveeditor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportVerifierTest {
    @Test
    fun acceptsAnExactBackupReadBack() {
        val source = byteArrayOf(0, 1, 2, 3, 4, 5)
        assertTrue(ExportVerifier.verifyBackup(source, source.copyOf()))
    }

    @Test
    fun rejectsTruncatedBackupReadBack() {
        val source = byteArrayOf(0, 1, 2, 3, 4, 5)
        val truncated = byteArrayOf(0, 1, 2, 3, 4)
        assertFalse(ExportVerifier.verifyBackup(source, truncated))
    }

    @Test
    fun rejectsBackupReadBackWithAnyChangedByte() {
        val source = byteArrayOf(0, 1, 2, 3, 4, 5)
        val changed = source.copyOf().also { it[3] = 99 }
        assertFalse(ExportVerifier.verifyBackup(source, changed))
    }
}
