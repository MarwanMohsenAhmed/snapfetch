package com.snapfetch.app.core.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.snapfetch.app.domain.model.MediaType
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MediaStoreSaver(
    private val context: Context,
    private val safHelper: SafDirectoryHelper
) {
    fun saveCompletedFile(
        sourceFile: File,
        displayName: String,
        mimeType: String,
        mediaType: MediaType,
        safTreeUriString: String?
    ): Uri {
        val safeName = sanitizeFilename(displayName)
        if (!safTreeUriString.isNullOrBlank()) {
            safHelper.saveFileToTree(
                treeUriString = safTreeUriString,
                sourceFile = sourceFile,
                displayName = safeName,
                mimeType = mimeType
            )?.let { return it }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStoreApi29Plus(sourceFile, safeName, mimeType, mediaType)
        } else {
            saveLegacyExternal(sourceFile, safeName, mimeType, mediaType)
        }
    }

    private fun saveViaMediaStoreApi29Plus(
        sourceFile: File,
        displayName: String,
        mimeType: String,
        mediaType: MediaType
    ): Uri {
        val (collection, relativePath) = when (mediaType) {
            MediaType.MUSIC -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MUSIC}/SnapFetch"
            MediaType.PICTURE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_PICTURES}/SnapFetch"
            MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MOVIES}/SnapFetch"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val insertedUri = resolver.insert(collection, values)
            ?: return saveAppPrivateFallback(sourceFile, displayName)

        try {
            resolver.openOutputStream(insertedUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
            }
            val doneValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(insertedUri, doneValues, null, null)
            return insertedUri
        } catch (_: Throwable) {
            runCatching { resolver.delete(insertedUri, null, null) }
            return saveAppPrivateFallback(sourceFile, displayName)
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacyExternal(
        sourceFile: File,
        displayName: String,
        mimeType: String,
        mediaType: MediaType
    ): Uri {
        val baseDir = when (mediaType) {
            MediaType.MUSIC -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            MediaType.PICTURE -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            MediaType.VIDEO -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }
        val destDir = File(baseDir, "SnapFetch").apply { mkdirs() }
        val destFile = uniqueTargetFile(destDir, displayName)
        return try {
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
            val collection = when (mediaType) {
                MediaType.MUSIC -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                MediaType.PICTURE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, destFile.absolutePath)
                put(MediaStore.MediaColumns.DISPLAY_NAME, destFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            }
            context.contentResolver.insert(collection, values) ?: Uri.fromFile(destFile)
        } catch (_: Throwable) {
            saveAppPrivateFallback(sourceFile, displayName)
        }
    }

    private fun saveAppPrivateFallback(sourceFile: File, displayName: String): Uri {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads").apply { mkdirs() }
        val target = uniqueTargetFile(dir, displayName)
        sourceFile.copyTo(target, overwrite = true)
        return Uri.fromFile(target)
    }

    private fun uniqueTargetFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$counter$ext")
            counter++
        }
        return candidate
    }

    fun sanitizeFilename(raw: String): String =
        raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(120).ifEmpty { "snapfetch_media" }
}
