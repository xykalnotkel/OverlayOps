package app.appsperms.core

/**
 * Katalog AppOp yang ditampilkan di UI.
 *
 * [op] nama resmi "android:xxx" yang dipakai binder IAppOpsService,
 * [shell] nama singkat yang dipakai perintah `appops`,
 * [fallbackCode] kode op di AOSP kalau refleksi strOpToOp tidak tersedia.
 */
data class OpDef(
    val op: String,
    val shell: String,
    val title: String,
    val description: String,
    val group: OpGroup,
    val fallbackCode: Int? = null,
)

enum class OpGroup(val title: String) {
    OVERLAY("Overlay & jendela"),
    PRIVACY("Privasi"),
    MEDIA("Kamera & mikrofon"),
    BACKGROUND("Latar belakang & sistem"),
}

object OpCatalog {

    val OVERLAY = OpDef(
        op = "android:system_alert_window",
        shell = "SYSTEM_ALERT_WINDOW",
        title = "Display over other apps",
        description = "Boleh menggambar jendela di atas aplikasi lain (overlay). " +
            "Tanpa op ini app sering gagal menampilkan bubble, floating button, atau " +
            "tampilan anti-overlay di app banking.",
        group = OpGroup.OVERLAY,
        fallbackCode = 24,
    )

    val ALL: List<OpDef> = listOf(
        OVERLAY,
        OpDef(
            op = "android:toast_window",
            shell = "TOAST_WINDOW",
            title = "Tampilkan toast",
            description = "Menampilkan pesan toast sistem di atas app lain.",
            group = OpGroup.OVERLAY,
            fallbackCode = 45,
        ),
        OpDef(
            op = "android:picture_in_picture",
            shell = "PICTURE_IN_PICTURE",
            title = "Picture-in-picture",
            description = "Menampilkan jendela video mengambang.",
            group = OpGroup.OVERLAY,
            fallbackCode = 67,
        ),
        OpDef(
            op = "android:project_media",
            shell = "PROJECT_MEDIA",
            title = "Proyeksi media (cast)",
            description = "Menampilkan media ke perangkat lain.",
            group = OpGroup.OVERLAY,
            fallbackCode = 46,
        ),
        OpDef(
            op = "android:read_clipboard",
            shell = "READ_CLIPBOARD",
            title = "Baca clipboard",
            description = "Membaca isi clipboard secara diam-diam.",
            group = OpGroup.PRIVACY,
            fallbackCode = 29,
        ),
        OpDef(
            op = "android:read_contacts",
            shell = "READ_CONTACTS",
            title = "Baca kontak",
            description = "Mengakses daftar kontak.",
            group = OpGroup.PRIVACY,
            fallbackCode = 4,
        ),
        OpDef(
            op = "android:read_calendar",
            shell = "READ_CALENDAR",
            title = "Baca kalender",
            description = "Mengakses data kalender.",
            group = OpGroup.PRIVACY,
            fallbackCode = 8,
        ),
        OpDef(
            op = "android:fine_location",
            shell = "FINE_LOCATION",
            title = "Lokasi presisi",
            description = "Mengakses GPS presisi tinggi.",
            group = OpGroup.PRIVACY,
            fallbackCode = 1,
        ),
        OpDef(
            op = "android:coarse_location",
            shell = "COARSE_LOCATION",
            title = "Lokasi kasar",
            description = "Mengakses lokasi perkiraan.",
            group = OpGroup.PRIVACY,
            fallbackCode = 0,
        ),
        OpDef(
            op = "android:mock_location",
            shell = "MOCK_LOCATION",
            title = "Lokasi palsu",
            description = "Memalsukan lokasi perangkat (mock location).",
            group = OpGroup.PRIVACY,
            fallbackCode = 58,
        ),
        OpDef(
            op = "android:camera",
            shell = "CAMERA",
            title = "Kamera",
            description = "Membuka kamera di belakang layar.",
            group = OpGroup.MEDIA,
            fallbackCode = 26,
        ),
        OpDef(
            op = "android:record_audio",
            shell = "RECORD_AUDIO",
            title = "Mikrofon",
            description = "Merekam audio.",
            group = OpGroup.MEDIA,
            fallbackCode = 27,
        ),
        OpDef(
            op = "android:write_settings",
            shell = "WRITE_SETTINGS",
            title = "Ubah system settings",
            description = "Mengubah pengaturan sistem tanpa izin normal.",
            group = OpGroup.BACKGROUND,
            fallbackCode = 23,
        ),
        OpDef(
            op = "android:get_usage_stats",
            shell = "GET_USAGE_STATS",
            title = "Statistik pemakaian app",
            description = "Membaca data pemakaian app (UsageStats).",
            group = OpGroup.BACKGROUND,
            fallbackCode = 43,
        ),
        OpDef(
            op = "android:request_install_packages",
            shell = "REQUEST_INSTALL_PACKAGES",
            title = "Minta install APK",
            description = "Meminta instalasi paket dari luar store.",
            group = OpGroup.BACKGROUND,
            fallbackCode = 66,
        ),
        OpDef(
            op = "android:request_delete_packages",
            shell = "REQUEST_DELETE_PACKAGES",
            title = "Minta hapus paket",
            description = "Meminta penghapusan aplikasi lain.",
            group = OpGroup.BACKGROUND,
            fallbackCode = 72,
        ),
        OpDef(
            op = "android:wake_lock",
            shell = "WAKE_LOCK",
            title = "Wakelock",
            description = "Menahan perangkat tetap bangun.",
            group = OpGroup.BACKGROUND,
            fallbackCode = 40,
        ),
        OpDef(
            op = "android:vibrate",
            shell = "VIBRATE",
            title = "Getar",
            description = "Mengakses getaran perangkat.",
            group = OpGroup.BACKGROUND,
            fallbackCode = 32,
        ),
        OpDef(
            op = "android:run_in_background",
            shell = "RUN_IN_BACKGROUND",
            title = "Jalan di latar belakang",
            description = "Diizinkan berjalan di background.",
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
