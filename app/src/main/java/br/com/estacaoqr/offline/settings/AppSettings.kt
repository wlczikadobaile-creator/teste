package br.com.estacaoqr.offline.settings

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var kioskEnabled: Boolean
        get() = prefs.getBoolean("kiosk_enabled", false)
        set(value) { prefs.edit().putBoolean("kiosk_enabled", value).apply() }

    var soundEnabled: Boolean
        get() = prefs.getBoolean("sound_enabled", true)
        set(value) { prefs.edit().putBoolean("sound_enabled", value).apply() }

    var useFrontCamera: Boolean
        get() = prefs.getBoolean("use_front_camera", false)
        set(value) { prefs.edit().putBoolean("use_front_camera", value).apply() }

    /** Tela sempre ligada, com brilho reduzido a 5% após 5 minutos sem uso. */
    var screenAlwaysOn: Boolean
        get() = prefs.getBoolean("screen_always_on", true)
        set(value) { prefs.edit().putBoolean("screen_always_on", value).apply() }
}
