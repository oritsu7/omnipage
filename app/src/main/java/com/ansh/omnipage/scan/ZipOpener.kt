package com.ansh.omnipage.scan

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

internal class ZipHandle(
    val zip: ZipFile,
    private val pfd: ParcelFileDescriptor?,
    private val temp: File?,
) : Closeable {
    override fun close() {
        runCatching { zip.close() }
        runCatching { pfd?.close() }
        temp?.delete()
    }
}

/**
 Opens a content:// document as a random-access ZipFile.
 Fast path: /proc/self/fd/N (works when the provider hands out a real file descriptor).
 Fallback: copy to a cache temp file (deleted on close).
*/
internal object ZipOpener {
    fun open(context: Context, uri: Uri): ZipHandle {
        val resolver = context.contentResolver
        val pfd = resolver.openFileDescriptor(uri, "r") ?: throw IOException("Cannot open $uri")
        try {
            return ZipHandle(ZipFile("/proc/self/fd/${pfd.fd}"), pfd, null)
        } catch (e: Exception) {
            runCatching { pfd.close() }
        }

        val temp = File.createTempFile("zip", ".tmp", context.cacheDir)
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Cannot read $uri")
            input.use { src -> temp.outputStream().use { dst -> src.copyTo(dst) } }
            return ZipHandle(ZipFile(temp), null, temp)
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }
}
