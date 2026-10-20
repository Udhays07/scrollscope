package com.scrollscope.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File

object Sharing {
    private fun uriFor(ctx: Context, file: File) =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)

    fun shareImage(ctx: Context, bitmap: Bitmap) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "scrollscope-today.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uriFor(ctx, file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Share today").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareText(ctx: Context, file: File) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uriFor(ctx, file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Share dump").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun dumpsDir(ctx: Context) = File(ctx.filesDir, "dumps").apply { mkdirs() }
}
