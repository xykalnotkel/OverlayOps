package app.appsperms

import app.appsperms.core.WmParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test parser `wm size` / `wm density` + perhitungan preset resolusi.
 * Salah di sini = tombol "Ubah resolusi" mengirim angka ngawur ke sistem,
 * jadi semua cabang format wajib tertutup test.
 */
class WmParserTest {

    @Test
    fun `parse ukuran fisik saja`() {
        val info = WmParser.parseDisplay("Physical size: 1080x2400", "Physical density: 440")
        assertEquals(1080, info.physicalW)
        assertEquals(2400, info.physicalH)
        assertEquals(440, info.physicalDensity)
        assertEquals(1080 to 2400, info.effectiveSize)
        assertTrue(!info.isSizeOverridden)
    }

    @Test
    fun `override mengalahkan ukuran fisik`() {
        val out = "Physical size: 1080x2400\nOverride size: 720x1600"
        val info = WmParser.parseDisplay(out, "Physical density: 440\nOverride density: 300")
        assertEquals(720 to 1600, info.effectiveSize)
        assertTrue(info.isSizeOverridden)
        assertTrue(info.isDensityOverridden)
        assertEquals(300, info.density)
    }

    @Test
    fun `toleran spasi dan kapitalisasi`() {
        val out = "   PHYSICAL SIZE : 1440x3200   "
        val info = WmParser.parseDisplay(out.lowercase().replace("physical size", "Physical size"), "")
        assertEquals(1440 to 3200, info.effectiveSize)
    }

    @Test
    fun `keluaran kosong tidak melempar`() {
        val info = WmParser.parseDisplay("Command failed", "Command failed")
        assertNull(info.effectiveSize)
        assertNull(info.density)
    }

    @Test
    fun `parseSize menolak format jelek`() {
        assertNull(WmParser.parseSize("720"))
        assertNull(WmParser.parseSize("72 x 1600x1"))
        assertNull(WmParser.parseSize("100x100")) // < MIN_DIMENSION
        assertNotNull(WmParser.parseSize("1080×2400")) // tanda kali unicode ikut diterima
    }

    @Test
    fun `validasi ukuran menolak landscape dan gila`() {
        val physical = 1080 to 2400
        assertNotNull(WmParser.validateSize("2400x1080", physical)) // landscape
        assertNotNull(WmParser.validateSize("9000x9000", physical))  // > 2x fisik
        assertNotNull(WmParser.validateSize("sukabumi", physical))
        assertNull(WmParser.validateSize("720x1600", physical))
    }

    @Test
    fun `skala mempertahankan rasio ke angka genap`() {
        // 1080x2400 @ 65% -> (702, 1560) — keduanya genap.
        val (w, h) = WmParser.scaledSize(1080, 2400, 65)
        assertEquals(702, w)
        assertEquals(1560, h)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
    }

    @Test
    fun `skala kecil tidak pernah di bawah batas minimum`() {
        val (w, h) = WmParser.scaledSize(1080, 2400, 5)
        assertTrue(w >= WmParser.MIN_DIMENSION)
        assertTrue(h >= WmParser.MIN_DIMENSION)
    }

    @Test
    fun `density mengikuti skala dan di-clamp`() {
        assertEquals(286, WmParser.scaledDensity(440, 65))
        assertTrue(WmParser.scaledDensity(120, 20) >= WmParser.MIN_DENSITY)
        assertTrue(WmParser.scaledDensity(640, 300) <= WmParser.MAX_DENSITY)
    }

    @Test
    fun `validasi density`() {
        assertNull(WmParser.validateDensity("320"))
        assertNotNull(WmParser.validateDensity("80"))
        assertNotNull(WmParser.validateDensity("999"))
        assertNotNull(WmParser.validateDensity("abc"))
    }
}
