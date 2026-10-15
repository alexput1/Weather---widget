package com.weather.miniwidget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*

@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class FloatingView(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var panel: View? = null
    private var countLabel: TextView? = null
    private val markers = mutableListOf<View>()
    private val ui = Handler(Looper.getMainLooper())

    private val counter = object : Runnable {
        override fun run() {
            countLabel?.text = "Кликов: ${ClickService.clickCount} | Точек: ${ClickService.clickPoints.size}"
            ui.postDelayed(this, 300)
        }
    }

    fun show() {
        if (panel != null) return

        val root = ScrollView(context).apply { setBackgroundColor(0xF01A1A1A.toInt()) }
        val l = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val title = TextView(context).apply {
            text = "🎯 Helper (тяни панель за меня)"
            setTextColor(0xFFFFFFFF.toInt()); textSize = 14f
        }
        countLabel = TextView(context).apply {
            text = "Кликов: 0 | Точек: 0"
            setTextColor(0xFF66FF66.toInt()); textSize = 12f
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

        l.addView(title)
        l.addView(countLabel)
        l.addView(slider("Задержка мс", ClickService.intervalMs.toInt().coerceAtMost(3000), 3000) {
            ClickService.intervalMs = it.toLong().coerceAtLeast(30)
        })
        l.addView(slider("Рандом %", ClickService.intervalJitterPercent, 50) {
            ClickService.intervalJitterPercent = it
        })
        l.addView(slider("Джиттер px", ClickService.positionJitterPx, 30) {
            ClickService.positionJitterPx = it
        })

        val addBtn = Button(context).apply {
            text = "🎯 Поставить точку"
            setOnClickListener { spawnMarker() }
        }

        val clearBtn = Button(context).apply {
            text = "🗑 Убрать все точки"
            setOnClickListener {
                ClickService.clickPoints.clear()
                markers.forEach { runCatching { wm.removeView(it) } }
                markers.clear()
                Toast.makeText(context, "Точки удалены", Toast.LENGTH_SHORT).show()
            }
        }

        val startBtn = Button(context).apply {
            text = if (ClickService.isRunning) "⏹ СТОП" else "▶ СТАРТ"
            setOnClickListener {
                if (ClickService.isRunning) {
                    ClickService.instance?.stopClicking(); text = "▶ СТАРТ"
                } else {
                    if (ClickService.clickPoints.isEmpty()) {
                        Toast.makeText(context, "Сначала поставь точку (🎯)", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    ClickService.instance?.startClicking(); text = "⏹ СТОП"
                }
            }
        }

        val hideBtn = Button(context).apply {
            text = "✖ Скрыть панель"
            setOnClickListener { hide() }
        }

        l.addView(addBtn)
        l.addView(clearBtn)
        l.addView(startBtn)
        l.addView(hideBtn)
        root.addView(l)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val p = WindowManager.LayoutParams(
            640,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30; y = 150
        }

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

        wm.addView(root, p)
        panel = root
        ClickService.isPanelVisible = true
        ui.post(counter)
    }

    private fun spawnMarker() {
        val sizePx = 120

        val container = FrameLayout(context)

        val outer = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FF3333"))
                setStroke(6, Color.parseColor("#FF2222"))
            }
        }
        container.addView(outer, FrameLayout.LayoutParams(sizePx, sizePx))

        val cross = TextView(context).apply {
            text = "✛"
            setTextColor(Color.parseColor("#FF2222"))
            textSize = 40f
            gravity = Gravity.CENTER
        }
        container.addView(cross, FrameLayout.LayoutParams(sizePx, sizePx))

        val num = TextView(context).apply {
            text = (ClickService.clickPoints.size + 1).toString()
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        container.addView(num, FrameLayout.LayoutParams(sizePx, sizePx))

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val metrics = context.resources.displayMetrics
        val startX = metrics.widthPixels / 2 - sizePx / 2
        val startY = metrics.heightPixels / 2 - sizePx / 2

        val mp = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }

        val point = ClickPoint(
            startX + sizePx / 2f,
            startY + sizePx / 2f
        )
        ClickService.clickPoints.add(point)
        val index = ClickService.clickPoints.size

        Toast.makeText(
            context,
            "Точка #$index создана. Перетащи её пальцем куда нужно.",
            Toast.LENGTH_LONG
        ).show()

        container.setOnTouchListener(object : View.OnTouchListener {
            var ix = 0
            var iy = 0
            var tx = 0f
            var ty = 0f
            var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        ix = mp.x
                        iy = mp.y
                        tx = e.rawX
                        ty = e.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        mp.x = ix + (e.rawX - tx).toInt()
                        mp.y = iy + (e.rawY - ty).toInt()
                        runCatching { wm.updateViewLayout(container, mp) }
                        point.x = mp.x + sizePx / 2f
                        point.y = mp.y + sizePx / 2f
                        moved = true
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        Toast.makeText(
                            context,
                            "Точка #$index: ${point.x.toInt()}, ${point.y.toInt()}",
                            Toast.LENGTH_SHORT
                        ).show()
                        return true
                    }
                }
                return false
            }
        })

        runCatching {
            wm.addView(container, mp)
            markers.add(container)
        }.onFailure {
            Toast.makeText(context, "Ошибка: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun hide() {
        panel?.let {
            wm.removeView(it); panel = null
            ClickService.isPanelVisible = false
            ui.removeCallbacks(counter)
        }
    }
}
