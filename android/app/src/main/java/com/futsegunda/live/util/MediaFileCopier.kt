package com.futsegunda.live.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Copia um vídeo escolhido (Uri do `PickVisualMedia`) pra um arquivo temporário,
 * sem redimensionar (diferente de PhotoResizer — vídeo sobe cru, o backend já
 * limita a 8MB em `upload_player_video`, api/api.php:481).
 */
object MediaFileCopier {
    fun copyVideoToCache(context: Context, source: Uri): File? {
        val mime = context.contentResolver.getType(source) ?: "video/mp4"
        val ext = when {
            mime.contains("webm") -> "webm"
            mime.contains("quicktime") || mime.contains("mov") -> "mov"
            else -> "mp4"
        }
        val outFile = File.createTempFile("player_video_", ".$ext", context.cacheDir)
        return try {
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            if (outFile.length() > 0) outFile else null
        } catch (e: Exception) {
            null
        }
    }
}
