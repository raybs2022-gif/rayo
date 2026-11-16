package com.rayo.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.MotionEvent

/** Pantalla transparente que escucha tu voz y le devuelve el texto a Rayo. */
class VozActivity : Activity() {

    companion object {
        @Volatile
        var actual: VozActivity? = null

        fun cancelar() {
            actual?.cerrar()
        }
    }

    private var reco: SpeechRecognizer? = null
    private var terminado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previa = actual
        if (previa != null && previa !== this) previa.cerrar()
        actual = this
        val pedir = intent.getStringArrayExtra("pedir")
        if (pedir != null) {
            val faltan = pedir.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (faltan.isEmpty()) {
                OverlayService.instance?.enviarVoz("permisos:ok")
                cerrar()
            } else {
                requestPermissions(faltan.toTypedArray(), 8)
            }
            return
        }
        val soloPermiso = intent.getBooleanExtra("perm", false)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), if (soloPermiso) 9 else 7)
        } else if (soloPermiso) {
            OverlayService.instance?.wakeTrasPermiso()
            cerrar()
        } else {
            iniciar()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val ok = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        when (requestCode) {
            8 -> {
                OverlayService.instance?.enviarVoz(if (ok) "permisos:ok" else "permisos:no")
                cerrar()
            }
            9 -> {
                if (ok) OverlayService.instance?.wakeTrasPermiso() else OverlayService.instance?.estadoWake("wake:nomic")
                cerrar()
            }
            else -> {
                if (ok) {
                    iniciar()
                } else {
                    OverlayService.instance?.enviarVoz("nomic")
                    cerrar()
                }
            }
        }
    }

    private fun iniciar() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            OverlayService.instance?.enviarVoz("noreco")
            cerrar()
            return
        }
        val idioma = intent.getStringExtra("lang") ?: "es"
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        reco = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                OverlayService.instance?.enviarVoz("listening")
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                OverlayService.instance?.enviarVoz("error:$error")
                cerrar()
            }

            override fun onResults(results: Bundle?) {
                val texto = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                OverlayService.instance?.enviarVoz("final:$texto")
                cerrar()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val texto = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                OverlayService.instance?.enviarVoz("partial:$texto")
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (idioma == "en") "en-US" else "es-PE")
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        r.startListening(i)
    }

    fun cerrar() {
        if (terminado) return
        terminado = true
        try {
            reco?.cancel()
            reco?.destroy()
        } catch (e: Exception) {
        }
        reco = null
        finish()
    }

    // Tocar cualquier parte de la pantalla cancela la escucha
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            OverlayService.instance?.enviarVoz("cancel")
            cerrar()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDestroy() {
        if (actual === this) actual = null
        if (!terminado) {
            terminado = true
            try {
                reco?.destroy()
            } catch (e: Exception) {
            }
        }
        super.onDestroy()
    }
}
