package ai.venice.ffaim

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

object ShizukuBridge {

    private val exec = Executors.newSingleThreadExecutor { r ->
        Thread(r, "shizuku").also { it.priority = Thread.NORM_PRIORITY + 1 }
    }

    fun ping(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        if (!ping()) false
        else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun request(code: Int) {
        try {
            Shizuku.requestPermission(code)
        } catch (t: Throwable) {
            Logx.e("shizuku request failed", t)
        }
    }

    fun run(cmd: String) {
        if (!hasPermission()) return
        exec.execute {
            try {
                val p = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
                p.waitFor()
            } catch (t: Throwable) {
                Logx.e("shizuku exec: $cmd", t)
            }
        }
    }
}
