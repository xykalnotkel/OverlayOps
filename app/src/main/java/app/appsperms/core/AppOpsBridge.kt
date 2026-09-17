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

            // v1.4.1 fix: kelas AIDL yang benar ada di com.android.internal.app —
            // nama "android.app.AppOpsManager$IAppOpsService" tidak pernah ada di
            // AOSP, jadi binder selalu ClassNotFoundException dan app jatuh ke
            // "Mode shell". Sekarang coba kandidat + toleran arg ekstra versi ROM.
            var svc: Any? = null
            var iface: Class<*>? = null
            for (stubName in STUB_CANDIDATES) {
                val cand = runCatching {
                    val stub = Class.forName(stubName)
                    stub.getMethod("asInterface", IBinder::class.java).invoke(null, wrapped)
                }.getOrNull()
                if (cand != null) {
                    svc = cand
                    iface = runCatching { Class.forName(stubName.removeSuffix("\$Stub")) }
                        .getOrNull()
                        ?: cand.javaClass.interfaces.firstOrNull()
                    break
                }
            }
            val ifc = iface ?: error("IAppOpsService tidak ditemukan di ROM ini")

            checkOperationMethod = findMethod(ifc, "checkOperation", Int::class.java, Int::class.java, String::class.java)
                ?: findMethod(ifc, "checkOperationRaw", Int::class.java, Int::class.java, String::class.java)
                ?: error("checkOperation() tidak tersedia di Android ini")
            setModeMethod = findMethod(ifc, "setMode", Int::class.java, Int::class.java, String::class.java, Int::class.java)
                ?: error("setMode() tidak tersedia di Android ini")
            noteOperationMethod = findMethod(ifc, "noteOperation", Int::class.java, Int::class.java, String::class.java)

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

    private val STUB_CANDIDATES = listOf(
        "com.android.internal.app.IAppOpsService\$Stub",
        "android.app.IAppOpsService\$Stub",
        "android.app.AppOpsManager\$IAppOpsService\$Stub",
    )

    /** Method AIDL dengan argumen awal pas, toleran terhadap param ekstra (variasi ROM). */
    private fun findMethod(owner: Class<*>, name: String, vararg leading: Class<*>): Method? {
        val declared = runCatching { owner.declaredMethods.toList() }.getOrDefault(emptyList())
        return (owner.methods.toList() + declared)
            .filter { m ->
                m.name == name && m.parameterTypes.size >= leading.size &&
                    (0 until leading.size).all { m.parameterTypes[it] == leading[it] }
            }
            .minByOrNull { it.parameterTypes.size }
    }

    /** Invoke sambil mengisi param ekstra (kalau ROM menambah arg) dengan default aman. */
    private fun invokeAdapted(m: Method, target: Any, vararg base: Any?): Any? {
        val need = m.parameterTypes.size
        if (base.size == need) return m.invoke(target, *base)
        val args = arrayOfNulls<Any>(need)
        base.forEachIndexed { i, v -> if (i < need) args[i] = v }
        for (i in base.size until need) {
            args[i] = when (m.parameterTypes[i]) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                else -> null
            }
        }
        return m.invoke(target, *args)
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
            val mode = invokeAdapted(method, service!!, code, uid, pkg) as Int
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
            invokeAdapted(method, service!!, code, uid, pkg, mode)
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
