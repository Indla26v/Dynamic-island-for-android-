package dev.indla.island.data.icons

import android.graphics.Bitmap
import android.util.LruCache
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory avatars and large icons extracted from notifications, keyed by package + person,
 * so the overlay never touches a `Notification` object. Nothing is written to disk.
 */
@Singleton
class IconStore @Inject constructor() {
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    operator fun get(key: String?): Bitmap? = key?.let(cache::get)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun contains(key: String) = cache.get(key) != null

    companion object {
        private const val MAX_BYTES = 4 * 1024 * 1024

        fun personKey(packageName: String, personKey: String) = "$packageName|$personKey"
    }
}
