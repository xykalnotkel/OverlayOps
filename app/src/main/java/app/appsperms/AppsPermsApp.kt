package app.appsperms

import android.app.Application
import android.util.Log
import app.appsperms.core.AppOpsBridge
import app.appsperms.core.Settings
import app.appsperms.guard.GhostGuardService

/**
 * HiddenApiBypass dipasang sedini mungkin — sebelum method hidden (IAppOpsService)
 * pertama kali di-resolve, karena pembatasan hidden API di Android 9+ hanya bisa
 * dibuka selama proses masih "fresh".
 */
class AppsPermsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Terapkan bahasa pilihan pengguna sebelum Activity pertama dibuat,
        // supaya tidak ada kedipan bahasa Indonesia lalu berubah.
        runCatching { Settings.applyStoredLanguage(this) }
            .onFailure { Log.w("AppsPermsApp", "gagal menerapkan bahasa", it) }
        // v1.4.2 menghentikan fitur pita sentuh lama: pada sebagian perangkat pita
        // tersebut justru menelan sentuhan normal. Upgrade harus langsung memulihkan layar.
        runCatching {
            Settings.setGuardEnabled(this, false)
            GhostGuardService.syncFromSettings(this)
        }.onFailure { Log.w("AppsPermsApp", "gagal mematikan guard lama", it) }
        runCatching { AppOpsBridge.hiddenApiBypass() }
            .onFailure { Log.w("AppsPermsApp", "gagal pasang HiddenApiBypass", it) }
    }
}
