package ai.venice.ffaim

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AimAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: AimAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Logx.d("accessibility connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
