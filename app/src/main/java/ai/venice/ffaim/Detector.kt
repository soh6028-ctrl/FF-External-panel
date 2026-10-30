package ai.venice.ffaim

import android.graphics.Bitmap
import android.graphics.Rect
import java.util.Arrays

/**
 * Pure geometric outline detector. Everything is threshold driven and every
 * threshold comes from the trained model. No clothing, skin or helmet colour.
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

    fun detect(bmp: Bitmap, originHalfX: Int, originHalfY: Int) {
        count = 0
        w = bmp.width
        h = bmp.height
        ox = originHalfX
        oy = originHalfY
        val n = w * h
        if (n <= 0) return
        if (px.size < n) px = IntArray(n)
        if (mask.size < n) mask = ByteArray(n)
        if (stack.size < n) stack = IntArray(n)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        buildMask(n)
        label(n)
    }

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
        if (bw < 3 || bh < 5) return
        val m = model
        if (bh < m.minHeight) return
        val aspect = bh.toFloat() / bw
        if (aspect < m.minAspect) return
        val fill = area.toFloat() / (bw * bh)
        if (fill > m.maxFill) return
        if (minX <= 0 || minY <= 0 || maxX >= w - 1 || maxY >= h - 1) return

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

        val sCx = (ox + (minX + maxX) * 0.5f) * 2f
        val sCy = (oy + (minY + maxY) * 0.5f) * 2f
        val sH = bh * 2f

        if (zone.width() > 0 && zone.contains(sCx.toInt(), sCy.toInt()) && sH > Geometry.screenH * 0.18f) return

        if (isTeammate(minX, maxX, minY, maxY)) return

        var headX: Float
        var headY: Float
        val circular = bh <= bw * 1.8f && bh <= 30
        if (circular) {
            headX = (minX + maxX) * 0.5f
            headY = (minY + maxY) * 0.5f
        } else {
            var r0 = minY
            while (r0 <= maxY) {
                var any = false
                var x = minX
                while (x <= maxX) {
                    if (mask[r0 * w + x].toInt() != 0) { any = true; break }
                    x++
                }
                if (any) break
                r0++
            }
            val band = maxOf(3, (bh * m.headBand).toInt())
            var sumX = 0L
            var cnt = 0
            val y2 = minOf(maxY, r0 + band)
            var yy = r0
            while (yy <= y2) {
                var x = minX
                while (x <= maxX) {
                    if (mask[yy * w + x].toInt() != 0) { sumX += x; cnt++ }
                    x++
                }
                yy++
            }
            headX = if (cnt > 0) sumX.toFloat() / cnt else (minX + maxX) * 0.5f
            headY = r0 + band * m.headBias
            if (headX < minX + 1) headX = (minX + 1).toFloat()
            if (headX > maxX - 1) headX = (maxX - 1).toFloat()
            if (headY > maxY.toFloat()) headY = maxY.toFloat()
        }

        val d = obtain()
        d.left = (ox + minX) * 2f
        d.top = (oy + minY) * 2f
        d.right = (ox + maxX) * 2f
        d.bottom = (oy + maxY) * 2f
        d.centreX = sCx
        d.centreY = sCy
        d.headX = (ox + headX) * 2f
        d.headY = (oy + headY) * 2f
        d.area = area
        d.sumR = sr
        d.sumG = sg
        d.sumB = sb
        d.minR = mr
        d.gapRatio = gapRatio
        d.fill = fill
        d.aspect = aspect
        d.height = sH
    }

    private fun isTeammate(minX: Int, maxX: Int, minY: Int, maxY: Int): Boolean {
        val bh = maxY - minY + 1
        val bandTop = minY - maxOf(6, (bh * 0.45f).toInt())
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
        return bright >= 22
    }

    private fun obtain(): Detection {
        if (count >= pool.size) pool.add(Detection())
        return pool[count++]
    }

    fun clearMask() {
        if (mask.isNotEmpty()) Arrays.fill(mask, 0)
    }
}
