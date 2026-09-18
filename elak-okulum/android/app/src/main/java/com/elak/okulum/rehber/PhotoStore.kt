package com.elak.okulum.rehber

import android.content.Context
import java.io.File

object PhotoStore {
    private fun dir(context: Context): File =
        File(context.filesDir, "rehber_photos").apply { if (!exists()) mkdirs() }

    private fun file(context: Context, studentId: Long, version: Long): File =
        File(dir(context), studentId.toString() + "_" + version.toString() + ".img")

    fun get(context: Context, token: String, studentId: Long, version: Long): ByteArray? {
        if (studentId <= 0) return null
        val target = file(context, studentId, version)
        if (target.exists() && target.length() > 0) {
            val freshEnough = version > 0L || (System.currentTimeMillis() - target.lastModified()) < 60L * 60L * 1000L
            if (freshEnough) return try { target.readBytes() } catch (_: Exception) { null }
        }
        val bytes = RehberApi.photoBytes(token, studentId, version) ?: return null
        return try {
            removeStudent(context, studentId)
            target.writeBytes(bytes)
            bytes
        } catch (_: Exception) {
            bytes
        }
    }

    fun removeStudent(context: Context, studentId: Long) {
        dir(context).listFiles()?.filter { it.name.startsWith(studentId.toString() + "_") }?.forEach {
            try { it.delete() } catch (_: Exception) {}
        }
    }

    fun invalidateUnversioned(context: Context) {
        dir(context).listFiles()?.filter { it.name.endsWith("_0.img") }?.forEach {
            try { it.delete() } catch (_: Exception) {}
        }
    }

    fun trim(context: Context, maxFiles: Int = 300) {
        val files = dir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(maxFiles).forEach { try { it.delete() } catch (_: Exception) {} }
    }
}
