package com.vin.browser.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import android.webkit.WebView

object TabThumbnailManager {

    // 20MB max memory cache for tab previews
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtMost(20 * 1024)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun getThumbnail(tabId: String): Bitmap? {
        return memoryCache.get(tabId)
    }

    fun setThumbnail(tabId: String, bitmap: Bitmap) {
        memoryCache.put(tabId, bitmap)
    }

    fun removeThumbnail(tabId: String) {
        memoryCache.remove(tabId)
    }

    fun clearAll() {
        memoryCache.evictAll()
    }

    fun captureFromWebView(tabId: String, webView: WebView) {
        try {
            val width = webView.width
            val height = webView.height
            if (width <= 0 || height <= 0) return

            // Scaled 16:10 thumbnail (approx 360 x 225 px)
            val targetWidth = 360
            val targetHeight = 225
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)

            val scaleX = targetWidth.toFloat() / width.toFloat()
            val scaleY = targetHeight.toFloat() / height.toFloat()
            canvas.scale(scaleX, scaleY)

            webView.draw(canvas)
            setThumbnail(tabId, bitmap)
        } catch (_: Exception) {}
    }
}