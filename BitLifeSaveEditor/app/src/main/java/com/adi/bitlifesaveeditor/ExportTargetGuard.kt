package com.adi.bitlifesaveeditor

/**
 * Prevents exporting to the exact URI used to import the source save.
 * This is an additional safety check; callers must still use a new destination.
 */
internal object ExportTargetGuard {
    fun canExport(sourceUri: String?, destinationUri: String?): Boolean =
        !sourceUri.isNullOrBlank() &&
            !destinationUri.isNullOrBlank() &&
            sourceUri != destinationUri
}
