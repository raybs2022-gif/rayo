package com.rayo.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Escucha siempre, sin internet, solo para detectar «Oye Rayo».
 * Usa un modelo pequeño de Vosk que se descarga una sola vez (unos 40 MB).
 */
class WakeEngine(
    private val ctx: Context,
    private val alEstado: (String) -> Unit,
    private val alDespertar: () -> Unit
) {
    companion object {
        const val MODELO = "vosk-model-small-es-0.42"
        const val URL_MODELO = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
        const val GRAMATICA = "[\"oye rayo\", \"oiga rayo\", \"[unk]\"]"
    }

    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var service: SpeechService? = null

    @Volatile
    private var querido = false

    @Volatile
    private var cargando = false

    @Volatile
    private var disparado = false

    var corriendo = false
        private set

    fun iniciar() {
        querido = true
        if (corriendo || cargando) return
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            alEstado("wake:nomic")
            return
        }
        if (model == null) {
            prepararModelo()
        } else {
            arrancar()
        }
    }

    fun detener() {
        querido = false
        corriendo = false
        try {
            service?.stop()
        } catch (e: Exception) {
        }
        try {
            service?.shutdown()
        } catch (e: Exception) {
        }
        try {
            recognizer?.close()
        } catch (e: Exception) {
        }
        service = null
        recognizer = null
    }

    private fun prepararModelo() {
        cargando = true
        Thread {
            try {
                val base = File(ctx.filesDir, "vosk")
                val dir = File(base, MODELO)
                if (!File(dir, "listo").exists()) {
                    descargar(base)
                }
                main.post { alEstado("wake:loading") }
                val m = Model(dir.absolutePath)
                main.post {
                    model = m
                    cargando = false
                    if (querido) arrancar()
                }
            } catch (e: Throwable) {
                main.post {
                    cargando = false
                    alEstado("wake:error:" + (e.message ?: "desconocido"))
                }
            }
        }.start()
    }

    private fun descargar(base: File) {
        base.mkdirs()
        val conn = URL(URL_MODELO).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true
        if (conn.responseCode != 200) throw Exception("descarga " + conn.responseCode)
        val total = conn.contentLength.toLong()
        var ultimo = -1
        val raiz = base.canonicalPath
        ZipInputStream(Contador(conn.inputStream) { leido ->
            if (total > 0) {
                val pct = (leido * 100 / total).toInt()
                if (pct != ultimo) {
                    ultimo = pct
                    main.post { alEstado("wake:download:$pct") }
                }
            }
        }).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val out = File(base, e.name)
                if (!out.canonicalPath.startsWith(raiz)) throw Exception("zip inválido")
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { fos -> zin.copyTo(fos) }
                }
                zin.closeEntry()
                e = zin.nextEntry
            }
        }
        File(base, "$MODELO/listo").writeText("ok")
    }

    private fun arrancar() {
        try {
            val m = model ?: return
            val rec = Recognizer(m, 16000.0f, GRAMATICA)
            val svc = SpeechService(rec, 16000.0f)
            recognizer = rec
            service = svc
            disparado = false
            svc.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    revisar(hypothesis)
                }

                override fun onResult(hypothesis: String?) {
                    revisar(hypothesis)
                }

                override fun onFinalResult(hypothesis: String?) {
                    revisar(hypothesis)
                }

                override fun onError(exception: Exception?) {
                    main.post { alEstado("wake:error:" + (exception?.message ?: "audio")) }
                }

                override fun onTimeout() {}
            })
            corriendo = true
            alEstado("wake:on")
        } catch (e: Exception) {
            corriendo = false
            alEstado("wake:error:" + (e.message ?: "micrófono"))
        }
    }

    private fun revisar(h: String?) {
        if (h == null || disparado) return
        var crudo = ""
        try {
            val j = JSONObject(h)
            crudo = j.optString("partial", j.optString("text", ""))
        } catch (e: Exception) {
        }
        val texto = crudo.lowercase()
        if (texto.contains("rayo") && (texto.contains("oye") || texto.contains("oiga"))) {
            disparado = true
            main.post { alDespertar() }
        }
    }

    private class Contador(entrada: InputStream, val alAvanzar: (Long) -> Unit) : FilterInputStream(entrada) {
        private var total = 0L

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) {
                total++
                alAvanzar(total)
            }
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) {
                total += n
                alAvanzar(total)
            }
            return n
        }
    }
}
