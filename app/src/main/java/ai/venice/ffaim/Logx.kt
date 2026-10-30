package ai.venice.ffaim

import android.util.Log

object Logx {
    private const val TAG = "FFAim"
    fun d(m: String) { Log.d(TAG, m) }
    fun e(m: String, t: Throwable? = null) { Log.e(TAG, m, t) }
}
