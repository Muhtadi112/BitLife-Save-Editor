package com.adi.bitlifesaveeditor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTargetGuardTest {
    @Test
    fun rejectsTheExactImportedSourceUri() {
        val uri = "content://com.android.externalstorage.documents/document/primary%3ABitLife%2FsavedLife.data"
        assertFalse(ExportTargetGuard.canExport(uri, uri))
    }

    @Test
    fun rejectsMissingSourceOrDestinationUri() {
        assertFalse(ExportTargetGuard.canExport(null, "content://provider/new-file"))
        assertFalse(ExportTargetGuard.canExport("content://provider/source", null))
        assertFalse(ExportTargetGuard.canExport("", "content://provider/new-file"))
    }

    @Test
    fun allowsANewDestinationUri() {
        assertTrue(
            ExportTargetGuard.canExport(
                "content://provider/source",
                "content://provider/savedLife_edited.data"
            )
        )
    }
}
