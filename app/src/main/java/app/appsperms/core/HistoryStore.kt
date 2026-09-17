package app.appsperms.core

import android.content.Context
import java.io.File

/**
 * Riwayat perubahan AppOps yang dilakukan app ini — dasar fitur "Undo semua".
 *
 * Format satu baris (append-only, file `op_history.log` di filesDir):
 * ```
 * epochMillis|paket|android:op|DARI|KE
 * ```
 * Batas | dipilih karena nama paket/op Android tidak mungkin memuatnya.
 * Baris rusak dilewati (bukan crash) — file log adalah data pengguna,
 * bisa tersentuh half-write saat proses mati mendadak.
 *
 * Encode/decode ada di [HistoryCodec] yang MURNI dan diuji JVM; file IO di sini.
 */
data class HistoryLine(
    val timeMs: Long,
    val pkg: String,
    val op: String,
    val from: OpStatus,
    val to: OpStatus,
)

/** Logika murni serialisasi riwayat — lihat `HistoryCodecTest`. */
object HistoryCodec {

    /** Batas baris yang disimpan; lebih tua dipangkas dari depan. */
    const val MAX_LINES = 500

    fun encode(line: HistoryLine): String =
        "${line.timeMs}|${line.pkg}|${line.op}|${line.from.name}|${line.to.name}"

    /** null untuk baris rusak/rusak sebagian — sengaja tidak melempar. */
    fun decode(raw: String): HistoryLine? {
        val parts = raw.split('|')
        if (parts.size != 5) return null
        val time = parts[0].toLongOrNull() ?: return null
        val from = runCatching { OpStatus.valueOf(parts[3]) }.getOrNull() ?: return null
        val to = runCatching { OpStatus.valueOf(parts[4]) }.getOrNull() ?: return null
        if (parts[1].isBlank() || parts[2].isBlank()) return null
        return HistoryLine(time, parts[1], parts[2], from, to)
    }

    fun decodeAll(text: String): List<HistoryLine> =
        text.lineSequence().mapNotNull { if (it.isBlank()) null else decode(it) }.toList()

    /** Pangkas ke MAX_LINES terakhir. */
    fun trim(lines: List<HistoryLine>): List<HistoryLine> =
        if (lines.size <= MAX_LINES) lines else lines.takeLast(MAX_LINES)

    /**
     * Rencana undo: untuk tiap (paket, op) ambil status PALING AWAL yang tercatat.
     * Kenapa paling awal? Kalau user mengubah app yang sama 3x, "kembali ke semula"
     * yang intuitif adalah kondisi sebelum rangkaian itu — bukan langkah tengahnya.
     */
    fun undoPlan(lines: List<HistoryLine>): List<HistoryLine> =
        lines
            .groupBy { it.pkg to it.op }
            .values
            .map { group -> group.minByOrNull { it.timeMs }!! }
            .sortedBy { it.timeMs }

    /** Ringkasan teks untuk "Salin" (juga dipakai di clipboard). */
    fun renderText(lines: List<HistoryLine>, labelOf: (String) -> String): String =
        lines.joinToString("\n") {
            "${it.timeMs}\t${it.pkg}\t${labelOf(it.op)}\t${it.from.name} -> ${it.to.name}"
        }
}

/** Persistensi sederhana: baca/tulis file di filesDir, aman dipanggil dari thread IO. */
class HistoryStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "op_history.log")

    fun read(): List<HistoryLine> = runCatching {
        synchronized(this) {
            if (!file.exists()) emptyList() else HistoryCodec.decodeAll(file.readText())
        }
    }.getOrDefault(emptyList())

    /** @Synchronized: batch write & aksi tunggal bisa datang bersamaan dari thread IO. */
    @Synchronized
    fun append(line: HistoryLine) {
        runCatching {
            val all = HistoryCodec.decodeAll(if (file.exists()) file.readText() else "") + line
            file.writeText(HistoryCodec.trim(all).joinToString("\n", postfix = "\n") { HistoryCodec.encode(it) })
        }
    }

    @Synchronized
    fun clear() {
        runCatching { file.delete() }
    }
}
