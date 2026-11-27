package io.github.robin19666.zapretmanager.core

import android.content.Context
import android.content.pm.PackageManager

/**
 * Базовые приложения, которые обычно нужно пускать через белый список:
 * мессенджеры, YouTube, Instagram, сервисы Google и Play Маркет.
 */
object BaseApps {
    val packages: List<String> = listOf(
        // Telegram
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.messenger.beta",
        "org.thunderdog.challegram",
        "org.telegram.plus",
        // WhatsApp / Signal
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.thoughtcrime.securesms",
        // Google
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.vending",
        "com.google.android.googlequicksearchbox",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.google.android.gm",
        "com.google.android.apps.maps",
        "com.google.android.apps.photos",
        "com.google.android.apps.docs",
        "com.google.android.apps.youtube.kids",
        "com.google.android.apps.messaging",
        "com.google.android.dialer",
        "com.google.android.contacts",
        "com.google.android.calendar",
        "com.google.android.keep",
        "com.google.android.play.games",
        "com.google.android.apps.tachyon",
        "com.google.android.videos",
        // Соцсети и мессенджеры
        "com.instagram.android",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.facebook.lite",
        "com.discord",
        "com.twitter.android",
        "com.snapchat.android",
        "com.linkedin.android",
        "com.vkontakte.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "tv.twitch.android.app",
        "com.spotify.music",
        "com.soundcloud.android",
        "org.mozilla.firefox",
        "com.openai.chatgpt",
        "com.github.android",
    )

    /** Какие из базовых приложений реально установлены. */
    fun installed(context: Context): List<String> {
        val pm = context.packageManager
        return packages.filter { pkg ->
            runCatching {
                pm.getPackageInfo(pkg, PackageManager.GET_META_DATA)
                true
            }.getOrDefault(false)
        }
    }

    /** Добавляет установленные базовые приложения к уже выбранным. */
    fun withBase(context: Context, current: Set<String>): Set<String> =
        current + installed(context)
}
