package com.ansh.omnipage.ui.common

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.ansh.omnipage.data.bookFormat
import com.ansh.omnipage.data.db.BookEntity
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> "${(bytes / 1024.0).roundToInt()} KB"
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    else -> String.format(Locale.US, "%.2f GB", bytes / 1073741824.0)
}

fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

fun shareBook(context: Context, book: BookEntity) {
    val uri = Uri.parse(book.uri)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = book.bookFormat?.mime ?: "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, book.title)
        clipData = ClipData.newRawUri(book.title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, "Share ${book.title}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't share this file", Toast.LENGTH_SHORT).show()
    }
}
