package com.adi.bitlifesaveeditor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSnapshotGuardTest {
    @Test
    fun acceptsAnUnchangedSourceSnapshot() {
        val imported = byteArrayOf(1, 2, 3, 4)
        assertTrue(SourceSnapshotGuard.matches(imported, imported.copyOf()))
    }

    @Test
    fun rejectsSourceChangedAfterImport() {
        val imported = byteArrayOf(1, 2, 3, 4)
        val current = byteArrayOf(1, 2, 9, 4)
        assertFalse(SourceSnapshotGuard.matches(imported, current))
    }

    @Test
    fun rejectsMissingOrUnreadableSource() {
        val imported = byteArrayOf(1, 2, 3, 4)
        assertFalse(SourceSnapshotGuard.matches(imported, null))
        assertFalse(SourceSnapshotGuard.matches(null, imported))
    }
}
