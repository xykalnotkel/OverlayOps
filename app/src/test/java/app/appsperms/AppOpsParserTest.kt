package app.appsperms

import app.appsperms.core.AppOpsParser
import app.appsperms.core.OpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test untuk parser `appops`. Ini logika paling rawan di app: format keluaran
 * `appops get` berbeda antar ROM (HyperOS, One UI, ColorOS, Pixel), dan salah parse
 * berarti app menampilkan status izin yang BOHONG ke user.
 */
class AppOpsParserTest {

    @Test
    fun `membaca baris standar`() {
        val hasil = AppOpsParser.parseAppOpsGet("SYSTEM_ALERT_WINDOW: allow")
        assertEquals(OpStatus.ALLOWED, hasil["SYSTEM_ALERT_WINDOW"])
    }

    @Test
    fun `mengabaikan keterangan tambahan setelah titik koma`() {
        val hasil = AppOpsParser.parseAppOpsGet("RUN_ANY_IN_BACKGROUND: ignore; time=+1m2s")
        assertEquals(OpStatus.IGNORED, hasil["RUN_ANY_IN_BACKGROUND"])
    }

    @Test
    fun `membaca baris Uid mode`() {
        val hasil = AppOpsParser.parseAppOpsGet("Uid mode: RUN_ANY_IN_BACKGROUND: foreground")
        assertEquals(OpStatus.FOREGROUND, hasil["RUN_ANY_IN_BACKGROUND"])
    }

    @Test
    fun `membaca deny sebagai diblokir`() {
        val hasil = AppOpsParser.parseAppOpsGet("CAMERA: deny")
        assertEquals(OpStatus.ERRORED, hasil["CAMERA"])
    }

    @Test
    fun `membaca beberapa baris sekaligus`() {
        val keluaran = """
            SYSTEM_ALERT_WINDOW: allow
            CAMERA: ignore
            RECORD_AUDIO: deny
            READ_CLIPBOARD: default
        """.trimIndent()
        val hasil = AppOpsParser.parseAppOpsGet(keluaran)
        assertEquals(4, hasil.size)
        assertEquals(OpStatus.ALLOWED, hasil["SYSTEM_ALERT_WINDOW"])
        assertEquals(OpStatus.IGNORED, hasil["CAMERA"])
        assertEquals(OpStatus.ERRORED, hasil["RECORD_AUDIO"])
        assertEquals(OpStatus.DEFAULT, hasil["READ_CLIPBOARD"])
    }

    @Test
    fun `tahan terhadap baris sampah dan teks lokal`() {
        val keluaran = """
            No operations.
            paket.lokal: allow
            _PRIVATE_OP: allow
            : allow
            SYSTEM_ALERT_WINDOW: allow
        """.trimIndent()
        val hasil = AppOpsParser.parseAppOpsGet(keluaran)
        // hanya nama op yang sah (HURUF_BESAR, minimal 3 huruf) yang diambil
        assertEquals(1, hasil.size)
        assertTrue(hasil.containsKey("SYSTEM_ALERT_WINDOW"))
    }

    @Test
    fun `nama op huruf kecil tidak dianggap op`() {
        val hasil = AppOpsParser.parseAppOpsGet("package.name: allow")
        assertTrue(hasil.isEmpty())
    }

    @Test
    fun `queryOp membuang header Uid dan baris kosong`() {
        val keluaran = """
            Uid 1000:
            com.contoh.satu

            com.contoh.dua
        """.trimIndent()
        val paket = AppOpsParser.parseQueryOpPackages(keluaran)
        assertEquals(listOf("com.contoh.satu", "com.contoh.dua"), paket)
    }
}
