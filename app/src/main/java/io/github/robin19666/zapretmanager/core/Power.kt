package io.github.robin19666.zapretmanager.core

import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Семейство прошивки: у каждого свой путь к экрану батареи и менеджеру автозапуска. */
enum class VendorFamily { XIAOMI, HUAWEI, SAMSUNG, OPPO, VIVO, MEIZU, ASUS, OTHER }

/**
 * Настройки энергосбережения и автозапуска.
 *
 * Android усыпляет фоновые процессы (Doze, «адаптивная батарея»), из-за чего
 * VPN/прокси-сервис может быть остановлен системой. Приложению нужен режим
 * «без ограничений», иначе туннель рвётся при выключенном экране.
 *
 * Прошивки Xiaomi (MIUI/HyperOS), Huawei, Samsung, OPPO/realme, vivo и Meizu
 * держат **свои** экраны батареи и автозапуска: универсальный
 * `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` на них открывает не то, что
 * нужно пользователю. Поэтому сначала пробуем известные экраны производителя,
 * а универсальные настройки — только как последний запасной вариант.
 *
 * На прошивках вроде HyperOS/MIUI режим «без ограничений» не всегда выставляет
 * флаг `isIgnoringBatteryOptimizations`, поэтому дополнительно смотрим
 * standby-bucket: ACTIVE/WORKING_SET означают, что система не душит приложение.
 */
object Power {
    private const val FLAG = Intent.FLAG_ACTIVITY_NEW_TASK

    fun isIgnoring(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val manager = context.getSystemService(PowerManager::class.java)
        if (manager != null && manager.isIgnoringBatteryOptimizations(context.packageName)) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val stats = context.getSystemService(UsageStatsManager::class.java)
            val bucket = runCatching { stats?.appStandbyBucket }.getOrNull()
            if (bucket == UsageStatsManager.STANDBY_BUCKET_ACTIVE ||
                bucket == UsageStatsManager.STANDBY_BUCKET_WORKING_SET
            ) {
                return true
            }
        }
        return false
    }

    fun statusText(context: Context): String = if (isIgnoring(context)) {
        "без ограничений"
    } else {
        "оптимизируется системой"
    }

    /** Определение прошивки — чистая функция, покрыта тестами. */
    fun vendorFamily(manufacturer: String = Build.MANUFACTURER, brand: String = Build.BRAND): VendorFamily {
        val source = "$manufacturer $brand".lowercase()
        return when {
            source.contains("xiaomi") || source.contains("redmi") ||
                source.contains("poco") || source.contains("miui") -> VendorFamily.XIAOMI
            source.contains("huawei") || source.contains("honor") -> VendorFamily.HUAWEI
            source.contains("samsung") -> VendorFamily.SAMSUNG
            source.contains("oppo") || source.contains("realme") ||
                source.contains("oneplus") || source.contains("one plus") -> VendorFamily.OPPO
            source.contains("vivo") || source.contains("iqoo") -> VendorFamily.VIVO
            source.contains("meizu") || source.contains("flyme") -> VendorFamily.MEIZU
            source.contains("asus") -> VendorFamily.ASUS
            else -> VendorFamily.OTHER
        }
    }

    /** Человеческое название прошивки для интерфейса. */
    fun vendorName(family: VendorFamily = vendorFamily()): String = when (family) {
        VendorFamily.XIAOMI -> "Xiaomi (HyperOS/MIUI)"
        VendorFamily.HUAWEI -> "Huawei/Honor"
        VendorFamily.SAMSUNG -> "Samsung"
        VendorFamily.OPPO -> "OPPO/realme/OnePlus"
        VendorFamily.VIVO -> "vivo/iQOO"
        VendorFamily.MEIZU -> "Meizu"
        VendorFamily.ASUS -> "ASUS"
        VendorFamily.OTHER -> "Android"
    }

    /** Что именно нажать на этой прошивке — показываем в диалоге и настройках. */
    fun vendorHint(
        context: Context,
        family: VendorFamily = vendorFamily(),
    ): String = vendorHintFor(family)

    /** Чистая часть подсказки — без контекста, покрыта тестами. */
    internal fun vendorHintFor(family: VendorFamily): String = when (family) {
        VendorFamily.XIAOMI ->
            "HyperOS/MIUI: «Приложения → Zapret Manager → Экономия заряда → Без ограничений» " +
                "и «Контроль активности → Автозапуск» (иначе система выгружает сервис)."
        VendorFamily.HUAWEI ->
            "EMUI/HarmonyOS: «Батарея → Запуск приложений» — включите автозапуск и " +
                "снимите галочку «управлять автоматически»."
        VendorFamily.SAMSUNG ->
            "One UI: «Батарея → Ограничения фоновой активности» — уберите приложение из списка " +
                "спящих, «Батарея → Режим энергосбережения» — не ограничивать."
        VendorFamily.OPPO ->
            "ColorOS/realme UI: «Батарея → Энергосбережение для приложений» и " +
                "«Автозапуск» (или «Разрешения → Автозапуск»)."
        VendorFamily.VIVO ->
            "FuntouchOS/OriginOS: «Батарея → Фоновый запуск» и «Автозапуск» в i-менеджере."
        VendorFamily.MEIZU ->
            "Flyme: «Безопасность → Разрешения → Автозапуск» и «Батарея → Управление энергией»."
        VendorFamily.ASUS ->
            "ZenUI: «Батарея → Автозапуск» и «Менеджер питания → Разрешить в фоне»."
        VendorFamily.OTHER ->
            "Система: «Батарея → Без ограничений» для этого приложения."
    }

    /**
     * Экран батареи для приложения: на прошивках производителей — их собственный,
     * иначе карточка приложения, а в самом конце — общий список оптимизации.
     */
    fun openPowerSettings(context: Context): Boolean {
        val intents = ArrayList<Intent>()
        when (vendorFamily()) {
            VendorFamily.XIAOMI -> {
                intents += component(
                    "com.miui.powerkeeper/com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                    mapOf(
                        "package_name" to context.packageName,
                        "package_label" to appLabel(context),
                    ),
                )
                intents += component("com.miui.powerkeeper/com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity")
            }
            VendorFamily.SAMSUNG -> {
                intents += component("com.samsung.android.lool/com.samsung.android.sm.ui.battery.BatteryActivity")
                intents += component("com.samsung.android.lool/com.samsung.android.sm.battery.ui.BatteryActivity")
            }
            VendorFamily.OPPO -> {
                intents += component("com.coloros.oppoguardelf/com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")
                intents += component("com.coloros.safecenter/com.coloros.safecenter.permission.PermissionManagerActivity")
            }
            VendorFamily.VIVO -> {
                intents += component("com.vivo.permissionmanager/com.vivo.permissionmanager.activity.PurviewTabActivity")
            }
            VendorFamily.ASUS -> {
                intents += component("com.asus.mobilemanager/com.asus.mobilemanager.powersaver.PowerSaverSettings")
            }
            VendorFamily.HUAWEI, VendorFamily.MEIZU, VendorFamily.OTHER -> Unit
        }
        // Карточка приложения: на любой прошивке оттуда есть вход в «Батарея».
        intents += appSettingsIntent(context)
        intents += requestIntent()
        intents += Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(FLAG)
        return openFirst(context, intents)
    }

    /** Менеджер автозапуска производителя (на «чистом» Android его нет). */
    fun openAutostartSettings(context: Context): Boolean {
        val intents = ArrayList<Intent>()
        when (vendorFamily()) {
            VendorFamily.XIAOMI -> {
                intents += component("com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity")
                intents += component("com.miui.securitycenter/com.miui.powercenter.PowerSettings")
            }
            VendorFamily.HUAWEI -> {
                intents += component("com.huawei.systemmanager/com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                intents += component("com.huawei.systemmanager/com.huawei.systemmanager.optimize.process.ProtectActivity")
                intents += component("com.huawei.systemmanager/com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
            }
            VendorFamily.SAMSUNG -> {
                intents += component("com.samsung.android.lool/com.samsung.android.sm.ui.battery.BatteryActivity")
            }
            VendorFamily.OPPO -> {
                intents += component("com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity")
                intents += component("com.coloros.safecenter/com.coloros.safecenter.startupapp.StartupAppListActivity")
                intents += component("com.oppo.safe/com.oppo.safe.permission.startup.StartupAppListActivity")
            }
            VendorFamily.VIVO -> {
                intents += component("com.vivo.permissionmanager/com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                intents += component("com.iqoo.secure/com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
                intents += component("com.iqoo.secure/com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
            }
            VendorFamily.MEIZU -> {
                intents += component("com.meizu.safe/com.meizu.safe.permission.SmartBGActivity")
                intents += component("com.meizu.safe/com.meizu.safe.security.SHOW_APPSEC")
            }
            VendorFamily.ASUS -> {
                intents += component("com.asus.mobilemanager/com.asus.mobilemanager.autostart.AutoStartActivity")
            }
            VendorFamily.OTHER -> Unit
        }
        intents += appSettingsIntent(context)
        return openFirst(context, intents)
    }

    /** Экран «Приложение → ...»: там есть и батарея, и разрешения. */
    fun appSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(FLAG)

    /** Совместимость: раньше здесь была одна универсальная попытка. */
    fun open(context: Context): Boolean = openPowerSettings(context)

    /** Общий список приложений без оптимизации батареи. */
    fun requestIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(FLAG)

    fun openAppSettings(context: Context): Boolean = runCatching {
        context.startActivity(appSettingsIntent(context))
        true
    }.getOrDefault(false)

    private fun component(path: String, extras: Map<String, String> = emptyMap()): Intent {
        val pkg = path.substringBefore('/')
        val cls = path.substringAfter('/')
        val fullName = if (cls.startsWith(".")) pkg + cls else cls
        val intent = Intent().setComponent(ComponentName(pkg, fullName)).addFlags(FLAG)
        extras.forEach { (key, value) -> intent.putExtra(key, value) }
        return intent
    }

    /** Пробует экраны по порядку: первый доступный и открывается. */
    private fun openFirst(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            val opened = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (opened) {
                LogBus.d("Питание", "Открыт экран: ${intent.component?.flattenToShortString() ?: intent.action}")
                return true
            }
        }
        LogBus.w("Питание", "Не удалось открыть ни один экран настроек питания")
        return false
    }

    private fun appLabel(context: Context): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(context.packageName, 0)).toString()
    }.getOrDefault("Zapret Manager")
}
