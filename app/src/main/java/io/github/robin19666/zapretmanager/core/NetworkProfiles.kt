package io.github.robin19666.zapretmanager.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Снимок текущей сети: тип, ключ для сопоставления с профилем и подпись для интерфейса. */
data class NetworkSnapshot(
    val kind: String,
    val matchKey: String,
    val label: String,
    val wifiEnabled: Boolean = false,
) {
    val isWifi: Boolean get() = kind == KIND_WIFI
    val isMobile: Boolean get() = kind == KIND_MOBILE

    companion object {
        const val KIND_WIFI = "wifi"
        const val KIND_MOBILE = "mobile"
        const val KIND_NONE = "none"
        const val KIND_DEFAULT = "default"

        val NONE = NetworkSnapshot(KIND_NONE, "none", "Нет сети")
    }
}

/** Конфигурация под конкретную сеть: Wi-Fi по имени, мобильная по оператору или запасная. */
data class NetworkProfile(
    val id: String,
    val label: String,
    val matchKey: String,
    val kind: String,
    val strategyId: String,
    val customArgs: String = "",
    val tgPort: Int = 1443,
    val tgRoute: String = "cf_proxy_ws",
    val tgMaskingDomain: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("matchKey", matchKey)
        put("kind", kind)
        put("strategyId", strategyId)
        put("customArgs", customArgs)
        put("tgPort", tgPort)
        put("tgRoute", tgRoute)
        put("tgMaskingDomain", tgMaskingDomain)
    }

    companion object {
        fun fromJson(json: JSONObject): NetworkProfile = NetworkProfile(
            id = json.optString("id"),
            label = json.optString("label"),
            matchKey = json.optString("matchKey"),
            kind = json.optString("kind", NetworkSnapshot.KIND_WIFI),
            strategyId = json.optString("strategyId", "general"),
            customArgs = json.optString("customArgs"),
            tgPort = json.optInt("tgPort", 1443),
            tgRoute = json.optString("tgRoute", "cf_proxy_ws"),
            tgMaskingDomain = json.optString("tgMaskingDomain"),
        )
    }
}

object NetworkProfiles {
    private const val FILE_NAME = "network-profiles.json"
    private const val FALLBACK_KEY = "*"

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun all(context: Context): List<NetworkProfile> = runCatching {
        val target = file(context)
        if (!target.exists()) return emptyList()
        val array = JSONArray(target.readText())
        (0 until array.length()).mapNotNull { index ->
            runCatching { NetworkProfile.fromJson(array.getJSONObject(index)) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, profiles: List<NetworkProfile>) {
        runCatching {
            val array = JSONArray()
            profiles.forEach { array.put(it.toJson()) }
            file(context).writeText(array.toString(2))
        }
    }

    fun upsert(context: Context, profile: NetworkProfile) {
        val list = all(context).toMutableList()
        val index = list.indexOfFirst { it.id == profile.id }
        if (index >= 0) list[index] = profile else list += profile
        save(context, list)
    }

    fun delete(context: Context, id: String) {
        save(context, all(context).filterNot { it.id == id })
    }

    /** Приоритет: точное совпадение сети → запасной профиль («*»). */
    fun match(profiles: List<NetworkProfile>, snapshot: NetworkSnapshot): NetworkProfile? {
        if (snapshot.kind == NetworkSnapshot.KIND_NONE) return null
        profiles.firstOrNull { it.matchKey == snapshot.matchKey }?.let { return it }
        return profiles.firstOrNull { it.matchKey == FALLBACK_KEY || it.kind == NetworkSnapshot.KIND_DEFAULT }
    }

    fun current(context: Context): NetworkProfile? = match(all(context), NetworkInfo.current(context))

    fun profileFor(context: Context, snapshot: NetworkSnapshot): NetworkProfile? =
        match(all(context), snapshot)

    fun newProfile(
        snapshot: NetworkSnapshot,
        strategyId: String,
        customArgs: String,
        tgPort: Int,
        tgRoute: String,
        tgMaskingDomain: String,
    ): NetworkProfile = NetworkProfile(
        id = "np-" + System.currentTimeMillis().toString(16),
        label = snapshot.label,
        matchKey = snapshot.matchKey,
        kind = snapshot.kind,
        strategyId = strategyId,
        customArgs = customArgs,
        tgPort = tgPort,
        tgRoute = tgRoute,
        tgMaskingDomain = tgMaskingDomain,
    )
}

/** Определение текущей сети без запроса location: SSID, если доступен, иначе шлюз. */
object NetworkInfo {
    fun current(context: Context): NetworkSnapshot {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkSnapshot.NONE
        val network = cm.activeNetwork ?: return NetworkSnapshot.NONE
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkSnapshot.NONE
        val wifiEnabled = runCatching {
            context.getSystemService(WifiManager::class.java)?.isWifiEnabled == true
        }.getOrDefault(false)

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val ssid = wifiSsid(caps)
            val gateway = runCatching {
                cm.getLinkProperties(network)?.routes
                    ?.firstOrNull { it.isDefaultRoute }
                    ?.gateway?.hostAddress
            }.getOrNull()
            val key = when {
                !ssid.isNullOrBlank() -> "wifi:$ssid"
                !gateway.isNullOrBlank() -> "wifi:gw:$gateway"
                else -> "wifi:*"
            }
            val label = when {
                !ssid.isNullOrBlank() -> ssid
                !gateway.isNullOrBlank() -> "Wi-Fi ($gateway)"
                else -> "Wi-Fi"
            }
            return NetworkSnapshot(NetworkSnapshot.KIND_WIFI, key, label, wifiEnabled)
        }

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            val operator = runCatching {
                context.getSystemService(TelephonyManager::class.java)?.networkOperatorName
            }.getOrNull()?.trim().orEmpty()
            val label = if (operator.isBlank()) "Мобильная сеть" else "Мобильная сеть ($operator)"
            val key = if (operator.isBlank()) "mobile:*" else "mobile:$operator"
            return NetworkSnapshot(NetworkSnapshot.KIND_MOBILE, key, label, wifiEnabled)
        }

        return NetworkSnapshot(NetworkSnapshot.KIND_NONE, "none", "Нет сети", wifiEnabled)
    }

    private fun wifiSsid(caps: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val info = caps.transportInfo as? WifiInfo ?: return null
        val raw = runCatching { info.ssid }.getOrNull() ?: return null
        val cleaned = raw.trim().trim('"')
        if (cleaned.isBlank()) return null
        if (cleaned.equals("<unknown ssid>", ignoreCase = true)) return null
        if (cleaned.contains("unknown", ignoreCase = true)) return null
        return cleaned
    }
}
