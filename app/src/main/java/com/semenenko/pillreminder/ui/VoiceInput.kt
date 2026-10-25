package com.semenenko.pillreminder.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.semenenko.pillreminder.R
import com.semenenko.pillreminder.data.Prefs
import java.util.Locale

/**
 * Голосовой ввод через стандартное окно распознавания речи Google (RecognizerIntent), без звуковых сигналов.
 *
 * На время показа окна приглушаются звуковые сигналы начала/конца записи, потом громкость возвращается.
 * Сама программа микрофон не использует и разрешение на запись звука не запрашивает: речь обрабатывает
 * системный сервис распознавания.
 * В текстовых полях речь ДОПИСЫВАЕТСЯ к уже введённому (первая буква и буква после . ! ? — заглавные),
 * в числовых полях значение заменяется. Очистить поле можно крестиком.
 */
@Stable
class VoiceController internal constructor(
    private val ctx: Context,
    private val locale: Locale,
) {
    val available: Boolean =
        ctx.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()

    internal var launch: (Intent) -> Unit = {}
    private var pending: ((String) -> Unit)? = null

    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val prefs = Prefs(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private val mutedByUs = mutableListOf<Int>()
    private val unmuteRunnable = Runnable { unmuteBeeps() }

    /** Показать окно распознавания; результат придёт в [onText]. */
    fun start(onText: (String) -> Unit) {
        val lang = when (locale.language) {
            "ru" -> "ru-RU"
            "en" -> "en-US"
            else -> locale.toLanguageTag()
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        pending = onText
        muteBeeps()
        try {
            launch(intent)
        } catch (e: Exception) {
            pending = null
            Toast.makeText(ctx, R.string.voice_error, Toast.LENGTH_SHORT).show()
            scheduleUnmute()
        }
    }

    internal fun onResult(code: Int, data: Intent?) {
        val callback = pending
        pending = null
        scheduleUnmute()
        if (code != Activity.RESULT_OK) return
        data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?.let { callback?.invoke(it) }
    }

    /** Небольшая задержка, чтобы не прозвучал «хвост» сигнала окончания записи. */
    private fun scheduleUnmute() {
        handler.removeCallbacks(unmuteRunnable)
        handler.postDelayed(unmuteRunnable, 600)
    }

    fun release() {
        handler.removeCallbacks(unmuteRunnable)
        pending = null
        unmuteBeeps()
    }

    private fun muteBeeps() {
        handler.removeCallbacks(unmuteRunnable)
        for (s in BEEP_STREAMS) {
            try {
                if (s !in mutedByUs && !audio.isStreamMute(s)) {
                    audio.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0)
                    mutedByUs += s
                }
            } catch (_: SecurityException) {
                // Режим «Не беспокоить» не даёт менять этот поток — пропускаем
            }
        }
        // Запоминаем на случай, если процесс будет убит до возврата звука
        prefs.mutedStreams = mutedByUs.joinToString(",")
    }

    private fun unmuteBeeps() {
        for (s in mutedByUs) {
            try {
                audio.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0)
            } catch (_: SecurityException) {
            }
        }
        mutedByUs.clear()
        prefs.mutedStreams = ""
    }

    companion object {
        private val BEEP_STREAMS = intArrayOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM,
        )

        /** Вызывается при старте приложения: вернуть звук, если прошлый запуск был прерван во время распознавания. */
        fun restoreAudio(ctx: Context) {
            val prefs = Prefs(ctx)
            val saved = prefs.mutedStreams
            if (saved.isEmpty()) return
            val audio = ctx.getSystemService(AudioManager::class.java)
            saved.split(",").mapNotNull { it.toIntOrNull() }.forEach {
                try {
                    audio.adjustStreamVolume(it, AudioManager.ADJUST_UNMUTE, 0)
                } catch (_: SecurityException) {
                }
            }
            prefs.mutedStreams = ""
        }
    }
}

@Composable
fun rememberVoiceController(): VoiceController {
    val ctx = LocalContext.current
    val locale = currentLocale()
    val controller = remember(locale) { VoiceController(ctx, locale) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        controller.onResult(it.resultCode, it.data)
    }
    controller.launch = { launcher.launch(it) }
    // Окно Google закрывает наш экран только «поверх», поэтому звук возвращаем лишь при выходе с экрана
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    return controller
}

/**
 * Поле ввода с кнопкой микрофона и крестиком очистки.
 * Для числовых полей ([numeric]) из речи извлекается число («двадцать пять» → 25) и заменяет значение.
 */
@Composable
fun VoiceTextField(
    id: String,
    value: String,
    onValueChange: (String) -> Unit,
    voice: VoiceController,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    maxDigits: Int = 4,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        trailingIcon = if (voice.available || value.isNotEmpty()) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Крестик появляется, когда в поле что-то есть
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.field_clear))
                        }
                    }
                    if (voice.available) {
                        IconButton(
                            onClick = {
                                // Текст в поле на момент нажатия: новая речь дописывается к нему
                                val base = value
                                voice.start { raw ->
                                    val text = if (numeric) {
                                        parseSpokenNumber(raw)?.toString()?.take(maxDigits)
                                    } else {
                                        continueText(base, raw)
                                    }
                                    if (!text.isNullOrEmpty()) onValueChange(text)
                                }
                            },
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.voice_start))
                        }
                    }
                }
            }
        } else null,
    )
}

private val SENTENCE_START = Regex("(^|[.!?]\\s+)(\\p{Ll})")

/** Заглавная буква в начале текста и после «. ! ?». */
internal fun capitalizeSentences(s: String): String =
    SENTENCE_START.replace(s.trimStart()) { it.groupValues[1] + it.groupValues[2].uppercase() }

/** Распознанная речь дописывается к уже введённому тексту [base]. */
internal fun continueText(base: String, spoken: String): String {
    val joined = if (base.isBlank()) spoken.trim() else base.trimEnd() + " " + spoken.trim()
    return capitalizeSentences(joined)
}

private val NUMBER_WORDS: Map<String, Int> = mapOf(
    // русский
    "ноль" to 0, "один" to 1, "одна" to 1, "одну" to 1, "одно" to 1, "два" to 2, "две" to 2, "три" to 3,
    "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10,
    "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13, "четырнадцать" to 14, "пятнадцать" to 15,
    "шестнадцать" to 16, "семнадцать" to 17, "восемнадцать" to 18, "девятнадцать" to 19,
    "двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50, "шестьдесят" to 60,
    "семьдесят" to 70, "восемьдесят" to 80, "девяносто" to 90, "сто" to 100,
    // английский
    "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
    "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
    "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
    "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60,
    "seventy" to 70, "eighty" to 80, "ninety" to 90,
)

/** Число из распознанной речи: цифры («25») или слова («двадцать пять», «twenty five»). */
internal fun parseSpokenNumber(raw: String): Int? {
    Regex("\\d+").find(raw)?.value?.toIntOrNull()?.let { return it }
    var total = 0
    var found = false
    for (w in raw.lowercase().split(Regex("[\\s,.-]+"))) {
        if (w == "hundred") {
            total = if (total in 1..9) total * 100 else total + 100
            found = true
            continue
        }
        NUMBER_WORDS[w]?.let {
            total += it
            found = true
        }
    }
    return if (found) total else null
}
