package app.appsperms.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import app.appsperms.R
import app.appsperms.core.AccessSnapshot
import app.appsperms.core.AccessState
import app.appsperms.core.AppTypeFilter
import app.appsperms.core.OpCatalog
import app.appsperms.core.OpDef
import app.appsperms.core.OpStatus
import app.appsperms.core.Settings as AppPrefs
import app.appsperms.core.ShizukuBridge
import app.appsperms.core.SortMode
import app.appsperms.core.StatusFilter
import app.appsperms.databinding.ActivityMainBinding
import app.appsperms.databinding.DialogAppDetailBinding
import app.appsperms.databinding.ItemOpBinding
import app.appsperms.model.AppEntry
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: AppListAdapter

    private var detailDialog: AlertDialog? = null
    private var lastNotice: String? = null

    /** v1.4: notifikasi foreground service perisai butuh izin runtime di Android 13+. */
    private val notifPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { probe("binder masuk") }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { probe("binder mati") }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, granted ->
        probe(if (granted == 0) "izin diberikan" else null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AppListAdapter(
            onOpen = { entry -> showAppDetail(entry) },
            onChangeOverlay = { entry -> pickOverlayStatus(entry) },
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.list.setHasFixedSize(false)
        binding.list.itemAnimator = null

        buildTabs()
        buildTypeFilter()
        buildStatusFilter()

        binding.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) =
                viewModel.setQuery(s?.toString().orEmpty())

            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.swipe.setOnRefreshListener { viewModel.refresh(showSpinner = false) }
        binding.swipe.setColorSchemeColors(getColor(R.color.brand))
        binding.swipe.setProgressBackgroundColorSchemeColor(getColor(R.color.surface))

        binding.modeChip.setOnClickListener { showConnectionDialog() }
        binding.menuButton.setOnClickListener { showMenuSheet() }
        // label untuk TalkBack — tombol ikon tanpa teks tidak terbaca tanpanya
        binding.menuButton.contentDescription = getString(R.string.cd_menu)
        binding.modeChip.contentDescription = getString(R.string.cd_status_chip)
        binding.search.contentDescription = getString(R.string.cd_search)
        // Urutan daftar saat app dibuka mengikuti Pengaturan.
        viewModel.setSort(AppPrefs.defaultSort(this))
        binding.btnGrant.setOnClickListener {
            when {
                !ShizukuBridge.isBinderAlive() ->
                    snack(getString(R.string.snack_shizuku_off_long))

                ShizukuBridge.hasPermission() -> {
                    snack(getString(R.string.snack_rechecking))
                    probe(null)
                }

                else -> ShizukuBridge.requestPermission()
            }
        }
        binding.btnOpenShizuku.setOnClickListener { openShizukuApp() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ShizukuBridge.addBinderReceivedListener(binderReceivedListener)
        ShizukuBridge.addBinderDeadListener(binderDeadListener)
        ShizukuBridge.addPermissionResultListener(permissionListener)
        probe(null)
    }

    override fun onResume() {
        super.onResume()
        // Izin bisa diberikan lewat app Shizuku saat kita di background -> cek ulang.
        probe(null)
    }

    override fun onStop() {
        ShizukuBridge.removeBinderReceivedListener(binderReceivedListener)
        ShizukuBridge.removeBinderDeadListener(binderDeadListener)
        ShizukuBridge.removePermissionResultListener(permissionListener)
        super.onStop()
    }

    override fun onDestroy() {
        detailDialog?.dismiss()
        super.onDestroy()
    }

    // ------------------------------------------------------------------- setup

    private fun probe(notice: String?) {
        lastNotice = notice
        viewModel.refreshAccess { snapshot -> notice?.let { announce(snapshot) } }
    }

    private fun announce(snapshot: AccessSnapshot) {
        when (snapshot.state) {
            AccessState.SHIZUKU_OFF -> snack(getString(R.string.snack_shizuku_off))
            AccessState.PERMISSION_DENIED -> snack(getString(R.string.snack_permission_missing))
            AccessState.BRIDGE_FAILED ->
                snack(getString(R.string.snack_bridge_failed, snapshot.bridgeError ?: "?"))

            AccessState.SHELL_FALLBACK ->
                snack(getString(R.string.snack_shell_active, snapshot.bridgeError ?: "?"))

            AccessState.READY_SHELL -> snack(getString(R.string.snack_connected_shell))
            AccessState.READY_ROOT -> snack(getString(R.string.snack_connected_root))
        }
    }

    private fun buildTabs() = with(binding.tabs) {
        removeAllTabs()
        addTab(newTab().setText(getString(R.string.tab_overlay)), 0, true)
        addTab(newTab().setText(getString(R.string.tab_apps)), 1, false)
        addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = viewModel.setTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun buildTypeFilter() {
        binding.typeFilterGroup.removeAllViews()
        AppTypeFilter.entries.forEach { f ->
            binding.typeFilterGroup.addView(filterChip(getString(f.labelRes), f == AppTypeFilter.ALL) {
                viewModel.setTypeFilter(f)
            })
        }
    }

    private fun buildStatusFilter() {
        binding.statusFilterGroup.removeAllViews()
        StatusFilter.entries.forEach { f ->
            binding.statusFilterGroup.addView(filterChip(getString(f.labelRes), f == StatusFilter.ALL) {
                viewModel.setStatusFilter(f)
            })
        }
    }

    private fun filterChip(text: String, checked: Boolean, onClick: () -> Unit) =
        Chip(this).apply {
            this.text = text
            isCheckable = true
            isChecked = checked
            setOnClickListener { onClick() }
        }

    // --------------------------------------------------------------- rendering

    private fun render(s: UiState) {
        val state = s.snapshot.state
        val color = ContextCompat.getColor(this, state.colorRes)

        binding.modeChip.text = getString(state.chipRes)
        binding.modeChip.setTextColor(color)
        binding.modeChip.backgroundTintList =
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x2E))

        val needsSetup = state == AccessState.SHIZUKU_OFF ||
            state == AccessState.PERMISSION_DENIED ||
            state == AccessState.BRIDGE_FAILED
        binding.setupCard.isVisible = needsSetup
        if (needsSetup) {
            binding.setupDesc.text = when (state) {
                AccessState.SHIZUKU_OFF -> getString(R.string.setup_off)
                AccessState.PERMISSION_DENIED -> getString(R.string.setup_permission)
                else -> getString(R.string.setup_bridge_failed, s.snapshot.bridgeError ?: "?")
            }
        }
        binding.btnGrant.isEnabled = s.snapshot.binderAlive
        binding.btnGrant.text = getString(
            if (s.snapshot.binderAlive && s.snapshot.permission) R.string.recheck_permission
            else R.string.request_permission
        )

        binding.swipe.isRefreshing = false
        binding.progress.isVisible = s.loading && s.apps.isEmpty()

        binding.tabs.getTabAt(0)?.text = getString(R.string.tab_overlay_count, s.overlayCount)
        binding.tabs.getTabAt(1)?.text = getString(R.string.tab_apps_count, s.apps.size)

        adapter.submitList(s.items)

        val empty = s.items.isEmpty() && !s.loading
        binding.emptyView.isVisible = empty
        if (empty) {
            when {
                !s.snapshot.canOperate -> {
                    binding.emptyTitle.setText(R.string.empty_no_access)
                    binding.emptyDesc.text = getString(R.string.empty_no_access_desc)
                }

                s.apps.isEmpty() -> {
                    binding.emptyTitle.setText(R.string.empty_no_data)
                    binding.emptyDesc.text = getString(R.string.empty_no_data_desc)
                }

                s.tab == 0 && s.query.isBlank() -> {
                    binding.emptyTitle.setText(R.string.empty_no_explicit)
                    binding.emptyDesc.text = getString(R.string.empty_no_explicit_desc)
                }

                else -> {
                    binding.emptyTitle.setText(R.string.empty_no_result)
                    binding.emptyDesc.text = getString(R.string.empty_no_result_desc)
                }
            }
        }

        binding.hintBar.text = when {
            s.busy != null -> s.busy
            s.query.isNotBlank() || s.typeFilter != AppTypeFilter.ALL || s.statusFilter != StatusFilter.ALL ->
                getString(R.string.hint_filtered, s.apps.size, s.overlayCount, s.allowedCount, s.blockedCount)

            else -> getString(R.string.hint_default, s.apps.size, s.overlayCount, s.allowedCount, s.blockedCount)
        }
    }

    // ------------------------------------------------------------------ aksi

    private fun pickOverlayStatus(entry: AppEntry) {
        if (!viewModel.state.value.snapshot.canOperate) {
            snack(getString(R.string.snack_not_connected))
            return
        }
        ModeSheet.show(this, entry, OpCatalog.OVERLAY, entry.overlayStatus) { status ->
            confirmThenApply(entry, OpCatalog.OVERLAY, status, entry.overlayStatus)
        }
    }

    /**
     * Mode Diblokir/Diabaikan bisa merusak app yang memang bergantung pada op ini.
     * Kalau pengguna tidak mematikannya di Pengaturan, tanya sekali dulu — karena
     * efeknya tidak kelihatan langsung di app yang diubah.
     */
    private fun confirmThenApply(entry: AppEntry, def: OpDef, status: OpStatus, previous: OpStatus) {
        val perluKonfirmasi = def.op == OpCatalog.OVERLAY.op &&
            entry.declaresOverlay &&
            (status == OpStatus.ERRORED || status == OpStatus.IGNORED) &&
            AppPrefs.confirmRiskyModes(this)

        if (!perluKonfirmasi) {
            applyAndOfferUndo(entry, def, status, previous)
            return
        }

        val catatan = getString(
            if (status == OpStatus.ERRORED) R.string.risk_note_denied else R.string.risk_note_ignored
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.risk_title)
            .setMessage(
                getString(R.string.risk_body, getString(status.labelRes), entry.label, catatan)
            )
            .setPositiveButton(R.string.risk_apply) { _, _ ->
                applyAndOfferUndo(entry, def, status, previous)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun applyAndOfferUndo(
        entry: AppEntry,
        def: OpDef,
        status: OpStatus,
        previous: OpStatus,
        onUndoCallback: (() -> Unit)? = null,
    ) {
        // `previous` adalah status op yang sebenarnya sebelum ditulis — diteruskan
        // supaya riwayat (v1.4) mencatat dari/ke yang akurat, bukan asumsi overlay.
        viewModel.applyStatus(entry, def, status, fromStatus = previous) { error, _ ->
            if (error == null) {
                snackWithUndo(
                    getString(R.string.applied, entry.label, getString(status.labelRes)),
                ) {
                    viewModel.applyStatus(entry, def, previous) { _, _ ->
                        snack(getString(R.string.snack_reverted))
                        onUndoCallback?.invoke()
                    }
                }
            } else {
                showError(error)
                onUndoCallback?.invoke()
            }
        }
    }

    // ------------------------------------------------------------------ menu

    private fun showMenuSheet() {
        val sortLabel = getString(viewModel.state.value.sort.labelRes)
        MenuSheet.show(this, sortLabel) { action -> handleMenu(action) }
    }

    /** Pengaturan ringan: bahasa, konfirmasi mode berisiko, peringatan UID, urutan daftar. */
    private fun showSettings() {
        SettingsSheet.show(this) { message ->
            snack(message)
            // Urutan default bisa berubah -> langsung diterapkan supaya konsisten.
            viewModel.setSort(AppPrefs.defaultSort(this))
        }
    }

    /**
     * v1.4 — Tuning: resolusi & DPI, animasi, booster ringan, perisai ghost-touch.
     * Notifikasi foreground service perisai butuh izin runtime di Android 13+,
     * jadi kita minta sekaligus di awal (kalau ditolak, service tetap jalan —
     * hanya notifikasinya yang tidak tampil).
     */
    private fun showTweaks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        TweaksSheet.show(this, viewModel.state.value.snapshot.canOperate) { message ->
            snack(message)
        }
    }

    /**
     * v1.4 — Riwayat & undo massal. Baris riwayat bisa diketuk untuk mencari
     * paketnya di daftar (jalur cepat buat koreksi satu app).
     */
    private fun showHistorySheet() {
        viewModel.historyLines { lines ->
            HistorySheet.show(
                this,
                lines,
                viewModel.state.value.snapshot.canOperate,
                onUndo = { finish ->
                    viewModel.undoHistory { applied, skipped, error ->
                        finish(applied, skipped, error)
                        viewModel.refresh(showSpinner = false)
                    }
                },
                onClear = { viewModel.clearHistory() },
            )
        }
    }

    /** Isi kolom pencarian dari tempat lain (mis. sheet riwayat). */
    fun searchFor(query: String) {
        binding.search.setText(query)
        binding.list.scrollToPosition(0)
    }

    private fun handleMenu(action: MenuAction) {
        when (action) {
            MenuAction.REFRESH -> viewModel.refresh()

            MenuAction.SORT -> {
                val next = if (viewModel.state.value.sort == SortMode.NAME) SortMode.STATUS else SortMode.NAME
                viewModel.setSort(next)
                snack(getString(R.string.snack_sort_now, getString(next.labelRes)))
            }

            MenuAction.BATCH -> showBatchDialog()

            MenuAction.TWEAKS -> showTweaks()

            MenuAction.BACKUP -> copyToClipboard(
                viewModel.exportBackup(),
                getString(R.string.clip_label_backup),
            )

            MenuAction.RESTORE -> showRestoreDialog()

            MenuAction.HISTORY -> showHistorySheet()

            MenuAction.REPORT -> MaterialAlertDialogBuilder(this)
                .setTitle(R.string.device_report)
                .setMessage(viewModel.deviceReport())
                .setPositiveButton(R.string.dialog_close, null)
                .show()

            MenuAction.COPY_REPORT -> copyToClipboard(
                viewModel.deviceReport(),
                getString(R.string.clip_label_report),
            )

            MenuAction.SHIZUKU -> openShizukuApp()

            MenuAction.OVERLAY_SETTINGS ->
                startActivitySafely(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))

            MenuAction.SETTINGS -> showSettings()

            MenuAction.ABOUT -> showAbout()
        }
    }

    private fun showBatchDialog() {
        val apps = viewModel.appsNow()
        if (apps.isEmpty()) {
            snack(getString(R.string.snack_no_apps))
            return
        }
        val userApps = apps.filter { !it.isSystem && (it.declaresOverlay || it.overlayStatus.isExplicit) }
        val declared = apps.filter { it.declaresOverlay }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.batch_title)
            .setItems(
                arrayOf(
                    getString(R.string.batch_item_block, userApps.size),
                    getString(R.string.batch_item_allow, declared.size),
                    getString(R.string.batch_item_reset, apps.count { it.overlayStatus.isExplicit }),
                ),
            ) { _, which ->
                when (which) {
                    0 -> confirmBatch(userApps, OpStatus.ERRORED, getString(R.string.batch_block_title))
                    1 -> confirmBatch(declared, OpStatus.ALLOWED, getString(R.string.batch_allow_title))
                    else -> confirmBatch(
                        apps.filter { it.overlayStatus.isExplicit },
                        OpStatus.DEFAULT,
                        getString(R.string.batch_reset_title),
                    )
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun confirmBatch(entries: List<AppEntry>, status: OpStatus, title: String) {
        if (entries.isEmpty()) {
            snack(getString(R.string.snack_no_match))
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(
                // Kalau ada target yang berbagi UID, peringatkan sekali di sini juga.
                if (AppPrefs.warnSharedUid(this) && entries.any { it.sharedUidCount > 1 }) {
                    getString(
                        R.string.batch_confirm_shared,
                        entries.size,
                        getString(status.labelRes),
                    )
                } else {
                    getString(R.string.batch_confirm, entries.size, getString(status.labelRes))
                },
            )
            .setPositiveButton(R.string.dialog_run) { _, _ ->
                viewModel.batchApplyOverlay(entries, status) { applied, error, previous ->
                    val message = if (error == null) {
                        getString(R.string.batch_done, applied)
                    } else {
                        getString(R.string.batch_done_error, applied, error)
                    }
                    snackWithUndo(message) {
                        viewModel.applyTargets(previous, getString(R.string.batch_restoring)) { count, err ->
                            snack(
                                if (err == null) getString(R.string.snack_restored_count, count)
                                else getString(R.string.snack_partial_fail, err)
                            )
                        }
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showRestoreDialog() {
        val clipboardText = readClipboard()
        val input = EditText(this).apply {
            hint = getString(R.string.restore_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 5
            maxLines = 12
            setTextColor(getColor(R.color.on_surface))
            setHintTextColor(getColor(R.color.on_surface_dim))
            setPadding(0, dp(10), 0, 0)
            if (clipboardText.contains("# AppsPerms backup")) setText(clipboardText)
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(20), dp(4), dp(20), 0)
            addView(
                input,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restore_title)
            .setMessage(R.string.restore_desc)
            .setView(container)
            .setPositiveButton(R.string.dialog_apply) { _, _ ->
                val pairs = viewModel.parseBackup(input.text.toString())
                if (pairs.isEmpty()) {
                    showError(getString(R.string.restore_invalid))
                    return@setPositiveButton
                }
                viewModel.restoreFrom(pairs) { applied, skipped, error ->
                    val msg = buildString {
                        append(getString(R.string.restore_failed, applied))
                        if (skipped > 0) append(getString(R.string.restore_skipped, skipped))
                        error?.let { append(" · ").append(it) }
                    }
                    snack(msg)
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showConnectionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.connection_title)
            .setMessage(viewModel.deviceReport())
            .setPositiveButton(R.string.request_permission) { _, _ ->
                if (ShizukuBridge.isBinderAlive()) ShizukuBridge.requestPermission()
                else snack(getString(R.string.snack_shizuku_off))
            }
            .setNeutralButton(R.string.open_shizuku) { _, _ -> openShizukuApp() }
            .setNegativeButton(R.string.dialog_close, null)
            .show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setMessage(getString(R.string.about_body))
            .setPositiveButton(R.string.dialog_close, null)
            .show()
    }

    // ---------------------------------------------------------------- detail

    private fun showAppDetail(entry: AppEntry) {
        val b = DialogAppDetailBinding.inflate(layoutInflater)
        b.detailIcon.setImageDrawable(entry.icon)
        b.detailLabel.text = entry.label
        b.detailPkg.text = entry.packageName
        b.detailBadgeUid.text = getString(R.string.detail_uid_value, entry.uid)
        b.detailBadgeSdk.text = getString(R.string.detail_sdk_value, entry.targetSdk)
        b.detailBadgeType.text = getString(
            if (entry.isSystem) R.string.detail_badge_system else R.string.detail_badge_user
        )
        b.btnCopyPkg.setOnClickListener {
            copyToClipboard(entry.packageName, getString(R.string.clip_label_pkg))
        }
        b.btnAppInfo.setOnClickListener { openAppInfo(entry.packageName) }
        b.btnSystemSettings.setOnClickListener {
            startActivitySafely(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${entry.packageName}"))
            )
        }
        b.opsContainer.removeAllViews()
        b.opsContainer.addView(
            TextView(this).apply {
                text = getString(R.string.loading)
                setTextColor(getColor(R.color.on_surface_dim))
                textSize = 12f
                setPadding(0, dp(12), 0, dp(12))
            }
        )

        detailDialog = MaterialAlertDialogBuilder(this)
            .setView(b.root)
            .setPositiveButton(R.string.dialog_close, null)
            .create()
        detailDialog?.show()

        viewModel.readOps(entry) { ops -> renderOps(b, entry, ops) }
    }

    private fun renderOps(b: DialogAppDetailBinding, entry: AppEntry, ops: List<Pair<OpDef, OpStatus>>) {
        if (isFinishing || isDestroyed) return
        b.opsContainer.removeAllViews()
        if (ops.isEmpty()) {
            b.opsContainer.addView(
                TextView(this).apply {
                    setText(R.string.ops_unavailable)
                    setTextColor(getColor(R.color.deny))
                    textSize = 12f
                }
            )
            return
        }
        ops.forEach { (def, initialStatus) ->
            val row = ItemOpBinding.inflate(layoutInflater, b.opsContainer, false)
            row.opTitle.setText(def.titleRes)
            row.opDesc.text = getString(def.descRes) + "\n" + def.op
            row.opStatus.bindStatusChip(initialStatus)
            var currentStatus = initialStatus
            row.opRow.setOnClickListener {
                ModeSheet.show(this, entry, def, currentStatus) { picked ->
                    val prev = currentStatus
                    currentStatus = picked
                    row.opStatus.bindStatusChip(picked)
                    applyAndOfferUndo(entry, def, picked, prev) {
                        currentStatus = prev
                        row.opStatus.bindStatusChip(prev)
                    }
                }
            }
            b.opsContainer.addView(row.root)
        }
    }

    // --------------------------------------------------------------- helpers

    private fun openShizukuApp() {
        val intent = packageManager.getLaunchIntentForPackage(ShizukuBridge.SHIZUKU_PACKAGE)
        if (intent != null) {
            startActivitySafely(intent)
        } else {
            startActivitySafely(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${ShizukuBridge.SHIZUKU_PACKAGE}"),
                )
            )
        }
    }

    private fun openAppInfo(pkg: String) =
        startActivitySafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

    private fun startActivitySafely(intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (t: Throwable) {
            snack(getString(R.string.snack_cant_open, t.message ?: "?"))
        }
    }

    private fun copyToClipboard(text: String, label: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        snack(getString(R.string.copied, label))
    }

    private fun readClipboard(): String {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ""
        return if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.apply_failed)
            .setMessage(message)
            .setPositiveButton(R.string.dialog_close, null)
            .show()
    }

    private fun snack(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun snackWithUndo(message: String, onUndo: () -> Unit) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { onUndo() }
            .setActionTextColor(getColor(R.color.brand))
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
