package dev.indla.island.ui.island

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import dev.indla.island.data.icons.IconStore

/** An app's launcher icon, loaded and memory-cached by Coil, keyed by package. */
data class AppIcon(val packageName: String)

private class AppIconFetcher(private val data: AppIcon, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val drawable = options.context.packageManager.getApplicationIcon(data.packageName)
        return ImageFetchResult(image = drawable.asImage(), isSampled = false, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<AppIcon> {
        override fun create(data: AppIcon, options: Options, imageLoader: ImageLoader): Fetcher =
            AppIconFetcher(data, options)
    }
}

private class AppIconKeyer : Keyer<AppIcon> {
    override fun key(data: AppIcon, options: Options) = "app-icon:${data.packageName}"
}

object IslandImages {
    @Volatile private var loader: ImageLoader? = null

    fun loader(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext)
            .components {
                add(AppIconFetcher.Factory())
                add(AppIconKeyer())
            }
            .build()
            .also { loader = it }
    }
}

/** Gives the island access to avatars the listener cached. Null in previews. */
val LocalIconStore = staticCompositionLocalOf<IconStore?> { null }

/** A sender avatar from the icon store, falling back to the app icon. */
@Composable
fun Avatar(iconKey: String?, packageName: String, size: Dp, modifier: Modifier = Modifier) {
    val store = LocalIconStore.current
    val bitmap = remember(iconKey, store) { store?.get(iconKey)?.asImageBitmap() }
    val shaped = modifier.size(size).clip(CircleShape)
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = shaped, contentScale = ContentScale.Crop)
    } else {
        AppIconImage(packageName, size, modifier)
    }
}

@Composable
fun AppIconImage(packageName: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.size(size)) {
        AsyncImage(
            model = AppIcon(packageName),
            contentDescription = null,
            imageLoader = remember(context) { IslandImages.loader(context) },
            modifier = Modifier.size(size).clip(CircleShape).background(Color(0x33FFFFFF)),
        )
    }
}
