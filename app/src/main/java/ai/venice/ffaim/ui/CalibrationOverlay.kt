package ai.venice.ffaim.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import ai.venice.ffaim.Geometry
import ai.venice.ffaim.Prefs
import ai.venice.ffaim.Ui

class CalibrationOverlay(ctx: Context, private val prefs: Prefs) : FrameLayout(ctx) {

    var onClose: (() -> Unit)? = null

    private companion object {
        const val MODE_FOV = 0
        const val MODE_AMMO = 1
        const val MODE_SELF = 2
    }

    private var mode = MODE_FOV
    private val board = Board(ctx)
    private val bar: LinearLayout

    init {
        setBackgroundColor(Color.argb(150, 0, 0, 0))
        addView(board, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 12f), Ui.dp(ctx, 8f), Ui.dp(ctx, 12f), Ui.dp(ctx, 8f))
            background = Ui.pill(ctx, Ui.BG_BOT, Ui.CYAN_SOFT)
        }

        bar.addView(btn("FOV") { mode = MODE_FOV; board.invalidate() })
        bar.addView(btn("AMMO") { mode = MODE_AMMO; board.invalidate() })
        bar.addView(btn("SELF") { mode = MODE_SELF; board.invalidate() })
        bar.addView(btn("Reset") {
            when (mode) {
                MODE_FOV -> prefs.resetFov()
                MODE_AMMO -> prefs.resetAmmoBox()
                else -> prefs.resetSelfZone()
            }
            board.syncFromPrefs()
            board.invalidate()
        })

        val radius = SeekBar(ctx).apply {
            max = 250
            progress = prefs.fovRadius - 90
            layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 150f), Ui.dp(ctx, 34f))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, fromUser: Boolean) {
                    prefs.fovRadius = 90 + v
                    board.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        bar.addView(radius)

        bar.addView(btn("SAVE") {
            board.commit()
            onClose?.invoke()
        })

        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.topMargin = Ui.dp(ctx, 10f)
        addView(bar, lp)
    }

    private fun btn(text: String, act: () -> Unit): Button {
        val b = Button(context)
        b.text = text
        b.textSize = 11f
        b.isAllCaps = false
        b.setTextColor(Ui.TEXT)
        b.background = Ui.pill(context, Ui.CYAN_SOFT, Ui.CYAN)
        b.minWidth = 0
        b.minHeight = 0
        b.setPadding(Ui.dp(context, 10f), Ui.dp(context, 4f), Ui.dp(context, 10f), Ui.dp(context, 4f))
        b.setOnClickListener { act() }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(context, 32f))
        lp.rightMargin = Ui.dp(context, 6f)
        b.layoutParams = lp
        return b
    }

    fun sync() = board.syncFromPrefs()

    private inner class Board(c: Context) : View(c) {

        private val fovRect = Rect()
        private val ammo = Rect()
        private val self = Rect()
        private var handle = -1
        private var downX = 0f
        private var downY = 0f

        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = Ui.dp(c, 2f).toFloat()
            color = Ui.CYAN
        }
        private val ammoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = Ui.dp(c, 2f).toFloat()
            color = Ui.AMBER
        }
        private val selfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = Ui.dp(c, 2f).toFloat()
            color = Ui.DANGER
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Ui.TEXT
            textSize = Ui.dp(c, 11f).toFloat()
        }

        init { syncFromPrefs() }

        fun syncFromPrefs() {
            val cx = Geometry.screenW / 2 + prefs.fovOffX
            val cy = Geometry.screenH / 2 + prefs.fovOffY
            val r = prefs.fovRadius
            fovRect.set(cx - r, cy - r, cx + r, cy + r)
            ammo.set(prefs.ammoBox())
            self.set(prefs.selfZone())
        }

        fun commit() {
            val cx = Geometry.screenW / 2
            val cy = Geometry.screenH / 2
            prefs.fovRadius = fovRect.width() / 2
            prefs.fovOffX = fovRect.centerX() - cx
            prefs.fovOffY = fovRect.centerY() - cy
            prefs.setAmmoBox(ammo)
            prefs.setSelfZone(self)
        }

        override fun onDraw(canvas: Canvas) {
            val r = fovRect.width() / 2f
            val cx = fovRect.centerX().toFloat()
            val cy = fovRect.centerY().toFloat()
            canvas.drawCircle(cx, cy, r, ringPaint)
            canvas.drawRect(ammo, ammoPaint)
            canvas.drawRect(self, selfPaint)
            canvas.drawText("FOV", cx - Ui.dp(context, 12f), cy - r - Ui.dp(context, 8f), text)
            canvas.drawText("AMMO", ammo.left.toFloat(), (ammo.top - Ui.dp(context, 6f)).toFloat(), text)
            canvas.drawText("SELF", self.left.toFloat(), (self.top - Ui.dp(context, 6f)).toFloat(), text)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x
                    downY = e.y
                    handle = when (mode) {
                        MODE_FOV -> if (fovRect.contains(e.x.toInt(), e.y.toInt()) ||
                            Math.hypot(
                                (e.x - fovRect.centerX()).toDouble(),
                                (e.y - fovRect.centerY()).toDouble()
                            ) < fovRect.width() / 2 + 60
                        ) 0 else -1
                        MODE_AMMO -> if (ammo.contains(e.x.toInt(), e.y.toInt())) 1 else -1
                        else -> if (self.contains(e.x.toInt(), e.y.toInt())) 2 else -1
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.x - downX).toInt()
                    val dy = (e.y - downY).toInt()
                    when (handle) {
                        0 -> fovRect.offset(dx, dy)
                        1 -> ammo.offset(dx, dy)
                        2 -> self.offset(dx, dy)
                    }
                    downX = e.x
                    downY = e.y
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    handle = -1
                    return true
                }
            }
            return super.onTouchEvent(e)
        }
    }
}
