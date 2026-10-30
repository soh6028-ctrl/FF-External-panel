package ai.venice.ffaim.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import ai.venice.ffaim.Detection
import ai.venice.ffaim.Ui

class DebugOverlayView(ctx: Context) : View(ctx) {

    private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(ctx, 1.6f).toFloat()
        color = Ui.CYAN
    }
    private val head = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(ctx, 1.4f).toFloat()
        color = Ui.AMBER
    }

    private var list: List<Detection> = emptyList()
    private var n = 0

    fun update(src: List<Detection>, count: Int) {
        list = src
        n = count
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val arm = Ui.dp(context, 7f).toFloat()
        for (i in 0 until n) {
            if (i >= list.size) break
            val d = list[i]
            val l = d.left
            val t = d.top
            val r = d.right
            val b = d.bottom
            canvas.drawLine(l, t, l + arm, t, box)
            canvas.drawLine(l, t, l, t + arm, box)
            canvas.drawLine(r, t, r - arm, t, box)
            canvas.drawLine(r, t, r, t + arm, box)
            canvas.drawLine(l, b, l + arm, b, box)
            canvas.drawLine(l, b, l, b - arm, box)
            canvas.drawLine(r, b, r - arm, b, box)
            canvas.drawLine(r, b, r, b - arm, box)
            canvas.drawCircle(d.headX, d.headY, Ui.dp(context, 4f).toFloat(), head)
        }
    }
}
