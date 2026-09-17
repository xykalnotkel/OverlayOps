package app.appsperms

import app.appsperms.core.GhostGuard
import app.appsperms.core.GhostGuard.Band
import app.appsperms.core.GhostGuard.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Geometri pita anti ghost-touch — pure function, jadi bisa diuji presisi:
 * salah hitung = sebagian layar terkubur diam-diam atau ghost touch lolos.
 */
class GhostGuardTest {

    private fun bands(sides: Set<Side>, t: Int, w: Int = 1000, h: Int = 2000): List<Band> =
        GhostGuard.bands(w, h, t, sides)

    @Test
    fun `empat sisi menutup tiap tepi dan sudut tidak ditumpuk`() {
        val list = bands(Side.entries.toSet(), 100)
        assertEquals(4, list.size)
        val top = list.first { it.side == Side.TOP }
        val bottom = list.first { it.side == Side.BOTTOM }
        val left = list.first { it.side == Side.LEFT }
        val right = list.first { it.side == Side.RIGHT }
        assertEquals(0, top.y)
        assertEquals(1000, top.w)
        assertEquals(1900, bottom.y)
        // sisi kiri/kanan DIANTARA pita atas-bawah -> tidak dobel consume di sudut
        assertEquals(100, left.y)
        assertEquals(1800, left.h)
        assertEquals(900, right.x)
    }

    @Test
    fun `tanpa sisi tidak ada pita`() {
        assertTrue(bands(emptySet(), 100).isEmpty())
    }

    @Test
    fun `ketebalan nol = mati bersih`() {
        assertTrue(bands(setOf(Side.BOTTOM), 0).isEmpty())
    }

    @Test
    fun `ketebalan ekstrem di-clamp jangan menutupi seluruh layar`() {
        val list = bands(setOf(Side.LEFT, Side.RIGHT), 900)
        // tinggi pita samping jadi 2000-2*900 = 200, bukan nol & bukan negatif
        assertTrue(list.all { it.h >= 0 && it.w >= 0 })
        val left = list.first { it.side == Side.LEFT }
        assertEquals(200, left.h)
    }

    @Test
    fun `side lebih tebal dari setengah lebar di-clamp`() {
        val list = bands(setOf(Side.LEFT), 600, w = 1000)
        val left = list.first { it.side == Side.LEFT }
        assertEquals(500, left.w)
    }

    @Test
    fun `fraksi cakupan masuk akal`() {
        // layar 1000x2000; pita bawah setebal 100 = 5%
        val f = GhostGuard.coveredFraction(1000, 2000, bands(setOf(Side.BOTTOM), 100))
        assertEquals(0.05, f, 1e-9)
    }

    @Test
    fun `layar horizontal tetap valid`() {
        val list = bands(setOf(Side.TOP, Side.BOTTOM), 50, w = 2400, h = 1080)
        assertEquals(2, list.size)
        assertTrue(list.all { it.w == 2400 && it.h == 50 })
    }
}
