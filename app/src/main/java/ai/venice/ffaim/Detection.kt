package ai.venice.ffaim

class Detection {
    var left = 0f
    var top = 0f
    var right = 0f
    var bottom = 0f
    var headX = 0f
    var headY = 0f
    var centreX = 0f
    var centreY = 0f
    var area = 0
    var sumR = 0L
    var sumG = 0L
    var sumB = 0L
    var minR = 255
    var gapRatio = 0f
    var fill = 0f
    var aspect = 0f
    var height = 0f

    fun iou(o: Detection): Float {
        val ix = minOf(right, o.right) - maxOf(left, o.left)
        val iy = minOf(bottom, o.bottom) - maxOf(top, o.top)
        if (ix <= 0f || iy <= 0f) return 0f
        val inter = ix * iy
        val a = (right - left) * (bottom - top)
        val b = (o.right - o.left) * (o.bottom - o.top)
        return inter / (a + b - inter)
    }
}
