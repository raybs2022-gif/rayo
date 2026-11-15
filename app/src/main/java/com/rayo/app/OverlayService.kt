package com.rayo.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.rayo.app.STOP"
        const val CHANNEL = "rayo"
        private const val FLAGS_COLLAPSED =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        private const val FLAGS_EXPANDED =
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    }

    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private var view: RayoWebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var anim: ValueAnimator? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var hablaIngles = false

    private var expanded = false
    private var roamOn = true
    private var dragging = false
    private var savedX = 0
    private var savedY = 0
    private var sw = 0
    private var sh = 0
    private var d = 1f

    private val roamRunnable = Runnable { roamStep() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (view == null) crearOverlay()
        return START_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Rayo", NotificationManager.IMPORTANCE_LOW)
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Cerrar Rayo",
            stopIntent
        ).build()
        val notif = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Rayo está activo")
            .setContentText("Toca «Cerrar Rayo» para quitarlo")
            .addAction(action)
            .setOngoing(true)
            .build()
        startForeground(1, notif)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun crearOverlay() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        d = dm.density
        sw = dm.widthPixels
        sh = dm.heightPixels
        val size = (132 * d).toInt()
        lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            FLAGS_COLLAPSED,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = sw - size - (8 * d).toInt()
        lp.y = sh - size - (140 * d).toInt()

        val v = RayoWebView(this)
        v.setBackgroundColor(Color.TRANSPARENT)
        v.settings.javaScriptEnabled = true
        v.settings.domStorageEnabled = true
        v.settings.mediaPlaybackRequiresUserGesture = false
        @Suppress("DEPRECATION")
        v.settings.allowUniversalAccessFromFileURLs = true
        @Suppress("DEPRECATION")
        v.settings.allowFileAccessFromFileURLs = true
        v.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
        }
        v.addJavascriptInterface(Bridge(), "Android")
        v.loadUrl("file:///android_asset/index.html")
        view = v
        wm.addView(v, lp)
        iniciarVoz()
        scheduleRoam(3000)
    }

    // ---------- Voz de Rayo (texto a voz del sistema) ----------
    private fun usarEspanol(t: TextToSpeech) {
        var r = t.setLanguage(Locale("es", "PE"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            r = t.setLanguage(Locale("es", "ES"))
        }
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale("es"))
        }
    }

    private fun iniciarVoz() {
        tts = TextToSpeech(applicationContext) { status ->
            val t = tts
            if (status == TextToSpeech.SUCCESS && t != null) {
                usarEspanol(t)
                t.setSpeechRate(1.05f)
                ttsReady = true
            }
        }
    }

    // ---------- Vista: mientras está colapsada, el toque se maneja aquí (arrastrar / tocar) ----------
    @SuppressLint("ViewConstructor", "ClickableViewAccessibility")
    inner class RayoWebView(c: Context) : WebView(c) {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (expanded) return super.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = lp.x; startY = lp.y
                    moved = false
                    stopRoam()
                    evaluateJavascript("window.rayoTouch&&rayoTouch('down')", null)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && hypot(dx, dy) > 8 * d) {
                        moved = true; dragging = true
                        evaluateJavascript("window.rayoTouch&&rayoTouch('drag')", null)
                    }
                    if (moved) {
                        lp.x = (startX + dx).toInt().coerceIn(0, max(0, sw - lp.width))
                        lp.y = (startY + dy).toInt().coerceIn(0, max(0, sh - lp.height))
                        wm.updateViewLayout(this, lp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val fueArrastre = moved
                    dragging = false
                    if (!fueArrastre) evaluateJavascript("rayoToggle(true)", null)
                    else {
                        evaluateJavascript("window.rayoTouch&&rayoTouch('drop')", null)
                        scheduleRoam(4000)
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    scheduleRoam(3000)
                }
            }
            return true
        }
    }

    // ---------- Puente JavaScript <-> Android ----------
    inner class Bridge {
        @JavascriptInterface
        fun expand(open: Boolean) { handler.post { setExpanded(open) } }

        @JavascriptInterface
        fun walk(on: Boolean) {
            handler.post {
                roamOn = on
                if (on) scheduleRoam(800) else stopRoam()
            }
        }

        @JavascriptInterface
        fun open(url: String) {
            handler.post {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) { }
            }
        }

        @JavascriptInterface
        fun speak(text: String) {
            handler.post {
                val t = tts
                if (ttsReady && t != null) {
                    if (hablaIngles) { usarEspanol(t); hablaIngles = false }
                    t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rayo")
                }
            }
        }

        @JavascriptInterface
        fun speakIn(text: String, lang: String) {
            handler.post {
                val t = tts
                if (ttsReady && t != null) {
                    if (lang == "en") {
                        t.setLanguage(Locale.US)
                        hablaIngles = true
                    } else if (hablaIngles) {
                        usarEspanol(t)
                        hablaIngles = false
                    }
                    t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rayo")
                }
            }
        }

        @JavascriptInterface
        fun setAutostart(on: Boolean) {
            getSharedPreferences("rayo", Context.MODE_PRIVATE).edit().putBoolean("autostart", on).apply()
        }

        @JavascriptInterface
        fun getAutostart(): Boolean =
            getSharedPreferences("rayo", Context.MODE_PRIVATE).getBoolean("autostart", true)

        @JavascriptInterface
        fun stopSpeak() { handler.post { tts?.stop() } }

        @JavascriptInterface
        fun vibrate(ms: Int) {
            try {
                val vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vib.vibrate(VibrationEffect.createOneShot(ms.toLong().coerceIn(1L, 200L), VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (e: Exception) { }
        }

        @JavascriptInterface
        fun copy(text: String) {
            handler.post {
                try {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Rayo", text))
                } catch (e: Exception) { }
            }
        }

        @JavascriptInterface
        fun openApp(name: String): String {
            return try {
                val pm = packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = pm.queryIntentActivities(intent, 0)
                val q = name.trim().lowercase()
                var best: android.content.pm.ResolveInfo? = null
                for (r in apps) {
                    val label = r.loadLabel(pm).toString().lowercase()
                    if (label == q) { best = r; break }
                    if (best == null && q.length >= 3 && label.contains(q)) best = r
                }
                if (best == null) {
                    ""
                } else {
                    val launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName)
                    if (launch == null) {
                        ""
                    } else {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        handler.post { try { startActivity(launch) } catch (e: Exception) { } }
                        best.loadLabel(pm).toString()
                    }
                }
            } catch (e: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun quit() { handler.post { stopSelf() } }
    }

    private fun setExpanded(open: Boolean) {
        val v = view ?: return
        if (open == expanded) return
        stopRoam()
        if (open) {
            savedX = lp.x; savedY = lp.y
            lp.width = min(sw - (16 * d).toInt(), (380 * d).toInt())
            lp.height = min((sh * 0.78f).toInt(), (580 * d).toInt())
            lp.x = (sw - lp.width) / 2
            lp.y = max((24 * d).toInt(), sh - lp.height - (90 * d).toInt())
            lp.flags = FLAGS_EXPANDED
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        } else {
            val s = (132 * d).toInt()
            lp.width = s; lp.height = s
            lp.x = savedX.coerceIn(0, max(0, sw - s))
            lp.y = savedY.coerceIn(0, max(0, sh - s))
            lp.flags = FLAGS_COLLAPSED
        }
        expanded = open
        wm.updateViewLayout(v, lp)
        if (open) v.requestFocus() else scheduleRoam(2500)
    }

    // ---------- Paseo automático por toda la pantalla ----------
    private fun scheduleRoam(ms: Long) {
        handler.removeCallbacks(roamRunnable)
        if (roamOn && !expanded) handler.postDelayed(roamRunnable, ms)
    }

    private fun stopRoam() {
        handler.removeCallbacks(roamRunnable)
        val a = anim
        anim = null
        a?.cancel()
        view?.evaluateJavascript("rayoLean(0)", null)
    }

    private fun roamStep() {
        val v = view ?: return
        if (!roamOn || expanded || dragging) return
        val maxX = max(1, sw - lp.width)
        val maxY = max(1, sh - lp.height - (48 * d).toInt())
        val tx = Random.nextInt(0, maxX)
        val ty = Random.nextInt(0, maxY)
        val sx = lp.x
        val sy = lp.y
        val dist = hypot((tx - sx).toFloat(), (ty - sy).toFloat())
        val dash = Random.nextFloat() < 0.3f
        val speed = (if (dash) 1500f else 380f) * d
        val dur = max(700L, (dist / speed * 1000).toLong())
        val tilt = if (tx > sx) (if (dash) 16 else 8) else (if (dash) -16 else -8)
        v.evaluateJavascript("rayoLean($tilt)", null)

        val va = ValueAnimator.ofFloat(0f, 1f)
        va.duration = dur
        va.interpolator = AccelerateDecelerateInterpolator()
        va.addUpdateListener { a ->
            val f = a.animatedValue as Float
            lp.x = (sx + (tx - sx) * f).toInt()
            lp.y = (sy + (ty - sy) * f).toInt()
            try { wm.updateViewLayout(v, lp) } catch (e: Exception) { }
        }
        va.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (anim === va) {
                    v.evaluateJavascript("rayoLean(0)", null)
                    scheduleRoam(Random.nextLong(600, 2800))
                }
            }
        })
        anim = va
        va.start()
    }

    override fun onDestroy() {
        handler.removeCallbacks(roamRunnable)
        anim?.cancel()
        anim = null
        view?.let {
            try { wm.removeView(it) } catch (e: Exception) { }
            it.destroy()
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
        view = null
        super.onDestroy()
    }
}
