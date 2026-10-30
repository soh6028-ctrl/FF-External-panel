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
    private val live = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = Ui.dp(ctx, 11f).toFloat()
        typeface = android.graphics.Typeface.MONOSPACE
        color = Ui.OK
    }

    var fps = 0
    var targets = 0
    var lock = false
    var onTarget = false
    var rounds = 6
    var badge = false

    fun refresh() { invalidate() }

    override fun onDraw(canvas: Canvas) {
        val lh = Ui.dp(context, 15f).toFloat()
        val x = Ui.dp(context, 8f).toFloat()
        var y = lh
        canvas.drawText("FPS $fps", x, y, p)
        y += lh
        canvas.drawText("TGT $targets", x, y, p)
        y += lh
        canvas.drawText(if (lock) "LOCK ON" else "LOCK off", x, y, if (lock) accent else p)
        y += lh
        canvas.drawText(if (onTarget) "RETICLE red" else "RETICLE grey", x, y, if (onTarget) live else p)
        y += lh
        canvas.drawText("RND $rounds", x, y, p)
        if (badge) {
            y += lh
            canvas.drawText("TRAINED", x, y, accent)
        }
    }
}
