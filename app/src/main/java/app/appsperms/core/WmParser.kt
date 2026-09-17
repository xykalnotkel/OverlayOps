package app.appsperms.core

/**
 * Logika murni untuk tweak resolusi & DPI lewat perintah `wm`.
 *
 * Sama seperti [AppOpsParser], file ini sengaja tanpa import Android supaya
 * bisa diuji JVM biasa (`WmParserTest`). Parsing yang salah di sini berarti
 * UI menampilkan resolusi yang bohong atau — lebih buruk — mengirim nilai
 * kacau ke `wm size`, jadi semua cabang format ditest.
 *
 * Format keluaran `wm size` / `wm density` (AOSP, stabil sejak Android 7):
 * ```
 * Physical size: 1080x2400
 * Override size: 720x1600
 * Physical density: 440
 * Override density: 320
 * ```
 */
object WmParser {

    /** Batas aman density supaya user tidak bisa mengunci layar jadi tak terbaca. */
    const val MIN_DENSITY = 120
    const val MAX_DENSITY = 640

    /** Dimensi minimal agar UI masih bisa dipakai (tombol tidak saling tindih). */
    const val MIN_DIMENSION = 480

    /** Hasil pembacaan `wm size` + `wm density`. Nilai null = tidak terbaca. */
    data class DisplayInfo(
        val physicalW: Int? = null,
        val physicalH: Int? = null,
        val overrideW: Int? = null,
        val overrideH: Int? = null,
        val physicalDensity: Int? = null,
        val overrideDensity: Int? = null,
    ) {
        /** Resolusi yang sekarang aktif dipakai layar. */
        val effectiveSize: Pair<Int, Int>?
            get() = (overrideW ?: physicalW)?.let { w -> (overrideH ?: physicalH)?.let { h -> w to h } }

        val density: Int? get() = overrideDensity ?: physicalDensity
        val isSizeOverridden: Boolean get() = overrideW != null && overrideH != null
        val isDensityOverridden: Boolean get() = overrideDensity != null
    }

    /** Ambil pasangan WxH dari baris berformat `label: 1080x2400`. */
    private fun parseWH(output: String, label: String): Pair<Int, Int>? =
        output.lineSequence()
            .firstOrNull { it.trim().startsWith(label, ignoreCase = true) }
            ?.substringAfter(':')?.trim()
            ?.let { parseSize(it) }

    /** "1080x2400" -> (1080, 2400). Toleran spasi, kapital X, dan angka lebar apa pun. */
    fun parseSize(text: String): Pair<Int, Int>? {
        val m = Regex("""^\s*(\d{3,5})\s*[xX×]\s*(\d{3,5})\s*$""").find(text) ?: return null
        val w = m.groupValues[1].toIntOrNull() ?: return null
        val h = m.groupValues[2].toIntOrNull() ?: return null
        if (w < MIN_DIMENSION || h < MIN_DIMENSION) return null
        return w to h
    }

    /** Satu angka dianggap sisi pendek; sisi lain dihitung dari rasio layar fisik. */
    fun resolveSize(text: String, physical: Pair<Int, Int>?, landscape: Boolean = false): Pair<Int, Int>? {
        parseSize(text)?.let { return it }
        val short = text.trim().toIntOrNull()?.takeIf { it >= MIN_DIMENSION } ?: return null
        val (pw, ph) = physical ?: return null
        val ratio = maxOf(pw, ph).toDouble() / minOf(pw, ph)
        val long = evenDown((short * ratio).toInt()).coerceAtLeast(MIN_DIMENSION)
        return if (landscape) long to short else short to long
    }

    /** Validasi ukuran baru, termasuk input satu angka yang sudah di-resolve UI. */
    fun validateSize(text: String, physical: Pair<Int, Int>?, landscape: Boolean = false): String? {
        val parsed = resolveSize(text, physical, landscape)
            ?: return "Ketik satu sisi (contoh 1080) atau WXH (contoh 1080x2400)"
        val (w, h) = parsed
        physical?.let { (pw, ph) ->
            if (w > pw * 2 || h > ph * 2) return "Terlalu besar — maksimal 2x resolusi fisik (${pw}x$ph)."
        }
        return null // null = valid
    }

    /** Parse gabungan output `wm size` + `wm density` menjadi [DisplayInfo]. */
    fun parseDisplay(sizeOutput: String, densityOutput: String): DisplayInfo {
        val (pw, ph) = parseWH(sizeOutput, "Physical size") ?: (null to null)
        val (ow, oh) = parseWH(sizeOutput, "Override size") ?: (null to null)
        val pd = parseNumber(densityOutput, "Physical density")
        val od = parseNumber(densityOutput, "Override density")
        return DisplayInfo(pw, ph, ow, oh, pd, od)
    }

    /** `Physical density: 440` — satu angka, BUKAN pasangan WxH (density bukan ukuran dua dimensi). */
    private fun parseNumber(output: String, label: String): Int? =
        output.lineSequence()
            .firstOrNull { it.trim().startsWith(label, ignoreCase = true) }
            ?.substringAfter(':')?.trim()
            ?.toIntOrNull()

    /** Validasi density manual. Null = valid. */
    fun validateDensity(text: String): String? {
        val v = text.trim().toIntOrNull() ?: return "Density harus angka, contoh 360"
        if (v !in MIN_DENSITY..MAX_DENSITY) return "Density harus di rentang $MIN_DENSITY–$MAX_DENSITY"
        return null
    }

    /**
     * Skala resolusi mempertahankan rasio: (1080x2400) @ 65% -> (702x1560) dibulatkan
     * ke genap supaya tidak ada sisi ganjil yang bikin ROM tertentu membuang 1 px.
     */
    fun scaledSize(w: Int, h: Int, percent: Int): Pair<Int, Int> {
        val f = percent / 100.0
        val sw = evenDown((w * f).toInt()).coerceAtLeast(MIN_DIMENSION)
        val sh = evenDown((h * f).toInt()).coerceAtLeast(MIN_DIMENSION)
        return sw to sh
    }

    /** Density ideal untuk resolusi hasil skala (UI tetap proporsional, GPU bekerja lebih ringan). */
    fun scaledDensity(physicalDensity: Int, percent: Int): Int =
        (physicalDensity * percent / 100.0).toInt().coerceIn(MIN_DENSITY, MAX_DENSITY)

    private fun evenDown(v: Int): Int = if (v % 2 == 0) v else v - 1

    /** Preset yang ditawarkan di UI, dalam persen dari resolusi fisik. */
    val PRESETS: List<Int> = listOf(50, 65, 75, 90)
}
