package app.appsperms.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.appsperms.R
import app.appsperms.core.AccessSnapshot
import app.appsperms.core.AppTypeFilter
import app.appsperms.core.HistoryCodec
import app.appsperms.core.HistoryLine
import app.appsperms.core.HistoryStore
import app.appsperms.core.OpCatalog
import app.appsperms.core.OpDef
import app.appsperms.core.OpStatus
import app.appsperms.core.ShizukuBridge
import app.appsperms.core.SortMode
import app.appsperms.core.StatusFilter
import app.appsperms.data.AppsRepository
import app.appsperms.model.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class UiState(
    val snapshot: AccessSnapshot = AccessSnapshot.unknown(),
    val loading: Boolean = false,
    val busy: String? = null,
    val apps: List<AppEntry> = emptyList(),
    val items: List<ListItem> = emptyList(),
    val query: String = "",
    val tab: Int = 0,
    val typeFilter: AppTypeFilter = AppTypeFilter.ALL,
    val statusFilter: StatusFilter = StatusFilter.ALL,
    val sort: SortMode = SortMode.NAME,
) {
    val overlayCount: Int get() = apps.count { it.declaresOverlay || it.overlayStatus.isExplicit }
    val allowedCount: Int get() = apps.count { it.overlayStatus == OpStatus.ALLOWED }
    val blockedCount: Int
        get() = apps.count { it.overlayStatus == OpStatus.ERRORED || it.overlayStatus == OpStatus.IGNORED }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppsRepository(app)
    private val history = HistoryStore(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val probing = AtomicBoolean(false)

    /** Cek ulang status Shizuku — dipanggil tiap onStart/onResume supaya tidak basi. */
    fun refreshAccess(onResult: (AccessSnapshot) -> Unit = {}) {
        if (!probing.compareAndSet(false, true)) return
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                runCatching { ShizukuBridge.probe(getApplication()) }
                    .getOrElse { AccessSnapshot.unknown() }
            }
            _state.update { it.copy(snapshot = snapshot) }
            probing.set(false)
            onResult(snapshot)
            if (snapshot.canOperate) refresh()
        }
    }

    fun refresh(showSpinner: Boolean = true) {
        if (showSpinner) _state.update { it.copy(loading = true) }
        val preferShell = _state.value.snapshot.preferShell
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching { repo.loadApps(preferShell) }.getOrDefault(emptyList())
            }
            _state.update { s -> s.copy(apps = list, loading = false).recompute() }
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query).recompute() }
    fun setTab(index: Int) = _state.update { it.copy(tab = index).recompute() }
    fun setTypeFilter(f: AppTypeFilter) = _state.update { it.copy(typeFilter = f).recompute() }
    fun setStatusFilter(f: StatusFilter) = _state.update { it.copy(statusFilter = f).recompute() }
    fun setSort(s: SortMode) = _state.update { it.copy(sort = s).recompute() }

    // ------------------------------------------------------------------ ops

    fun readOps(entry: AppEntry, onDone: (List<Pair<OpDef, OpStatus>>) -> Unit) {
        val preferShell = _state.value.snapshot.preferShell
        viewModelScope.launch {
            val ops = withContext(Dispatchers.IO) {
                runCatching { repo.readOps(entry, preferShell) }.getOrDefault(emptyList())
            }
            onDone(ops)
        }
    }

    /**
     * Ubah mode satu op. Optimistic UI update: langsung ubah status di UI seketika tanpa jeda.
     * [fromStatus] = status op yang sebenarnya SEBELUM ditulis (diisi dari UI yang
     * membacanya langsung, bukan asumsi) supaya riwayat/undo akurat untuk op non-overlay.
     */
    fun applyStatus(
        entry: AppEntry,
        def: OpDef,
        status: OpStatus,
        fromStatus: OpStatus? = null,
        onDone: (error: String?, previous: OpStatus) -> Unit,
    ) {
        val previous = entry.overlayStatus
        if (def.op == OpCatalog.OVERLAY.op && previous == status) {
            onDone(null, previous)
            return
        }

        // Optimistic UI update: langsung ubah status di list seketika (0 ms feedback)
        if (def.op == OpCatalog.OVERLAY.op) {
            patchEntry(entry.packageName) { it.copy(overlayStatus = status) }
        }

        val preferShell = _state.value.snapshot.preferShell
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching { repo.writeOp(entry, def, status, preferShell) }
                    .getOrElse { it.message ?: it.javaClass.simpleName }
            }
            if (error != null && def.op == OpCatalog.OVERLAY.op) {
                // Eksekusi gagal -> rollback ke status semula
                patchEntry(entry.packageName) { it.copy(overlayStatus = previous) }
            } else if (error == null) {
                recordHistory(entry.packageName, def.op, fromStatus ?: previous, status)
            }
            onDone(error, previous)
        }
    }

    // --------------------------------------------------------------- riwayat

    private fun recordHistory(pkg: String, op: String, from: OpStatus, to: OpStatus) {
        if (from == to) return
        viewModelScope.launch(Dispatchers.IO) {
            history.append(HistoryLine(System.currentTimeMillis(), pkg, op, from, to))
        }
    }

    fun historyLines(onDone: (List<HistoryLine>) -> Unit) {
        viewModelScope.launch {
            onDone(withContext(Dispatchers.IO) { history.read().asReversed() })
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) { history.clear() }
    }

    /**
     * Undo massal: tiap (paket, op) yang pernah diubah di riwayat dikembalikan ke
     * status PALING AWAL yang tercatat. Rencana dihitung di [HistoryCodec.undoPlan];
     * paket yang sudah ter-uninstall dilewati.
     */
    fun undoHistory(onDone: (applied: Int, skipped: Int, error: String?) -> Unit) {
        viewModelScope.launch {
            val apps = _state.value.apps
            val plan = withContext(Dispatchers.IO) {
                HistoryCodec.undoPlan(history.read())
            }.mapNotNull { line ->
                val entry = apps.firstOrNull { it.packageName == line.pkg } ?: return@mapNotNull null
                val def = OpCatalog.byName(line.op) ?: return@mapNotNull null
                Triple(entry, def, line.from)
            }
            // Lewati yang sudah sesuai — jangan menulis ulang tanpa perlu.
            val pending = plan.filter { (entry, def, target) ->
                !(def.op == OpCatalog.OVERLAY.op && entry.overlayStatus == target)
            }
            if (pending.isEmpty()) {
                onDone(0, plan.size, null)
                return@launch
            }
            applyPlan(pending, getApplication<Application>().getString(R.string.history_undoing), record = false) { applied, error ->
                onDone(applied, plan.size - pending.size + (pending.size - applied).coerceAtLeast(0), error)
            }
        }
    }

    /** Inti aksi massal: terapkan status ke banyak app sekaligus, dengan progress & optimistic update. */
    fun applyTargets(
        targets: List<Pair<AppEntry, OpStatus>>,
        label: String,
        onDone: (applied: Int, error: String?) -> Unit,
    ) = applyPlan(targets.map { Triple(it.first, OpCatalog.OVERLAY, it.second) }, label, record = true, onDone = onDone)

    private fun applyPlan(
        plan: List<Triple<AppEntry, OpDef, OpStatus>>,
        label: String,
        record: Boolean,
        onDone: (applied: Int, error: String?) -> Unit,
    ) {
        if (plan.isEmpty()) return
        val preferShell = _state.value.snapshot.preferShell
        _state.update { it.copy(busy = "$label 0/${plan.size}…") }

        // Optimistic update seketika untuk op overlay (satu-satunya yang tampil di list utama)
        val overlayPatch = plan.filter { it.second.op == OpCatalog.OVERLAY.op }
            .associate { it.first.packageName to it.third }
        if (overlayPatch.isNotEmpty()) {
            _state.update { s ->
                s.copy(apps = s.apps.map { entry ->
                    overlayPatch[entry.packageName]?.let { entry.copy(overlayStatus = it) } ?: entry
                }).recompute()
            }
        }

        viewModelScope.launch {
            var applied = 0
            var firstError: String? = null
            withContext(Dispatchers.IO) {
                plan.forEachIndexed { index, (entry, def, status) ->
                    val error = runCatching {
                        repo.writeOp(entry, def, status, preferShell)
                    }.getOrElse { it.message ?: it.javaClass.simpleName }
                    if (error == null) {
                        applied++
                        if (record && def.op == OpCatalog.OVERLAY.op && entry.overlayStatus != status) {
                            history.append(
                                HistoryLine(
                                    System.currentTimeMillis(),
                                    entry.packageName,
                                    def.op,
                                    entry.overlayStatus,
                                    status,
                                ),
                            )
                        }
                    } else {
                        if (firstError == null) firstError = "${entry.label}: $error"
                        // Rollback optimistic entry untuk target yang gagal
                        _state.update { s ->
                            s.copy(apps = s.apps.map {
                                if (it.packageName == entry.packageName) entry else it
                            }).recompute()
                        }
                    }
                    _state.update { it.copy(busy = "$label ${index + 1}/${plan.size}…") }
                }
            }
            _state.update { it.copy(busy = null) }
            onDone(applied, firstError)
        }
    }

    fun batchApplyOverlay(
        entries: List<AppEntry>,
        status: OpStatus,
        onDone: (applied: Int, error: String?, previous: List<Pair<AppEntry, OpStatus>>) -> Unit,
    ) {
        val previous = entries.map { it to it.overlayStatus }
        applyTargets(entries.map { it to status }, getApplication<Application>().getString(R.string.batch_processing)) { applied, error ->
            onDone(applied, error, previous)
        }
    }

    /** Restore dari teks backup (paket=status). */
    fun restoreFrom(
        pairs: List<Pair<String, OpStatus>>,
        onDone: (applied: Int, skipped: Int, error: String?) -> Unit,
    ) {
        val apps = _state.value.apps
        val targets = pairs.mapNotNull { (pkg, status) ->
            apps.firstOrNull { it.packageName == pkg }?.let { it to status }
        }
        val skipped = pairs.size - targets.size
        if (targets.isEmpty()) {
            onDone(0, skipped, getApplication<Application>().getString(R.string.restore_none_matched))
            return
        }
        applyTargets(targets, getApplication<Application>().getString(R.string.batch_processing)) { applied, error -> onDone(applied, skipped, error) }
    }

    fun exportBackup(): String = repo.exportBackup(_state.value.apps)

    fun parseBackup(text: String): List<Pair<String, OpStatus>> = repo.parseBackup(text)

    fun deviceReport(): String = ShizukuBridge.deviceSummary(getApplication(), _state.value.snapshot)

    fun appsNow(): List<AppEntry> = _state.value.apps

    // -------------------------------------------------------------- internal

    private inline fun patchEntry(pkg: String, transform: (AppEntry) -> AppEntry) {
        _state.update { s ->
            s.copy(apps = s.apps.map { if (it.packageName == pkg) transform(it) else it }).recompute()
        }
    }

    private fun UiState.recompute(): UiState =
        copy(items = buildItems(apps, query, tab, typeFilter, statusFilter, sort))

    private fun buildItems(
        apps: List<AppEntry>,
        query: String,
        tab: Int,
        typeFilter: AppTypeFilter,
        statusFilter: StatusFilter,
        sort: SortMode,
    ): List<ListItem> {
        val base = when (tab) {
            0 -> apps.filter { it.declaresOverlay || it.overlayStatus.isExplicit }
            else -> apps
        }
        val filtered = base.filter {
            it.matches(query) && statusFilter.accepts(it) && typeFilter.accepts(it)
        }
        val sorted = when (sort) {
            SortMode.NAME -> filtered.sortedBy { it.labelLower }
            SortMode.STATUS -> filtered.sortedWith(
                compareBy({ it.overlayStatus.sortRank }, { it.labelLower })
            )
        }

        // Pemisah section app terinstall vs app sistem (dimatikan saat user memfilter tipe).
        if (typeFilter != AppTypeFilter.ALL) return sorted.map { ListItem.App(it) }

        val userApps = sorted.filter { !it.isSystem }
        val systemApps = sorted.filter { it.isSystem }
        return buildList {
            if (userApps.isNotEmpty()) {
                add(ListItem.Header(getApplication<Application>().getString(R.string.header_user_apps), userApps.size))
                userApps.forEach { add(ListItem.App(it)) }
            }
            if (systemApps.isNotEmpty()) {
                add(ListItem.Header(getApplication<Application>().getString(R.string.header_system_apps), systemApps.size))
                systemApps.forEach { add(ListItem.App(it)) }
            }
        }
    }
}
