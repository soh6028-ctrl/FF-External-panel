package ai.venice.ffaim

import java.util.Random
import kotlin.math.sqrt

/**
 * Runs on its own 8 ms tick, independent of the detection rate. Detection only
 * pushes snapshots in; the tick never waits for one.
 */
class AimEngine {

    companion object {
        const val NONE = 0
        const val PRESS = 1
        const val MOVE = 2
        const val RELEASE = 3
        private const val LEAD = 0.055f
        private const val MAX_STEP = 140f
        private const val SWITCH_MARGIN = 0.72f
        private const val DECOY_MS = 3000L
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
    var lockOn = false
        private set
    var onTarget = false
        private set

    private val rnd = Random()
    private val pool = ArrayList<Detection>(32)
    private var count = 0
    private var reticle = false

    private var locked = false
    private var lbValid = false
    private var lbLeft = 0f
    private var lbTop = 0f
    private var lbRight = 0f
    private var lbBottom = 0f

    private var lastX = 0f
    private var lastY = 0f
    private var lastT = 0L
    private var velX = 0f
    private var velY = 0f
    private var roundInBurst = 0

    private var headOffset = 0f
    private var searchIdx = 0
    private var searchWait = 0
    private var confirmed = false
    private var sinceConfirm = 0

    private var swept = 0f
    private var decoyCx = 0f
    private var decoyT = 0L
    private var decoyUntil = 0L
    private var decoyX = -1f
    private var decoyY = -1f

    private val steps = floatArrayOf(
        0f, 2f, 4f, 6f, 8f, 10f, 12f, 14f, -2f, -4f, -6f, -8f, -10f, -12f, -14f
    )

    fun onRoundFired() { roundInBurst = (roundInBurst + 1) % 6 }

    fun resetBurst() { roundInBurst = 0 }

    fun setDetections(src: List<Detection>, n: Int, reticleRed: Boolean) {
        count = 0
        for (i in 0 until n) {
            if (i >= src.size) break
            val s = src[i]
            if (count >= pool.size) pool.add(Detection())
            val d = pool[count++]
            d.left = s.left
            d.top = s.top
            d.right = s.right
            d.bottom = s.bottom
            d.headX = s.headX
            d.headY = s.headY
            d.centreX = s.centreX
            d.centreY = s.centreY
            d.height = s.height
            d.aspect = s.aspect
            d.fill = s.fill
            d.gapRatio = s.gapRatio
        }
        reticle = reticleRed
    }

    fun releaseNow(): Int {
        if (!locked) return NONE
        locked = false
        lbValid = false
        lockOn = false
        onTarget = false
        velX = 0f
        velY = 0f
        return RELEASE
    }

    fun tick(now: Long, crossX: Float, crossY: Float, fovR: Float): Int {
        onTarget = reticle
        if (!aimbot || count == 0) return releaseNow()

        val r2 = fovR * fovR

        var cur = -1
        if (lbValid) {
            var bestIou = 0.22f
            for (i in 0 until count) {
                val d = pool[i]
                val ix = minOf(d.right, lbRight) - maxOf(d.left, lbLeft)
                val iy = minOf(d.bottom, lbBottom) - maxOf(d.top, lbTop)
                if (ix <= 0f || iy <= 0f) continue
                val inter = ix * iy
                val a = (d.right - d.left) * (d.bottom - d.top)
                val b = (lbRight - lbLeft) * (lbBottom - lbTop)
                val iou = inter / (a + b - inter)
                if (iou > bestIou) {
                    bestIou = iou
                    cur = i
                }
            }
        }

        val curD = if (cur >= 0) dist(pool[cur].headX, pool[cur].headY, crossX, crossY) else -1f

        var rival = -1
        var rivalD = Float.MAX_VALUE
        for (i in 0 until count) {
            if (i == cur) continue
            val d = pool[i]
            if (d.centreX == decoyX && d.centreY == decoyY && now < decoyUntil) continue
            val hd = dist(d.headX, d.headY, crossX, crossY)
            if (hd > fovR) continue
            if (curD >= 0f && hd > curD * SWITCH_MARGIN) continue
            if (hd < rivalD) {
                rivalD = hd
                rival = i
            }
        }

        val pick = if (cur < 0) rival else if (rival >= 0 && rivalD < curD * SWITCH_MARGIN) rival else cur
        if (pick < 0) return releaseNow()

        val t = pool[pick]
        val hd = dist(t.headX, t.headY, crossX, crossY)
        if (hd * hd > r2) return releaseNow()

        val switched = pick != cur || !lbValid
        lbLeft = t.left
        lbTop = t.top
        lbRight = t.right
        lbBottom = t.bottom
        lbValid = true

        if (switched) {
            headOffset = 0f
            searchIdx = 0
            searchWait = 0
            confirmed = false
            sinceConfirm = 0
            swept = 0f
            decoyCx = t.centreX
            decoyT = now
        }

        // stuck-box give-up: the player's own character never moves while the camera does
        if (!switched && now - decoyT > 500L) {
            if (swept > 450f && kotlin.math.abs(t.centreX - decoyCx) < 25f) {
                decoyX = t.centreX
                decoyY = t.centreY
                decoyUntil = now + DECOY_MS
                swept = 0f
                return releaseNow()
            }
            decoyCx = t.centreX
            decoyT = now
            swept = 0f
        }

        val headCount = ((headshotPct * 6) + 50) / 100
        val bodyAnchor = headshot && roundInBurst < (6 - headCount)

        if (locked && headshot && !bodyAnchor) {
            if (reticle) {
                confirmed = true
                sinceConfirm = 0
            } else {
                sinceConfirm++
                if (confirmed && sinceConfirm > 6) confirmed = false
                if (!confirmed) {
                    searchWait++
                    if (searchWait >= 2) {
                        searchWait = 0
                        searchIdx = (searchIdx + 1) % steps.size
                        headOffset = steps[searchIdx]
                    }
                }
            }
        }

        val anchorY = if (bodyAnchor) t.top + (t.bottom - t.top) * 0.27f else t.headY + headOffset

        val dt = if (lastT == 0L) 0.033f else ((now - lastT).coerceIn(1L, 200L)) / 1000f
        if (lastT != 0L) {
            velX = velX * 0.65f + ((t.headX - lastX) / dt) * 0.35f
            velY = velY * 0.65f + ((t.headY - lastY) / dt) * 0.35f
            velX = velX.coerceIn(-1400f, 1400f)
            velY = velY.coerceIn(-1400f, 1400f)
        }
        lastX = t.headX
        lastY = t.headY
        lastT = now

        val mx = (t.headX + velX * LEAD) - crossX
        val my = (anchorY + velY * LEAD) - crossY

        if (!locked) {
            locked = true
            lockOn = true
            val g = baseGain + rnd.nextFloat() * 0.10f - 0.05f
            dx = (mx * g).coerceIn(-MAX_STEP, MAX_STEP)
            dy = (my * g - 0.7f).coerceIn(-MAX_STEP, MAX_STEP)
            swept += kotlin.math.abs(dx) + kotlin.math.abs(dy)
            return PRESS
        }

        if (mx * mx + my * my < deadzone * deadzone) return NONE

        val g = baseGain + rnd.nextFloat() * 0.10f - 0.05f
        dx = (mx * g).coerceIn(-MAX_STEP, MAX_STEP)
        dy = (my * g - 0.7f).coerceIn(-MAX_STEP, MAX_STEP)
        swept += kotlin.math.abs(dx) + kotlin.math.abs(dy)
        return MOVE
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val a = x1 - x2
        val b = y1 - y2
        return sqrt(a * a + b * b)
    }
}
