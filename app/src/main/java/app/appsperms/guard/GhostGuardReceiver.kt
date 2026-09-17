package app.appsperms.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Penerima aksi notifikasi perisai (PAUSE / STOP).
 *
 * Sengaja receiver manifest yang non-exported + intent EKSPLISIT: satu-satunya
 * pengirim yang sah adalah PendingIntent milik app ini sendiri, jadi tidak ada
 * pihak luar yang bisa mematikan perisai diam-diam.
 */
class GhostGuardReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != GhostGuardService.ACTION_PAUSE && action != GhostGuardService.ACTION_STOP) return
        // Service sedang foreground (aksi ini hanya datang dari notifikasinya),
        // jadi startService biasa aman dari batasan Oreo.
        runCatching {
            context.startService(
                Intent(context, GhostGuardService::class.java).setAction(action),
            )
        }
    }
}
