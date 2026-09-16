package app.appsperms.core

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Pilihan bahasa tampilan. [tag] kosong = ikuti bahasa sistem. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    INDONESIAN("in"),
    ENGLISH("en"),
}

/**
 * Preferensi ringan berbasis SharedPreferences.
 *
 * Kenapa tidak pakai DataStore/Room? Karena app ini sengaja dibuat sekecil mungkin
 * (target < 2 MB) dan preferensinya cuma 4 nilai sederhana.
 */
object Settings {

    private const val FILE = "appsperms_settings"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_CONFIRM_RISK = "confirm_risk"
    private const val KEY_SHARED_UID = "warn_shared_uid"
    private const val KEY_SORT = "default_sort"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- bahasa

    fun language(context: Context): AppLanguage {
        val stored = prefs(context).getString(KEY_LANGUAGE, null) ?: return AppLanguage.SYSTEM
        return AppLanguage.entries.firstOrNull { it.name == stored } ?: AppLanguage.SYSTEM
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.name).apply()
        applyLanguage(language)
    }

    /** Terapkan bahasa tersimpan — panggil sekali di Application.onCreate(). */
    fun applyStoredLanguage(context: Context) = applyLanguage(language(context))

    private fun applyLanguage(language: AppLanguage) {
        val locales = if (language.tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    // -------------------------------------------------------------- perilaku

    /** Tanya dulu sebelum menerapkan mode berisiko (deny/ignore) ke app yang minta overlay. */
    fun confirmRiskyModes(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CONFIRM_RISK, true)

    fun setConfirmRiskyModes(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_CONFIRM_RISK, value).apply()
    }

    /** Tampilkan peringatan kalau beberapa app berbagi UID yang sama. */
    fun warnSharedUid(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHARED_UID, true)

    fun setWarnSharedUid(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHARED_UID, value).apply()
    }

    // ----------------------------------------------------------------- urutan

    fun defaultSort(context: Context): SortMode {
        val stored = prefs(context).getString(KEY_SORT, null)
        return SortMode.entries.firstOrNull { it.name == stored } ?: SortMode.NAME
    }

    fun setDefaultSort(context: Context, mode: SortMode) {
        prefs(context).edit().putString(KEY_SORT, mode.name).apply()
    }
}
