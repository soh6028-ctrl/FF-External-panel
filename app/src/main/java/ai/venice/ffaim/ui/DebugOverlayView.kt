package ai.venice.ffaim.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import ai.venice.ffaim.Detection
import ai.venice.ffaim.Ui

class DebugOverlayView(ctx: Context) : View(ctx) {

    private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(ctx, 1.4f).toFloat()
        color = Ui.CYAN
    }
    private val head = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
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
        for (i in 0 until n) {
            if (i >= list.size) break
            val d = list[i]
            canvas.drawRect(d.left, d.top, d.right, d.bottom, box)
            canvas.drawCircle(d.headX, d.headY, Ui.dp(context, 3f).toFloat(), head)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            color = Color.TRANSPARENT
        })
    }
}
