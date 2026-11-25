package com.memora.app

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.initialize
import com.memora.app.reminders.ReminderScheduler

class MemoraApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    Firebase.initialize(this)

    // Firebase App Check — required for Firebase AI Logic.
    // Debug provider logs a debug UUID in Logcat for emulators, Samsung test devices, and hackathon judging;
    // Release builds use Play Integrity attestation.
    val factory = if (BuildConfig.DEBUG) {
      DebugAppCheckProviderFactory.getInstance()
    } else {
      PlayIntegrityAppCheckProviderFactory.getInstance()
    }
    Firebase.appCheck.installAppCheckProviderFactory(factory)

    // Enable Cloud Firestore persistent offline cache
    runCatching {
      Firebase.firestore.firestoreSettings = firestoreSettings {
        setLocalCacheSettings(persistentCacheSettings {})
      }
    }

    // Create notification channel for Visual Intelligence Reminders
    ReminderScheduler.ensureNotificationChannel(this)
  }
}
