package com.adi.bitlifesaveeditor

/**
 * Confirms that the imported source bytes are still identical to the file
 * currently referenced by the Storage Access Framework URI.
 */
internal object SourceSnapshotGuard {
    fun matches(imported: ByteArray?, current: ByteArray?): Boolean =
        imported != null && current != null && imported.contentEquals(current)
}
