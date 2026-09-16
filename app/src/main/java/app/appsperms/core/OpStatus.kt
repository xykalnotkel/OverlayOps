package app.appsperms.core

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import app.appsperms.R
import app.appsperms.model.AppEntry

/**
 * Status sebuah AppOp, dipetakan langsung ke mode integer di AppOpsManager.
 */
enum class OpStatus(
    val mode: Int,
    @StringRes val labelRes: Int,
    @ColorRes val colorRes: Int,
    @StringRes val descRes: Int,
    val sortRank: Int,
) {
    ALLOWED(0, R.string.status_allowed, R.color.ok, R.string.desc_allowed, 0),
    IGNORED(1, R.string.status_ignored, R.color.ignored, R.string.desc_ignored, 1),
    ERRORED(2, R.string.status_denied, R.color.deny, R.string.desc_denied, 2),
    FOREGROUND(4, R.string.status_foreground, R.color.warn, R.string.desc_foreground, 3),
    DEFAULT(3, R.string.status_default, R.color.neutral, R.string.desc_default, 4),
    UNKNOWN(-1, R.string.status_unknown, R.color.neutral, R.string.desc_unknown, 5);

    val isExplicit: Boolean get() = this == ALLOWED || this == IGNORED || this == ERRORED

    companion object {
        fun fromMode(mode: Int): OpStatus = entries.firstOrNull { it.mode == mode } ?: UNKNOWN

        /** Opsi yang bisa dipilih user saat mengubah mode (urutan tampil di sheet). */
        val choices: List<OpStatus> = listOf(ALLOWED, IGNORED, ERRORED, DEFAULT, FOREGROUND)

        fun forShellName(name: String): OpStatus = when (name.trim().lowercase()) {
            "allow", "allowed" -> ALLOWED
            "ignore", "ignored" -> IGNORED
            "deny", "denied", "errored" -> ERRORED
            "default", "missing" -> DEFAULT
            "foreground" -> FOREGROUND
            else -> UNKNOWN
        }

        fun shellName(status: OpStatus): String = when (status) {
            ALLOWED -> "allow"
            IGNORED -> "ignore"
            ERRORED -> "deny"
            DEFAULT -> "default"
            FOREGROUND -> "foreground"
            UNKNOWN -> "default"
        }
    }
}

/** Filter cepat untuk daftar app berdasarkan status overlay. */
enum class StatusFilter(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    ALLOWED(R.string.filter_allowed),
    BLOCKED(R.string.filter_denied),
    DEFAULT(R.string.filter_default);

    fun accepts(entry: AppEntry): Boolean = when (this) {
        ALL -> true
        ALLOWED -> entry.overlayStatus == OpStatus.ALLOWED
        BLOCKED -> entry.overlayStatus == OpStatus.ERRORED || entry.overlayStatus == OpStatus.IGNORED
        DEFAULT -> entry.overlayStatus == OpStatus.DEFAULT || entry.overlayStatus == OpStatus.UNKNOWN
    }
}

/** Dari mana perintah dijalankan. */
enum class AccessMode(val label: String) {
    NONE("—"),
    SHIZUKU_SHELL("Shizuku · shell"),
    SHIZUKU_ROOT("Shizuku · root"),
}

/** Backend teknis yang dipakai untuk baca/tulis AppOps. */
enum class Backend { NONE, BINDER, SHELL }
