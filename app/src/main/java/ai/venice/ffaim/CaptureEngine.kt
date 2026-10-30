package ai.venice.ffaim

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.view.Surface

class CaptureEngine(private val ctx: Context) {

    @Volatile var busy = false
    var onFrame: (() -> Unit)? = null

    private var projection: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val thread = HandlerThread("ff-capture", Process.THREAD_PRIORITY_DISPLAY)
    private var handler: Handler? = null
    private var scratch = IntArray(0)

    private var fovBmp: Bitmap? = null
    private var ammoBmp: Bitmap? = null
    private var halfW = 0
    private var halfH = 0

    var fovOriginX = 0
        private set
    var fovOriginY = 0
        private set

    val fovBitmap: Bitmap? get() = fovBmp
    val ammoBitmap: Bitmap? get() = ammoBmp

    fun start(proj: MediaProjection, prefs: Prefs) {
        projection = proj
        Geometry.refresh(ctx)
        halfW = Geometry.screenW / 2
        halfH = Geometry.screenH / 2
        if (halfW < 64 || halfH < 64) return
        thread.start()
        handler = Handler(thread.looper)
        scratch = IntArray(halfW)
        reader = ImageReader.newInstance(halfW, halfH, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ r -> onImage(r, prefs) }, handler)
        val surf: Surface = reader?.surface ?: return
        vd = projection?.createVirtualDisplay(
            "ffaim",
            halfW, halfH,
            ctx.resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surf, null, handler
        )
    }

    private fun onImage(r: ImageReader, prefs: Prefs) {
        val img = r.acquireLatestImage() ?: return
        try {
            if (busy) return
            val p = img.planes[0]
            val cx = Geometry.screenW / 2 + prefs.fovOffX
            val cy = Geometry.screenH / 2 + prefs.fovOffY
            val halfR = prefs.fovRadius / 2
            val fov = Rect(cx / 2 - halfR, cy / 2 - halfR, cx / 2 + halfR, cy / 2 + halfR)
            clamp(fov)
            if (fov.width() < 32 || fov.height() < 32) return
            val fb = ensure(fov.width(), fov.height(), true) ?: return
            fovOriginX = fov.left
            fovOriginY = fov.top
            copy(p, fov, fb)

            val a = prefs.ammoBox()
            val ar = Rect(a.left / 2, a.top / 2, a.right / 2, a.bottom / 2)
            clamp(ar)
            if (ar.width() >= 12 && ar.height() >= 12) {
                val ab = ensure(ar.width(), ar.height(), false) ?: return
                copy(p, ar, ab)
            }
            onFrame?.invoke()
        } catch (t: Throwable) {
            Logx.e("capture frame", t)
        } finally {
            img.close()
        }
    }

    private fun clamp(r: Rect) {
        if (r.left < 0) r.left = 0
        if (r.top < 0) r.top = 0
        if (r.right > halfW) r.right = halfW
        if (r.bottom > halfH) r.bottom = halfH
        if (r.right <= r.left) r.right = (r.left + 1).coerceAtMost(halfW)
        if (r.bottom <= r.top) r.bottom = (r.top + 1).coerceAtMost(halfH)
    }

    private fun ensure(w: Int, h: Int, isFov: Boolean): Bitmap? {
        val cur = if (isFov) fovBmp else ammoBmp
        if (cur != null && cur.width == w && cur.height == h) return cur
        val nb = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (t: Throwable) {
            return null
        }
        if (isFov) fovBmp = nb else ammoBmp = nb
        return nb
    }

    private fun copy(plane: Image.Plane, src: Rect, dst: Bitmap) {
        val buf = plane.buffer
        val rowStride = plane.rowStride / 4
        val pixStride = plane.pixelStride / 4
        val w = src.width()
        val h = src.height()
        if (pixStride != 1) return
        if (scratch.size < w) scratch = IntArray(w)
        buf.position(0)
        val ib = buf.asIntBuffer()
        for (row in 0 until h) {
            ib.position((src.top + row) * rowStride + src.left)
            ib.get(scratch, 0, w)
            dst.setPixels(scratch, 0, w, 0, row, w, 1)
        }
    }

    fun stop() {
        try { vd?.release() } catch (t: Throwable) { }
        vd = null
        try { reader?.close() } catch (t: Throwable) { }
        reader = null
        try { thread.quitSafely() } catch (t: Throwable) { }
        fovBmp = null
        ammoBmp = null
    }
}
