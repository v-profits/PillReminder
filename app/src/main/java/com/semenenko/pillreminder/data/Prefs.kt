package com.semenenko.pillreminder.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

const val RATING_FIRST_AT = 10
const val RATING_REPEAT_EVERY = 50

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var themeMode: Int
        get() = sp.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(v) = sp.edit { putInt("theme", v) }

    var hideOnLockScreen: Boolean
        get() = sp.getBoolean("hide_lock", false)
        set(v) = sp.edit { putBoolean("hide_lock", v) }

    /** Через сколько минут повторить напоминание, если уведомление смахнули, а приём не отмечен (0 — не повторять). */
    var repeatMinutes: Int
        get() = sp.getInt("repeat", 10)
        set(v) = sp.edit { putInt("repeat", v) }

    var lastNotified: Long
        get() = sp.getLong("last_notified", 0L)
        set(v) = sp.edit { putLong("last_notified", v) }

    var lastMissedCheck: Long
        get() = sp.getLong("last_missed", 0L)
        set(v) = sp.edit { putLong("last_missed", v) }

    /** Сколько раз пользователь открывал приложение. */
    var launchCount: Int
        get() = sp.getInt("launch_count", 0)
        set(v) = sp.edit { putInt("launch_count", v) }

    /** Пользователь уже поставил оценку: окно с просьбой оценить больше не показывается. */
    var ratingDone: Boolean
        get() = sp.getBoolean("rating_done", false)
        set(v) = sp.edit { putBoolean("rating_done", v) }

    /** С какого по счёту открытия показывать окно оценки (первый раз — 10-е открытие). */
    var ratingNextAt: Int
        get() = sp.getInt("rating_next_at", RATING_FIRST_AT)
        set(v) = sp.edit { putInt("rating_next_at", v) }

    /** Пора ли показать окно оценки. При «да» следующий показ откладывается ещё на [RATING_REPEAT_EVERY] открытий. */
    fun ratingDue(): Boolean {
        if (ratingDone || launchCount < ratingNextAt) return false
        ratingNextAt = launchCount + RATING_REPEAT_EVERY
        return true
    }

    var disclaimerAccepted: Boolean
        get() = sp.getBoolean("disclaimer", false)
        set(v) = sp.edit { putBoolean("disclaimer", v) }

    /** Звуковые потоки, приглушённые на время голосового ввода (чтобы вернуть звук после сбоя). */
    var mutedStreams: String
        get() = sp.getString("muted_streams", "").orEmpty()
        set(v) = sp.edit { putString("muted_streams", v) }

    fun reset() = sp.edit { clear() }
}
