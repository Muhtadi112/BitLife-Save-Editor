package com.adi.bitlifesaveeditor

/**
 * SAF identity data used to detect two URI aliases that resolve to the same
 * document. Android-specific URI resolution is performed by MainActivity.
 */
internal data class SafDocumentIdentity(
    val authority: String,
    val documentId: String
)

/**
 * Rejects ambiguous or destructive export targets before opening an output
 * stream. The caller must read the destination first; only a readable,
 * zero-length destination is accepted. Existing non-empty files are never
 * overwritten, even when their bytes differ from the imported save.
 */
internal object ExportTargetGuard {
    fun canExport(
        sourceUri: String?,
        destinationUri: String?,
        sourceBytes: ByteArray?,
        destinationBytesBeforeWrite: ByteArray?,
        sourceCanonicalUri: String? = null,
        destinationCanonicalUri: String? = null,
        sourceDocumentIdentity: SafDocumentIdentity? = null,
        destinationDocumentIdentity: SafDocumentIdentity? = null
    ): Boolean {
        if (sourceUri.isNullOrBlank() || destinationUri.isNullOrBlank()) return false
        if (sourceUri == destinationUri) return false

        if (!sourceCanonicalUri.isNullOrBlank() &&
            sourceCanonicalUri == destinationCanonicalUri
        ) return false

        if (sourceDocumentIdentity != null &&
            sourceDocumentIdentity == destinationDocumentIdentity
        ) return false

        if (sourceBytes == null || sourceBytes.isEmpty()) return false
        if (destinationBytesBeforeWrite == null) return false

        // CreateDocument should return a newly-created empty document. A
        // non-empty destination may be an unrelated user file, so fail closed
        // instead of truncating or replacing it.
        if (destinationBytesBeforeWrite.isNotEmpty()) return false

        return true
    }
}
