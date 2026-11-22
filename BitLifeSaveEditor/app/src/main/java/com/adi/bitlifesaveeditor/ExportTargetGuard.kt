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
 * stream. The caller must read the destination first; an unreadable target is
 * not considered safe.
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

        // A different URI can still be an alias for the same underlying file.
        // If its current bytes equal the imported source, refuse to risk
        // truncating the original through a provider alias.
        if (sourceBytes.contentEquals(destinationBytesBeforeWrite)) return false

        return true
    }
}
