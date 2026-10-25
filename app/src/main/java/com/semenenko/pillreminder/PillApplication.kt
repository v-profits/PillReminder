package com.semenenko.pillreminder

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.semenenko.pillreminder.data.Prefs
import com.semenenko.pillreminder.notify.Notifier
import com.semenenko.pillreminder.ui.VoiceController

class PillApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        val now = System.currentTimeMillis()
        if (prefs.lastNotified == 0L) prefs.lastNotified = now
        if (prefs.lastMissedCheck == 0L) prefs.lastMissedCheck = now
        Notifier.createChannels(this)
        VoiceController.restoreAudio(this)
    }
}
