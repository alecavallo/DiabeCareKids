package com.diabecarekids.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.diabecarekids.app.domain.TipoComida

/**
 * Shows an offline local meal reminder via [NotificationManager] (design D6).
 *
 * Uses a simple `meal_reminders` channel with default importance, guarded for
 * API 26+ (channel is a no-op below O). The small icon is a system drawable
 * because this app ships no image assets. POST_NOTIFICATIONS (API 33+) is
 * declared in the manifest; the runtime request is deferred/no-op for this
 * slice — on API 33+ the notify is silently dropped when the permission is
 * denied, which is an acceptable placeholder per design.
 */
class MealReminderNotifier(
    private val context: Context,
) {

    fun showReminder(tipo: TipoComida) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val builder = notificationBuilder(tipo)

        manager.notify(tipo.name.hashCode(), builder.build())
    }

    private fun notificationBuilder(tipo: TipoComida): android.app.Notification.Builder {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(MEAL_TITLES.getValue(tipo))
            .setContentText(CONTENT_TEXT)
            .setAutoCancel(true)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "meal_reminders"
        const val CHANNEL_NAME = "Meal reminders"
        const val CONTENT_TEXT = "It's almost time for this meal — get ready to log it."

        private val MEAL_TITLES = mapOf(
            TipoComida.DESAYUNO to "Breakfast reminder",
            TipoComida.ALMUERZO to "Lunch reminder",
            TipoComida.MERIENDA to "Snack reminder",
            TipoComida.CENA to "Dinner reminder",
        )
    }
}
