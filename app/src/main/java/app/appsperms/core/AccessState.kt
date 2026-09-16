package app.appsperms.core

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import app.appsperms.R
import app.appsperms.model.AppEntry

/**
 * Hasil probe status akses. Dipakai UI untuk menampilkan pesan yang BENAR:
 * "Shizuku mati", "izin belum diberikan", atau "izin sudah ada tapi bridge gagal".
 */
data class AccessSnapshot(
    val shizukuInstalled: Boolean = false,
    val binderAlive: Boolean = false,
    val permission: Boolean = false,
    val uid: Int = -1,
    val version: Int = -1,
    val bridgeReady: Boolean = false,
    val bridgeError: String? = null,
    val shellAvailable: Boolean = false,
    val shellError: String? = null,
) {
    val isRoot: Boolean get() = permission && uid == 0

    val state: AccessState get() = when {
        !binderAlive -> AccessState.SHIZUKU_OFF
        !permission -> AccessState.PERMISSION_DENIED
        bridgeReady -> if (isRoot) AccessState.READY_ROOT else AccessState.READY_SHELL
        shellAvailable -> AccessState.SHELL_FALLBACK
        else -> AccessState.BRIDGE_FAILED
    }

    /** True kalau kita masih bisa baca/tulis AppOps (lewat jalur mana pun). */
    val canOperate: Boolean get() = state.canOperate

    /** Jalur tulis yang akan dipakai. */
    val preferShell: Boolean get() = !bridgeReady

    companion object {
        fun unknown() = AccessSnapshot()
    }
}

/**
 * Status koneksi yang ditampilkan ke user. Setiap state punya pesan & warna sendiri
 * supaya tidak lagi salah menyalahkan izin Shizuku.
 */
enum class AccessState(
    @StringRes val chipRes: Int,
    @ColorRes val colorRes: Int,
    val canOperate: Boolean,
) {
    /** Shizuku belum jalan sama sekali. */
    SHIZUKU_OFF(R.string.state_off, R.color.deny, false),

    /** Shizuku jalan, tapi app ini belum diizinkan. */
    PERMISSION_DENIED(R.string.state_no_permission, R.color.deny, false),

    /** Izin ada, tapi binder & shell dua-duanya gagal. */
    BRIDGE_FAILED(R.string.state_bridge_failed, R.color.deny, false),

    /** Izin ada, refleksi binder gagal (mis. dibatasi ROM), jalan lewat perintah `appops`. */
    SHELL_FALLBACK(R.string.state_shell_fallback, R.color.warn, true),

    /** Semua lancar lewat binder, identitas shell (uid 2000). */
    READY_SHELL(R.string.state_ready_shell, R.color.ok, true),

    /** Semua lancar lewat binder, identitas root (uid 0). */
    READY_ROOT(R.string.state_ready_root, R.color.ok, true),
}

/** Cakupan app yang ditampilkan. */
enum class AppTypeFilter(@StringRes val labelRes: Int) {
    ALL(R.string.type_all),
    USER(R.string.type_user),
    SYSTEM(R.string.type_system);

    fun accepts(entry: AppEntry): Boolean = when (this) {
        ALL -> true
        USER -> !entry.isSystem
        SYSTEM -> entry.isSystem
    }
}

enum class SortMode(@StringRes val labelRes: Int) {
    NAME(R.string.sort_name),
    STATUS(R.string.sort_status),
}
