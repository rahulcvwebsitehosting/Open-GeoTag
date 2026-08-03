package com.geotagcv

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.geotagcv.databinding.ItemPhotoHistoryBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

class PhotoHistoryAdapter(
    private val contentResolver: ContentResolver,
    private val onPhotoSelected: (PhotoRecord) -> Unit
) : ListAdapter<PhotoRecord, PhotoHistoryAdapter.PhotoViewHolder>(DiffCallback) {
    private val thumbnailExecutor = Executors.newFixedThreadPool(2)
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun release() {
        thumbnailExecutor.shutdownNow()
        bitmapCache.evictAll()
    }

    inner class PhotoViewHolder(
        private val binding: ItemPhotoHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(record: PhotoRecord) {
            val key = record.uri.toString()
            binding.root.setOnClickListener { onPhotoSelected(record) }
            binding.root.contentDescription = binding.root.context.getString(
                R.string.open_saved_photo_description,
                record.displayName
            )
            binding.tvPhotoName.text = record.displayName
            binding.tvPhotoDate.text = SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
            ).format(Date(record.capturedAt))
            binding.tvPhotoSize.text = formatBytes(record.sizeBytes)
            binding.ivPhotoThumbnail.tag = key
            binding.ivPhotoThumbnail.setImageResource(R.drawable.ic_outline_camera_alt_24)

            bitmapCache.get(key)?.let {
                binding.ivPhotoThumbnail.setImageBitmap(it)
                return
            }

            thumbnailExecutor.execute {
                val bitmap = decodeThumbnail(record)
                if (bitmap != null) bitmapCache.put(key, bitmap)
                binding.ivPhotoThumbnail.post {
                    if (binding.ivPhotoThumbnail.tag == key && bitmap != null) {
                        binding.ivPhotoThumbnail.setImageBitmap(bitmap)
                    }
                }
            }
        }

        private fun decodeThumbnail(record: PhotoRecord): Bitmap? = runCatching {
            val sourceFile = if (record.uri.scheme == ContentResolver.SCHEME_FILE) {
                File(record.uri.path.orEmpty()).takeIf(File::exists)
            } else {
                null
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            (sourceFile?.inputStream() ?: contentResolver.openInputStream(record.uri))?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sampleSize = 1
            while (max(bounds.outWidth, bounds.outHeight) / sampleSize > THUMBNAIL_SIZE_PX) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            (sourceFile?.inputStream() ?: contentResolver.openInputStream(record.uri))?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull()
    }

    private object DiffCallback : DiffUtil.ItemCallback<PhotoRecord>() {
        override fun areItemsTheSame(oldItem: PhotoRecord, newItem: PhotoRecord): Boolean =
            oldItem.uri == newItem.uri

        override fun areContentsTheSame(oldItem: PhotoRecord, newItem: PhotoRecord): Boolean =
            oldItem == newItem
    }

    companion object {
        private const val THUMBNAIL_SIZE_PX = 400
        private fun cacheSizeKb(): Int =
            (Runtime.getRuntime().maxMemory() / 1024L / 24L).coerceAtMost(8 * 1024L).toInt()

        private fun formatBytes(bytes: Long): String = when {
            bytes <= 0L -> ""
            bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0)
            bytes >= 1024L -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
