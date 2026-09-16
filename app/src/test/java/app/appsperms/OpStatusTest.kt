package app.appsperms

import app.appsperms.core.AccessSnapshot
import app.appsperms.core.AccessState
import app.appsperms.core.AppTypeFilter
import app.appsperms.core.OpStatus
import app.appsperms.model.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test pemetaan mode/status AppOps — inti dari benar-tidaknya warna & label di UI. */
class OpStatusTest {

    @Test
    fun `mode integer cocok dengan AppOpsManager`() {
        assertEquals(0, OpStatus.ALLOWED.mode)
        assertEquals(1, OpStatus.IGNORED.mode)
        assertEquals(2, OpStatus.ERRORED.mode)
        assertEquals(3, OpStatus.DEFAULT.mode)
        assertEquals(4, OpStatus.FOREGROUND.mode)
    }

    @Test
    fun `fromMode mengembalikan UNKNOWN untuk mode tak dikenal`() {
        assertEquals(OpStatus.ALLOWED, OpStatus.fromMode(0))
        assertEquals(OpStatus.UNKNOWN, OpStatus.fromMode(999))
        assertEquals(OpStatus.UNKNOWN, OpStatus.fromMode(-5))
    }

    @Test
    fun `nama perintah shell pulang pergi tetap konsisten`() {
        listOf(OpStatus.ALLOWED, OpStatus.IGNORED, OpStatus.ERRORED, OpStatus.DEFAULT, OpStatus.FOREGROUND)
            .forEach { status ->
                val namaShell = OpStatus.shellName(status)
                assertEquals(status, OpStatus.forShellName(namaShell))
            }
    }

    @Test
    fun `alias nama shell dari berbagai ROM dikenali`() {
        assertEquals(OpStatus.ALLOWED, OpStatus.forShellName("allowed"))
        assertEquals(OpStatus.IGNORED, OpStatus.forShellName("Ignored"))
        assertEquals(OpStatus.ERRORED, OpStatus.forShellName("denied"))
        assertEquals(OpStatus.DEFAULT, OpStatus.forShellName("missing"))
        assertEquals(OpStatus.UNKNOWN, OpStatus.forShellName("entah"))
    }

    @Test
    fun `isExplicit hanya untuk status yang ditulis app`() {
        assertTrue(OpStatus.ALLOWED.isExplicit)
        assertTrue(OpStatus.IGNORED.isExplicit)
        assertTrue(OpStatus.ERRORED.isExplicit)
        assertFalse(OpStatus.DEFAULT.isExplicit)
        assertFalse(OpStatus.UNKNOWN.isExplicit)
    }
}

/** Test pencarian & filter tipe app. */
class AppEntryTest {

    private fun entri(nama: String, paket: String, sistem: Boolean = false) = AppEntry(
        packageName = paket,
        label = nama,
        uid = 10000,
        isSystem = sistem,
        targetSdk = 34,
        enabled = true,
        declaresOverlay = true,
        icon = null,
    )

    @Test
    fun `pencarian mengabaikan besar kecil huruf`() {
        val app = entri("Kamera Bawah Air", "com.contoh.kamera")
        assertTrue(app.matches("kamera"))
        assertTrue(app.matches("KAMERA"))
        assertTrue(app.matches("contoh.kamera"))
        assertFalse(app.matches("whatsapp"))
    }

    @Test
    fun `pencarian kosong menerima semua`() {
        assertTrue(entri("Apa Saja", "com.a").matches(""))
        assertTrue(entri("Apa Saja", "com.a").matches("   "))
    }

    @Test
    fun `filter tipe memisahkan app sistem dan user`() {
        val sistem = entri("Pengaturan", "com.android.settings", sistem = true)
        val user = entri("WhatsApp", "com.whatsapp")
        assertTrue(AppTypeFilter.ALL.accepts(sistem))
        assertTrue(AppTypeFilter.ALL.accepts(user))
        assertTrue(AppTypeFilter.SYSTEM.accepts(sistem))
        assertFalse(AppTypeFilter.SYSTEM.accepts(user))
        assertTrue(AppTypeFilter.USER.accepts(user))
        assertFalse(AppTypeFilter.USER.accepts(sistem))
    }
}

/** Test pemilihan state koneksi — menentukan pesan mana yang benar ditampilkan. */
class AccessSnapshotTest {

    @Test
    fun `shizuku mati`() {
        val snapshot = AccessSnapshot(shizukuInstalled = true, binderAlive = false)
        assertEquals(AccessState.SHIZUKU_OFF, snapshot.state)
        assertFalse(snapshot.canOperate)
    }

    @Test
    fun `izin belum diberikan`() {
        val snapshot = AccessSnapshot(shizukuInstalled = true, binderAlive = true, permission = false)
        assertEquals(AccessState.PERMISSION_DENIED, snapshot.state)
        assertFalse(snapshot.canOperate)
    }

    @Test
    fun `siap lewat shell dan root`() {
        val shell = AccessSnapshot(binderAlive = true, permission = true, bridgeReady = true, uid = 2000)
        assertEquals(AccessState.READY_SHELL, shell.state)
        assertFalse(shell.preferShell)

        val root = AccessSnapshot(binderAlive = true, permission = true, bridgeReady = true, uid = 0)
        assertEquals(AccessState.READY_ROOT, root.state)
        assertTrue(root.isRoot)
    }

    @Test
    fun `fallback shell tetap bisa mengubah izin`() {
        val snapshot = AccessSnapshot(binderAlive = true, permission = true, shellAvailable = true)
        assertEquals(AccessState.SHELL_FALLBACK, snapshot.state)
        assertTrue(snapshot.canOperate)
        assertTrue(snapshot.preferShell)
    }

    @Test
    fun `binder dan shell dua-duanya gagal`() {
        val snapshot = AccessSnapshot(binderAlive = true, permission = true)
        assertEquals(AccessState.BRIDGE_FAILED, snapshot.state)
        assertFalse(snapshot.canOperate)
    }
}
