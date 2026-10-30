package ai.venice.ffaim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import ai.venice.ffaim.ui.BubbleView
import ai.venice.ffaim.ui.CalibrationOverlay
import ai.venice.ffaim.ui.DebugOverlayView
import ai.venice.ffaim.ui.FovRingView
import ai.venice.ffaim.ui.HudView
import ai.venice.ffaim.ui.PanelView

class OverlayService : Service(), PanelView.Listener {

    companion object {
        @Volatile var running = false
        private const val CH = "ffaim"
        private const val NID = 4201
        private const val TICK = 8L
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var detector: Detector
    private val engine = AimEngine()
    private val ammo = AmmoCounter()
    private lateinit var trainer: Trainer
    private var capture: CaptureEngine? = null
    private var projection: MediaProjection? = null
    private var backend: InjectionBackend? = null

    private var panel: PanelView? = null
    private var bubble: BubbleView? = null
    private var ring: FovRingView? = null
    private var hud: HudView? = null
    private var dbg: DebugOverlayView? = null
    private var cal: CalibrationOverlay? = null

    private val main = Handler(Looper.getMainLooper())
    private var visionThread: HandlerThread? = null
    private var visionHandler: Handler? = null
    private var visionBusy = false
    private var lastFpsT = 0L
    private var frames = 0
    private var fps = 0

    private var lastLock = false
    private var lastOn = false

    private var displayListener: DisplayManager.DisplayListener? = null

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            aimTick()
            main.postDelayed(this, TICK)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        trainer = Trainer(this)
        detector = Detector()
        ModelStore.load(this)?.let { detector.model = it }
        Geometry.refresh(this)
        startVisionThread()
        ammo.onRound = { engine.onRoundFired() }
        registerDisplayListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY
        startForegroundCompat()
        val code = intent.getIntExtra("code", 0)
        val data = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra("data", Intent::class.java)
        else
            @Suppress("DEPRECATION") intent.getParcelableExtra("data")
        if (data != null && code != 0) {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = mpm.getMediaProjection(code, data)
            if (proj != null) attach(proj)
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CH, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(ch)
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = Notification.Builder(this, CH)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun attach(proj: MediaProjection) {
        projection = proj
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                main.post { shutdown() }
            }
        }, main)
        buildOverlays()
        val cap = CaptureEngine(this)
        cap.onFrame = { onFrame() }
        cap.start(proj, prefs)
        capture = cap
        backend = if (prefs.backend == 1) ShizukuBackend() else AccessibilityBackend()
        refreshModel()
        main.post(ticker)
    }

    private fun refreshModel() {
        val m = ModelStore.load(this)
        if (m != null) detector.model = m
    }

    private fun startVisionThread() {
        val t = HandlerThread("ff-vision", Process.THREAD_PRIORITY_DISPLAY)
        t.start()
        visionThread = t
        visionHandler = Handler(t.looper)
    }

    private fun onFrame() {
        if (visionBusy) return
        visionBusy = true
        val h = visionHandler
        if (h == null) {
            visionBusy = false
            return
        }
        h.post { processFrame() }
    }

    private fun processFrame() {
        try {
            val cap = capture ?: return
            val fov = cap.fovBitmap ?: return
            val now = SystemClock.uptimeMillis()
            val cross = Geometry.crosshair()
            detector.detect(fov, cap.fovOriginX, cap.fovOriginY, cap.invScale,
                cross.x.toFloat(), cross.y.toFloat())
            ammo.feed(cap.ammoBitmap, now)
            if (trainer.recording) trainer.sample(detector, detector.count)
            engine.setDetections(detector.items, detector.count, detector.reticleRed)
            if (prefs.debug) dbg?.update(detector.items, detector.count)

            frames++
            if (now - lastFpsT >= 1000L) {
                fps = frames
                frames = 0
                lastFpsT = now
                main.post {
                    val h = hud ?: return@post
                    h.fps = fps
                    h.targets = detector.count
                    h.rounds = ammo.roundsRemaining
                    h.badge = prefs.trained
                    h.refresh()
                }
            }
        } catch (t: Throwable) {
            Logx.e("vision", t)
        } finally {
            capture?.busy = false
            visionBusy = false
        }
    }

    private fun aimTick() {
        val cross = Geometry.crosshair()
        engine.aimbot = prefs.aimbot && prefs.trained
        engine.headshot = prefs.headshot
        engine.headshotPct = prefs.headshotPct
        val act = engine.tick(
            SystemClock.uptimeMillis(),
            cross.x.toFloat(), cross.y.toFloat(), prefs.fovRadius.toFloat()
        )
        if (act != AimEngine.NONE) dispatchAim(act, engine.dx, engine.dy, cross)

        if (engine.lockOn != lastLock || engine.onTarget != lastOn) {
            lastLock = engine.lockOn
            lastOn = engine.onTarget
            hud?.lock = lastLock
            hud?.onTarget = lastOn
            hud?.refresh()
        }
    }

    private fun dispatchAim(act: Int, dx: Float, dy: Float, cross: Point) {
        val b = backend ?: return
        when (act) {
            AimEngine.PRESS -> {
                b.press(cross.x.toFloat(), cross.y.toFloat())
                if (dx != 0f || dy != 0f) b.dragBy(dx, dy)
            }
            AimEngine.MOVE -> b.dragBy(dx, dy)
            AimEngine.RELEASE -> b.release()
        }
    }

    private fun overlayParams(touchable: Boolean): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams()
        p.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        p.format = PixelFormat.TRANSLUCENT
        p.gravity = Gravity.TOP or Gravity.START
        p.flags = if (touchable) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        return p
    }

    private fun buildOverlays() {
        main.post {
            if (prefs.ring) addRing()
            if (prefs.debug) addDebug()
            addHud()
            addPanel()
        }
    }

    private fun addRing() {
        if (ring != null) return
        val v = FovRingView(this, prefs)
        val p = overlayParams(false)
        p.width = Geometry.screenW
        p.height = Geometry.screenH
        p.x = 0
        p.y = 0
        ring = v
        try { wm.addView(v, p) } catch (t: Throwable) { Logx.e("ring", t) }
    }

    private fun addDebug() {
        if (dbg != null) return
        val v = DebugOverlayView(this)
        val p = overlayParams(false)
        p.width = Geometry.screenW
        p.height = Geometry.screenH
        dbg = v
        try { wm.addView(v, p) } catch (t: Throwable) { Logx.e("dbg", t) }
    }

    private fun addHud() {
        if (hud != null) return
        val v = HudView(this)
        val p = overlayParams(false)
        p.width = Ui.dp(this, 104f)
        p.height = Ui.dp(this, 108f)
        p.x = Ui.dp(this, 10f)
        p.y = Ui.dp(this, 8f)
        hud = v
        try { wm.addView(v, p) } catch (t: Throwable) { Logx.e("hud", t) }
    }

    private fun addPanel() {
        if (panel != null) return
        val v = PanelView(this, prefs)
        v.listener = this
        val p = overlayParams(true)
        p.width = Ui.dp(this, 250f)
        p.height = (Geometry.screenH * 0.82f).toInt()
        p.x = prefs.panelX
        p.y = prefs.panelY
        panel = v
        try { wm.addView(v, p) } catch (t: Throwable) { Logx.e("panel", t) }
        v.refresh()
    }

    private fun removePanel() {
        val v = panel ?: return
        panel = null
        try { wm.removeView(v) } catch (t: Throwable) { }
    }

    private fun showBubble() {
        if (bubble != null) return
        val v = BubbleView(this)
        v.onTap = {
            removeBubble()
            addPanel()
        }
        v.onDrag = { dx, dy ->
            val p = v.layoutParams as WindowManager.LayoutParams
            p.x = (p.x + dx).toInt()
            p.y = (p.y + dy).toInt()
            prefs.bubbleX = p.x
            prefs.bubbleY = p.y
            try { wm.updateViewLayout(v, p) } catch (t: Throwable) { }
        }
        val p = overlayParams(true)
        val size = Ui.dp(this, 52f)
        p.width = size
        p.height = size
        p.x = prefs.bubbleX
        p.y = prefs.bubbleY
        bubble = v
        try { wm.addView(v, p) } catch (t: Throwable) { Logx.e("bubble", t) }
    }

    private fun removeBubble() {
        val v = bubble ?: return
        bubble = null
        try { wm.removeView(v) } catch (t: Throwable) { }
    }

    private fun openCalibration() {
        if (cal != null) return
        val v = CalibrationOverlay(this, prefs)
        v.onClose = {
            cal = null
            try { wm.removeView(v) } catch (t: Throwable) { }
            refreshModel()
            ring?.invalidate()
        }
        val p = overlayParams(true)
        p.width = Geometry.screenW
        p.height = Geometry.screenH
        cal = v
        try { wm.addView(v, p) } catch (t: Throwable) { Logx.e("cal", t) }
    }

    override fun onAimbot(b: Boolean) {
        prefs.aimbot = b
        if (!b) {
            engine.releaseNow()
            backend?.release()
        }
    }

    override fun onHeadshot(b: Boolean) {
        prefs.headshot = b
        engine.resetBurst()
        panel?.refresh()
    }

    override fun onHeadshotPct(v: Int) {
        prefs.headshotPct = v
        engine.resetBurst()
    }

    override fun onFov(v: Int) {
        prefs.fovRadius = v
        ring?.invalidate()
    }

    override fun onFovOffset(dx: Int, dy: Int) {
        prefs.fovOffX = dx
        prefs.fovOffY = dy
        ring?.invalidate()
    }

    override fun onResetFov() {
        prefs.resetFov()
        ring?.invalidate()
        panel?.refresh()
    }

    override fun onResetSelf() { prefs.resetSelfZone() }

    override fun onResetAmmo() { prefs.resetAmmoBox() }

    override fun onDebug(b: Boolean) {
        prefs.debug = b
        if (b) {
            addDebug()
        } else {
            val v = dbg ?: return
            dbg = null
            try { wm.removeView(v) } catch (t: Throwable) { }
        }
    }

    override fun onRing(b: Boolean) {
        prefs.ring = b
        if (b) {
            addRing()
        } else {
            val v = ring ?: return
            ring = null
            try { wm.removeView(v) } catch (t: Throwable) { }
        }
    }

    override fun onLearningMode(b: Boolean) {
        prefs.learningMode = b
        panel?.refresh()
    }

    override fun onRecord() {
        trainer.start()
        ammo.reset()
        engine.resetBurst()
        panel?.setProgress("Recording\u2026")
    }

    override fun onStopRecord() {
        trainer.stop()
        panel?.setProgress("Stopped \u2014 tap ADD to keep it")
    }

    override fun onAdd() {
        trainer.stop()
        if (trainer.addCount() < 8) {
            panel?.setProgress("Too little data \u2014 record longer")
            return
        }
        if (trainer.save()) {
            val n = ModelStore.sessionCount(this)
            panel?.setProgress("Saved recording $n/3")
            if (n >= 3) train()
        } else {
            panel?.setProgress("Save failed")
        }
    }

    private fun train() {
        panel?.setProgress("Learning 1/3")
        main.postDelayed({
            panel?.setProgress("Learning 2/3")
            main.postDelayed({
                panel?.setProgress("Learning 3/3")
                main.postDelayed({
                    panel?.setProgress("Building model\u2026")
                    val t = visionThread
                    if (t == null) build() else Handler(t.looper).post { build() }
                }, 500L)
            }, 400L)
        }, 400L)
    }

    private fun build() {
        val model = trainer.build()
        main.post {
            if (model == null) {
                panel?.setProgress("Not enough clean samples \u2014 record again")
                return@post
            }
            ModelStore.save(this, model)
            detector.model = model
            prefs.trained = true
            engine.resetBurst()
            panel?.refresh()
            panel?.setProgress("Understood \u2014 enemies, teammates, my character, and head positions are all clear.")
            Toast.makeText(this, "TRAINED SUCCESSFUL \u2014 all toggles unlocked", Toast.LENGTH_LONG).show()
            banner()
        }
    }

    private fun banner() {
        val box = FrameLayout(this)
        box.background = Ui.panelBackground(this)
        val lay = FrameLayout(this)
        val tv = TextView(this)
        tv.text = "TRAINED SUCCESSFUL"
        tv.setTextColor(Ui.OK)
        tv.textSize = 18f
        tv.gravity = Gravity.CENTER
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        val tv2 = TextView(this)
        tv2.text = "All toggles unlocked."
        tv2.setTextColor(Ui.TEXT_DIM)
        tv2.textSize = 11f
        tv2.gravity = Gravity.CENTER
        lay.addView(tv, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        val lp2 = FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)
        lp2.topMargin = Ui.dp(this, 26f)
        lay.addView(tv2, lp2)
        box.addView(lay, FrameLayout.LayoutParams(-1, Ui.dp(this, 78f), Gravity.CENTER))
        val p = overlayParams(false)
        p.width = Ui.dp(this, 300f)
        p.height = Ui.dp(this, 78f)
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        p.y = Ui.dp(this, 40f)
        try { wm.addView(box, p) } catch (t: Throwable) { return }
        main.postDelayed({
            try { wm.removeView(box) } catch (t: Throwable) { }
        }, 2800L)
    }

    override fun onTest() {
        val b = backend ?: return
        val cross = Geometry.crosshair()
        b.press(cross.x.toFloat(), cross.y.toFloat())
        main.postDelayed({ b.dragBy(90f, 0f) }, 60L)
        main.postDelayed({ b.dragBy(-45f, 30f) }, 160L)
        main.postDelayed({
            b.release()
            Toast.makeText(this, "Test swipe sent \u2014 did the camera move?", Toast.LENGTH_SHORT).show()
        }, 300L)
    }

    override fun onCalibrate() = openCalibration()

    override fun onBackend(v: Int) {
        prefs.backend = v
        backend?.release()
        backend = if (v == 1) ShizukuBackend() else AccessibilityBackend()
        Toast.makeText(this, if (v == 1) "Shizuku backend" else "Accessibility backend", Toast.LENGTH_SHORT).show()
    }

    override fun onStopPanel() = shutdown()

    override fun onMinimise() {
        removePanel()
        showBubble()
    }

    override fun onDrag(dx: Float, dy: Float) {
        val v = panel ?: return
        val p = v.layoutParams as WindowManager.LayoutParams
        p.x = (p.x + dx).toInt().coerceIn(-Ui.dp(this, 60f), Geometry.screenW - Ui.dp(this, 80f))
        p.y = (p.y + dy).toInt().coerceIn(-Ui.dp(this, 10f), Geometry.screenH - Ui.dp(this, 60f))
        prefs.panelX = p.x
        prefs.panelY = p.y
        try { wm.updateViewLayout(v, p) } catch (t: Throwable) { }
    }

    private fun registerDisplayListener() {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val l = Geometry.displayListener { main.post { onDisplayChanged() } }
        displayListener = l
        try { dm.registerDisplayListener(l, main) } catch (t: Throwable) { }
    }

    private fun onDisplayChanged() {
        Geometry.refresh(this)
        resize(ring)
        resize(dbg)
        resize(cal)
        ring?.invalidate()
    }

    private fun resize(v: View?) {
        if (v == null) return
        val p = v.layoutParams as? WindowManager.LayoutParams ?: return
        p.width = Geometry.screenW
        p.height = Geometry.screenH
        try { wm.updateViewLayout(v, p) } catch (t: Throwable) { }
    }

    private fun shutdown() {
        running = false
        main.removeCallbacks(ticker)
        backend?.release()
        capture?.stop()
        capture = null
        try { projection?.stop() } catch (t: Throwable) { }
        projection = null
        visionHandler?.looper?.quitSafely()
        visionHandler = null
        visionThread = null
        displayListener?.let {
            try {
                (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(it)
            } catch (e: Throwable) { }
        }
        displayListener = null
        val views = listOf<View?>(cal, panel, bubble, ring, hud, dbg)
        for (v in views) {
            if (v != null) try { wm.removeView(v) } catch (t: Throwable) { }
        }
        cal = null
        panel = null
        bubble = null
        ring = null
        hud = null
        dbg = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacks(ticker)
        try { backend?.release() } catch (t: Throwable) { }
        super.onDestroy()
    }
}
