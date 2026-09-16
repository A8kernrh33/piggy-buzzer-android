package com.a8kernrh33.buzzer

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * User-enabled accessibility service for Buzzer.
 *
 * This service is intentionally limited: it does not read window contents,
 * inspect credentials, perform taps, or capture the screen. Its presence lets
 * the user explicitly enable the Android accessibility capability when needed.
 */
class BuzzerAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally no-op. Accessibility is opt-in and no UI content is read.
    }

    override fun onInterrupt() {
        // Nothing to interrupt.
    }
}
