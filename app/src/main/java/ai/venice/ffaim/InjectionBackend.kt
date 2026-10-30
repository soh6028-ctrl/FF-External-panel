package ai.venice.ffaim

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper

interface InjectionBackend {
    val available: Boolean
    fun press(x: Float, y: Float)
    fun dragBy(dx: Float, dy: Float)
    fun release()
}

/**
 * Continuous held pointer. The stroke is kept alive by re-continuing it from the
 * onCompleted callback, one ~28 ms chunk at a time, so the game sees a genuine drag.
 */
class AccessibilityBackend : InjectionBackend {

    private companion object {
        const val CHUNK = 28L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var stroke: GestureDescription.StrokeDescription? = null
    private var curX = 0f
    private var curY = 0f
    private var pendX = 0f
    private var pendY = 0f
    private var active = false
    private var gen = 0

    override val available: Boolean
        get() = AimAccessibilityService.instance != null

    private val callback = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(gesture: GestureDescription?) {
            if (!active) return
            step(gen)
        }

        override fun onCancelled(gesture: GestureDescription?) {
            active = false
            stroke = null
        }
    }

    override fun press(x: Float, y: Float) {
        val svc = AimAccessibilityService.instance ?: return
        release()
        gen++
        val g = gen
        curX = x
        curY = y
        pendX = 0f
        pendY = 0f
        active = true
        val p = Path()
        p.moveTo(x, y)
        p.lineTo(x, y)
        val s = GestureDescription.StrokeDescription(p, 0L, CHUNK, true)
        stroke = s
        svc.dispatchGesture(GestureDescription.Builder().addStroke(s).build(), callback, handler)
        if (g != gen) active = false
    }

    override fun dragBy(dx: Float, dy: Float) {
        pendX += dx
        pendY += dy
    }

    private fun step(g: Int) {
        if (g != gen) return
        val svc = AimAccessibilityService.instance ?: run { active = false; return }
        val s = stroke ?: run { active = false; return }
        val px = curX
        val py = curY
        curX = (curX + pendX).coerceIn(2f, Geometry.screenW - 2f)
        curY = (curY + pendY).coerceIn(2f, Geometry.screenH - 2f)
        pendX = 0f
        pendY = 0f
        val p = Path()
        p.moveTo(px, py)
        p.lineTo(curX, curY)
        val ns = try {
            s.continueStroke(p, 0L, CHUNK, true)
        } catch (t: Throwable) {
            active = false
            stroke = null
            return
        }
        stroke = ns
        svc.dispatchGesture(GestureDescription.Builder().addStroke(ns).build(), callback, handler)
    }

    override fun release() {
        val s = stroke
        active = false
        stroke = null
        gen++
        if (s == null) return
        val svc = AimAccessibilityService.instance ?: return
        val p = Path()
        p.moveTo(curX, curY)
        p.lineTo(curX, curY)
        try {
            val ns = s.continueStroke(p, 0L, 1L, false)
            svc.dispatchGesture(GestureDescription.Builder().addStroke(ns).build(), null, handler)
        } catch (t: Throwable) {
            // stroke already finished
        }
    }
}

/**
 * Shizuku `input motionevent` fallback. Higher latency, no touch indicator.
 */
class ShizukuBackend : InjectionBackend {

    private companion object {
        const val TICK = 40L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var curX = 0f
    private var curY = 0f
    private var pendX = 0f
    private var pendY = 0f

    override val available: Boolean
        get() = ShizukuBridge.hasPermission()

    private val ticker = object : Runnable {
        override fun run() {
            if (!active) return
            if (pendX != 0f || pendY != 0f) {
                curX = (curX + pendX).coerceIn(2f, Geometry.screenW - 2f)
                curY = (curY + pendY).coerceIn(2f, Geometry.screenH - 2f)
                pendX = 0f
                pendY = 0f
                ShizukuBridge.run("input motionevent MOVE ${curX.toInt()} ${curY.toInt()}")
            }
            handler.postDelayed(this, TICK)
        }
    }

    override fun press(x: Float, y: Float) {
        if (!available) return
        release()
        curX = x
        curY = y
        pendX = 0f
        pendY = 0f
        active = true
        ShizukuBridge.run("input motionevent DOWN ${x.toInt()} ${y.toInt()}")
        handler.postDelayed(ticker, TICK)
    }

    override fun dragBy(dx: Float, dy: Float) {
        pendX += dx
        pendY += dy
    }

    override fun release() {
        if (!active) return
        active = false
        handler.removeCallbacks(ticker)
        ShizukuBridge.run("input motionevent UP ${curX.toInt()} ${curY.toInt()}")
    }
}
