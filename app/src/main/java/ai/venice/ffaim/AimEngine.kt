package ai.venice.ffaim

import java.util.Random

class AimEngine {

    companion object {
        const val NONE = 0
        const val PRESS = 1
        const val MOVE = 2
        const val RELEASE = 3
        private const val LEAD = 0.055f
        private const val MAX_STEP = 140f
    }

    var aimbot = false
    var headshot = false
    var headshotPct = 100
    var deadzone = 3f
    var baseGain = 1.28f

    var dx = 0f
        private set
    var dy = 0f
        private set

    private val rnd = Random()
    private var locked = false
    private var target: Detection? = null
    private var lastX = 0f
    private var lastY = 0f
    private var lastT = 0L
    private var velX = 0f
    private var velY = 0f
    private var roundInBurst = 0

    var lockOn = false
        private set

    fun onRoundFired() {
        roundInBurst = (roundInBurst + 1) % 6
    }

    fun resetBurst() {
        roundInBurst = 0
    }

    fun releaseNow(): Int {
        if (!locked) return NONE
        locked = false
        target = null
        lockOn = false
        velX = 0f
        velY = 0f
        return RELEASE
    }

    fun update(
        dets: List<Detection>,
        count: Int,
        crossX: Float,
        crossY: Float,
        now: Long,
        fovRadius: Float
    ): Int {
        if (!aimbot || count == 0) return releaseNow()

        var best: Detection? = null
        val prev = target
        if (prev != null) {
            var bestIou = 0.2f
            for (i in 0 until count) {
                val d = dets[i]
                val v = d.iou(prev)
                if (v > bestIou) {
                    bestIou = v
                    best = d
                }
            }
        }
        if (best == null) {
            var bestDist = Float.MAX_VALUE
            for (i in 0 until count) {
                val d = dets[i]
                val ddx = d.headX - crossX
                val ddy = d.headY - crossY
                val dist = ddx * ddx + ddy * ddy
                if (dist < bestDist) {
                    bestDist = dist
                    best = d
                }
            }
        }
        val t = best ?: return releaseNow()

        val hx = t.headX
        val hy = t.headY
        val r2 = fovRadius * fovRadius
        val ddx0 = hx - crossX
        val ddy0 = hy - crossY
        if (ddx0 * ddx0 + ddy0 * ddy0 > r2) return releaseNow()

        val headCount = ((headshotPct * 6) + 50) / 100
        val bodyAnchor = headshot && roundInBurst < (6 - headCount)
        val anchorY = if (bodyAnchor) hy + (t.bottom - t.top) * 0.24f else hy

        val dt = if (lastT == 0L) 0.033f else ((now - lastT).coerceIn(1L, 200L)) / 1000f
        if (lastT != 0L) {
            val nvx = (hx - lastX) / dt
            val nvy = (hy - lastY) / dt
            velX = velX * 0.65f + nvx * 0.35f
            velY = velY * 0.65f + nvy * 0.35f
            velX = velX.coerceIn(-1400f, 1400f)
            velY = velY.coerceIn(-1400f, 1400f)
        }
        lastX = hx
        lastY = hy
        lastT = now

        val px = hx + velX * LEAD
        val py = anchorY + velY * LEAD
        var mx = px - crossX
        var my = py - crossY

        target = t

        if (!locked) {
            locked = true
            lockOn = true
            val g = baseGain + rnd.nextFloat() * 0.10f - 0.05f
            dx = (mx * g).coerceIn(-MAX_STEP, MAX_STEP)
            dy = (my * g - 0.7f).coerceIn(-MAX_STEP, MAX_STEP)
            return PRESS
        }

        if (mx * mx + my * my < deadzone * deadzone) return NONE

        val g = baseGain + rnd.nextFloat() * 0.10f - 0.05f
        dx = (mx * g).coerceIn(-MAX_STEP, MAX_STEP)
        dy = (my * g - 0.7f).coerceIn(-MAX_STEP, MAX_STEP)
        return MOVE
    }
}
