package dev.gearturner.sparklauncher.feature.search

import android.content.Intent
import android.content.res.Resources
import android.provider.Settings
import androidx.annotation.StringRes

/** a system settings screen search can open; keywords are comma separated so they translate as one string */
data class SettingsEntry(val action: String, @param:StringRes val title: Int, @param:StringRes val keywords: Int)

/** a settings screen with its text resolved for the current language */
data class SettingHit(val action: String, val title: String, val keywords: List<String>)

/**
 * only public settings actions are listed, so every entry opens a screen the system documents.
 * a device that lacks one, such as nfc, reports it when opened instead of being filtered here,
 * which would need visibility into the settings app that spark does not otherwise want.
 */
object SettingsSearch {

    val entries: List<SettingsEntry> = listOf(
        SettingsEntry(Settings.ACTION_WIFI_SETTINGS, R.string.setting_wifi, R.string.setting_wifi_keywords),
        SettingsEntry(Settings.ACTION_WIRELESS_SETTINGS, R.string.setting_network, R.string.setting_network_keywords),
        SettingsEntry(Settings.ACTION_DATA_USAGE_SETTINGS, R.string.setting_data_usage, R.string.setting_data_usage_keywords),
        SettingsEntry(Settings.ACTION_AIRPLANE_MODE_SETTINGS, R.string.setting_airplane, R.string.setting_airplane_keywords),
        SettingsEntry(Settings.ACTION_VPN_SETTINGS, R.string.setting_vpn, R.string.setting_vpn_keywords),
        SettingsEntry(Settings.ACTION_BLUETOOTH_SETTINGS, R.string.setting_bluetooth, R.string.setting_bluetooth_keywords),
        SettingsEntry(Settings.ACTION_NFC_SETTINGS, R.string.setting_nfc, R.string.setting_nfc_keywords),
        SettingsEntry(Settings.ACTION_CAST_SETTINGS, R.string.setting_cast, R.string.setting_cast_keywords),
        SettingsEntry(Settings.ACTION_DISPLAY_SETTINGS, R.string.setting_display, R.string.setting_display_keywords),
        SettingsEntry(Settings.ACTION_NIGHT_DISPLAY_SETTINGS, R.string.setting_night_light, R.string.setting_night_light_keywords),
        SettingsEntry(Settings.ACTION_DREAM_SETTINGS, R.string.setting_screen_saver, R.string.setting_screen_saver_keywords),
        SettingsEntry(Intent.ACTION_SET_WALLPAPER, R.string.setting_wallpaper, R.string.setting_wallpaper_keywords),
        SettingsEntry(Settings.ACTION_SOUND_SETTINGS, R.string.setting_sound, R.string.setting_sound_keywords),
        SettingsEntry(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, R.string.setting_dnd, R.string.setting_dnd_keywords),
        SettingsEntry(Intent.ACTION_POWER_USAGE_SUMMARY, R.string.setting_battery, R.string.setting_battery_keywords),
        SettingsEntry(Settings.ACTION_APPLICATION_SETTINGS, R.string.setting_apps, R.string.setting_apps_keywords),
        SettingsEntry(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, R.string.setting_default_apps, R.string.setting_default_apps_keywords),
        SettingsEntry(Settings.ACTION_HOME_SETTINGS, R.string.setting_home_app, R.string.setting_home_app_keywords),
        SettingsEntry(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, R.string.setting_storage, R.string.setting_storage_keywords),
        SettingsEntry(Settings.ACTION_LOCATION_SOURCE_SETTINGS, R.string.setting_location, R.string.setting_location_keywords),
        SettingsEntry(Settings.ACTION_SECURITY_SETTINGS, R.string.setting_security, R.string.setting_security_keywords),
        SettingsEntry(Settings.ACTION_PRIVACY_SETTINGS, R.string.setting_privacy, R.string.setting_privacy_keywords),
        SettingsEntry(Settings.ACTION_SYNC_SETTINGS, R.string.setting_accounts, R.string.setting_accounts_keywords),
        SettingsEntry(Settings.ACTION_ACCESSIBILITY_SETTINGS, R.string.setting_accessibility, R.string.setting_accessibility_keywords),
        SettingsEntry(Settings.ACTION_LOCALE_SETTINGS, R.string.setting_languages, R.string.setting_languages_keywords),
        SettingsEntry(Settings.ACTION_INPUT_METHOD_SETTINGS, R.string.setting_keyboard, R.string.setting_keyboard_keywords),
        SettingsEntry(Settings.ACTION_DATE_SETTINGS, R.string.setting_date_time, R.string.setting_date_time_keywords),
        SettingsEntry(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, R.string.setting_developer, R.string.setting_developer_keywords),
        SettingsEntry(Settings.ACTION_DEVICE_INFO_SETTINGS, R.string.setting_about, R.string.setting_about_keywords),
        SettingsEntry(Settings.ACTION_SETTINGS, R.string.setting_all, R.string.setting_all_keywords),
    )

    fun resolve(resources: Resources): List<SettingHit> = entries.map { entry ->
        SettingHit(
            action = entry.action,
            title = resources.getString(entry.title),
            keywords = resources.getString(entry.keywords).split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    fun search(all: List<SettingHit>, query: String, limit: Int = MAX_RESULTS): List<SettingHit> =
        rankMatches(all, query, label = { it.title }, keywords = { it.keywords }).take(limit)

    private const val MAX_RESULTS = 4
}
