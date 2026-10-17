package com.ansh.omnipage.scan

import android.content.ContentResolver
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Cheap content fingerprint: SHA-1 over (size, first 64 KB, last 64 KB).
 * Fast enough to run over thousands of files, strong enough to spot duplicates and moved files.
 */
internal object Fingerprint {
    private const val CHUNK = 64 * 1024

    fun compute(resolver: ContentResolver, uri: Uri, size: Long): String? = try {
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { stream ->
                val channel = stream.channel
                val digest = MessageDigest.getInstance("SHA-1")
                digest.update(size.toString().toByteArray())
                val buffer = ByteBuffer.allocate(CHUNK)

                fun readAt(position: Long) {
                    buffer.clear()
                    channel.position(position)
                    val n = channel.read(buffer)
                    if (n > 0) digest.update(buffer.array(), 0, n)
                }

                readAt(0)
                if (size > CHUNK * 2L) readAt(size - CHUNK) else if (size > CHUNK) readAt(CHUNK.toLong())
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }
    } catch (e: Exception) {
        null
    }
}
