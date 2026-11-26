package com.snapfetch.app.core.perf

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import okhttp3.OkHttpClient

class CoilLowMemFactory(
    private val lowRamConfig: LowRamConfig,
    private val okHttpClient: OkHttpClient
) : SingletonImageLoader.Factory {

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(lowRamConfig.coilMemoryCacheBytes)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(lowRamConfig.coilDiskCacheBytes)
                    .build()
            }
            .crossfade(!lowRamConfig.isLowRamDevice)
            .build()
    }
}

@Composable
fun SnapThumbnail(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    targetWidthPx: Int = 320,
    preferRgb565: Boolean = true
) {
    val context = LocalContext.current
    val request = remember(model, targetWidthPx, preferRgb565) {
        ImageRequest.Builder(context)
            .data(model)
            .size(targetWidthPx)
            .apply {
                if (preferRgb565) {
                    bitmapConfig(Bitmap.Config.RGB_565)
                }
            }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
