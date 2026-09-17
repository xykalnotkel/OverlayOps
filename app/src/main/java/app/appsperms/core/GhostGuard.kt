package app.appsperms.core

/**
 * Logika murni "perisai ghost touch": menentukan pita (band) mana yang dipasang
 * dan di mana posisinya. Sengaja dipisah dari service supaya bisa diuji JVM:
 * salah hitung = sebagian layar terkubur diam-diam, atau ghost touch lolos.
 *
 * Aturan geometri (koordinat layar penuh, sudah termasuk status/navigation bar
 * karena jendela overlay memakai FLAG_LAYOUT_NO_LIMITS):
 *  - pita atas/bawah  = selebar layar, setebal T, nempel tepi;
 *  - pita kiri/kanan  = setebal T, sisanya DIANTARA pita atas & bawah, supaya
 *    sudut tidak ditumpuk dua jendela (hemat & tidak ada area "double consume").
 */
object GhostGuard {

    enum class Side { TOP, BOTTOM, LEFT, RIGHT }

    /** Satu jendela pita dalam piksel fisik. */
    data class Band(val side: Side, val x: Int, val y: Int, val w: Int, val h: Int) {
        val isHorizontal: Boolean get() = side == Side.TOP || side == Side.BOTTOM
    }

    /**
     * Susun daftar pita aktif. Kalau layar berputar (w > h) pemanggil tinggal
     * mengirim w/h yang tertukar — hasilnya simetris, tidak ada khusus.
     */
    fun bands(
        screenWidth: Int,
        screenHeight: Int,
        thickness: Int,
        enabled: Set<Side>,
    ): List<Band> {
        require(screenWidth > 0 && screenHeight > 0)
        val t = thickness.coerceAtLeast(0)
        val out = ArrayList<Band>(4)
        if (t == 0 || enabled.isEmpty()) return out

        // Pita samping jangan lebih tebal dari setengah lebar layar.
        val sideThickness = t.coerceAtMost(screenWidth / 2)
        val vInset = t.coerceAtMost(screenHeight / 2)
        val sideHeight = (screenHeight - 2 * vInset).coerceAtLeast(0)

        if (Side.TOP in enabled) out += Band(Side.TOP, 0, 0, screenWidth, t)
        if (Side.BOTTOM in enabled) out += Band(Side.BOTTOM, 0, screenHeight - t, screenWidth, t)
        if (Side.LEFT in enabled && sideHeight > 0)
            out += Band(Side.LEFT, 0, vInset, sideThickness, sideHeight)
        if (Side.RIGHT in enabled && sideHeight > 0)
            out += Band(Side.RIGHT, screenWidth - sideThickness, vInset, sideThickness, sideHeight)
        return out
    }

    /** Total piksel yang dikorbankan — dipakai untuk peringatan "terlalu lebar". */
    fun coveredFraction(screenWidth: Int, screenHeight: Int, bands: List<Band>): Double {
        val total = screenWidth.toLong() * screenHeight
        if (total <= 0) return 0.0
        val area = bands.sumOf { it.w.toLong() * it.h }
        return area.toDouble() / total
    }

    /** Batas wajar: perisai tidak boleh menutupi lebih dari 30% layar. */
    const val MAX_COVER_FRACTION = 0.30

    /** Ketebalan (dp) yang diizinkan di slider. */
    const val MIN_THICKNESS_DP = 12
    const val MAX_THICKNESS_DP = 96
}
