package ai.venice.ffaim.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import ai.venice.ffaim.Prefs
import ai.venice.ffaim.Ui

class PanelView(ctx: Context, private val prefs: Prefs) : FrameLayout(ctx) {

    interface Listener {
        fun onAimbot(b: Boolean)
        fun onHeadshot(b: Boolean)
        fun onHeadshotPct(v: Int)
        fun onFov(v: Int)
        fun onFovOffset(dx: Int, dy: Int)
        fun onResetFov()
        fun onResetSelf()
        fun onResetAmmo()
        fun onDebug(b: Boolean)
        fun onRing(b: Boolean)
        fun onLearningMode(b: Boolean)
        fun onRecord()
        fun onStopRecord()
        fun onAdd()
        fun onTest()
        fun onCalibrate()
        fun onBackend(v: Int)
        fun onStopPanel()
        fun onMinimise()
        fun onDrag(dx: Float, dy: Float)
    }

    var listener: Listener? = null

    private val root: LinearLayout
    private val body: LinearLayout
    private val badge: TextView
    private val progress: TextView
    private val recordRow: LinearLayout
    private val learnLabel: TextView

    private lateinit var aimbotSwitch: Switch
    private lateinit var headshotSwitch: Switch
    private lateinit var debugSwitch: Switch
    private lateinit var ringSwitch: Switch
    private lateinit var learnSwitch: Switch
    private lateinit var pctBar: SeekBar
    private lateinit var pctLabel: TextView
    private lateinit var fovBar: SeekBar
    private lateinit var fovLabel: TextView

    init {
        background = Ui.panelBackground(ctx)
        elevation = Ui.dp(ctx, 10f).toFloat()

        root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val header = LinearLayout(ctx)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(Ui.dp(ctx, 14f), Ui.dp(ctx, 10f), Ui.dp(ctx, 8f), Ui.dp(ctx, 6f))
        root.addView(header)

        val title = TextView(ctx)
        title.text = "FF AIM"
        title.setTextColor(Ui.TEXT)
        title.textSize = 14f
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        badge = TextView(ctx)
        badge.text = "LOCKED"
        badge.setTextColor(Ui.AMBER)
        badge.textSize = 10f
        badge.setPadding(Ui.dp(ctx, 6f), Ui.dp(ctx, 2f), Ui.dp(ctx, 6f), Ui.dp(ctx, 2f))
        badge.background = Ui.pill(ctx, Color.TRANSPARENT, Ui.AMBER)
        header.addView(badge)

        val min = TextView(ctx)
        min.text = "\u2013"
        min.setTextColor(Ui.CYAN)
        min.textSize = 18f
        min.setPadding(Ui.dp(ctx, 12f), 0, Ui.dp(ctx, 6f), Ui.dp(ctx, 4f))
        min.setOnClickListener { listener?.onMinimise() }
        header.addView(min)

        header.setOnTouchListener(dragHandler())

        val sv = ScrollView(ctx)
        sv.isFillViewport = true
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        body = LinearLayout(ctx)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(ctx, 14f), Ui.dp(ctx, 4f), Ui.dp(ctx, 14f), Ui.dp(ctx, 14f))
        sv.addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        aimbotSwitch = toggle("Aimbot") { listener?.onAimbot(it) }
        headshotSwitch = toggle("Headshot") { listener?.onHeadshot(it) }

        pctLabel = caption("Headshot 100%")
        pctBar = slider(10, 100, prefs.headshotPct) {
            pctLabel.text = "Headshot $it%"
            listener?.onHeadshotPct(it)
        }

        fovLabel = caption("FOV radius ${prefs.fovRadius}")
        fovBar = slider(90, 340, prefs.fovRadius) {
            fovLabel.text = "FOV radius $it"
            listener?.onFov(it)
        }

        val offX = caption("FOV X offset ${prefs.fovOffX}")
        slider(-300, 300, prefs.fovOffX + 300) {
            offX.text = "FOV X offset ${it - 300}"
            listener?.onFovOffset(it - 300, prefs.fovOffY)
        }
        val offY = caption("FOV Y offset ${prefs.fovOffY}")
        slider(-300, 300, prefs.fovOffY + 300) {
            offY.text = "FOV Y offset ${it - 300}"
            listener?.onFovOffset(prefs.fovOffX, it - 300)
        }

        section("Reset")
        val resetRow = LinearLayout(ctx)
        resetRow.orientation = LinearLayout.HORIZONTAL
        resetRow.addView(small("FOV") { listener?.onResetFov() })
        resetRow.addView(small("Self zone") { listener?.onResetSelf() })
        resetRow.addView(small("Ammo box") { listener?.onResetAmmo() })
        body.addView(resetRow)

        section("Display")
        debugSwitch = toggle("Debug overlay") { listener?.onDebug(it) }
        ringSwitch = toggle("FOV ring") { listener?.onRing(it) }

        section("Injection")
        val injRow = LinearLayout(ctx)
        injRow.orientation = LinearLayout.HORIZONTAL
        injRow.addView(small("Accessibility") { listener?.onBackend(0) })
        injRow.addView(small("Shizuku") { listener?.onBackend(1) })
        injRow.addView(small("Test") { listener?.onTest() })
        body.addView(injRow)

        body.addView(small("Calibration") { listener?.onCalibrate() })

        section("Learning")
        learnSwitch = toggle("Learning mode") { listener?.onLearningMode(it) }
        learnLabel = caption("Train the AI first.")

        progress = TextView(ctx)
        progress.setTextColor(Ui.CYAN)
        progress.textSize = 11f
        body.addView(progress)

        recordRow = LinearLayout(ctx)
        recordRow.orientation = LinearLayout.HORIZONTAL
        recordRow.addView(small("RECORD") { listener?.onRecord() })
        recordRow.addView(small("STOP") { listener?.onStopRecord() })
        recordRow.addView(small("ADD") { listener?.onAdd() })
        body.addView(recordRow)

        body.addView(small("Stop panel") { listener?.onStopPanel() })

        refresh()
    }

    fun refresh() {
        val locked = !prefs.trained
        aimbotSwitch.isChecked = prefs.aimbot
        headshotSwitch.isChecked = prefs.headshot
        debugSwitch.isChecked = prefs.debug
        ringSwitch.isChecked = prefs.ring
        learnSwitch.isChecked = prefs.learningMode
        pctBar.progress = prefs.headshotPct
        pctLabel.text = "Headshot ${prefs.headshotPct}%"
        fovBar.progress = prefs.fovRadius
        fovLabel.text = "FOV radius ${prefs.fovRadius}"

        for (v in listOf<View>(aimbotSwitch, headshotSwitch, debugSwitch, ringSwitch, pctBar, fovBar)) {
            v.isEnabled = !locked
            v.alpha = if (locked) 0.35f else 1f
        }
        pctBar.isEnabled = !locked && prefs.headshot
        pctBar.alpha = if (pctBar.isEnabled) 1f else 0.3f

        recordRow.visibility = if (prefs.learningMode) View.VISIBLE else View.GONE
        badge.text = if (locked) "LOCKED" else "TRAINED"
        badge.setTextColor(if (locked) Ui.AMBER else Ui.OK)
        badge.background = Ui.pill(context, Color.TRANSPARENT, if (locked) Ui.AMBER else Ui.OK)
        learnLabel.text = if (locked) "Train the AI first." else "Model active."
    }

    fun setProgress(t: String) { progress.text = t }

    private fun dragHandler(): OnTouchListener {
        var dx = 0f
        var dy = 0f
        return OnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx = e.rawX
                    dy = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    listener?.onDrag(e.rawX - dx, e.rawY - dy)
                    dx = e.rawX
                    dy = e.rawY
                    true
                }
                else -> true
            }
        }
    }

    private fun section(t: String) {
        val v = TextView(context)
        v.text = t.uppercase()
        v.setTextColor(Ui.TEXT_DIM)
        v.textSize = 9f
        v.setPadding(0, Ui.dp(context, 10f), 0, Ui.dp(context, 2f))
        body.addView(v)
    }

    private fun caption(t: String): TextView {
        val v = TextView(context)
        v.text = t
        v.setTextColor(Ui.TEXT_DIM)
        v.textSize = 11f
        body.addView(v)
        return v
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun toggle(label: String, on: (Boolean) -> Unit): Switch {
        val s = Switch(context)
        s.text = label
        s.setTextColor(Ui.TEXT)
        s.textSize = 12f
        s.thumbTintList = ColorStateList.valueOf(Ui.CYAN)
        s.trackTintList = ColorStateList.valueOf(Color.argb(70, 41, 224, 255))
        s.setPadding(0, Ui.dp(context, 6f), 0, Ui.dp(context, 6f))
        s.setOnCheckedChangeListener { _, b -> on(b) }
        body.addView(s)
        return s
    }

    private fun slider(min: Int, max: Int, value: Int, on: (Int) -> Unit): SeekBar {
        val sb = SeekBar(context)
        sb.max = max - min
        sb.progress = (value - min).coerceIn(0, max - min)
        sb.progressTintList = ColorStateList.valueOf(Ui.CYAN)
        sb.thumbTintList = ColorStateList.valueOf(Ui.CYAN)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = on(p + min)
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        body.addView(sb)
        return sb
    }

    private fun small(label: String, act: () -> Unit): Button {
        val b = Button(context)
        b.text = label
        b.textSize = 10f
        b.isAllCaps = false
        b.setTextColor(Ui.TEXT)
        b.background = Ui.pill(context, Ui.CYAN_SOFT, Ui.CYAN)
        b.minWidth = 0
        b.minHeight = 0
        b.setPadding(Ui.dp(context, 9f), Ui.dp(context, 4f), Ui.dp(context, 9f), Ui.dp(context, 4f))
        b.setOnClickListener { act() }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(context, 30f))
        lp.rightMargin = Ui.dp(context, 6f)
        b.layoutParams = lp
        return b
    }
}
