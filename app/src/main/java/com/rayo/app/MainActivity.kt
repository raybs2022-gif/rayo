package com.rayo.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(56, 140, 56, 56)
        }
        val title = TextView(this).apply {
            text = "⚡ Rayo"
            textSize = 34f
            gravity = Gravity.CENTER
        }
        val info = TextView(this).apply {
            text = "Tu mascota flotante con internet.\n\n" +
                "Toca el botón, da el permiso «Mostrar sobre otras apps» y Rayo aparecerá flotando sobre todo lo que abras."
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
        }
        val start = Button(this).apply {
            text = "Activar Rayo flotante"
            setOnClickListener { activar() }
        }
        val stop = Button(this).apply {
            text = "Quitar Rayo"
            setOnClickListener {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
                Toast.makeText(this@MainActivity, "Rayo se fue a descansar", Toast.LENGTH_SHORT).show()
            }
        }
        box.addView(title)
        box.addView(info)
        box.addView(start)
        box.addView(stop)
        setContentView(box)
    }

    private fun activar() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Activa «Mostrar sobre otras apps» para Rayo y vuelve", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        Toast.makeText(this, "¡Rayo activado!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
