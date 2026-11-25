package com.memora.app.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.memora.app.MainActivity
import com.memora.app.R

class MemoraReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    ReminderScheduler.ensureNotificationChannel(context)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) return
    }

    val id = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: "memora-rem"
    val title = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TITLE) ?: "MEMORA Reminder"
    val subtitle = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_SUBTITLE)
      ?: "Tap to open your saved memory"

    val openIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra("open_tab", "reminders")
    }
    val openPending = PendingIntent.getActivity(
      context,
      id.hashCode(),
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(title)
      .setContentText(subtitle)
      .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(openPending)
      .build()

    runCatching {
      NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
    }
  }
}
