package io.github.sorry4nothing.cevios.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import io.github.sorry4nothing.cevios.R

object Notifications {
    /** Die Kanäle, die der Server in jedem Push nennt: Ein Kanal, den das Gerät nicht kennt, zeigt den Push nicht. */
    const val CHAT_CHANNEL = "chat"
    const val PROGRAMS_CHANNEL = "programme"
    const val MESSAGES_CHANNEL = "mitteilungen"

    private const val PREFS = "push"
    private const val ASKED = "permission_asked"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHAT_CHANNEL, context.getString(R.string.channel_chat), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(PROGRAMS_CHANNEL, context.getString(R.string.channel_programs), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(MESSAGES_CHANNEL, context.getString(R.string.channel_messages), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    /** Wahr, wenn das Gerät der App Mitteilungen zeigen lässt (Erlaubnis ab Android 13 und Schalter in den Einstellungen). */
    fun enabled(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Wahr genau einmal: wenn die Erlaubnis fehlt (ab Android 13) und die App noch nie gefragt hat. */
    fun shouldAskOnce(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || enabled(context)) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(ASKED, false)) return false
        prefs.edit { putBoolean(ASKED, true) }
        return true
    }
}
