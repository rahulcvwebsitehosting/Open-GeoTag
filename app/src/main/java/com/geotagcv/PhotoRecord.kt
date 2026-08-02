package com.geotagcv

import android.net.Uri

data class PhotoRecord(
    val uri: Uri,
    val displayName: String,
    val capturedAt: Long,
    val sizeBytes: Long
)
