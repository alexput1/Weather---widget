package com.weather.miniwidget

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlin.random.Random

data class ClickPoint(var x: Float, var y: Float)

class ClickService : AccessibilityService() {

    companion object {
        var instance: ClickService? = null
        var isRunning = false
        var intervalMs = 900L
        var intervalJitterPercent = 30
        var positionJitterPx = 6
        var tapDurationMin = 25L
        var tapDurationMax = 90L
        var missChancePercent = 2
        var swipeChancePercent = 8
        var fatigueEveryMin = 25
        var fatigueEveryMax = 60
        var clickPoints = mutableListOf<ClickPoint>()
        var clickCount = 0
        var isPanelVisible = false
        var currentForegroundPackage = ""
        var targetPackage = ""
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pointIndex = 0
    private var nextFatigueAt = 0

    private val clickRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || clickPoints.isEmpty()) return

            if (targetPackage.isNotEmpty() &&
                !currentForegroundPackage.contains(targetPackage, true)) {
                handler.postDelayed(this, 1000); return
            }

            if (nextFatigueAt == 0) nextFatigueAt = Random.nextInt(fatigueEveryMin, fatigueEveryMax + 1)
            if (clickCount > 0 && clickCount % nextFatigueAt == 0) {
                nextFatigueAt = Random.nextInt(fatigueEveryMin, fatigueEveryMax + 1)
                handler.postDelayed(this, Random.nextLong(1500, 6001)); return
            }

            val base = clickPoints[pointIndex % clickPoints.size]
            pointIndex++

            val jx = Random.nextInt(-positionJitterPx, positionJitterPx + 1).toFloat()
            val jy = Random.nextInt(-positionJitterPx, positionJitterPx + 1).toFloat()
            val x = (base.x + jx).coerceAtLeast(1f)
            val y = (base.y + jy).coerceAtLeast(1f)

            val isMiss = Random.nextInt(100) < missChancePercent
            val isSwipe = !isMiss && Random.nextInt(100) < swipeChancePercent

            when {
                isMiss -> {
                    val mx = x + Random.nextInt(-80, 81)
                    val my = y + Random.nextInt(-80, 81)
                    tap(mx, my, Random.nextLong(tapDurationMin, tapDurationMax))
                }
                isSwipe -> {
                    val dx = Random.nextInt(-120, 121).toFloat()
                    val dy = Random.nextInt(-120, 121).toFloat()
                    swipe(x, y, x + dx, y + dy, Random.nextLong(60, 160))
                }
                else -> tap(x, y, Random.nextLong(tapDurationMin, tapDurationMax))
            }
            clickCount++

            val delta = (intervalMs * intervalJitterPercent / 100).coerceAtLeast(1)
            val delay = (intervalMs + Random.nextLong(-delta, delta + 1)).coerceAtLeast(30)
            handler.postDelayed(this, delay)
        }
    }

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            event.packageName?.let { currentForegroundPackage = it.toString() }
        }
    }
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean {
        instance = null; stopClicking(); return super.onUnbind(intent)
    }

    fun startClicking() {
        if (isRunning || clickPoints.isEmpty()) return
        isRunning = true; pointIndex = 0; clickCount = 0; nextFatigueAt = 0
        handler.post(clickRunnable)
    }
    fun stopClicking() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacks(clickRunnable)
    }

    private fun tap(x: Float, y: Float, d: Long) {
        val p = Path().apply { moveTo(x, y) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, d)).build(), null, null)
    }
    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, d: Long) {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, d)).build(), null, null)
    }
}
