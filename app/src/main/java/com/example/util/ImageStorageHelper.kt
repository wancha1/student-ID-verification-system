package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Helper to copy passport photos selected from the device photo picker
 * into the app's persistent internal storage directory.
 */
object ImageStorageHelper {

    private const val TAG = "ImageStorageHelper"
    private const val PHOTOS_DIR = "student_photos"

    /**
     * Reads the image content from a device content Uri and saves a persistent copy
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

            // Return file URI path
            Uri.fromFile(destinationFile).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save student photo from uri: $sourceUri", e)
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
