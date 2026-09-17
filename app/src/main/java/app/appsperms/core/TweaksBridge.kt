package app.appsperms.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Eksekutor perintah tweak (`wm`, `settings`, `am`, `pm`) lewat shell Shizuku.
 *
 * Pola: semua panggilan shell jalan DI SERIAL executor (satu-satu, supaya perintah
 * `wm` yang bergantian tidak adu cepat dengan reset otomatis), hasilnya dikirim balik
 * ke main thread. `onDone(null)` = sukses; pesan error = teks mentah dari ROM,
 * sengaja tidak diterjemahkan supaya user bisa menyalinnya saat cari solusi.
 */
object TweaksBridge {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "appsperms-tweaks").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Hasil netral dari satu perintah shell. */
    private fun run(command: String): String? {
        val result = ShizukuBridge.shell(command)
            ?: return "Jalur shell Shizuku tidak tersedia (coba laporan perangkat)."
        return if (result.ok) null else result.message
    }

    fun io(block: () -> Unit) = executor.execute(block)

    fun postMain(block: () -> Unit) = mainHandler.post(block)

    // ------------------------------------------------------------- tampilan

    /** Baca `wm size` + `wm density` sekaligus. Error non-fatal: info bisa sebagian null. */
    fun readDisplay(onDone: (info: WmParser.DisplayInfo, error: String?) -> Unit) {
        io {
            val sizeOut = ShizukuBridge.shell("wm size")
            val densOut = ShizukuBridge.shell("wm density")
            val info = WmParser.parseDisplay(
                sizeOut?.stdout.orEmpty(),
                densOut?.stdout.orEmpty(),
            )
            val error = when {
                sizeOut == null -> "shell tidak tersedia"
                sizeOut.stdout.isBlank() && densOut?.stdout.isNullOrBlank() -> sizeOut.message
                else -> null
            }
            postMain { onDone(info, error) }
        }
    }

    fun applySize(width: Int, height: Int, onDone: (String?) -> Unit) =
        applyWithCallback("wm size ${width}x$height", onDone)

    fun applyDensity(density: Int, onDone: (String?) -> Unit) =
        applyWithCallback("wm density $density", onDone)

    fun resetSize(onDone: (String?) -> Unit) = applyWithCallback("wm size reset", onDone)
    fun resetDensity(onDone: (String?) -> Unit) = applyWithCallback("wm density reset", onDone)

    /** Reset KEDUANYA dalam satu perintah agar tidak separuh jalan saat error. */
    fun resetDisplay(onDone: (String?) -> Unit) =
        applyWithCallback("wm size reset && wm density reset", onDone)

    private fun applyWithCallback(command: String, onDone: (String?) -> Unit) {
        io {
            val error = run(command)
            postMain { onDone(error) }
        }
    }

    // -------------------------------------------------------------- animasi

    private val animKeys = listOf(
        "window_animation_scale",
        "transition_animation_scale",
        "animator_duration_scale",
    )

    /** Baca skala animasi global; null = tidak terbaca, nilai = rata-rata (biasanya sama ketiganya). */
    fun readAnimScales(onDone: (Float?) -> Unit) {
        io {
            val out = ShizukuBridge.shell(
                animKeys.joinToString(" ; ") { "settings get global $it" },
            )
            val first = out?.stdout?.lineSequence()?.firstOrNull()?.trim()
            val value = first?.takeIf { it.isNotBlank() && it != "null" }?.toFloatOrNull()
            postMain { onDone(value) }
        }
    }

    fun setAnimScales(scale: Float, onDone: (String?) -> Unit) {
        io {
            // Satu perintah gabungan supaya ketiganya atomik-sekali-pakai.
            val cmd = animKeys.joinToString(" && ") { "settings put global $it $scale" }
            val error = run(cmd)
            postMain { onDone(error) }
        }
    }

    // ------------------------------------------------------------- "booster"

    /** `am kill-all` — sistem hanya menghapus proses CACHED, app foreground aman. */
    fun killBackground(onDone: (String?) -> Unit) {
        io {
            val error = run("am kill-all")
            postMain { onDone(error) }
        }
    }

    /** Trim cache seluruh app sampai device punya ruang longgar sebesar `targetBytes`. */
    fun trimCaches(targetBytes: Long, onDone: (String?) -> Unit) {
        io {
            val error = run("pm trim-caches $targetBytes")
            postMain { onDone(error) }
        }
    }

    // ------------------------------------------------- izin overlay utk diri

    /**
     * Perisai ghost-touch butuh jendela overlay; AppsPerms mengelola op itu sendiri.
     * Jadi kalau op kita belum allow, kita minta lewat Shizuku — fitur makan sendiri 😄.
     */
    fun allowOwnOverlay(packageName: String, onDone: (String?) -> Unit) {
        io {
            val error = ShizukuBridge.shellSetOp(packageName, OpCatalog.OVERLAY.shell, OpStatus.ALLOWED)
            postMain { onDone(error) }
        }
    }
}
