package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.CalendarContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.model.AttachmentKind
import com.example.data.model.PendingAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

private const val MAX_INLINE_BYTES = 18 * 1024 * 1024 // Keep safely under 20MB inline limit

fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun shareText(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}

fun shareBitmap(context: Context, bitmap: Bitmap, title: String = "BD AI Artwork") {
    try {
        val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(sharedDir, "bd_ai_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Image"))
    } catch (_: Exception) {
    }
}

fun openEmailDraft(context: Context, subject: String, body: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        shareText(context, subject, body)
    }
}

fun openCalendarEventDraft(context: Context, title: String, description: String) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, title)
        putExtra(CalendarContract.Events.DESCRIPTION, description)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

fun openMapsSearch(context: Context, query: String) {
    val encoded = Uri.encode(query.ifBlank { "Dhaka, Bangladesh" })
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

suspend fun readBitmapFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val raw = BitmapFactory.decodeStream(stream) ?: return@use null
            val maxDim = 1536
            if (raw.width <= maxDim && raw.height <= maxDim) {
                raw
            } else {
                val scale = maxDim.toFloat() / maxOf(raw.width, raw.height)
                Bitmap.createScaledBitmap(
                    raw,
                    (raw.width * scale).toInt().coerceAtLeast(1),
                    (raw.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            }
        }
    } catch (_: Exception) {
        null
    }
}

suspend fun readPendingAttachment(
    context: Context,
    uri: Uri,
    forcedKind: AttachmentKind? = null
): Result<PendingAttachment> = withContext(Dispatchers.IO) {
    try {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = queryDisplayName(context, uri) ?: "attachment"

        val kind = forcedKind ?: when {
            mime.startsWith("image/") -> AttachmentKind.IMAGE
            mime.startsWith("video/") -> AttachmentKind.VIDEO
            else -> AttachmentKind.DOCUMENT
        }

        if (kind == AttachmentKind.IMAGE) {
            val bitmap = readBitmapFromUri(context, uri)
                ?: return@withContext Result.failure(IllegalStateException("Unable to decode selected image."))
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            return@withContext Result.success(
                PendingAttachment(
                    displayName = name,
                    mimeType = "image/jpeg",
                    kind = AttachmentKind.IMAGE,
                    bytes = baos.toByteArray(),
                    previewBitmap = bitmap
                )
            )
        }

        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read == -1) break
                total += read
                if (total > MAX_INLINE_BYTES) {
                    return@withContext Result.failure(
                        IllegalStateException("File is larger than 18MB. Please choose a smaller file.")
                    )
                }
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        } ?: return@withContext Result.failure(IllegalStateException("Unable to open selected file."))

        Result.success(
            PendingAttachment(
                displayName = name,
                mimeType = mime,
                kind = kind,
                bytes = bytes
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
    } catch (_: Exception) {
        null
    }
}
