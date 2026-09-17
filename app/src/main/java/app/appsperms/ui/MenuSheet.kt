package app.appsperms.ui

import android.content.Context
import android.view.LayoutInflater
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import app.appsperms.R
import app.appsperms.databinding.ItemMenuBinding
import app.appsperms.databinding.SheetMenuBinding

/**
 * Daftar aksi menu. Memakai enum (bukan nomor id seperti menu lama) supaya salah
 * pasang aksi tidak mungkin terjadi.
 */
enum class MenuAction {
    REFRESH,
    SORT,
    BATCH,
    TWEAKS,
    BACKUP,
    RESTORE,
    HISTORY,
    REPORT,
    COPY_REPORT,
    SHIZUKU,
    OVERLAY_SETTINGS,
    SETTINGS,
    ABOUT,
}

/**
 * Menu utama dalam bentuk bottom sheet, dikelompokkan jadi tiga bagian:
 * Kelola (aksi sehari-hari) → Diagnostik → Bantuan & info.
 *
 * Alasannya: app ini dipakai satu tangan di HP, dan PopupMenu bawaan menaruh
 * 10 item beruntun tanpa pengelompokan — susah dipindai mata.
 */
object MenuSheet {

    fun show(context: Context, sortLabel: String, onPick: (MenuAction) -> Unit) {
        val dialog = BottomSheetDialog(context)
        val binding = SheetMenuBinding.inflate(LayoutInflater.from(context))
        val inflater = LayoutInflater.from(context)

        fun row(group: String?, title: String, subtitle: String, action: MenuAction) {
            val item = ItemMenuBinding.inflate(inflater, binding.menuContainer, false)
            if (group != null) {
                item.itemGroup.isVisible = true
                item.itemGroup.text = group
            }
            item.itemTitle.text = title
            item.itemSub.text = subtitle
            item.itemChevron.contentDescription = context.getString(R.string.cd_menu_chevron)
            item.itemRow.setOnClickListener {
                dialog.dismiss()
                onPick(action)
            }
            binding.menuContainer.addView(item.root)
        }

        val kelola = context.getString(R.string.menu_group_manage)
        row(kelola, context.getString(R.string.menu_refresh), context.getString(R.string.menu_refresh_sub), MenuAction.REFRESH)
        row(null, context.getString(R.string.menu_sort), context.getString(R.string.menu_sort_sub, sortLabel), MenuAction.SORT)
        row(null, context.getString(R.string.menu_batch), context.getString(R.string.menu_batch_sub), MenuAction.BATCH)
        row(null, context.getString(R.string.menu_tweaks), context.getString(R.string.menu_tweaks_sub), MenuAction.TWEAKS)
        row(null, context.getString(R.string.menu_backup), context.getString(R.string.menu_backup_sub), MenuAction.BACKUP)
        row(null, context.getString(R.string.menu_restore), context.getString(R.string.menu_restore_sub), MenuAction.RESTORE)

        val diagnostik = context.getString(R.string.menu_group_diag)
        row(diagnostik, context.getString(R.string.menu_history), context.getString(R.string.menu_history_sub), MenuAction.HISTORY)
        row(null, context.getString(R.string.menu_report), context.getString(R.string.menu_report_sub), MenuAction.REPORT)
        row(null, context.getString(R.string.menu_copy_report), context.getString(R.string.menu_copy_report_sub), MenuAction.COPY_REPORT)

        val bantuan = context.getString(R.string.menu_group_help)
        row(bantuan, context.getString(R.string.menu_settings), context.getString(R.string.menu_settings_sub), MenuAction.SETTINGS)
        row(null, context.getString(R.string.menu_shizuku), context.getString(R.string.menu_shizuku_sub), MenuAction.SHIZUKU)
        row(null, context.getString(R.string.menu_overlay_settings), context.getString(R.string.menu_overlay_settings_sub), MenuAction.OVERLAY_SETTINGS)
        row(null, context.getString(R.string.menu_about), context.getString(R.string.menu_about_sub), MenuAction.ABOUT)

        dialog.setContentView(binding.root)
        dialog.show()
    }
}
