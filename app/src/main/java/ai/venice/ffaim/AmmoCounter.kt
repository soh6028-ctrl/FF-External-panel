package ai.venice.ffaim

import android.graphics.Bitmap

/**
 * Counts fired rounds by watching the lit-pixel mass of the ammo counter box.
 */
class AmmoCounter {

    private var prev = -1
    private var lastFire = 0L
    var roundsRemaining = 6
        private set

    var onRound: (() -> Unit)? = null

    fun reset() {
        prev = -1
        roundsRemaining = 6
    }

    fun feed(bmp: Bitmap?, now: Long) {
        if (bmp == null) return
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return
        val stepX = if (w > 90) 2 else 1
        val stepY = if (h > 90) 2 else 1
        var lit = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = bmp.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r > 150 && g > 150 && b > 150) lit++
                x += stepX
            }
            y += stepY
        }
        val p = prev
        prev = lit
        if (p < 0) return
        val drop = p - lit
        if (drop >= maxOf(5, p / 8) && now - lastFire > 55L) {
            lastFire = now
            roundsRemaining = if (roundsRemaining > 1) roundsRemaining - 1 else 6
            onRound?.invoke()
        }
        if (lit > p + maxOf(6, p / 5) && now - lastFire > 320L) {
            roundsRemaining = 6
        }
    }
}
