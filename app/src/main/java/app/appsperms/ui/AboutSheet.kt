package app.appsperms.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import app.appsperms.BuildConfig
import app.appsperms.R
import app.appsperms.databinding.SheetAboutBinding

/** Halaman Tentang yang ringkas, informatif, dan tidak lagi berupa dialog teks panjang. */
object AboutSheet {
    fun show(context: Context) {
        val dialog = BottomSheetDialog(context)
        val b = SheetAboutBinding.inflate(LayoutInflater.from(context))
        b.aboutVersion.text = context.getString(
            R.string.about_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )
        b.aboutWebsite.setOnClickListener { open(context, "https://appsperms.haekal.web.id/") }
        b.aboutSource.setOnClickListener { open(context, "https://github.com/xykalnotkel/OverlayOps") }
        dialog.setContentView(b.root)
        dialog.show()
    }

    private fun open(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}
