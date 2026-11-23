package com.adi.bitlifesaveeditor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTargetGuardTest {
    private val sourceUri = "content://provider/source"
    private val newUri = "content://provider/new-file"
    private val sourceBytes = byteArrayOf(1, 2, 3, 4, 5)

    @Test
    fun rejectsTheExactImportedSourceUri() {
        assertFalse(
            ExportTargetGuard.canExport(
                sourceUri, sourceUri, sourceBytes, byteArrayOf()
            )
        )
    }

    @Test
    fun rejectsCanonicalUriAliases() {
        assertFalse(
            ExportTargetGuard.canExport(
                sourceUri,
                newUri,
                sourceBytes,
                byteArrayOf(),
                sourceCanonicalUri = "content://provider/canonical/123",
                destinationCanonicalUri = "content://provider/canonical/123"
            )
        )
    }

    @Test
    fun rejectsMatchingSafDocumentIdentity() {
        val identity = SafDocumentIdentity("com.android.documents", "primary:BitLife/savedLife.data")
        assertFalse(
            ExportTargetGuard.canExport(
                sourceUri,
                newUri,
                sourceBytes,
                byteArrayOf(),
                sourceDocumentIdentity = identity,
                destinationDocumentIdentity = identity
            )
        )
    }

    @Test
    fun rejectsDifferentUriWithSameExistingBytes() {
        assertFalse(
            ExportTargetGuard.canExport(
                sourceUri, newUri, sourceBytes, sourceBytes.copyOf()
            )
        )
    }

    @Test
    fun rejectsDifferentNonEmptyDestinationContents() {
        assertFalse(
            ExportTargetGuard.canExport(
                sourceUri, newUri, sourceBytes, byteArrayOf(9, 8, 7)
            )
        )
    }

    @Test
    fun rejectsMissingUrisOrUnreadableDestination() {
        assertFalse(ExportTargetGuard.canExport(null, newUri, sourceBytes, byteArrayOf()))
        assertFalse(ExportTargetGuard.canExport(sourceUri, null, sourceBytes, byteArrayOf()))
        assertFalse(ExportTargetGuard.canExport(sourceUri, newUri, sourceBytes, null))
        assertFalse(ExportTargetGuard.canExport("", newUri, sourceBytes, byteArrayOf()))
    }

    @Test
    fun rejectsEmptySourceSnapshot() {
        assertFalse(ExportTargetGuard.canExport(sourceUri, newUri, byteArrayOf(), byteArrayOf()))
    }

    @Test
    fun allowsAReadableNewEmptyDestination() {
        assertTrue(
            ExportTargetGuard.canExport(
                sourceUri, newUri, sourceBytes, byteArrayOf()
            )
        )
    }
}
