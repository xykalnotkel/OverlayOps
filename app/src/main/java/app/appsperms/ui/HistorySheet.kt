package app.appsperms.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import app.appsperms.R
import app.appsperms.core.HistoryCodec
import app.appsperms.core.HistoryLine
import app.appsperms.core.OpCatalog
import app.appsperms.core.OpStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bottom sheet riwayat perubahan (v1.4): daftar edit AppOps yang dilakukan app ini,
 * plus **Undo massal** — mengembalikan tiap (app, op) ke status paling awal yang
 * tercatat, **Bersihkan riwayat**, dan **Salin** (buat laporan ke teman/issue).
 */
object HistorySheet {

    fun show(
        context: Context,
        lines: List<HistoryLine>,
        canOperate: Boolean,
        onUndo: (finish: (applied: Int, skipped: Int, error: String?) -> Unit) -> Unit,
        onClear: () -> Unit,
    ) {
        val dialog = BottomSheetDialog(context)
        val b = app.appsperms.databinding.SheetHistoryBinding.inflate(LayoutInflater.from(context))

        b.historySubtitle.text = context.getString(R.string.history_subtitle, lines.size)
        b.historyEmpty.isVisible = lines.isEmpty()
        b.historyScroll.isVisible = lines.isNotEmpty()

        val timeFmt = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault())
        lines.take(50).forEach { line ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(4, dp(context, 7), 4, dp(context, 7))
            }
            val def = OpCatalog.byName(line.op)
            val opLabel = def?.let { context.getString(it.titleRes) }
                ?: line.op.substringAfter(':').lowercase(Locale.US).replace('_', ' ')
            val pkgLabel = line.pkg

            val head = TextView(context).apply {
                textSize = 13f
                setTextColor(context.getColor(R.color.on_surface))
                text = "$opLabel · ${statusArrow(context, line.from)} → ${statusArrow(context, line.to)}"
            }
            val sub = TextView(context).apply {
                textSize = 10.5f
                setTextColor(context.getColor(R.color.on_surface_dim))
                text = "$pkgLabel · ${timeFmt.format(Date(line.timeMs))}"
            }
            row.addView(head)
            row.addView(sub)
            row.setOnClickListener {
                dialog.dismiss()
                // Shortcut: buka detail paket lewat pencarian (pakai nama paket sebagai query).
                (context as? MainActivity)?.searchFor(line.pkg)
            }
            b.historyContainer.addView(row)
        }

        val undoCount = HistoryCodec.undoPlan(lines).size
        b.historyUndoBtn.isEnabled = canOperate && lines.isNotEmpty()
        b.historyUndoBtn.text = context.getString(R.string.history_undo_all, undoCount)
        b.historyUndoBtn.setOnClickListener {
            b.historyUndoBtn.isEnabled = false
            b.historyUndoBtn.setText(R.string.history_undoing)
            onUndo { applied, skipped, error ->
                dialog.dismiss()
                val msg = context.getString(R.string.history_undo_done, applied, skipped)
                toast(context, if (error == null) msg else "$msg · $error")
            }
        }

        b.historyCopyBtn.setOnClickListener {
            val text = HistoryCodec.renderText(lines) { op ->
                OpCatalog.byName(op)?.let { context.getString(it.titleRes) } ?: op
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("AppsPerms history", text))
            toast(context, context.getString(R.string.copied, "riwayat"))
        }

        b.historyClearBtn.setOnClickListener {
            onClear()
            dialog.dismiss()
            toast(context, context.getString(R.string.history_cleared))
        }

        dialog.setContentView(b.root)
        dialog.show()
    }

    private fun statusArrow(context: Context, status: OpStatus): String =
        context.getString(status.labelRes)

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun toast(context: Context, message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    }
}
