package io.github.robin19666.zapretmanager.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.robin19666.zapretmanager.byedpi.DpiEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Плитка быстрых настроек: включает и выключает ускорение соединения. */
@SuppressLint("StartActivityAndCollapseDeprecated")
class ZapretTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Плитка должна обновляться не только по onStartListening, но и когда
        // движок изменил состояние из UI/сторожа.
        scope.launch {
            DpiEngine.state.collect { refresh() }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (DpiEngine.state.value.running) {
            DpiEngine.stop()
        } else {
            val permission = VpnService.prepare(this)
            if (permission != null) {
                openVpnConsent(permission)
            } else {
                DpiEngine.startNow()
            }
        }
        refresh()
    }

    private fun openVpnConsent(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val running = DpiEngine.state.value.running
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Zapret"
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
