package app.appsperms.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper Shizuku: probe status akses, minta izin, dan jalankan perintah `appops`
 * (jalur utama yang paling tahan-banting; binder dipakai sebagai bonus).
 */
object ShizukuBridge {

    private const val TAG = "ShizukuBridge"

    const val REQUEST_CODE = 4210
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    // ---------------------------------------------------------------- status

    fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun myUid(): Int = try {
        Shizuku.getUid()
    } catch (t: Throwable) {
        -1
    }

    fun version(): Int = try {
        Shizuku.getVersion()
    } catch (t: Throwable) {
        -1
    }

    fun isInstalledHuh(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (t: Throwable) {
        false
    }

    /** Satu tembakan: semua informasi yang dibutuhkan UI untuk memutuskan pesan yang tepat. */
    fun probe(context: Context, connectBridge: Boolean = true): AccessSnapshot {
        val alive = isBinderAlive()
        val allowed = alive && hasPermission()
        val shellOk = allowed && shellAvailable

        var bridgeReady = false
        var bridgeError: String? = null
        if (allowed && connectBridge) {
            bridgeReady = AppOpsBridge.connect()
            bridgeError = AppOpsBridge.lastError
        }

        return AccessSnapshot(
            shizukuInstalled = isInstalledHuh(context),
            binderAlive = alive,
            permission = allowed,
            uid = if (alive) myUid() else -1,
            version = if (alive) version() else -1,
            bridgeReady = bridgeReady,
            bridgeError = bridgeError,
            shellAvailable = shellOk,
            shellError = if (!shellOk && allowed) "Shizuku.newProcess() tidak bisa diakses" else null,
        )
    }

    fun requestPermission() = runCatching { Shizuku.requestPermission(REQUEST_CODE) }

    fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) =
        runCatching { Shizuku.addBinderReceivedListenerSticky(listener) }

    fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) =
        runCatching { Shizuku.removeBinderReceivedListener(listener) }

    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) =
        runCatching { Shizuku.addBinderDeadListener(listener) }

    fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) =
        runCatching { Shizuku.removeBinderDeadListener(listener) }

    fun addPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) =
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }

    fun removePermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) =
        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }

    // ----------------------------------------------------------------- shell

    /**
     * Shizuku API 13 menyembunyikan Shizuku.newProcess (masih ada di class, tapi private),
     * jadi dipanggil lewat refleksi. Kalau gagal, semua fungsi shell mati dan app
     * jatuh ke jalur binder saja.
     */
    private val newProcessMethod: Method? by lazy {
        runCatching {
            Shizuku::class.java
                .getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java,
                )
                .apply { isAccessible = true }
        }.onFailure { Log.w(TAG, "newProcess() tidak bisa diakses", it) }.getOrNull()
    }

    val shellAvailable: Boolean get() = newProcessMethod != null

    data class ShellResult(val code: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = code == 0
        val message: String get() = stderr.ifBlank { stdout }.trim().ifBlank { "exit $code" }
    }

    /** Jalankan perintah dengan hak akses Shizuku. Panggil dari thread IO. */
    fun shell(command: String): ShellResult? {
        val method = newProcessMethod ?: return null
        return try {
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val errBuffer = StringBuilder()
            val errThread = Thread {
                runCatching {
                    process.errorStream.bufferedReader().forEachLine { errBuffer.append(it).append('\n') }
                }
            }.apply { isDaemon = true; start() }
            val stdout = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            runCatching { errThread.join(1_000) }
            ShellResult(code, stdout, errBuffer.toString())
        } catch (t: Throwable) {
            Log.w(TAG, "shell gagal: $command", t)
            null
        }
    }

    // ------------------------------------------------------- perintah appops

    /**
     * Baca mode overlay SEMUA paket sekaligus (4 perintah) — jauh lebih cepat
     * daripada satu perintah per paket.
     */
    fun shellQueryOverlayBulk(): Map<String, OpStatus>? {
        val map = HashMap<String, OpStatus>()
        var anySuccess = false
        for (status in listOf(OpStatus.ALLOWED, OpStatus.IGNORED, OpStatus.ERRORED, OpStatus.FOREGROUND)) {
            val result = shell("appops query-op ${OpCatalog.OVERLAY.shell} ${OpStatus.shellName(status)}")
                ?: continue
            if (!result.ok) continue
            anySuccess = true
            AppOpsParser.parseQueryOpPackages(result.stdout).forEach { map[it] = status }
        }
        return if (anySuccess) map else null
    }

    /** Semua op milik satu paket dalam SATU perintah: `appops get <pkg>`. */
    fun shellReadOps(pkg: String): Map<String, OpStatus>? {
        val result = shell("appops get $pkg") ?: return null
        if (!result.ok) return null
        val parsed = parseAppOpsGet(result.stdout)
        if (parsed.isEmpty()) return null
        return parsed
    }

    /**
     * Parser `appops get <pkg>` — logikanya dipindah ke [AppOpsParser] supaya bisa
     * diuji lewat unit test JVM tanpa Android. Fungsi ini tinggal jadi jembatan.
     */
    fun parseAppOpsGet(output: String): Map<String, OpStatus> =
        AppOpsParser.parseAppOpsGet(output)

    /** Tulis satu op: `appops set --uid <pkg> <OP> <mode>`. */
    fun shellSetOp(pkg: String, shellOp: String, status: OpStatus): String? {
        val result = shell("appops set --uid $pkg $shellOp ${OpStatus.shellName(status)}")
            ?: return "perintah appops tidak bisa dijalankan (jalur shell mati)"
        return if (result.ok) null else result.message
    }

    /** Baca satu op (fallback kalau `appops get <pkg>` tidak bisa diparse). */
    fun shellGetOp(pkg: String, shellOp: String): OpStatus {
        val result = shell("appops get $pkg $shellOp") ?: return OpStatus.UNKNOWN
        val text = result.stdout.ifBlank { result.stderr }
        if (text.isBlank() || text.contains("No operations", ignoreCase = true)) return OpStatus.DEFAULT
        val parsed = parseAppOpsGet(text)
        parsed[shellOp]?.let { return it }
        val line = text.lineSequence().firstOrNull { it.contains(shellOp) } ?: return OpStatus.DEFAULT
        return OpStatus.forShellName(line.substringAfter(':').trim().substringBefore(';'))
    }

    // ------------------------------------------------------------ diagnostics

    fun deviceSummary(context: Context, snapshot: AccessSnapshot = probe(context)): String {
        val shizuku = when {
            !snapshot.shizukuInstalled -> "belum terinstall"
            !snapshot.binderAlive -> "terinstall, tapi belum berjalan"
            !snapshot.permission -> "berjalan (v${snapshot.version}), izin BELUM diberikan"
            else -> "berjalan (v${snapshot.version}, uid=${snapshot.uid}, ${if (snapshot.isRoot) "root" else "shell"})"
        }
        return buildString {
            append("Android ").append(Build.VERSION.RELEASE)
            append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Perangkat: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("ROM: ").append(Build.DISPLAY).append('\n')
            append("Shizuku: ").append(shizuku).append('\n')
            append("Status: ").append(snapshot.state.name).append('\n')
            append("Jalur aktif: ").append(if (snapshot.preferShell) "perintah `appops` (shell)" else "binder IAppOpsService").append('\n')
            append("Binder: ").append(if (snapshot.bridgeReady) "OK" else "gagal").append('\n')
            snapshot.bridgeError?.let { append("  error binder: ").append(it).append('\n') }
            append("Shell Shizuku: ").append(if (snapshot.shellAvailable) "OK" else "tidak tersedia").append('\n')
            append("opCode(overlay): ").append(AppOpsBridge.opCode(OpCatalog.OVERLAY.op) ?: "belum ter-resolve (pakai fallback 24)").append('\n')
            append("Timestamp: ").append(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            )
        }
    }
}
