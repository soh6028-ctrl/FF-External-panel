package ai.venice.ffaim

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Two-path outline detector.
 *  Path A: vivid hollow head ring not under the crosshair -> ring centre is the head.
 *  Path B: thin body silhouette -> head as a fraction of box height, refined later
 *          by the reticle. Fraction follows AimBuddy's measured curve: 10% of the
 *          box height for a head-to-feet figure, 30% as the box squares up.
 * Self rejection is by solidity, never by size.
 */
class Detector {

    var model: AimModel = AimModel.defaultModel()
        set(v) {
            field = v
            if (v.selfZone.size == 4) {
                zone.set(v.selfZone[0], v.selfZone[1], v.selfZone[2], v.selfZone[3])
            }
        }

    private val zone = Rect()

    private var px = IntArray(0)
    private var mask = ByteArray(0)
    private var stack = IntArray(0)
    private val pool = ArrayList<Detection>(96)
    val items: ArrayList<Detection> get() = pool
    var count = 0
        private set

    private var w = 0
    private var h = 0
    private var ox = 0
    private var oy = 0
    private var sc = 1.5f
    private var cropArea = 1
    private var ccx = 0
    private var ccy = 0

    @Volatile var reticleRed = false
        private set

    fun detect(
        bmp: Bitmap, originHalfX: Int, originHalfY: Int, scale: Float,
        crossX: Float, crossY: Float
    ) {
        count = 0
        w = bmp.width
        h = bmp.height
        ox = originHalfX
        oy = originHalfY
        sc = scale
        ccx = ((crossX / sc) - ox).toInt()
        ccy = ((crossY / sc) - oy).toInt()
        val n = w * h
        if (n <= 0) return
        cropArea = n
        if (px.size < n) px = IntArray(n)
        if (mask.size < n) mask = ByteArray(n)
        if (stack.size < n) stack = IntArray(n)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        buildMask(n)
        label(n)
        sampleReticle()
    }

    /** Dull-to-vivid red only. Wood (r-g~40) and sand (r-g~10) stay out. */
    private fun buildMask(n: Int) {
        val m = model
        val msk = mask
        val p = px
        for (i in 0 until n) {
            val c = p[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            msk[i] = if (r >= m.minR && r - g >= m.redOverGreen && r - b >= m.redOverBlue) 1 else 0
        }
    }

    /** Red on the crosshair means the reticle is live, i.e. the aim is on the enemy. */
    private fun sampleReticle() {
        var c = 0
        var y = (ccy - 12).coerceAtLeast(0)
        val yEnd = (ccy + 12).coerceAtMost(h - 1)
        while (y <= yEnd) {
            var x = (ccx - 12).coerceAtLeast(0)
            val xEnd = (ccx + 12).coerceAtMost(w - 1)
            while (x <= xEnd) {
                if (mask[y * w + x].toInt() != 0) c++
                x++
            }
            y++
        }
        reticleRed = c >= 8
    }

    private fun label(n: Int) {
        val msk = mask
        val st = stack
        for (i in 0 until n) {
            if (msk[i].toInt() != 1) continue
            var sp = 0
            st[sp++] = i
            msk[i] = 2
            var minX = w
            var maxX = -1
            var minY = h
            var maxY = -1
            var area = 0
            var sr = 0L
            var sg = 0L
            var sb = 0L
            var mr = 255
            while (sp > 0) {
                val cur = st[--sp]
                val cx = cur % w
                val cy = cur / w
                area++
                if (cx < minX) minX = cx
                if (cx > maxX) maxX = cx
                if (cy < minY) minY = cy
                if (cy > maxY) maxY = cy
                val c = px[cur]
                val r = (c shr 16) and 0xFF
                sr += r
                sg += (c shr 8) and 0xFF
                sb += c and 0xFF
                if (r < mr) mr = r
                if (cx > 0) { val k = cur - 1; if (msk[k].toInt() == 1) { msk[k] = 2; st[sp++] = k } }
                if (cx < w - 1) { val k = cur + 1; if (msk[k].toInt() == 1) { msk[k] = 2; st[sp++] = k } }
                if (cy > 0) { val k = cur - w; if (msk[k].toInt() == 1) { msk[k] = 2; st[sp++] = k } }
                if (cy < h - 1) { val k = cur + w; if (msk[k].toInt() == 1) { msk[k] = 2; st[sp++] = k } }
            }
            evaluate(minX, maxX, minY, maxY, area, sr, sg, sb, mr)
        }
    }

    private fun evaluate(
        minX: Int, maxX: Int, minY: Int, maxY: Int,
        area: Int, sr: Long, sg: Long, sb: Long, mr: Int
    ) {
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        if (bw < 4 || bh < 4) return
        if (area > cropArea * 0.42f) return
        if (minX <= 0 || minY <= 0 || maxX >= w - 1 || maxY >= h - 1) return

        val aspect = bh.toFloat() / bw
        val fill = area.toFloat() / (bw * bh)
        val m = model

        // reticle ring sitting on the crosshair: not an enemy, do not derive a head from it
        if (ccx in minX..maxX && ccy in minY..maxY && bw <= 42 && bh <= 42 &&
            aspect in 0.55f..1.85f && fill <= 0.50f
        ) return

        // ---- path A: head ring away from the crosshair --------------------
        if (bw in 7..150 && bh in 7..150 && aspect in 0.52f..1.95f && fill <= 0.46f &&
            isHollowRing(minX, maxX, minY, maxY)
        ) {
            if (!isTeammate(minX, maxX, minY, maxY)) {
                emit(minX, maxX, minY, maxY, area, sr, sg, sb, mr,
                    (minX + maxX) * 0.5f, (minY + maxY) * 0.5f, 0f, aspect, fill)
            }
            return
        }

        // ---- path B: body silhouette -------------------------------------
        if (bh < m.minHeight) return
        if (aspect < m.minAspect) return
        if (fill > m.maxFill) return

        var gapRows = 0
        var rows = 0
        var y = minY
        while (y <= maxY) {
            rows++
            var hit = false
            var x = minX
            while (x <= maxX) {
                if (mask[y * w + x].toInt() != 0) { hit = true; break }
                x += 2
            }
            if (!hit) gapRows++
            y += 2
        }
        val gapRatio = if (rows == 0) 1f else gapRows.toFloat() / rows
        if (gapRatio > m.maxGapRatio) return

        if (isSelf(minX, maxX, minY, maxY, fill)) return
        if (isTeammate(minX, maxX, minY, maxY)) return

        val pad = (bw * 0.20f).toInt()
        val xLo = (minX + pad).coerceAtMost(maxX)
        val xHi = (maxX - pad).coerceAtLeast(minX)
        val headX = ((xLo + xHi) * 0.5f).coerceIn(minX + 1f, maxX - 1f)
        val f = 0.10f + 0.20f * (((1.8f - aspect) / 0.8f).coerceIn(0f, 1f))
        val headY = (minY + bh * f).coerceAtMost(maxY.toFloat())

        emit(minX, maxX, minY, maxY, area, sr, sg, sb, mr, headX, headY, gapRatio, aspect, fill)
    }

    private fun isSelf(minX: Int, maxX: Int, minY: Int, maxY: Int, fill: Float): Boolean {
        if (zone.width() <= 0) return false
        if (fill < 0.32f) return false
        val sCx = (ox + (minX + maxX) * 0.5f) * sc
        val sCy = (oy + (minY + maxY) * 0.5f) * sc
        val sH = (maxY - minY + 1) * sc
        if (sH < Geometry.screenH * 0.08f) return false
        return zone.contains(sCx.toInt(), sCy.toInt())
    }

    private fun isHollowRing(minX: Int, maxX: Int, minY: Int, maxY: Int): Boolean {
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        val cx = (minX + maxX) / 2
        val cy = (minY + maxY) / 2
        val hw = maxOf(1, (bw * 0.16f).toInt())
        val hh = maxOf(1, (bh * 0.16f).toInt())
        var cIn = 0
        var cTot = 0
        var y = (cy - hh).coerceAtLeast(minY)
        while (y <= (cy + hh).coerceAtMost(maxY)) {
            var x = (cx - hw).coerceAtLeast(minX)
            while (x <= (cx + hw).coerceAtMost(maxX)) {
                cTot++
                if (mask[y * w + x].toInt() != 0) cIn++
                x++
            }
            y++
        }
        if (cTot == 0) return false
        if (cIn.toFloat() / cTot > 0.30f) return false

        val t = maxOf(2, (minOf(bw, bh) * 0.26f).toInt())
        var bIn = 0
        var bTot = 0
        y = minY
        while (y <= maxY) {
            var x = minX
            while (x <= maxX) {
                val dx = minOf(x - minX, maxX - x)
                val dy = minOf(y - minY, maxY - y)
                if (dx < t || dy < t) {
                    bTot++
                    if (mask[y * w + x].toInt() != 0) bIn++
                }
                x++
            }
            y++
        }
        if (bTot == 0) return false
        return bIn.toFloat() / bTot >= 0.42f
    }

    private fun isTeammate(minX: Int, maxX: Int, minY: Int, maxY: Int): Boolean {
        val bh = maxY - minY + 1
        val bandTop = minY - maxOf(6, (bh * 0.55f).toInt())
        if (bandTop < 1) return false
        val x0 = maxOf(1, minX - (maxX - minX) / 2)
        val x1 = minOf(w - 2, maxX + (maxX - minX) / 2)
        val m = model
        var green = 0
        var bright = 0
        var y = bandTop
        while (y < minY) {
            var x = x0
            while (x <= x1) {
                val c = px[y * w + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (g >= m.greenMin && g - r >= m.greenOverRed && g - b >= m.greenOverBlue) green++
                val mx = maxOf(r, g, b)
                val mn = minOf(r, g, b)
                if (mx >= m.brightMin && mx - mn < 34) bright++
                x++
            }
            y++
        }
        if (green >= 6) return true
        return bright >= 26
    }

    private fun emit(
        minX: Int, maxX: Int, minY: Int, maxY: Int,
        area: Int, sr: Long, sg: Long, sb: Long, mr: Int,
        headX: Float, headY: Float, gapRatio: Float, aspect: Float, fill: Float
    ) {
        val d = obtain()
        d.left = (ox + minX) * sc
        d.top = (oy + minY) * sc
        d.right = (ox + maxX) * sc
        d.bottom = (oy + maxY) * sc
        d.centreX = (ox + (minX + maxX) * 0.5f) * sc
        d.centreY = (oy + (minY + maxY) * 0.5f) * sc
        d.headX = (ox + headX) * sc
        d.headY = (oy + headY) * sc
        d.area = area
        d.sumR = sr
        d.sumG = sg
        d.sumB = sb
        d.minR = mr
        d.gapRatio = gapRatio
        d.aspect = aspect
        d.fill = fill
        d.height = (maxY - minY + 1) * sc
    }

    private fun obtain(): Detection {
        if (count >= pool.size) pool.add(Detection())
        return pool[count++]
    }
}
