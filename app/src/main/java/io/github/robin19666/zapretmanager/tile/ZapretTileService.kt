package io.github.robin19666.zapretmanager.tile

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.robin19666.zapretmanager.byedpi.DpiEngine

/** Плитка быстрых настроек: включает и выключает обход DPI. */
class ZapretTileService : TileService() {

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
}
