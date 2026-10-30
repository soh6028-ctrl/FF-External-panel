package ai.venice.ffaim

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val data = res.data
            if (res.resultCode == RESULT_OK && data != null) {
                startOverlay(data)
            } else {
                toast("Screen capture denied")
            }
        }

    private val shizukuListener =
        Shizuku.OnRequestPermissionResultListener { _, grant ->
            toast(if (grant == PackageManager.PERMISSION_GRANTED) "Shizuku granted" else "Shizuku denied")
            render()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Geometry.refresh(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 32, 48, 32)
        root.setBackgroundColor(0xFF05070A.toInt())

        val title = TextView(this)
        title.text = "FF AIM"
        title.setTextColor(Ui.CYAN)
        title.textSize = 22f
        root.addView(title)

        val sub = TextView(this)
        sub.text = "External vision aim panel \u2014 no root, no offsets"
        sub.setTextColor(Ui.TEXT_DIM)
        sub.textSize = 11f
        root.addView(sub)

        status = TextView(this)
        status.setTextColor(Ui.TEXT)
        status.textSize = 11f
        status.setPadding(0, 18, 0, 18)
        root.addView(status)

        root.addView(action("1. Overlay permission") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(action("2. Enable accessibility injector") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(action("3. Shizuku permission (optional)") {
            if (!ShizukuBridge.ping()) {
                toast("Shizuku not running")
            } else if (!ShizukuBridge.hasPermission()) {
                ShizukuBridge.request(7001)
            } else {
                toast("Shizuku already granted")
            }
        })
        root.addView(action("4. Battery optimisation exemption") {
            val i = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(i)
        })
        root.addView(action("START PANEL") { begin() })
        root.addView(action("STOP PANEL") { stopService(Intent(this, OverlayService::class.java)) })

        val sv = ScrollView(this)
        sv.addView(root)
        setContentView(sv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
        ))
        root.gravity = Gravity.START

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 91)
        }
        try { Shizuku.addRequestPermissionResultListener(shizukuListener) } catch (t: Throwable) { }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener) } catch (t: Throwable) { }
        super.onDestroy()
    }

    private fun render() {
        val overlay = Settings.canDrawOverlays(this)
        val acc = AimAccessibilityService.instance != null
        val shz = ShizukuBridge.hasPermission()
        status.text = buildString {
            append(if (overlay) "[ok] overlay\n" else "[--] overlay missing\n")
            append(if (acc) "[ok] accessibility\n" else "[--] accessibility missing\n")
            append(if (shz) "[ok] shizuku\n" else "[--] shizuku optional\n")
            append("\nInjector backend: ")
            append(if (Prefs(this@MainActivity).backend == 0) "accessibility" else "shizuku")
        }
    }

    private fun begin() {
        if (!Settings.canDrawOverlays(this)) {
            toast("Grant overlay permission first")
            return
        }
        if (AimAccessibilityService.instance == null && !ShizukuBridge.hasPermission()) {
            toast("Enable accessibility or grant Shizuku")
            return
        }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun startOverlay(data: Intent) {
        val i = Intent(this, OverlayService::class.java)
        i.putExtra("code", RESULT_OK)
        i.putExtra("data", data)
        ContextCompat.startForegroundService(this, i)
        toast("Panel started \u2014 switch to Free Fire")
        moveTaskToBack(true)
    }

    private fun action(label: String, act: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 12f
        b.allCaps = false
        b.setTextColor(Ui.TEXT)
        b.background = Ui.pill(this, Ui.CYAN_SOFT, Ui.CYAN)
        b.setOnClickListener { act() }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 42f)
        )
        lp.topMargin = Ui.dp(this, 6f)
        b.layoutParams = lp
        return b
    }

    private fun toast(t: String) {
        android.widget.Toast.makeText(this, t, android.widget.Toast.LENGTH_SHORT).show()
    }
}
