package ai.venice.ffaim.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import ai.venice.ffaim.Ui

class BubbleView(ctx: Context) : View(ctx) {

    var onTap: (() -> Unit)? = null
    var onDrag: ((Float, Float) -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var moved = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.BG_TOP
        style = Paint.Style.FILL
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.CYAN
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(ctx, 1.6f).toFloat()
    }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.CYAN
        textSize = Ui.dp(ctx, 15f).toFloat()
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(cx, cy) - Ui.dp(context, 2f)
        canvas.drawCircle(cx, cy, r, fill)
        canvas.drawCircle(cx, cy, r, stroke)
        canvas.drawText("AI", cx, cy + Ui.dp(context, 5f), glyph)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                moved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                if (Math.abs(dx) > Ui.dp(context, 4f) || Math.abs(dy) > Ui.dp(context, 4f)) {
                    moved = true
                    onDrag?.invoke(dx, dy)
                    downX = e.rawX
                    downY = e.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) onTap?.invoke()
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}
