package app.appsperms.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import app.appsperms.R
import app.appsperms.core.OpCatalog
import app.appsperms.core.OpDef
import app.appsperms.core.OpStatus
import app.appsperms.core.Settings
import app.appsperms.databinding.ItemModeOptionBinding
import app.appsperms.databinding.SheetModeBinding
import app.appsperms.model.AppEntry

/**
 * Bottom sheet pemilih mode: tiap opsi diberi penjelasan singkat + konsekuensinya,
 * plus peringatan kalau memblokir overlay app yang memang meminta izin itu.
 */
object ModeSheet {

    fun show(
        context: Context,
        entry: AppEntry,
        def: OpDef,
        current: OpStatus,
        onPick: (OpStatus) -> Unit,
    ) {
        val dialog = BottomSheetDialog(context)
        val b = SheetModeBinding.inflate(LayoutInflater.from(context))

        b.sheetTitle.setText(def.titleRes)
        b.sheetSub.text = "${entry.label} · ${entry.packageName}"
        b.sheetCurrent.text = context.getString(
            R.string.sheet_current,
            context.getString(current.labelRes),
            OpStatus.shellName(current),
        )

        val peringatan = mutableListOf<String>()

        if (def.op == OpCatalog.OVERLAY.op && entry.declaresOverlay) {
            peringatan += context.getString(R.string.warn_overlay_break)
        }
        // AppOps disimpan per-UID: klon / profil kerja satu app akan ikut berubah.
        if (entry.sharedUidCount > 1 && Settings.warnSharedUid(context)) {
            peringatan += context.getString(
                R.string.warn_shared_uid,
                entry.sharedUidCount,
                entry.sharedUidCount - 1,
            )
        }
        if (peringatan.isNotEmpty()) {
            b.sheetWarning.isVisible = true
            b.sheetWarning.text = peringatan.joinToString("\n\n")
        }

        OpStatus.choices.forEach { status ->
            val row = ItemModeOptionBinding.inflate(LayoutInflater.from(context), b.modeContainer, false)
            row.optionTitle.text = context.getString(status.labelRes)
            row.optionDesc.text = context.getString(status.descRes)
            row.optionDot.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, status.colorRes))
            row.optionNow.isVisible = status == current
            row.optionRoot.setOnClickListener {
                dialog.dismiss()
                onPick(status)
            }
            b.modeContainer.addView(row.root)
        }

        dialog.setContentView(b.root)
        dialog.show()
    }
}
