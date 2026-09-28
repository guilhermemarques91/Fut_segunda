package com.futsegunda.live.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Port do resize client-side do painel web (handlePlayerPhotoSelect,
 * frontend/index.html:2029-2062): reduz pro lado maior ≤256px e recomprime
 * JPEG qualidade 0.85. Lá vira data-URI inline no JSON; aqui vira um
 * arquivo de verdade que sobe via upload_player_photo (Fase 1 já migra
 * fotos de data-URI pra upload real, ver plano).
 */
object PhotoResizer {
    private const val MAX_SIDE = 256
    private const val JPEG_QUALITY = 85

    /** Devolve um arquivo JPEG temporário (cache dir) já redimensionado, ou null se falhar. */
    fun resizeToJpeg(context: Context, source: Uri): File? {
        val original = context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it) }
            ?: return null
        val scale = min(1f, MAX_SIDE.toFloat() / maxOf(original.width, original.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(original, (original.width * scale).toInt().coerceAtLeast(1), (original.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            original
        }
        val outFile = File.createTempFile("player_photo_", ".jpg", context.cacheDir)
        FileOutputStream(outFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        if (scaled !== original) scaled.recycle()
        original.recycle()
        return outFile
    }
}
