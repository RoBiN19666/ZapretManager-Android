package io.github.robin19666.zapretmanager.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.robin19666.zapretmanager.ui.MainActivity

/**
 * Попытка открыть приложение после установки обновления.
 *
 * Android 10+ запрещает запускать активность из фона, поэтому это «best effort»:
 * если система разрешит — приложение откроется само; если нет — останется
 * уведомление «Обновление установлено», а в установщике есть кнопка «Открыть».
 */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        UpdateState.cancelRelaunch(context)
        Notifications.cancelInstalling(context)
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            )
            LogBus.i("Обновление", "Приложение открыто после обновления")
        }.onFailure {
            LogBus.w("Обновление", "Автозапуск после обновления недоступен системой: ${it.message}")
        }
    }
}
