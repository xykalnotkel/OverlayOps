package app.appsperms.model

import android.graphics.drawable.Drawable
import app.appsperms.core.OpStatus

data class AppEntry(
    val packageName: String,
    val label: String,
    val uid: Int,
    val isSystem: Boolean,
    val targetSdk: Int,
    val enabled: Boolean,
    val declaresOverlay: Boolean,
    val icon: Drawable?,
    val overlayStatus: OpStatus = OpStatus.UNKNOWN,
    /** Jumlah paket (termasuk app ini) yang berbagi UID sama. Di atas 1 = klon/profil kerja. */
    val sharedUidCount: Int = 1,
) {
    val labelLower: String = label.lowercase()
    val packageLower: String = packageName.lowercase()

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return labelLower.contains(q) || packageLower.contains(q)
    }
}
