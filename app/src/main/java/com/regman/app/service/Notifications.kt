package com.regman.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.regman.app.R

object Notifications {
    const val CHANNEL_ID = "regman_register"
    const val NOTIF_ID = 1001

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, ctx.getString(R.string.notif_channel_register), NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun running(ctx: Context, text: String) = NotificationCompat.Builder(ctx, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(ctx.getString(R.string.notif_register_running))
        .setContentText(text)
        .setOngoing(true)
        .build()
}
