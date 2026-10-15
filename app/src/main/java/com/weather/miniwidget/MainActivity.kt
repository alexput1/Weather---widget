package com.weather.miniwidget

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private var floating: FloatingView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ClickService.targetPackage = "madfut"

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        }
        findViewById<Button>(R.id.btnShow).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Нужно окно", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            if (ClickService.instance == null) {
                Toast.makeText(this, "Включи сервис", Toast.LENGTH_LONG).show(); return@setOnClickListener
            }
            floating = FloatingView(this).also { it.show() }
        }
    }

    override fun onResume() {
        super.onResume()
        val a = if (ClickService.instance != null) "✅" else "❌"
        val o = if (Settings.canDrawOverlays(this)) "✅" else "❌"
        findViewById<TextView>(R.id.status).text = "Сервис: $a | Окно: $o"
    }
}
