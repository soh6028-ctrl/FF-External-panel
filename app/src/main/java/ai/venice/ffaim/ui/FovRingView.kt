package ai.venice.ffaim.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import ai.venice.ffaim.Geometry
import ai.venice.ffaim.Prefs
import ai.venice.ffaim.Ui

class FovRingView(ctx: Context, private val prefs: Prefs) : View(ctx) {

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(ctx, 1.6f).toFloat()
        color = Ui.CYAN_SOFT
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(200, 41, 224, 255)
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(ctx, 6f).toFloat()
        color = Color.argb(28, 41, 224, 255)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = Geometry.screenW / 2f + prefs.fovOffX
        val cy = Geometry.screenH / 2f + prefs.fovOffY
        val r = prefs.fovRadius.toFloat()
        canvas.drawCircle(cx, cy, r, glow)
        canvas.drawCircle(cx, cy, r, ring)
        canvas.drawCircle(cx, cy, Ui.dp(context, 1.5f).toFloat(), dot)
    }
}
