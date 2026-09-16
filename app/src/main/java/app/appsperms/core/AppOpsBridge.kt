package app.appsperms.core

import android.app.AppOpsManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

/**
 * Jembatan ke IAppOpsService yang tersembunyi — jalur utamanya lewat Shizuku.
 *
 * Kenapa Shizuku? `AndroidManifest.permission.MANAGE_APP_OPS_MODES` hanya dipegang
 * oleh shell/root, jadi app biasa tidak bisa membaca atau mengubah AppOps milik app lain.
 * Shizuku meminjamkan identitas shell (UID 2000) atau root (UID 0) tanpa perlu root permanen.
 */
object AppOpsBridge {

    private const val TAG = "AppOpsBridge"

    private var service: Any? = null
    private var checkOperationMethod: Method? = null
    private var setModeMethod: Method? = null
    private var noteOperationMethod: Method? = null
    private var strOpToOpMethod: Method? = null
    private var opToNameMethod: Method? = null

    private val opCodeCache = HashMap<String, Int>()
    private val nameByCode = HashMap<Int, String>()

    var lastError: String? = null
        private set

    var backend: Backend = Backend.NONE
        private set

    val isReady: Boolean get() = service != null

    fun hiddenApiBypass() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        runCatching {
            org.lsposed.hiddenapibypass.HiddenApiBypass
                .addHiddenApiExemptions("Landroid/", "Lcom/android/", "Landroidx/")
        }.onFailure { Log.w(TAG, "HiddenApiBypass tidak bisa dipasang", it) }
    }

    /** Bangun koneksi ke AppOpsService. Panggil dari thread IO. */
    fun connect(): Boolean {
        if (isReady) return true
        lastError = null
        return try {
            hiddenApiBypass()

            val raw: IBinder = SystemServiceHelper.getSystemService("appops")
            val wrapped = ShizukuBinderWrapper(raw)

            val stub = Class.forName("android.app.AppOpsManager\$IAppOpsService\$Stub")
            val asInterface = stub.getMethod("asInterface", IBinder::class.java)
            val svc = asInterface.invoke(null, wrapped) ?: error("asInterface() mengembalikan null")

            val iface = Class.forName("android.app.AppOpsManager\$IAppOpsService")
            checkOperationMethod = iface.getMethod(
                "checkOperation",
                Int::class.java,
                Int::class.java,
                String::class.java,
            )
            setModeMethod = iface.getMethod(
                "setMode",
                Int::class.java,
                Int::class.java,
                String::class.java,
                Int::class.java,
            )
            noteOperationMethod = runCatching {
                iface.getMethod(
                    "noteOperation",
                    Int::class.java,
                    Int::class.java,
                    String::class.java,
                )
            }.getOrNull()

            strOpToOpMethod = resolveStatic(AppOpsManager::class.java, "strOpToOp", String::class.java)
            opToNameMethod = resolveStatic(AppOpsManager::class.java, "opToName", Int::class.java)

            service = svc
            backend = Backend.BINDER
            true
        } catch (t: Throwable) {
            lastError = t.javaClass.simpleName + ": " + (t.message ?: "tanpa pesan")
            Log.w(TAG, "Gagal connect ke IAppOpsService", t)
            service = null
            backend = Backend.NONE
            false
        }
    }

    fun disconnect() {
        service = null
        backend = Backend.NONE
        opCodeCache.clear()
        nameByCode.clear()
    }

    private fun resolveStatic(owner: Class<*>, name: String, vararg params: Class<*>): Method? {
        runCatching { owner.getMethod(name, *params) }.getOrNull()?.let { return it }
        return runCatching {
            owner.getDeclaredMethod(name, *params).apply { isAccessible = true }
        }.getOrNull()
    }

    /** "android:system_alert_window" -> 24. Null kalau tidak bisa dipetakan. */
    fun opCode(op: String): Int? {
        opCodeCache[op]?.let { return it }

        strOpToOpMethod?.let { m ->
            runCatching { m.invoke(null, op) as Int }
                .getOrNull()
                ?.takeIf { it > -1 }
                ?.let { opCodeCache[op] = it; return it }
        }

        opToNameMethod?.let { m ->
            if (nameByCode.isEmpty()) {
                for (code in 0..120) {
                    runCatching { m.invoke(null, code) as? String }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() && it != "null" }
                        ?.let { nameByCode[code] = it }
                }
            }
            nameByCode.entries.firstOrNull { it.value == op }?.let {
                opCodeCache[op] = it.key
                return it.key
            }
        }

        OpCatalog.byName(op)?.fallbackCode?.let { opCodeCache[op] = it; return it }
        return null
    }

    /** Baca status sebuah op milik paket tertentu. Panggil dari thread IO. */
    fun getStatus(op: String, uid: Int, pkg: String): OpStatus {
        if (!isReady && !connect()) return OpStatus.UNKNOWN
        val code = opCode(op) ?: return OpStatus.UNKNOWN
        val method = checkOperationMethod ?: return OpStatus.UNKNOWN
        return try {
            val mode = method.invoke(service, code, uid, pkg) as Int
            OpStatus.fromMode(mode)
        } catch (t: Throwable) {
            Log.w(TAG, "checkOperation gagal untuk $pkg/$op", t)
            OpStatus.UNKNOWN
        }
    }

    /**
     * Ubah status op. `mode` diambil dari [OpStatus.mode].
     * Panggil dari thread IO. Mengembalikan null kalau sukses.
     */
    fun setStatus(op: String, uid: Int, pkg: String, mode: Int): String? {
        if (!isReady && !connect()) return lastError ?: "Belum tersambung ke Shizuku"
        val code = opCode(op) ?: return "Kode AppOp tidak dikenali: $op"
        val method = setModeMethod ?: return "setMode() tidak tersedia di Android ini"
        return try {
            method.invoke(service, code, uid, pkg, mode)
            null
        } catch (t: Throwable) {
            val cause = (t.cause ?: t)
            val msg = cause.javaClass.simpleName + ": " + (cause.message ?: "gagal mengubah mode")
            Log.w(TAG, "setMode gagal $pkg/$op", cause)
            msg
        }
    }

    /** Semua op di katalog untuk satu paket. Panggil dari thread IO. */
    fun readAll(uid: Int, pkg: String): List<Pair<OpDef, OpStatus>> =
        OpCatalog.ALL.map { def -> def to getStatus(def.op, uid, pkg) }

    /** Cek apakah paket ini masih punya overlay yang benar-benar aktif. */
    fun overlayIsAllowed(uid: Int, pkg: String): Boolean =
        getStatus(OpCatalog.OVERLAY.op, uid, pkg) == OpStatus.ALLOWED

    /** Info 1 baris untuk laporan perangkat. */
    fun describe(): String = buildString {
        append("backend=").append(backend.name)
        append(", opCode(overlay)=").append(opCode(OpCatalog.OVERLAY.op) ?: "?")
        lastError?.let { append(", error=").append(it) }
    }

    /** True kalau app ini memang meminta izin overlay normal (SYSTEM_ALERT_WINDOW di manifest). */
    fun declaresOverlayPermission(pm: PackageManager, pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.any { it == android.Manifest.permission.SYSTEM_ALERT_WINDOW } == true
    } catch (t: Throwable) {
        false
    }
}
