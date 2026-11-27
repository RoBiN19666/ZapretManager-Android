package io.github.robin19666.zapretmanager.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.robin19666.zapretmanager.R
import io.github.robin19666.zapretmanager.ui.MainActivity

@SuppressLint("MissingPermission")
object Notifications {
    const val CH_TUNNEL = "tunnel"
    const val CH_TG = "tgproxy"
    const val CH_UPDATES = "updates"
    const val ID_TUNNEL = 1001
    const val ID_TG = 1002
    const val ID_UPDATE = 1003
    const val ID_INSTALL = 1004

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CH_TUNNEL, context.getString(R.string.channel_tunnel), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        manager.createNotificationChannel(
            NotificationChannel(CH_TG, context.getString(R.string.channel_tgproxy), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        manager.createNotificationChannel(
            NotificationChannel(CH_UPDATES, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun serviceNotification(
        context: Context,
        channel: String,
        title: String,
        text: String,
        ongoing: Boolean = true,
        showWhen: Boolean = true,
    ): android.app.Notification {
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setShowWhen(showWhen)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent(context))
            .build()
    }

    fun notifyUpdate(context: Context, version: String, text: String) {
        if (!canPost(context)) return
        runCatching {
            val n = NotificationCompat.Builder(context, CH_UPDATES)
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setContentTitle("Доступно обновление $version")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(contentIntent(context))
                .build()
            NotificationManagerCompat.from(context).notify(ID_UPDATE, n)
        }
    }

    /** Висит, пока идёт установка: пользователь видит, что нужно подождать. */
    fun notifyInstalling(context: Context, version: String) {
        if (!canPost(context)) return
        runCatching {
            val n = NotificationCompat.Builder(context, CH_UPDATES)
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setContentTitle("Обновление $version устанавливается…")
                .setContentText("Подтвердите установку в системном окне — приложение откроется само")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Подтвердите установку в системном окне. После обновления приложение " +
                            "запустится автоматически, ускорение соединения и Telegram-прокси восстановятся.",
                    )
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentIntent(context))
                .build()
            NotificationManagerCompat.from(context).notify(ID_INSTALL, n)
        }
    }

    fun cancelInstalling(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_INSTALL) }
    }

    fun notifyUpdated(context: Context, version: String) {
        if (!canPost(context)) return
        runCatching {
            val n = NotificationCompat.Builder(context, CH_UPDATES)
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setContentTitle("Обновление установлено")
                .setContentText("Zapret Manager обновлён до $version")
                .setAutoCancel(true)
                .setContentIntent(contentIntent(context))
                .build()
            NotificationManagerCompat.from(context).notify(ID_UPDATE, n)
        }
    }
}
