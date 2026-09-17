package app.appsperms

import app.appsperms.core.HistoryCodec
import app.appsperms.core.HistoryLine
import app.appsperms.core.OpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Serialisasi riwayat + rencana undo (v1.4). File log adalah data pengguna —
 * decoder tidak boleh melempar saat baris rusak, dan undoPlan harus deterministik.
 */
class HistoryCodecTest {

    private fun line(
        t: Long,
        pkg: String = "com.example.app",
        op: String = "android:system_alert_window",
        from: OpStatus = OpStatus.DEFAULT,
        to: OpStatus = OpStatus.ALLOWED,
    ) = HistoryLine(t, pkg, op, from, to)

    @Test
    fun `encode lalu decode menghasilkan baris yang sama`() {
        val original = line(1_700_000_000_000)
        val back = HistoryCodec.decode(HistoryCodec.encode(original))
        assertEquals(original, back)
    }

    @Test
    fun `baris rusak tidak pernah melempar`() {
        assertNull(HistoryCodec.decode(""))
        assertNull(HistoryCodec.decode("cuma satu kata"))
        assertNull(HistoryCodec.decode("123|pkg|op|ALLOWED")) // kurang field
        assertNull(HistoryCodec.decode("abc|pkg|op|ALLOWED|DEFAULT")) // time bukan angka
        assertNull(HistoryCodec.decode("1|pkg|op|NOT_A_STATUS|ALLOWED")) // enum palsu
        assertNull(HistoryCodec.decode("1||op|ALLOWED|DEFAULT")) // pkg kosong
    }

    @Test
    fun `decodeAll melewati baris rusak dan menjaga urutan`() {
        val text = buildString {
            append(HistoryCodec.encode(line(1))).append('\n')
            append("baris sampah yang tidak dikenal").append('\n')
            append(HistoryCodec.encode(line(2))).append('\n')
        }
        val all = HistoryCodec.decodeAll(text)
        assertEquals(2, all.size)
        assertEquals(1L, all[0].timeMs)
        assertEquals(2L, all[1].timeMs)
    }

    @Test
    fun `trim membatasi ke MAX_LINES terbaru`() {
        val lines = (1L..700L).map { line(it) }
        val trimmed = HistoryCodec.trim(lines)
        assertEquals(HistoryCodec.MAX_LINES, trimmed.size)
        assertEquals(201L, trimmed.first().timeMs)
        assertEquals(700L, trimmed.last().timeMs)
    }

    @Test
    fun `undoPlan mengambil status TERAWAL per pasangan app-op`() {
        val lines = listOf(
            line(10, pkg = "a", to = OpStatus.ALLOWED, from = OpStatus.DEFAULT),
            line(20, pkg = "a", to = OpStatus.ERRORED, from = OpStatus.ALLOWED),
            line(30, pkg = "b", to = OpStatus.IGNORED, from = OpStatus.DEFAULT),
        )
        val plan = HistoryCodec.undoPlan(lines)
        assertEquals(2, plan.size)
        val a = plan.first { it.pkg == "a" }
        assertEquals(OpStatus.DEFAULT, a.from) // yang paling awal, bukan langkah tengah
        assertEquals(10L, a.timeMs)
    }

    @Test
    fun `undoPlan terurut kronologis`() {
        val lines = listOf(line(99, pkg = "z"), line(11, pkg = "y"), line(50, pkg = "x"))
        val plan = HistoryCodec.undoPlan(lines)
        assertEquals(listOf(11L, 50L, 99L), plan.map { it.timeMs })
    }

    @Test
    fun `renderText memuat seluruh informasi penting`() {
        val text = HistoryCodec.renderText(listOf(line(5))) { "overlay" }
        assertTrue(text.contains("com.example.app"))
        assertTrue(text.contains("overlay"))
        assertTrue(text.contains("DEFAULT -> ALLOWED"))
    }
}
