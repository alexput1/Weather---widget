package com.weather.miniwidget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*

@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class FloatingView(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var panel: View? = null
    private var crosshair: View? = null
    private var countLabel: TextView? = null
    private val ui = Handler(Looper.getMainLooper())

    private val counter = object : Runnable {
        override fun run() {
            countLabel?.text = "Кликов: ${ClickService.clickCount}"
            ui.postDelayed(this, 500)
        }
    }

    fun show() {
        if (panel != null) return
        val root = ScrollView(context).apply { setBackgroundColor(0xE61A1A1A.toInt()) }
        val l = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40)
        }
        val title = TextView(context).apply {
            text = "🎯 Helper"; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f
        }
        countLabel = TextView(context).apply {
            text = "Кликов: 0"; setTextColor(0xFF66FF66.toInt()); textSize = 12f
        }

        fun slider(label: String, init: Int, max: Int, onChange: (Int) -> Unit): LinearLayout {
            val b = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val tv = TextView(context).apply {
                text = "$label: $init"; setTextColor(0xFFFFFFFF.toInt()); textSize = 12f
            }
            val sb = SeekBar(context).apply {
                this.max = max; progress = init
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                        tv.text = "$label: $p"; onChange(p)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            b.addView(tv); b.addView(sb); return b
        }

        l.addView(title); l.addView(countLabel)
        l.addView(slider("Пауза мс", ClickService.intervalMs.toInt().coerceAtMost(3000), 3000) {
            ClickService.intervalMs = it.toLong().coerceAtLeast(30)
        })
        l.addView(slider("Рандом %", ClickService.intervalJitterPercent, 50) {
            ClickService.intervalJitterPercent = it
        })
        l.addView(slider("Джиттер px", ClickService.positionJitterPx, 30) {
            ClickService.positionJitterPx = it
        })

        val addBtn = Button(context).apply {
            text = "➕ Точка (${ClickService.clickPoints.size})"
            setOnClickListener {
                enterSelect(); text = "➕ Точка (${ClickService.clickPoints.size})"
            }
        }
        val clearBtn = Button(context).apply {
            text = "🗑 Очистить"
            setOnClickListener {
                ClickService.clickPoints.clear(); addBtn.text = "➕ Точка (0)"
            }
        }
        val startBtn = Button(context).apply {
            text = if (ClickService.isRunning) "⏹ СТОП" else "▶ СТАРТ"
            setOnClickListener {
                if (ClickService.isRunning) {
                    ClickService.instance?.stopClicking(); text = "▶ СТАРТ"
                } else {
                    if (ClickService.clickPoints.isEmpty()) {
                        Toast.makeText(context, "Добавь точку", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    ClickService.instance?.startClicking(); text = "⏹ СТОП"
                }
            }
        }
        val hideBtn = Button(context).apply {
            text = "✖"; setOnClickListener { hide() }
        }
        l.addView(addBtn); l.addView(clearBtn); l.addView(startBtn); l.addView(hideBtn)
        root.addView(l)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val p = WindowManager.LayoutParams(
            600, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 30; y = 150 }

        title.setOnTouchListener(object : View.OnTouchListener {
            var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        ix = p.x; iy = p.y; tx = e.rawX; ty = e.rawY; return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        p.x = ix + (e.rawX - tx).toInt()
                        p.y = iy + (e.rawY - ty).toInt()
                        wm.updateViewLayout(root, p); return true
                    }
                }
                return false
            }
        })

        wm.addView(root, p); panel = root
        ClickService.isPanelVisible = true; ui.post(counter)
    }

    private fun enterSelect() {
        if (crosshair != null) return
        Toast.makeText(context, "Тапни по экрану", Toast.LENGTH_LONG).show()
        val o = View(context).apply { setBackgroundColor(0x2200FF00) }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        o.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                ClickService.clickPoints.add(ClickPoint(e.rawX, e.rawY))
                Toast.makeText(context,
                    "Точка (${e.rawX.toInt()}, ${e.rawY.toInt()})", Toast.LENGTH_SHORT).show()
                wm.removeView(o); crosshair = null
            }
            true
        }
        wm.addView(o, p); crosshair = o
    }

    fun hide() {
        panel?.let {
            wm.removeView(it); panel = null
            ClickService.isPanelVisible = false
            ui.removeCallbacks(counter)
        }
    }
}
