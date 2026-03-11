package com.thunderpass.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.thunderpass.ui.diceBearUrl
import java.io.File
import java.net.URL

/**
 * Builds a DiceBear PNG URL from the same seed used in the app's SVG avatars.
 * Glance widgets cannot render SVG, so we swap the path to /png.
 */
internal fun diceBearPngUrl(seed: String): String =
    diceBearUrl(seed, transparent = false).replace("/svg?", "/png?")

private fun avatarCacheFile(context: Context, seed: String): File =
    File(context.cacheDir, "tp_avatar_${seed.filter(Char::isLetterOrDigit).take(40)}.png")

/**
 * Downloads the DiceBear avatar as a [Bitmap], with an optional 24-hour disk cache.
 * When [context] is provided, the decoded PNG is stored in [Context.cacheDir] and
 * returned from disk on subsequent calls — eliminating the network round-trip on
 * every widget update (which was the main cause of sluggish toggle response).
 * Returns `null` on any failure (no network, timeout, bad response).
 * Must be called from a background / IO thread.
 */
internal fun loadAvatarBitmap(seed: String, context: Context? = null): Bitmap? {
    // Return from disk cache if still fresh (< 24 h old)
    if (context != null) {
        val cached = avatarCacheFile(context, seed)
        if (cached.exists() && System.currentTimeMillis() - cached.lastModified() < 86_400_000L) {
            BitmapFactory.decodeFile(cached.absolutePath)?.let { return it }
        }
    }

    // Download from network
    val bitmap = runCatching {
        val url = URL(diceBearPngUrl(seed))
        val conn = url.openConnection().apply {
            connectTimeout = 4_000
            readTimeout    = 4_000
        }
        conn.getInputStream().use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    // Persist to disk so the next update skips the network
    if (bitmap != null && context != null) {
        runCatching {
            avatarCacheFile(context, seed)
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    return bitmap
}
