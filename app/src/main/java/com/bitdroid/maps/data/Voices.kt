package com.bitdroid.maps.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** A natural-sounding Piper voice (runs fully on the phone through sherpa-onnx). */
data class VoiceInfo(
    val id: String,
    val name: String,
    val flag: String,
    val emoji: String,
    val description: String,
    /** sherpa-onnx packaged model name, e.g. vits-piper-en_US-amy-medium */
    val pack: String,
    /** Shipped inside the APK (no download needed). */
    val bundled: Boolean = false,
) {
    val url get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$pack.tar.bz2"
}

/** Paths sherpa-onnx needs for one installed voice. */
data class VoiceFiles(val model: String, val tokens: String, val dataDir: String)

object Voices {
    const val SYSTEM = "system"

    val all = listOf(
        VoiceInfo("amy", "Amy", "🇺🇸", "👩🏻", "Warm and clear", "vits-piper-en_US-amy-medium", bundled = true),
        VoiceInfo("kristin", "Kristin", "🇺🇸", "👩🏼", "Bright and friendly", "vits-piper-en_US-kristin-medium"),
        VoiceInfo("jenny", "Jenny", "🇬🇧", "👩🏽", "British", "vits-piper-en_GB-jenny_dioco-medium"),
        VoiceInfo("ryan", "Ryan", "🇺🇸", "👨🏻", "Guy voice", "vits-piper-en_US-ryan-medium"),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

/** Installs voices: copies the bundled one out of the APK, downloads the rest. */
class VoiceStore(private val context: Context) {
    /** Download / unpack progress per voice id, 0..1. Missing = idle. */
    val progress = mutableStateMapOf<String, Float>()
    var error by mutableStateOf<String?>(null)

    private fun dir(id: String) = File(context.filesDir, "voices/$id")

    fun isReady(id: String): Boolean = File(dir(id), ".ready").exists()

    fun files(id: String): VoiceFiles? {
        val d = dir(id)
        if (!isReady(id)) return null
        val model = d.listFiles()?.firstOrNull { it.name.endsWith(".onnx") } ?: return null
        return VoiceFiles(model.absolutePath, File(d, "tokens.txt").absolutePath, File(d, "espeak-ng-data").absolutePath)
    }

    /** Makes the voice usable. Returns true when it's ready. */
    suspend fun install(v: VoiceInfo): Boolean = withContext(Dispatchers.IO) {
        if (isReady(v.id)) return@withContext true
        if (progress.containsKey(v.id)) return@withContext false
        progress[v.id] = 0f
        error = null
        val target = dir(v.id)
        try {
            target.deleteRecursively()
            target.mkdirs()
            if (v.bundled && copyAsset("voices/${v.id}", target)) {
                // copied from the APK
            } else {
                download(v, target)
            }
            File(target, ".ready").writeText("ok")
            true
        } catch (e: Exception) {
            target.deleteRecursively()
            error = "Couldn't get ${v.name}. Check your connection and try again."
            false
        } finally {
            progress.remove(v.id)
        }
    }

    fun delete(id: String) {
        val v = Voices.byId(id) ?: return
        if (!v.bundled) dir(id).deleteRecursively()
    }

    private fun copyAsset(path: String, to: File): Boolean {
        val am = context.assets
        val children = am.list(path) ?: return false
        if (children.isEmpty()) return false
        fun copy(p: String, dst: File) {
            val list = am.list(p) ?: emptyArray()
            if (list.isEmpty()) {
                dst.parentFile?.mkdirs()
                am.open(p).use { input -> dst.outputStream().use { input.copyTo(it) } }
            } else {
                dst.mkdirs()
                list.forEach { copy("$p/$it", File(dst, it)) }
            }
        }
        children.forEach { copy("$path/$it", File(to, it)) }
        return true
    }

    private fun download(v: VoiceInfo, to: File) {
        Http.client.newCall(Request.Builder().url(v.url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("empty")
            val total = body.contentLength().toFloat()
            var read = 0L
            val counting = object : FilterInputStream(body.byteStream()) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = super.read(b, off, len)
                    if (n > 0) {
                        read += n
                        if (total > 0) progress[v.id] = (read / total).coerceIn(0f, 0.99f)
                    }
                    return n
                }
            }
            untar(BZip2CompressorInputStream(BufferedInputStream(counting)), to)
        }
    }

    /** Unpacks a tar, dropping the archive's top folder (vits-piper-…/). */
    private fun untar(input: InputStream, to: File) {
        TarArchiveInputStream(input).use { tar ->
            val root = to.canonicalPath
            while (true) {
                val e = tar.nextEntry ?: break
                val rel = e.name.substringAfter('/', "")
                if (rel.isBlank()) continue
                val out = File(to, rel)
                if (!out.canonicalPath.startsWith(root)) continue
                if (e.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { tar.copyTo(it) }
                }
            }
        }
    }
}
