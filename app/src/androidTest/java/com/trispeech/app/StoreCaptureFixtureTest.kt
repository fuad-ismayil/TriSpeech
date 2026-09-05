package com.trispeech.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trispeech.app.data.TranscriptEntity
import com.trispeech.app.data.TriSpeechDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Sample content is written only into the separately installed storepreview package. */
@RunWith(AndroidJUnit4::class)
class StoreCaptureFixtureTest {
    @Test fun prepareStoreScreens() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".storepreview"))
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putBoolean("dark_theme", true).commit()
        context.getSharedPreferences("development_preview", Context.MODE_PRIVATE).edit().putBoolean("hide_notice", true).commit()
        context.getSharedPreferences("app_locale", Context.MODE_PRIVATE).edit().putString("selected_language", "en").commit()
        val db = TriSpeechDatabase.get(context)
        val examples = listOf(
            Triple("Project briefing", "Today we agreed on three priorities for the next release. First, make the recording flow easier to use. Second, keep important ideas easy to find. Third, give everyone a clear way to share the finished transcript.\n\nThe design review is on Thursday. Bring one example of a small improvement that would make a big difference. We will choose the next steps together.", 146000L),
            Triple("Ideas on the go", "A thought for the next workshop: start with a question, leave room for silence, and capture the ideas while they are still fresh. The best notes are the ones we can return to later.", 78000L),
            Triple("Language practice", "Read the paragraph once at a natural pace. Listen for the words that need more attention, then try again. A little practice every day makes a difference.", 94000L)
        )
        for ((index, sample) in examples.withIndex()) {
            val operation = "store-sample-$index"
            if (db.transcriptDao().getByOperationId(operation) == null) {
                db.transcriptDao().insert(TranscriptEntity(title = sample.first, text = sample.second,
                    languageCode = "en", engine = "NATIVE_STT", model = "BEST_BALANCE",
                    createdAtEpochMs = 1788598800000L - index * 3600000L, durationMs = sample.third, operationId = operation))
            }
        }
        // Render the existing vector icon without changing its design.
        val icon = ContextCompat.getDrawable(context, R.drawable.ic_launcher)!!
        val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 1024, 1024)
        icon.draw(Canvas(bitmap))
        File(context.getExternalFilesDir(null), "store-icon.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        // Compose a native vector/text feature banner at Uptodown's exact dimensions.
        val banner = Bitmap.createBitmap(1024, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(banner)
        canvas.drawColor(Color.BLACK)
        val type = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textSize = 25f
        }
        canvas.drawText("TriSpeech", 64f, 83f, type)
        type.textSize = 58f
        canvas.drawText("Your words.", 64f, 220f, type)
        canvas.drawText("Within reach.", 64f, 292f, type)
        type.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        type.textSize = 24f
        type.color = Color.LTGRAY
        canvas.drawText("Record. Transcribe. Keep.", 64f, 356f, type)
        type.textSize = 16f
        type.color = Color.GRAY
        canvas.drawText("NATIVE STT   /   OFFLINE WHISPER   /   ONLINE", 64f, 440f, type)
        icon.setBounds(654, 98, 976, 420)
        icon.draw(canvas)
        File(context.getExternalFilesDir(null), "store-feature.png").outputStream().use {
            banner.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        Unit
    }
}
