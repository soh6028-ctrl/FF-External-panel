package ai.venice.ffaim

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.WindowManager

object Geometry {
    @Volatile var screenW: Int = 2400
    @Volatile var screenH: Int = 1080
    @Volatile var density: Float = 2.75f

    fun refresh(ctx: Context) {
        try {
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = wm.currentWindowMetrics.bounds
                if (b.width() > 0 && b.height() > 0) {
                    screenW = b.width()
                    screenH = b.height()
                }
            } else {
                @Suppress("DEPRECATION")
                val dm = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(dm)
                if (dm.widthPixels > 0) {
                    screenW = dm.widthPixels
                    screenH = dm.heightPixels
                }
            }
            density = ctx.resources.displayMetrics.density
        } catch (t: Throwable) {
            Logx.e("geometry refresh failed", t)
        }
        val dm = ctx.resources.displayMetrics
        if (screenW < screenH) {
            val t = screenW; screenW = screenH; screenH = t
        }
        Logx.d("display ${screenW}x${screenH} d=${dm.density}")
    }

    fun crosshair(): Point = Point(screenW / 2, screenH / 2)

    fun displayListener(onChange: () -> Unit): DisplayManager.DisplayListener {
        return object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) { onChange() }
        }
    }
}
