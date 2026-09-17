package app.appsperms.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.appsperms.R
import app.appsperms.core.GhostGuard
import app.appsperms.core.Settings as AppPrefs
import app.appsperms.ui.MainActivity
import kotlin.math.abs

/**
 * Perisai anti ghost-touch.
 *
 * Cara kerja: ghost touch hampir selalu lahir di zona tetap — tepi bawah (navbar
 * hantu), tepi tempat jempol menggenggam, atau sudut yang sering tersenggol.
 * Service ini memasang jendela overlay transparan persis di zona itu untuk MENELAN
 * event sentuhan sebelum sempat masuk ke aplikasi di bawahnya. Di Android 12+ ada
 * bonus: sistem juga memblokir sentuhan ke area yang tertutup overlay yang bisa
 * disentuh — dua lapis.
 *
 * Butuh op `SYSTEM_ALERT_WINDOW` untuk diri sendiri — dan AppsPerms adalah manajer
 * op itu, jadi izinnya bisa diberikan dari app sendiri (dogfooding 😄).
 *
 * Jalur kabur (biar user tidak pernah terkunci):
 *  - tahan 1,5 dtk pada pita → membuka layar utama AppsPerms;
 *  - aksi notifikasi "Tahan 60 dtk" melepas pita sementara, lalu pasang sendiri;
 *  - aksi notifikasi "Matikan" → stop service + switch di sheet ikut mati;
 *  - addView gagal (overlay belum diizinkan) → service berhenti rapi, pesan
 *    disimpan di [lastError] supaya sheet bisa menampilkannya apa adanya.
 */
class GhostGuardService : Service() {

    companion object {
        private const val TAG = "GhostGuard"
        private const val CHANNEL_ID = "ghost_guard"
        private const val NOTIF_ID = 771

        const val ACTION_UPDATE = "app.appsperms.guard.UPDATE"
        const val ACTION_PAUSE = "app.appsperms.guard.PAUSE"
        const val ACTION_STOP = "app.appsperms.guard.STOP"

        @Volatile
        var isRunning: Boolean = false
            private set

        /** Error terakhir dari addView() — dibaca sheet untuk pesan yang jujur. */
        @Volatile
        var lastError: String? = null

        /**
         * Nyalakan/matikan perisai sesuai switch di Settings.
         * Semua perubahan konfigurasi juga lewat sini supaya satu pintu.
         */
        fun syncFromSettings(context: Context) {
            val intent = Intent(context, GhostGuardService::class.java).setAction(ACTION_UPDATE)
            if (AppPrefs.guardEnabled(context)) {
                lastError = null
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
    }

    private val windows = ArrayList<View>(4)
    private var manager: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pauseResumeTask: Runnable? = null
    private var pauseUntilElapsed = 0L
    private var started = false

    /** Reaksi cepat saat layar muter / nyala: susun ulang geometri pita. */
    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (AppPrefs.guardEnabled(this@GhostGuardService)) rebuildNow()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(systemReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(systemReceiver, filter)
            }
        }.onFailure { Log.w(TAG, "gagal register receiver sistem", it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pauseFor(60_000)

            ACTION_STOP -> {
                AppPrefs.setGuardEnabled(this, false)
                cancelPause()
                teardown()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                lastError = null
                ensureForeground()
                rebuildNow()
                if (lastError != null) {
                    Log.w(TAG, "gagal memasang pita: ${lastError}")
                    teardown()
                    stopForegroundCompat()
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(systemReceiver) }
        cancelPause()
        teardown()
        isRunning = false
        started = false
        super.onDestroy()
    }

    // ----------------------------------------------------------- pita overlay

    private fun rebuildNow() {
        teardown()
        if (!AppPrefs.guardEnabled(this)) {
            stopSelf()
            return
        }
        if (pauseResumeTask != null) {
            // Sedang ditahan: jangan cabut hitungannya, cukup sinkronkan notifikasi.
            updateNotification()
            return
        }
        val metrics = resources.displayMetrics
        val thicknessPx = (AppPrefs.guardThicknessDp(this) * metrics.density).toInt()
        val bands = GhostGuard.bands(
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            thickness = thicknessPx,
            enabled = AppPrefs.guardSides(this),
        )
        if (bands.isEmpty()) {
            lastError = getString(R.string.guard_no_band)
            return
        }
        val test = AppPrefs.guardTestMode(this)
        var failure: String? = null
        for (band in bands) {
            val view = BandView(this, test)
            try {
                requireNotNull(manager).addView(view, layoutParams(band))
                windows += view
            } catch (t: Throwable) {
                failure = t.javaClass.simpleName + ": " + (t.message ?: "?")
                break
            }
        }
        if (failure != null) {
            teardown()
            lastError = failure
            return
        }
        updateNotification()
    }

    private fun teardown() {
        windows.forEach { v -> runCatching { manager?.removeView(v) } }
        windows.clear()
    }

    private fun layoutParams(band: GhostGuard.Band): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            band.w,
            band.h,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // NOT_FOCUSABLE: jangan mencuri keyboard. NO_LIMITS + IN_SCREEN: boleh
            // nempel sampai tepi ekstrem (area status bar memang hak sistem).
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = band.x
            y = band.y
        }

    private fun pauseFor(millis: Long) {
        teardown()
        cancelPause()
        pauseUntilElapsed = SystemClock.elapsedRealtime() + millis
        val task = Runnable {
            pauseResumeTask = null
            pauseUntilElapsed = 0L
            if (AppPrefs.guardEnabled(this)) rebuildNow()
        }
        pauseResumeTask = task
        handler.postDelayed(task, millis)
        updateNotification()
    }

    private fun cancelPause() {
        pauseResumeTask?.let { handler.removeCallbacks(it) }
        pauseResumeTask = null
        pauseUntilElapsed = 0L
    }

    // ------------------------------------------------------------ notifikasi

    private fun ensureForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.guard_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        started = true
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) stopForeground(true)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val state = when {
            pauseResumeTask != null -> getString(R.string.guard_notif_paused, pauseSecondsLeft())
            else -> getString(
                R.string.guard_notif_active,
                windows.size,
                AppPrefs.guardThicknessDp(this),
                getString(if (AppPrefs.guardTestMode(this)) R.string.guard_test_on else R.string.guard_test_off),
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_guard)
            .setContentTitle(getString(R.string.guard_notif_title))
            .setContentText(state)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(R.string.guard_notif_pause), commandPendingIntent(ACTION_PAUSE, 1))
            .addAction(0, getString(R.string.guard_notif_stop), commandPendingIntent(ACTION_STOP, 2))
            .build()
    }

    private fun pauseSecondsLeft(): Int =
        ((pauseUntilElapsed - SystemClock.elapsedRealtime()) / 1000L)
            .coerceAtLeast(0L)
            .toInt()

    /**
     * Aksi notifikasi = broadcast EKSPLISIT ke receiver non-exported di manifest.
     * Receiver memanggil startService untuk service yang sudah foreground, jadi
     * aman dari batasan "background service start" Android 12+.
     */
    private fun commandPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(action).setComponent(
            ComponentName(packageName, GhostGuardReceiver::class.java.name),
        )
        return PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun updateNotification() {
        isRunning = true
        if (!started) return
        runCatching {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIF_ID, buildNotification())
        }
    }
}

/**
 * Satu pita: menelan SEMUA sentuhan di areanya; tahan 1,5 dtk membuka AppsPerms
 * supaya user tidak pernah benar-benar terkunci di balik perisai.
 */
private class BandView(
    context: Context,
    testMode: Boolean,
) : View(context) {

    private var downAt = 0L
    private var downX = 0f
    private var downY = 0f

    init {
        // Warna 1-alpha: view dengan alpha 0 penuh dilewati hit-test, jadi harus
        // "ada" sedikit. Mode uji membuat pita terlihat merah tembus pandang.
        background = GradientDrawable().apply {
            setColor(if (testMode) 0x66FF5C5C.toInt() else Color.argb(1, 255, 255, 255))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downAt = SystemClock.elapsedRealtime()
                downX = event.rawX
                downY = event.rawY
            }

            MotionEvent.ACTION_MOVE ->
                if (abs(event.rawX - downX) > 48f || abs(event.rawY - downY) > 48f) {
                    downAt = 0L // digeser -> bukan long press
                }

            MotionEvent.ACTION_UP ->
                if (downAt != 0L && SystemClock.elapsedRealtime() - downAt >= 1_500) {
                    openHostApp()
                }
        }
        return true // telan mentah-mentah — inilah intisari perisainya
    }

    private fun openHostApp() {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }
}
