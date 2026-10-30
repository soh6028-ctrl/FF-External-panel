package ai.venice.ffaim.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import ai.venice.ffaim.Ui

class HudView(ctx: Context) : View(ctx) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = Ui.dp(ctx, 11f).toFloat()
        typeface = android.graphics.Typeface.MONOSPACE
        color = Ui.TEXT_DIM
    }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = Ui.dp(ctx, 11f).toFloat()
        typeface = android.graphics.Typeface.MONOSPACE
        color = Ui.CYAN
    }

    var fps = 0
    var targets = 0
    var lock = false
    var rounds = 6
    var badge = false

    fun refresh() { invalidate() }

    override fun onDraw(canvas: Canvas) {
        val lh = Ui.dp(context, 15f).toFloat()
        var y = lh
        canvas.drawText("FPS $fps", Ui.dp(context, 8f).toFloat(), y, p)
        y += lh
        canvas.drawText("TGT $targets", Ui.dp(context, 8f).toFloat(), y, p)
        y += lh
        canvas.drawText(if (lock) "LOCK ON" else "LOCK off", Ui.dp(context, 8f).toFloat(), y, if (lock) accent else p)
        y += lh
        canvas.drawText("RND $rounds", Ui.dp(context, 8f).toFloat(), y, p)
        if (badge) {
            y += lh
            canvas.drawText("TRAINED", Ui.dp(context, 8f).toFloat(), y, accent)
        }
    }
}
