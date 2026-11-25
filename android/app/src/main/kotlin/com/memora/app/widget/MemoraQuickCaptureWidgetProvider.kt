package com.memora.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.memora.app.MainActivity
import com.memora.app.R

class MemoraQuickCaptureWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    for (appWidgetId in appWidgetIds) {
      val views = RemoteViews(context.packageName, R.layout.widget_quick_capture)

      views.setOnClickPendingIntent(
        R.id.widget_root_open,
        buildLaunchIntent(context, 101, "vault", openCapture = false),
      )
      views.setOnClickPendingIntent(
        R.id.widget_btn_ask,
        buildLaunchIntent(context, 102, "search", openCapture = false),
      )
      views.setOnClickPendingIntent(
        R.id.widget_btn_capture,
        buildLaunchIntent(context, 103, "vault", openCapture = true),
      )

      appWidgetManager.updateAppWidget(appWidgetId, views)
    }
  }

  private fun buildLaunchIntent(
    context: Context,
    requestCode: Int,
    tab: String,
    openCapture: Boolean,
  ): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra("open_tab", tab)
      putExtra("open_capture", openCapture)
    }
    return PendingIntent.getActivity(
      context,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}
