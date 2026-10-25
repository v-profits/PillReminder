package com.semenenko.pillreminder.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.semenenko.pillreminder.R

/**
 * Окно «Оцените приложение». Оценка 4–5 ведёт на страницу приложения в RuStore,
 * оценка 1–3 открывает письмо разработчику (в почтовом приложении пользователя, сама программа ничего не отправляет).
 * Когда окно показывается, решает [com.semenenko.pillreminder.data.Prefs.ratingDue].
 */
@Composable
fun RatingDialog(onDismiss: () -> Unit, onRated: () -> Unit) {
    val ctx = LocalContext.current
    var stars by rememberSaveable { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rating_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.rating_text))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (i in 1..5) {
                        Icon(
                            if (i <= stars) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = stringResource(R.string.rating_stars_cd, i),
                            tint = if (i <= stars) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(44.dp)
                                .clickable { stars = i },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = stars > 0,
                onClick = {
                    if (stars >= 4) {
                        Toast.makeText(ctx, R.string.rating_thanks, Toast.LENGTH_SHORT).show()
                        openStorePage(ctx)
                    } else {
                        sendFeedbackMail(ctx, stars)
                    }
                    onRated()
                },
            ) { Text(stringResource(R.string.rating_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.rating_later)) } },
    )
}

/** Страница приложения в RuStore: сначала приложение RuStore, если его нет — сайт. */
private fun openStorePage(ctx: Context) {
    val pkg = ctx.packageName
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("rustore://apps.rustore.ru/app/$pkg")))
    } catch (_: ActivityNotFoundException) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.rustore.ru/catalog/app/$pkg")))
        } catch (_: ActivityNotFoundException) {
        }
    }
}

/** Письмо разработчику через почтовое приложение пользователя (без технических данных об устройстве). */
private fun sendFeedbackMail(ctx: Context, stars: Int) {
    val email = ctx.getString(R.string.feedback_email)
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        .putExtra(
            Intent.EXTRA_SUBJECT,
            ctx.getString(R.string.rating_mail_subject, ctx.getString(R.string.app_name), stars),
        )
    try {
        ctx.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, ctx.getString(R.string.rating_no_mail, email), Toast.LENGTH_LONG).show()
    }
}
