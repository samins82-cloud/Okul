package com.elak.okulum.rehber

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.Executors

object FastPhotoLoader {
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3)
    private val memory = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(
        context: Context,
        token: String,
        studentId: Long,
        version: Long,
        image: ImageView,
        onLoaded: (() -> Unit)? = null
    ) {
        if (studentId <= 0L || token.isBlank()) return
        val key = "$studentId:$version"
        image.tag = key
        memory.get(key)?.let { bitmap ->
            image.setImageBitmap(bitmap)
            onLoaded?.invoke()
            return
        }

        pool.execute {
            val bytes = PhotoStore.get(context.applicationContext, token, studentId, version) ?: return@execute
            val bitmap = decode(bytes, 240) ?: return@execute
            memory.put(key, bitmap)
            main.post {
                if (image.tag == key) {
                    image.setImageBitmap(bitmap)
                    onLoaded?.invoke()
                }
            }
        }
    }

    fun remove(context: Context, studentId: Long) {
        val keys = memory.snapshot().keys.filter { it.startsWith("$studentId:") }
        keys.forEach { memory.remove(it) }
        PhotoStore.removeStudent(context, studentId)
    }

    fun clearMemory() = memory.evictAll()

    private fun decode(bytes: ByteArray, req: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > req * 2 || bounds.outHeight / sample > req * 2) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
