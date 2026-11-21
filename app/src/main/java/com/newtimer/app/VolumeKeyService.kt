package com.newtimer.app

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class VolumeKeyService : AccessibilityService() {
    private var handledVolumeKey = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isVolumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (!isVolumeKey) return super.onKeyEvent(event)

        val isRinging = getSharedPreferences("timer", MODE_PRIVATE)
            .getBoolean("is_ringing", false)

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            handledVolumeKey = isRinging
            if (handledVolumeKey) TimerService.stopFromVolumeKey(this)
        }

        // Consume both down/up only while stopping an active ring. Otherwise,
        // let Android handle volume normally, including while the screen is locked.
        return handledVolumeKey
    }
}
