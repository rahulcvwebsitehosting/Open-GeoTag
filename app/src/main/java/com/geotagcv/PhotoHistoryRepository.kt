package com.geotagcv

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

class PhotoHistoryRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun recordCapture(uri: Uri) {
        val metadata = queryMetadata(uri)
        val newRecord = PhotoRecord(
            uri = uri,
            displayName = metadata?.displayName ?: uri.lastPathSegment ?: "Geo tag photo",
            capturedAt = metadata?.capturedAt?.takeIf { it > 0 } ?: System.currentTimeMillis(),
            sizeBytes = metadata?.sizeBytes ?: 0L
        )
        val records = loadRecordedPhotos().toMutableList()
        records.removeAll { it.uri == uri }
        records.add(0, newRecord)
        saveRecordedPhotos(records)
    }

    fun loadRecentPhotos(limit: Int = 20): List<PhotoRecord> =
        loadRecordedPhotos().sortedByDescending(PhotoRecord::capturedAt).take(limit)

    fun loadAllSavedPhotos(): List<PhotoRecord> {
        val results = LinkedHashMap<String, PhotoRecord>()
        loadRecordedPhotos().forEach { results[it.uri.toString()] = it }
        queryDefaultAlbum().forEach { record -> results[record.uri.toString()] = record }
        return results.values.sortedByDescending(PhotoRecord::capturedAt)
    }

    private fun queryDefaultAlbum(): List<PhotoRecord> {
        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Images.Media.DATA)
            }
        }.toTypedArray()

        val selection: String
        val arguments: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            arguments = arrayOf("DCIM/Geo Tag Photo/%")
        } else {
            @Suppress("DEPRECATION")
            selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            arguments = arrayOf("%/DCIM/Geo Tag Photo/%")
        }

        return runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arguments,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                buildList {
                    while (cursor.moveToNext()) {
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            cursor.getLong(idIndex)
                        )
                        add(
                            PhotoRecord(
                                uri = uri,
                                displayName = cursor.getString(nameIndex) ?: "Geo tag photo",
                                capturedAt = cursor.getLong(dateIndex) * 1000L,
                                sizeBytes = cursor.getLong(sizeIndex)
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun queryMetadata(uri: Uri): PhotoRecord? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            PhotoRecord(
                uri = uri,
                displayName = cursor.getString(0) ?: "Geo tag photo",
                capturedAt = cursor.getLong(1) * 1000L,
                sizeBytes = cursor.getLong(2)
            )
        }
    }.getOrNull()

    private fun loadRecordedPhotos(): List<PhotoRecord> = runCatching {
        val array = JSONArray(preferences.getString(KEY_HISTORY, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    PhotoRecord(
                        uri = Uri.parse(item.getString("uri")),
                        displayName = item.optString("name", "Geo tag photo"),
                        capturedAt = item.optLong("capturedAt", 0L),
                        sizeBytes = item.optLong("size", 0L)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun saveRecordedPhotos(records: List<PhotoRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("uri", record.uri.toString())
                    .put("name", record.displayName)
                    .put("capturedAt", record.capturedAt)
                    .put("size", record.sizeBytes)
            )
        }
        preferences.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "geo_tag_photo_history"
        private const val KEY_HISTORY = "captured_photos"
    }
}
