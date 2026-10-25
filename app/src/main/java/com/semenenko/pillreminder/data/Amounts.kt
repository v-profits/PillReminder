package com.semenenko.pillreminder.data

import android.content.Context
import com.semenenko.pillreminder.R

/*
 * Количества хранятся в ЧЕТВЕРТЯХ таблетки (целые числа, без ошибок округления):
 * 1 = ¼, 2 = ½, 3 = ¾, 4 = 1 таблетка, 6 = 1½ и т. д.
 */

/** Короткая запись: «¼», «½», «1», «1½», «2»... */
fun quartersGlyph(q: Int): String {
    val whole = q / 4
    val frac = when (q % 4) {
        1 -> "¼"
        2 -> "½"
        3 -> "¾"
        else -> ""
    }
    return if (whole == 0) frac else "$whole$frac"
}

/** Полная запись с единицей: «½ таблетки», «1 таблетка», «2 таблетки», «12½ таблетки». */
fun formatAmount(ctx: Context, q: Int): String {
    val whole = q / 4
    return when {
        q % 4 == 0 -> ctx.resources.getQuantityString(R.plurals.tablets_count, whole, whole)
        whole == 0 -> ctx.getString(R.string.amount_frac_small, quartersGlyph(q))
        else -> ctx.getString(R.string.amount_frac_big, quartersGlyph(q))
    }
}

/** Текст поля «12.5» → четверти (50). Допускает запятую; округляет до ближайшей четверти. */
fun parseQuarters(text: String): Int? {
    val d = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    return Math.round(d * 4).toInt().takeIf { it >= 0 }
}

/** Четверти → текст для поля ввода: 50 → «12.5», 49 → «12.25», 48 → «12». */
fun quartersToInput(q: Int): String {
    val whole = q / 4
    return when (q % 4) {
        0 -> "$whole"
        1 -> "$whole.25"
        2 -> "$whole.5"
        else -> "$whole.75"
    }
}

/** Оставляет только цифры и одну точку (запятая заменяется на точку), максимум 2 знака после неё. */
fun sanitizeDecimal(s: String): String {
    val sb = StringBuilder()
    var dot = false
    var decimals = 0
    var ints = 0
    for (c in s) {
        if (c.isDigit()) {
            if (dot) {
                if (decimals < 2) {
                    sb.append(c)
                    decimals++
                }
            } else if (ints < 5) {
                sb.append(c)
                ints++
            }
        } else if ((c == '.' || c == ',') && !dot) {
            dot = true
            sb.append('.')
        }
    }
    return sb.toString()
}
