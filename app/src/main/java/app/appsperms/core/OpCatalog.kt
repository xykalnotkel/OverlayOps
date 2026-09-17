package app.appsperms.core

import androidx.annotation.StringRes
import app.appsperms.R

/**
 * Katalog AppOp yang ditampilkan di UI.
 *
 * [op] nama resmi "android:xxx" yang dipakai binder IAppOpsService,
 * [shell] nama singkat yang dipakai perintah `appops`,
 * [titleRes]/[descRes] label & penjelasan dalam resource (ID + EN) — sejak v1.4.0
 * janji "UI sepenuhnya bisa diterjemahkan" dari v1.3.0 dituntaskan: tidak ada lagi
 * teks UI yang di-hardcode di kode,
 * [fallbackCode] kode op di AOSP kalau refleksi strOpToOp tidak tersedia.
 */
data class OpDef(
    val op: String,
    val shell: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val group: OpGroup,
    val fallbackCode: Int? = null,
)

enum class OpGroup(@param:StringRes val titleRes: Int) {
    OVERLAY(R.string.opgroup_overlay),
    PRIVACY(R.string.opgroup_privacy),
    MEDIA(R.string.opgroup_media),
    BACKGROUND(R.string.opgroup_background),
}

object OpCatalog {

    val OVERLAY = OpDef(
        op = "android:system_alert_window",
        shell = "SYSTEM_ALERT_WINDOW",
        titleRes = R.string.op_overlay_title,
        descRes = R.string.op_overlay_desc,
        group = OpGroup.OVERLAY,
        fallbackCode = 24,
    )

    val ALL: List<OpDef> = listOf(
        OVERLAY,
        OpDef(
            op = "android:toast_window",
            shell = "TOAST_WINDOW",
            titleRes = R.string.op_toast_title,
            descRes = R.string.op_toast_desc,
            group = OpGroup.OVERLAY,
            fallbackCode = 45,
        ),
        OpDef(
            op = "android:picture_in_picture",
            shell = "PICTURE_IN_PICTURE",
            titleRes = R.string.op_pip_title,
            descRes = R.string.op_pip_desc,
            group = OpGroup.OVERLAY,
            fallbackCode = 67,
        ),
        OpDef(
            op = "android:project_media",
            shell = "PROJECT_MEDIA",
            titleRes = R.string.op_cast_title,
            descRes = R.string.op_cast_desc,
            group = OpGroup.OVERLAY,
            fallbackCode = 46,
        ),
        OpDef(
            op = "android:read_clipboard",
            shell = "READ_CLIPBOARD",
            titleRes = R.string.op_clipboard_title,
            descRes = R.string.op_clipboard_desc,
            group = OpGroup.PRIVACY,
            fallbackCode = 29,
        ),
        OpDef(
            op = "android:read_contacts",
            shell = "READ_CONTACTS",
            titleRes = R.string.op_contacts_title,
            descRes = R.string.op_contacts_desc,
            group = OpGroup.PRIVACY,
            fallbackCode = 4,
        ),
        OpDef(
            op = "android:read_calendar",
            shell = "READ_CALENDAR",
            titleRes = R.string.op_calendar_title,
            descRes = R.string.op_calendar_desc,
            group = OpGroup.PRIVACY,
            fallbackCode = 8,
        ),
        OpDef(
            op = "android:fine_location",
            shell = "FINE_LOCATION",
            titleRes = R.string.op_location_fine_title,
            descRes = R.string.op_location_fine_desc,
            group = OpGroup.PRIVACY,
            fallbackCode = 1,
        ),
        OpDef(
            op = "android:coarse_location",
            shell = "COARSE_LOCATION",
            titleRes = R.string.op_location_coarse_title,
            descRes = R.string.op_location_coarse_desc,
            group = OpGroup.PRIVACY,
            fallbackCode = 0,
        ),
        OpDef(
            op = "android:mock_location",
            shell = "MOCK_LOCATION",
            titleRes = R.string.op_mock_location_title,
            descRes = R.string.op_mock_location_desc,
            group = OpGroup.PRIVACY,
            fallbackCode = 58,
        ),
        OpDef(
            op = "android:camera",
            shell = "CAMERA",
            titleRes = R.string.op_camera_title,
            descRes = R.string.op_camera_desc,
            group = OpGroup.MEDIA,
            fallbackCode = 26,
        ),
        OpDef(
            op = "android:record_audio",
            shell = "RECORD_AUDIO",
            titleRes = R.string.op_mic_title,
            descRes = R.string.op_mic_desc,
            group = OpGroup.MEDIA,
            fallbackCode = 27,
        ),
        OpDef(
            op = "android:write_settings",
            shell = "WRITE_SETTINGS",
            titleRes = R.string.op_write_settings_title,
            descRes = R.string.op_write_settings_desc,
            group = OpGroup.BACKGROUND,
            fallbackCode = 23,
        ),
        OpDef(
            op = "android:get_usage_stats",
            shell = "GET_USAGE_STATS",
            titleRes = R.string.op_usage_stats_title,
            descRes = R.string.op_usage_stats_desc,
            group = OpGroup.BACKGROUND,
            fallbackCode = 43,
        ),
        OpDef(
            op = "android:request_install_packages",
            shell = "REQUEST_INSTALL_PACKAGES",
            titleRes = R.string.op_install_apk_title,
            descRes = R.string.op_install_apk_desc,
            group = OpGroup.BACKGROUND,
            fallbackCode = 66,
        ),
        OpDef(
            op = "android:request_delete_packages",
            shell = "REQUEST_DELETE_PACKAGES",
            titleRes = R.string.op_delete_pkg_title,
            descRes = R.string.op_delete_pkg_desc,
            group = OpGroup.BACKGROUND,
            fallbackCode = 72,
        ),
        OpDef(
            op = "android:wake_lock",
            shell = "WAKE_LOCK",
            titleRes = R.string.op_wakelock_title,
            descRes = R.string.op_wakelock_desc,
            group = OpGroup.BACKGROUND,
            fallbackCode = 40,
        ),
        OpDef(
            op = "android:vibrate",
            shell = "VIBRATE",
            titleRes = R.string.op_vibrate_title,
            descRes = R.string.op_vibrate_desc,
            group = OpGroup.BACKGROUND,
            fallbackCode = 32,
        ),
        OpDef(
            op = "android:run_in_background",
            shell = "RUN_IN_BACKGROUND",
            titleRes = R.string.op_background_run_title,
            descRes = R.string.op_background_run_desc,
            group = OpGroup.BACKGROUND,
            fallbackCode = 63,
        ),
    )

    fun byName(op: String): OpDef? = ALL.firstOrNull { it.op == op }

    fun byShellName(name: String): OpDef? =
        ALL.firstOrNull { it.shell.equals(name, ignoreCase = true) }

    /** Beberapa op yang penting untuk mengingatkan user saat mematikan overlay. */
    val OPS_THAT_BREAK_WITHOUT_OVERLAY: List<String> = listOf(
        OVERLAY.op,
        "android:picture_in_picture",
    )
}
