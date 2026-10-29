package com.niramoy.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * On-device OCR with Tesseract (Apache-2.0) using English + Bangla "fast" models.
 * Why Tesseract: free, open source, offline, official Bengali model; PaddleOCR has no maintained
 * Bangla model and ML Kit is neither open source nor Bangla-capable.
 *
 * Models: bundled in assets/tessdata if present (scripts/fetch-tessdata.ps1), otherwise downloaded
 * once from the official tessdata_fast repo (~5 MB) and kept in app storage.
 */
object OcrEngine {
    private const val TAG = "OcrEngine"
    private val LANGS = listOf("eng", "ben")
    private const val BASE_URL = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/"

    private fun dataDir(context: Context) = File(context.filesDir, "tesseract")
    private fun modelFile(context: Context, lang: String) = File(dataDir(context), "tessdata/$lang.traineddata")

    fun modelsReady(context: Context) = LANGS.all { modelFile(context, it).let { f -> f.exists() && f.length() > 100_000 } }

    /** Copies bundled models or downloads them. Returns false when offline and nothing is bundled. */
    suspend fun ensureModels(context: Context): Boolean = withContext(Dispatchers.IO) {
        for (lang in LANGS) {
            val f = modelFile(context, lang)
            if (f.exists() && f.length() > 100_000) continue
            f.parentFile?.mkdirs()
            val tmp = File(f.path + ".part")
            val copied = runCatching {
                context.assets.open("tessdata/$lang.traineddata").use { i -> tmp.outputStream().use { i.copyTo(it) } }
                true
            }.getOrDefault(false)
            val ok = copied || runCatching {
                val c = URL(BASE_URL + "$lang.traineddata").openConnection() as HttpURLConnection
                c.instanceFollowRedirects = true
                c.connectTimeout = 20_000; c.readTimeout = 60_000
                c.inputStream.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                c.disconnect()
                tmp.length() > 100_000
            }.getOrElse { Log.w(TAG, "model download failed: ${it.message}"); false }
            if (!ok || !tmp.renameTo(f)) { tmp.delete(); return@withContext false }
        }
        true
    }

    /** Grayscale + contrast boost: helps on phone photos of paper. */
    private fun preprocess(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.5f
        val t = (-0.5f * contrast + 0.5f) * 255f
        cm.postConcat(ColorMatrix(floatArrayOf(contrast, 0f, 0f, 0f, t, 0f, contrast, 0f, 0f, t, 0f, 0f, contrast, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    suspend fun recognize(context: Context, bitmap: Bitmap): String? = withContext(Dispatchers.Default) {
        if (!ensureModels(context)) return@withContext null
        val api = TessBaseAPI()
        try {
            if (!api.init(dataDir(context).absolutePath, LANGS.joinToString("+"))) return@withContext null
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            val img = preprocess(bitmap)
            api.setImage(img)
            val text = api.utF8Text
            img.recycle()
            text
        } catch (e: Exception) {
            Log.e(TAG, "ocr failed", e); null
        } finally {
            api.recycle()
        }
    }
}
