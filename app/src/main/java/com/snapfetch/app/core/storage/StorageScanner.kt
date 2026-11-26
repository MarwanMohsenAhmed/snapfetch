package com.snapfetch.app.core.storage

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.snapfetch.app.domain.model.LibraryMediaItem
import com.snapfetch.app.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class JunkSummary(
    val imageCacheBytes: Long,
    val orphanPartBytes: Long,
    val tempFilesBytes: Long
) {
    val totalBytes: Long get() = imageCacheBytes + orphanPartBytes + tempFilesBytes
}

data class LargeFileItem(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val isVideo: Boolean
)

class StorageScanner(private val context: Context) {

    val trashDir: File
        get() = File(context.filesDir, "trash").apply { mkdirs() }

    val vaultDir: File
        get() = File(context.filesDir, "vault").apply { mkdirs() }

    val partsDir: File
        get() = File(context.cacheDir, "parts").apply { mkdirs() }

    suspend fun scanDeviceMedia(): List<LibraryMediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<LibraryMediaItem>()
        items += queryMediaCollection(
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            mediaType = MediaType.VIDEO,
            hasDuration = true
        )
        items += queryMediaCollection(
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            mediaType = MediaType.MUSIC,
            hasDuration = true
        )
        items += queryMediaCollection(
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            mediaType = MediaType.PICTURE,
            hasDuration = false
        )
        items.sortedByDescending { it.dateAddedMs }
    }

    private fun queryMediaCollection(
        collection: Uri,
        mediaType: MediaType,
        hasDuration: Boolean
    ): List<LibraryMediaItem> {
        val out = mutableListOf<LibraryMediaItem>()
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            if (hasDuration) add(MediaStore.MediaColumns.DURATION)
        }.toTypedArray()

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val durCol = if (hasDuration) cursor.getColumnIndex(MediaStore.MediaColumns.DURATION) else -1
                var count = 0
                while (cursor.moveToNext() && count < 250) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Media"
                    val size = cursor.getLong(sizeCol)
                    val dateSec = cursor.getLong(dateCol)
                    val durMs = if (durCol >= 0) cursor.getLong(durCol) else 0L
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    out.add(
                        LibraryMediaItem(
                            mediaKey = contentUri.toString(),
                            title = name,
                            subtitle = mediaType.name.lowercase().replaceFirstChar { it.uppercase() },
                            uri = contentUri.toString(),
                            thumbnailModel = contentUri,
                            mediaType = mediaType,
                            durationSec = (durMs / 1000L).toInt(),
                            sizeBytes = size,
                            dateAddedMs = dateSec * 1000L,
                            isAppDownload = false
                        )
                    )
                    count++
                }
            }
        }
        return out
    }

    suspend fun scanJunk(activePartPaths: Set<String>): JunkSummary = withContext(Dispatchers.IO) {
        val imgBytes = folderSize(File(context.cacheDir, "image_cache"))
        val orphanBytes = partsDir.listFiles()?.sumOf { f ->
            if (f.absolutePath !in activePartPaths) f.length() else 0L
        } ?: 0L
        val tempBytes = File(context.cacheDir, "compress_tmp").listFiles()?.sumOf { it.length() } ?: 0L
        JunkSummary(imgBytes, orphanBytes, tempBytes)
    }

    suspend fun cleanJunk(activePartPaths: Set<String>): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        File(context.cacheDir, "image_cache").listFiles()?.forEach { f ->
            freed += deleteRecursive(f)
        }
        partsDir.listFiles()?.forEach { f ->
            if (f.absolutePath !in activePartPaths) {
                val len = f.length()
                if (f.delete()) freed += len
            }
        }
        File(context.cacheDir, "compress_tmp").listFiles()?.forEach { f ->
            freed += deleteRecursive(f)
        }
        freed
    }

    suspend fun scanLargeFiles(minBytes: Long = 50L * 1024 * 1024): List<LargeFileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<LargeFileItem>()
        val collections = listOf(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to true,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to false
        )
        for ((uri, isVideo) in collections) {
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.SIZE
                    ),
                    "${MediaStore.MediaColumns.SIZE} >= ?",
                    arrayOf(minBytes.toString()),
                    "${MediaStore.MediaColumns.SIZE} DESC"
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    while (c.moveToNext()) {
                        results.add(
                            LargeFileItem(
                                uri = ContentUris.withAppendedId(uri, c.getLong(idCol)),
                                name = c.getString(nameCol) ?: "Large file",
                                sizeBytes = c.getLong(sizeCol),
                                isVideo = isVideo
                            )
                        )
                    }
                }
            }
        }
        results.sortedByDescending { it.sizeBytes }
    }

    fun moveUriToTrash(uriString: String, title: String): Boolean {
        return runCatching {
            val uri = Uri.parse(uriString)
            val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifEmpty { "deleted_item" }
            val dest = File(trashDir, "${System.currentTimeMillis()}_$safe")
            if (uri.scheme == "file") {
                val src = File(uri.path ?: return false)
                if (src.exists()) {
                    src.copyTo(dest, overwrite = true)
                    src.delete()
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
            true
        }.getOrDefault(false)
    }

    fun listTrashFiles(): List<File> = trashDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun listVaultFiles(): List<File> = vaultDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    private fun folderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { folderSize(it) } ?: 0L
    }

    private fun deleteRecursive(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) {
            val len = file.length()
            return if (file.delete()) len else 0L
        }
        var total = 0L
        file.listFiles()?.forEach { total += deleteRecursive(it) }
        file.delete()
        return total
    }
}
