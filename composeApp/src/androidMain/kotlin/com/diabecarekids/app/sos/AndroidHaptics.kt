package com.diabecarekids.app.sos

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.diabecarekids.app.platform.Haptics

/**
 * Android [Haptics] using the system vibrator. Uses [VibratorManager] on API 31+
 * and the legacy [Vibrator] singleton below it. The VIBRATE permission is
 * declared in the manifest.
 *
 * On successful SOS activation (REQ-SOS-001 SHOULD) plays a short burst to
 * confirm the trigger to the user even if the screen is not watched.
 */
class AndroidHaptics(
    context: Context,
) : Haptics {

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    override fun vibrateSosTriggered() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    TRIGGER_VIBRATION_MILLIS,
                    VibrationEffect.DEFAULT_AMPLITUDE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(TRIGGER_VIBRATION_MILLIS)
        }
    }

    private companion object {
        const val TRIGGER_VIBRATION_MILLIS = 250L
    }
}
