package com.mineserve.mobile.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import com.mineserve.mobile.data.BoundedInput
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Small shared memory cache; no disk cache, and at most two icon requests at a time. */
internal object AddonIconLoader {
    private const val MAX_DOWNLOAD_BYTES = 2 * 1024 * 1024
    private const val MAX_CACHE_BYTES = 2 * 1024 * 1024
    private const val FAILURE_COOLDOWN_MS = 60_000L
    private val slots = Semaphore(2)
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val failures = LruCache<String, Long>(64)

    suspend fun load(url: String): Bitmap? {
        if (url.isBlank()) return null
        cache.get(url)?.let { return it }
        val failedAt = failures.get(url)
        if (failedAt != null && SystemClock.elapsedRealtime() - failedAt < FAILURE_COOLDOWN_MS) return null
        return slots.withPermit {
            // Another visible row may have finished the same icon while we waited.
            cache.get(url)?.let { return@withPermit it }
            val recentFailure = failures.get(url)
            if (recentFailure != null && SystemClock.elapsedRealtime() - recentFailure < FAILURE_COOLDOWN_MS) return@withPermit null
            val bitmap = withContext(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                try {
                    coroutineContext.ensureActive()
                    val address = URL(url)
                    if (address.protocol != "https") return@withContext null
                    connection = address.openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("User-Agent", "MineHost/2.1")
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                    if (connection.contentLengthLong > MAX_DOWNLOAD_BYTES) return@withContext null
                    val context = coroutineContext
                    val bytes = connection.inputStream.use { input ->
                        BoundedInput.read(input, MAX_DOWNLOAD_BYTES) { context.ensureActive() }
                    }
                    context.ensureActive()
                    decode(bytes)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                } finally {
                    connection?.disconnect()
                }
            }
            if (bitmap != null) {
                cache.put(url, bitmap)
                failures.remove(url)
            } else {
                failures.put(url, SystemClock.elapsedRealtime())
            }
            bitmap
        }
    }

    suspend fun loadPlayerHead(playerName: String): Bitmap? {
        val clean = playerName.trim().removePrefix(".").replace(Regex("[^a-zA-Z0-9_]"), "")
        if (clean.isBlank()) return null
        val primaryUrl = "https://mc-heads.net/avatar/$clean/64"
        load(primaryUrl)?.let { return it }
        val fallbackUrl = "https://minotar.net/helm/$clean/64.png"
        return load(fallbackUrl)
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            bounds.outWidth > 16384 || bounds.outHeight > 16384) return null
        var sample = 1
        while (bounds.outWidth / sample > 96 || bounds.outHeight / sample > 96) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }
}
