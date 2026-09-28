package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Helper to copy and crop passport photos into the app's persistent internal storage directory.
 */
object ImageStorageHelper {

    private const val TAG = "ImageStorageHelper"
    private const val PHOTOS_DIR = "student_photos"

    /**
     * Reads image content from a device content Uri and saves a persistent copy
     * in the application's internal files directory.
     * Returns a local file Uri string (e.g. "file:///data/user/0/.../student_photos/photo_xyz.jpg").
     */
    fun saveImageUriToInternalStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val directory = File(context.filesDir, PHOTOS_DIR)
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val fileName = "photo_${UUID.randomUUID()}.jpg"
            val destinationFile = File(directory, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Uri.fromFile(destinationFile).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save student photo from uri: $sourceUri", e)
            null
        }
    }

    /**
     * Saves a cropped / edited Bitmap directly into persistent internal storage.
     * Compresses as JPEG with 90% quality for optimal passport photo resolution and storage efficiency.
     */
    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): String? {
        return try {
            val directory = File(context.filesDir, PHOTOS_DIR)
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val fileName = "photo_${UUID.randomUUID()}.jpg"
            val destinationFile = File(directory, fileName)

            FileOutputStream(destinationFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }

            Uri.fromFile(destinationFile).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cropped photo bitmap to internal storage", e)
            null
        }
    }

    /**
     * Loads a Bitmap from a content Uri or file path string with dimension bounds checking.
     */
    fun loadBitmap(context: Context, uriOrPath: String, maxDimension: Int = 1200): Bitmap? {
        return try {
            val uri = Uri.parse(uriOrPath)
            val inputStream: InputStream? = if (uri.scheme == "content") {
                context.contentResolver.openInputStream(uri)
            } else {
                val file = if (uri.scheme == "file") File(uri.path ?: "") else File(uriOrPath)
                if (file.exists()) file.inputStream() else null
            }

            inputStream?.use { stream ->
                val bytes = stream.readBytes()
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

                var sampleSize = 1
                val maxSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
                while (maxSide / (sampleSize * 2) >= maxDimension) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from $uriOrPath", e)
            null
        }
    }

    /**
     * Safely deletes an internal photo file if it was replaced or removed.
     */
    fun deleteInternalPhoto(context: Context, photoPath: String?) {
        if (photoPath.isNullOrBlank()) return
        try {
            val uri = Uri.parse(photoPath)
            if (uri.scheme == "file") {
                val file = File(uri.path ?: return)
                if (file.exists() && file.parentFile?.name == PHOTOS_DIR) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete photo: $photoPath", e)
        }
    }
}
