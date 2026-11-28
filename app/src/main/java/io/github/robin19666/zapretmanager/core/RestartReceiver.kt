package io.github.robin19666.zapretmanager.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.robin19666.zapretmanager.ui.MainActivity

/**
 * Попытка открыть приложение после установки обновления.
 *
 * Android 10+ запрещает запускать активность из фона, а прошивки вроде HyperOS
 * блокируют это ещё жёстче, поэтому запуск — «best effort»: сработает далеко не
 * везде. Пользователю мы это не обещаем: в интерфейсе и уведомлении написано,
 * что после установки приложение нужно открыть самому.
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
