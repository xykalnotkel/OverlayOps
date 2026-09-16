package app.appsperms.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.CompoundButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import app.appsperms.BuildConfig
import app.appsperms.R
import app.appsperms.core.AppLanguage
import app.appsperms.core.Settings
import app.appsperms.core.SortMode
import app.appsperms.databinding.SheetSettingsBinding

/** Pengaturan ringan: bahasa, konfirmasi mode berisiko, peringatan UID, urutan daftar. */
object SettingsSheet {

    fun show(context: Context, onChanged: (String) -> Unit) {
        val dialog = BottomSheetDialog(context)
        val b = SheetSettingsBinding.inflate(LayoutInflater.from(context))

        // ---- nilai awal
        when (Settings.language(context)) {
            AppLanguage.SYSTEM -> b.langSystem.isChecked = true
            AppLanguage.INDONESIAN -> b.langId.isChecked = true
            AppLanguage.ENGLISH -> b.langEn.isChecked = true
        }
        b.switchRisk.isChecked = Settings.confirmRiskyModes(context)
        b.switchSharedUid.isChecked = Settings.warnSharedUid(context)
        when (Settings.defaultSort(context)) {
            SortMode.NAME -> b.sortName.isChecked = true
            SortMode.STATUS -> b.sortStatus.isChecked = true
        }
        b.settingsFooter.text = context.getString(
            R.string.settings_footer,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )

        // ---- interaksi
        b.langGroup.setOnCheckedChangeListener { _, checkedId ->
            val language = when (checkedId) {
                R.id.langId -> AppLanguage.INDONESIAN
                R.id.langEn -> AppLanguage.ENGLISH
                else -> AppLanguage.SYSTEM
            }
            Settings.setLanguage(context, language)
            // Ganti bahasa memicu recreate Activity sendiri oleh AppCompat,
            // jadi sheet-nya kita tutup supaya tidak menggantung.
            dialog.dismiss()
            onChanged(context.getString(R.string.snack_language_set, labelOf(context, language)))
        }

        b.switchRisk.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            Settings.setConfirmRiskyModes(context, checked)
            onChanged(context.getString(if (checked) R.string.snack_risk_on else R.string.snack_risk_off))
        }

        b.switchSharedUid.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            Settings.setWarnSharedUid(context, checked)
        }

        b.sortGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.sortStatus) SortMode.STATUS else SortMode.NAME
            Settings.setDefaultSort(context, mode)
        }

        dialog.setContentView(b.root)
        dialog.show()
    }

    private fun labelOf(context: Context, language: AppLanguage): String = when (language) {
        AppLanguage.SYSTEM -> context.getString(R.string.settings_lang_system)
        AppLanguage.INDONESIAN -> context.getString(R.string.settings_lang_id)
        AppLanguage.ENGLISH -> context.getString(R.string.settings_lang_en)
    }
}
