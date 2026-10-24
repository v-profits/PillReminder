package ru.pillreminder.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.pillreminder.app.MainActivity
import ru.pillreminder.app.R
import ru.pillreminder.app.data.Medication
import ru.pillreminder.app.data.formatAmount
import ru.pillreminder.app.data.millisToLdt
import ru.pillreminder.app.data.Prefs
import java.util.Date
import java.util.Objects

object Notifier {
    const val CH_DOSE = "doses"
    const val CH_STOCK = "stock"

    const val ACTION_TAKEN = "ru.pillreminder.app.ACTION_TAKEN"
    const val ACTION_SKIP = "ru.pillreminder.app.ACTION_SKIP"
    const val ACTION_DISMISSED = "ru.pillreminder.app.ACTION_DISMISSED"
    const val EXTRA_MED = "med_id"
    const val EXTRA_AT = "scheduled_at"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_DOSE, ctx.getString(R.string.channel_doses), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = ctx.getString(R.string.channel_doses_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_STOCK, ctx.getString(R.string.channel_stock), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = ctx.getString(R.string.channel_stock_desc) },
        )
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notifId(medId: Long, at: Long): Int = Objects.hash(medId, at)

    private fun actionIntent(ctx: Context, action: String, medId: Long, at: Long): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("pill://dose/$medId/$at/$action"))
            .putExtra(EXTRA_MED, medId)
            .putExtra(EXTRA_AT, at)
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openAppIntent(ctx: Context, requestCode: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, requestCode, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @SuppressLint("MissingPermission")
    fun showDose(ctx: Context, med: Medication, at: Long) {
        if (!canPost(ctx)) return
        val id = notifId(med.id, at)
        val time = DateFormat.getTimeFormat(ctx).format(Date(at))
        val text = listOf(formatAmount(ctx, med.quartersAt(millisToLdt(at).toLocalTime())), med.dosage, time)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val b = NotificationCompat.Builder(ctx, CH_DOSE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(med.name)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setWhen(at)
            .setContentIntent(openAppIntent(ctx, id))
            .setDeleteIntent(actionIntent(ctx, ACTION_DISMISSED, med.id, at))
            .addAction(0, ctx.getString(R.string.action_taken), actionIntent(ctx, ACTION_TAKEN, med.id, at))
            .addAction(0, ctx.getString(R.string.action_skip), actionIntent(ctx, ACTION_SKIP, med.id, at))
        if (Prefs(ctx).hideOnLockScreen) {
            val pub = NotificationCompat.Builder(ctx, CH_DOSE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.notif_public_title))
                .setContentText(time)
                .build()
            b.setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(pub)
        }
        NotificationManagerCompat.from(ctx).notify(id, b.build())
    }

    fun cancelDose(ctx: Context, medId: Long, at: Long) {
        NotificationManagerCompat.from(ctx).cancel(notifId(medId, at))
    }

    @SuppressLint("MissingPermission")
    /** [left] — остаток в четвертях таблетки. */
    fun showLowStock(ctx: Context, med: Medication, left: Int) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_STOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_low_stock_title))
            .setContentText(ctx.getString(R.string.notif_low_stock_text, med.name, formatAmount(ctx, left)))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(ctx, 7_000_000 + med.id.toInt()))
            .build()
        NotificationManagerCompat.from(ctx).notify(7_000_000 + med.id.toInt(), n)
    }

    @SuppressLint("MissingPermission")
    fun showTest(ctx: Context) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_DOSE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_test_title))
            .setContentText(ctx.getString(R.string.notif_test_text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(ctx, 6_999_999))
            .build()
        NotificationManagerCompat.from(ctx).notify(6_999_999, n)
    }
}
