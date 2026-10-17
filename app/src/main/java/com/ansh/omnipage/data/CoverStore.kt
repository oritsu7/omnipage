package com.ansh.omnipage.data

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// Cover thumbnails live in filesDir/covers as JPEGs named after the file fingerprint.
class CoverStore(context: Context) {
    private val dir = File(context.filesDir, "covers").apply { mkdirs() }

    fun save(key: String, bitmap: Bitmap): String? = try {
        val file = File(dir, "$key.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        file.absolutePath
    } catch (e: IOException) {
        null
    }

    fun delete(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }
}
