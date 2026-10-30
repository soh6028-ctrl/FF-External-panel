package ai.venice.ffaim

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.lang.reflect.Method
import java.util.concurrent.Executors

object ShizukuBridge {

    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "shizuku") }

    private val newProcess: Method? = try {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            arrayOf<String>().javaClass,
            arrayOf<String>().javaClass,
            String::class.java
        ).apply { isAccessible = true }
    } catch (t: Throwable) {
        null
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
        val m = newProcess ?: return
        exec.execute {
            try {
                val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process
                p?.waitFor()
            } catch (t: Throwable) {
                Logx.e("shizuku exec: $cmd", t)
            }
        }
    }
}
