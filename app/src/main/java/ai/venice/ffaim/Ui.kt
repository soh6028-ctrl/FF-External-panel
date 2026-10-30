package ai.venice.ffaim

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

object Ui {
    const val CYAN = 0xFF29E0FF.toInt()
    const val CYAN_SOFT = 0x5529E0FF
    const val TEXT = 0xFFE8F6FF.toInt()
    const val TEXT_DIM = 0x99B9CBD8.toInt()
    const val BG_TOP = 0xE60C1219.toInt()
    const val BG_BOT = 0xF5060A0F.toInt()
    const val DANGER = 0xFFFF5470.toInt()
    const val OK = 0xFF35F0A0.toInt()
    const val AMBER = 0xFFFFC24B.toInt()

    fun dp(ctx: Context, v: Float): Int =
        (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun panelBackground(ctx: Context): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(BG_TOP, BG_BOT))
        g.cornerRadius = dp(ctx, 20f).toFloat()
        g.setStroke(dp(ctx, 1f), CYAN_SOFT)
        return g
    }

    fun pill(ctx: Context, fill: Int, stroke: Int = Color.TRANSPARENT): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = dp(ctx, 12f).toFloat()
        g.setColor(fill)
        if (stroke != Color.TRANSPARENT) g.setStroke(dp(ctx, 1f), stroke)
        return g
    }
}
