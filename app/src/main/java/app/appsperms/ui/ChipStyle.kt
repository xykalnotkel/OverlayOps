package app.appsperms.ui

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import app.appsperms.core.OpStatus

/** Warnai chip status sesuai statusnya (teks penuh + background transparan). */
fun TextView.bindStatusChip(status: OpStatus) {
    val color = ContextCompat.getColor(context, status.colorRes)
    text = context.getString(status.labelRes).uppercase()
    setTextColor(color)
    backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x2E))
}
