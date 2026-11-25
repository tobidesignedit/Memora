package com.memora.app.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.memora.app.data.ReminderItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object ReminderScheduler {
  const val CHANNEL_ID = "memora_visual_reminders"
  const val EXTRA_REMINDER_ID = "extra_reminder_id"
  const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
  const val EXTRA_REMINDER_SUBTITLE = "extra_reminder_subtitle"

  fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val existing = manager.getNotificationChannel(CHANNEL_ID)
      if (existing == null) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          "Visual Intelligence Reminders",
          NotificationManager.IMPORTANCE_HIGH,
        ).apply {
          description = "Reminders detected from flyers, timetables, payment notices, and voice notes."
        }
        manager.createNotificationChannel(channel)
      }
    }
  }

  fun scheduleReminder(context: Context, reminder: ReminderItem) {
    if (reminder.status == "completed" || reminder.status == "suggested") return
    ensureNotificationChannel(context)

    val triggerMillis = computeTriggerMillis(reminder.dueDateIso, reminder.dueTime)
    val intent = Intent(context, MemoraReminderReceiver::class.java).apply {
      putExtra(EXTRA_REMINDER_ID, reminder.id)
      putExtra(EXTRA_REMINDER_TITLE, reminder.title)
      val subtitle = buildString {
        append(reminder.dueDateIso)
        if (!reminder.dueTime.isNullOrBlank()) append(" · ${reminder.dueTime}")
        if (!reminder.venue.isNullOrBlank()) append(" · ${reminder.venue}")
      }
      putExtra(EXTRA_REMINDER_SUBTITLE, subtitle)
    }

    val pendingIntent = PendingIntent.getBroadcast(
      context,
      reminder.id.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    runCatching {
      alarmManager.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        triggerMillis,
        pendingIntent,
      )
    }
  }

  fun cancelReminder(context: Context, reminderId: String) {
    val intent = Intent(context, MemoraReminderReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
      context,
      reminderId.hashCode(),
      intent,
      PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )
    if (pendingIntent != null) {
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      alarmManager.cancel(pendingIntent)
    }
  }

  fun fireImmediatePreviewNotification(context: Context, reminder: ReminderItem) {
    ensureNotificationChannel(context)
    val intent = Intent(context, MemoraReminderReceiver::class.java).apply {
      putExtra(EXTRA_REMINDER_ID, reminder.id)
      putExtra(EXTRA_REMINDER_TITLE, "Reminder Set: ${reminder.title}")
      val subtitle = buildString {
        append("Scheduled for ${reminder.dueDateIso}")
        if (!reminder.dueTime.isNullOrBlank()) append(" at ${reminder.dueTime}")
        if (!reminder.venue.isNullOrBlank()) append(" · ${reminder.venue}")
      }
      putExtra(EXTRA_REMINDER_SUBTITLE, subtitle)
    }
    context.sendBroadcast(intent)
  }

  private fun computeTriggerMillis(dueDateIso: String, dueTime: String?): Long {
    val now = System.currentTimeMillis()
    return runCatching {
      val date = LocalDate.parse(dueDateIso)
      val time = if (!dueTime.isNullOrBlank()) {
        val parts = dueTime.trim().split(":")
        val h = parts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val m = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        LocalTime.of(h, m)
      } else {
        LocalTime.of(9, 0)
      }
      val epoch = LocalDateTime.of(date, time)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
      if (epoch > now) epoch else now + 60_000L
    }.getOrElse { now + 60_000L }
  }
}
