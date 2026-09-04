package com.trispeech.app.export

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.trispeech.app.data.TranscriptEntity
import java.io.File
import java.io.FileOutputStream

object TranscriptExporter {
    fun text(context: Context, transcript: TranscriptEntity): File = destination(context, transcript, "txt").also { it.writeText(transcript.text) }

    fun subtitles(context: Context, transcript: TranscriptEntity): File = destination(context, transcript, "srt").also { file ->
        file.writeText("1\n00:00:00,000 --> ${srtTime(transcript.durationMs.coerceAtLeast(1_000))}\n${transcript.text}\n")
    }

    fun pdf(context: Context, transcript: TranscriptEntity): File {
        val file = destination(context, transcript, "pdf")
        val document = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; isFakeBoldText = true }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f }
        val width = 595; val height = 842; val margin = 48f; val lineHeight = 19f
        val maxChars = 78
        val lines = transcript.text.split(Regex("\\s+")).fold(mutableListOf<String>()) { acc, word ->
            if (acc.isEmpty() || acc.last().length + word.length + 1 > maxChars) acc.add(word) else acc[acc.lastIndex] += " $word"; acc
        }
        var pageNumber = 1; var lineIndex = 0
        while (lineIndex < lines.size || pageNumber == 1) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
            var y = margin
            if (pageNumber == 1) { page.canvas.drawText(transcript.title, margin, y, titlePaint); y += 40f }
            while (lineIndex < lines.size && y < height - margin) { page.canvas.drawText(lines[lineIndex++], margin, y, bodyPaint); y += lineHeight }
            document.finishPage(page); pageNumber++
        }
        FileOutputStream(file).use(document::writeTo); document.close(); return file
    }

    fun uri(context: Context, file: File) = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    private fun destination(context: Context, transcript: TranscriptEntity, extension: String): File {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = transcript.title.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "transcript" }
        return File(directory, "$safe.$extension")
    }

    private fun srtTime(milliseconds: Long): String {
        val hours = milliseconds / 3_600_000
        val minutes = milliseconds / 60_000 % 60
        val seconds = milliseconds / 1_000 % 60
        val millis = milliseconds % 1_000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }
}
