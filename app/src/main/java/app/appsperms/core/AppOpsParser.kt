package app.appsperms.core

/**
 * Parser keluaran perintah `appops`.
 *
 * Sengaja dipisah dari [ShizukuBridge] supaya file ini **murni** — tanpa import Android
 * atau Shizuku sama sekali — sehingga bisa diuji lewat unit test JVM biasa
 * (lihat `app/src/test/java/app/appsperms/AppOpsParserTest.kt`).
 *
 * Format `appops get <pkg>` berbeda-beda antar ROM, jadi parser ini dibuat toleran:
 *
 * ```
 * SYSTEM_ALERT_WINDOW: allow
 * RUN_ANY_IN_BACKGROUND: ignore; time=+1m2s
 * Uid mode: RUN_ANY_IN_BACKGROUND: foreground
 * ```
 */
object AppOpsParser {

    /** Nama op selalu HURUF_BESAR_DENGAN_UNDERSCORE, minimal 3 huruf. */
    private val OP_NAME = Regex("^[A-Z][A-Z0-9_]{2,}$")

    /** Ubah keluaran `appops get <pkg>` menjadi peta nama-op → status. */
    fun parseAppOpsGet(output: String): Map<String, OpStatus> {
        val result = HashMap<String, OpStatus>()
        output.lineSequence().forEach { raw ->
            var line = raw.trim()
            if (line.startsWith("Uid mode:", ignoreCase = true)) {
                line = line.substringAfter(':').trim()
            }
            val separator = line.indexOf(':')
            if (separator <= 0) return@forEach

            val key = line.substring(0, separator).trim()
            if (!OP_NAME.matches(key)) return@forEach

            val rest = line.substring(separator + 1).trim()
            // "ignore; time=+1m2s" -> "ignore"
            val mode = rest.substringBefore(';').trim().substringBefore(' ').trim()
            if (mode.isNotEmpty()) result[key] = OpStatus.forShellName(mode)
        }
        return result
    }

    /**
     * Ambil daftar paket dari `appops query-op <OP> <mode>`.
     * Keluaran berisi header "Uid ..." yang harus dibuang.
     */
    fun parseQueryOpPackages(output: String): List<String> = output.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("Uid") && !it.contains(':') }
        .toList()
}
