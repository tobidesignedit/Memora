package com.memora.app.ui

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.memora.app.BuildConfig
import com.memora.app.data.AppUpdater
import com.memora.app.data.BenchmarkEntry
import com.memora.app.data.FileLibraryIndexer
import com.memora.app.data.MediaAutoIndexer
import com.memora.app.data.MemoraRepository
import com.memora.app.data.MemoryItem
import com.memora.app.data.MemoryThread
import com.memora.app.data.ReminderItem
import com.memora.app.data.RemoteConfigSnapshot
import com.memora.app.data.SearchAnswer
import com.memora.app.data.SideBySideBenchmarkResult
import com.memora.app.data.VaultSnapshot
import com.memora.app.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

enum class MemoraTab { VAULT, SEARCH, THREADS, REMINDERS }

data class IncomingSharePayload(
  val text: String = "",
  val url: String? = null,
  val bitmap: Bitmap? = null,
  val kindHint: String = "note",
  val sourceApp: String = "Android Share Sheet",
)

data class MemoraUiState(
  val activeTab: MemoraTab = MemoraTab.VAULT,
  val darkMode: Boolean = false,
  val online: Boolean = true,
  val uid: String? = null,
  val accountEmail: String? = null,
  val dismissedAuthLanding: Boolean = false,
  val authBusy: Boolean = false,
  val updateCheckBusy: Boolean = false,
  val updateDownloading: Boolean = false,
  val updateDownloadProgress: Int = 0,
  val appVersionCode: Int = BuildConfig.VERSION_CODE,
  val appVersionName: String = BuildConfig.VERSION_NAME,
  val updateAvailable: Boolean = false,
  val deviceModelLabel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
  val defaultPrivacyMode: String = "prefer_on_device",
  val remoteConfig: RemoteConfigSnapshot = RemoteConfigSnapshot(),
  val memories: List<MemoryItem> = emptyList(),
  val reminders: List<ReminderItem> = emptyList(),
  val threads: List<MemoryThread> = emptyList(),
  val benchmarks: List<BenchmarkEntry> = emptyList(),
  val searchHistory: List<SearchAnswer> = emptyList(),
  val searchBusy: Boolean = false,
  val captureBusy: Boolean = false,
  val synthesizingThreadId: String? = null,
  val captureSheetOpen: Boolean = false,
  val telemetrySheetOpen: Boolean = false,
  val selectedMemory: MemoryItem? = null,
  val incomingShare: IncomingSharePayload? = null,
  val activeFilter: String = "all",
  val quickQuery: String = "",
  val toastMessage: String? = null,
  val autoIndexEnabled: Boolean = false,
  val autoIndexScope: String = "everything",
  val autoIndexLocalOnly: Boolean = false,
  val autoIndexBusy: Boolean = false,
  val connectedFolderCount: Int = 0,
  val hasMediaPermission: Boolean = false,
  val indexedMediaCount: Int = 0,
  val benchmarkBusy: Boolean = false,
  val lastSideBySideBenchmark: SideBySideBenchmarkResult? = null,
)

class MemoraViewModel(application: Application) : AndroidViewModel(application) {
  val repo = MemoraRepository(application)
  val mediaIndexer = MediaAutoIndexer(application)
  val fileLibrary = FileLibraryIndexer(application)
  private val _ui = MutableStateFlow(
    MemoraUiState(
      online = repo.isOnline(),
      hasMediaPermission = mediaIndexer.hasMediaPermission(),
    ),
  )
  val ui: StateFlow<MemoraUiState> = _ui.asStateFlow()

  private var indexedMediaKeys: MutableSet<String> = mutableSetOf()
  private var cloudListeners: List<ListenerRegistration> = emptyList()
  private var tts: TextToSpeech? = null
  private var ttsReady = false

  init {
    tts = TextToSpeech(application) { status ->
      if (status == TextToSpeech.SUCCESS) {
        tts?.language = Locale.US
        ttsReady = true
      }
    }
    bootstrap()
  }

  private fun bootstrap() {
    viewModelScope.launch {
      val local = repo.loadLocal()
      indexedMediaKeys = local.indexedMediaKeys.toMutableSet()
      val permGranted = mediaIndexer.hasMediaPermission()
      val autoOn = local.autoIndexEnabled && permGranted
      _ui.update {
        it.copy(
          memories = sortMemories(local.memories),
          reminders = repo.annotateConflicts(local.reminders),
          threads = local.threads,
          benchmarks = local.benchmarks,
          online = repo.isOnline(),
          hasMediaPermission = permGranted,
          autoIndexEnabled = autoOn,
          autoIndexScope = local.autoIndexScope,
          autoIndexLocalOnly = local.autoIndexLocalOnly,
          indexedMediaCount = indexedMediaKeys.size,
          connectedFolderCount = fileLibrary.connectedFolders().size,
        )
      }

      if (autoOn) {
        mediaIndexer.startObserving { scanMediaNow(silent = true) }
      }

      val rc = repo.fetchRemoteConfig(forceFresh = false)
      val uid = repo.ensureSignedInUid()
      val email = repo.currentAccountEmail()
      val hasUpdate = rc.latestAppVersionCode > BuildConfig.VERSION_CODE && rc.apkDownloadUrl.isNotBlank()
      _ui.update {
        it.copy(
          uid = uid,
          accountEmail = email,
          remoteConfig = rc,
          updateAvailable = hasUpdate,
          defaultPrivacyMode = rc.defaultInferenceMode,
          online = repo.isOnline(),
        )
      }

      if (uid != null) {
        attachCloudListeners(uid)
        flushPendingMemories(uid)
      }
    }
  }

  private fun attachCloudListeners(uid: String) {
    cloudListeners.forEach { it.remove() }
    cloudListeners = repo.subscribeCloud(
      uid = uid,
      onMemories = { cloudList ->
        viewModelScope.launch {
          val localPrivateOrPending = _ui.value.memories.filter {
            it.privacyMode == "only_on_device" || it.syncStatus == "pending"
          }
          val merged = linkedMapOf<String, MemoryItem>()
          localPrivateOrPending.forEach { merged[it.id] = it }
          cloudList.forEach { merged[it.id] = it }
          val sorted = sortMemories(merged.values.toList())
          val threads = repo.reconcileThreads(sorted, _ui.value.threads, uid)
          _ui.update { it.copy(memories = sorted, threads = threads) }
          persistCurrent()
        }
      },
      onReminders = { cloudRems ->
        viewModelScope.launch {
          val merged = linkedMapOf<String, ReminderItem>()
          _ui.value.reminders.forEach { merged[it.id] = it }
          cloudRems.forEach { merged[it.id] = it }
          val annotated = repo.annotateConflicts(merged.values.sortedBy { it.dueDateIso })
          annotated.filter { it.status == "confirmed" || it.status == "snoozed" }
            .forEach { ReminderScheduler.scheduleReminder(getApplication(), it) }
          _ui.update { it.copy(reminders = annotated) }
          persistCurrent()
        }
      },
      onThreads = { cloudThreads ->
        viewModelScope.launch {
          val merged = linkedMapOf<String, MemoryThread>()
          _ui.value.threads.forEach { merged[it.id] = it }
          cloudThreads.forEach { merged[it.id] = it }
          _ui.update { it.copy(threads = merged.values.sortedByDescending { t -> t.updatedAt }) }
          persistCurrent()
        }
      },
    )
  }

  private suspend fun flushPendingMemories(uid: String) {
    val pending = _ui.value.memories.filter {
      it.syncStatus == "pending" && it.privacyMode != "only_on_device"
    }
    if (pending.isEmpty()) return
    val updated = _ui.value.memories.map { m ->
      if (m.syncStatus == "pending" && m.privacyMode != "only_on_device") {
        repo.syncMemoryToCloud(m, uid)
      } else {
        m
      }
    }
    _ui.update { it.copy(memories = sortMemories(updated)) }
    persistCurrent()
  }

  private suspend fun persistCurrent() {
    val state = _ui.value
    repo.saveLocal(
      VaultSnapshot(
        memories = state.memories,
        reminders = state.reminders,
        threads = state.threads,
        benchmarks = state.benchmarks.take(40),
        indexedMediaKeys = indexedMediaKeys.toSet(),
        autoIndexEnabled = state.autoIndexEnabled,
        autoIndexScope = state.autoIndexScope,
        autoIndexLocalOnly = state.autoIndexLocalOnly,
      ),
    )
  }

  fun onMediaPermissionResult(granted: Boolean) {
    _ui.update { it.copy(hasMediaPermission = granted) }
    if (granted) {
      enableAutoIndexInternal()
    } else {
      _ui.update {
        it.copy(
          autoIndexEnabled = false,
          toastMessage = "File access denied — auto-indexing stays off.",
        )
      }
      viewModelScope.launch { persistCurrent() }
    }
  }

  fun setAutoIndexEnabled(enabled: Boolean) {
    if (enabled) {
      if (mediaIndexer.hasMediaPermission()) {
        enableAutoIndexInternal()
      }
    } else {
      mediaIndexer.stopObserving()
      _ui.update {
        it.copy(
          autoIndexEnabled = false,
          toastMessage = "Automatic phone indexing paused.",
        )
      }
      viewModelScope.launch { persistCurrent() }
    }
  }

  private fun enableAutoIndexInternal() {
    _ui.update {
      it.copy(
        hasMediaPermission = true,
        autoIndexEnabled = true,
        toastMessage = when (it.autoIndexScope) {
          "screenshots" -> "Auto-indexing active — scanning screenshots privately…"
          "all_photos" -> "Auto-indexing active — scanning all images privately…"
          else -> "Auto-indexing active — scanning images, documents, audio & video privately…"
        },
      )
    }
    mediaIndexer.startObserving { scanMediaNow(silent = true) }
    viewModelScope.launch { persistCurrent() }
    scanMediaNow(silent = false)
  }

  fun setAutoIndexScope(scope: String) {
    _ui.update { it.copy(autoIndexScope = scope) }
    viewModelScope.launch { persistCurrent() }
    if (_ui.value.autoIndexEnabled) scanMediaNow(silent = false)
  }

  fun setAutoIndexLocalOnly(localOnly: Boolean) {
    _ui.update { it.copy(autoIndexLocalOnly = localOnly) }
    viewModelScope.launch { persistCurrent() }
  }

  fun onAppResumed() {
    val perm = mediaIndexer.hasMediaPermission()
    _ui.update { it.copy(hasMediaPermission = perm, online = repo.isOnline()) }
    if (_ui.value.autoIndexEnabled && perm) {
      scanMediaNow(silent = true)
    }
    viewModelScope.launch {
      val rc = repo.fetchRemoteConfig(forceFresh = false)
      val hasUpdate = rc.latestAppVersionCode > BuildConfig.VERSION_CODE && rc.apkDownloadUrl.isNotBlank()
      _ui.update { it.copy(remoteConfig = rc, updateAvailable = hasUpdate) }
    }
  }

  fun dismissAuthLanding() = _ui.update { it.copy(dismissedAuthLanding = true) }

  fun signInWithEmail(email: String, password: String) {
    if (_ui.value.authBusy) return
    if (email.isBlank() || password.length < 6) {
      _ui.update { it.copy(toastMessage = "Enter a valid email and 6+ character password.") }
      return
    }
    viewModelScope.launch {
      _ui.update { it.copy(authBusy = true) }
      try {
        repo.signInWithEmail(email, password)
          .onSuccess { (uid, mail) ->
            onAuthenticatedAccount(uid, mail, "Signed in as ${mail ?: "account"} — syncing live.")
          }
          .onFailure { err ->
            _ui.update { it.copy(toastMessage = err.localizedMessage ?: "Sign-in failed.") }
          }
      } finally {
        _ui.update { it.copy(authBusy = false) }
      }
    }
  }

  fun signUpWithEmail(email: String, password: String) {
    if (_ui.value.authBusy) return
    if (email.isBlank() || password.length < 6) {
      _ui.update { it.copy(toastMessage = "Enter a valid email and 6+ character password.") }
      return
    }
    viewModelScope.launch {
      _ui.update { it.copy(authBusy = true) }
      try {
        repo.signUpWithEmail(email, password)
          .onSuccess { (uid, mail) ->
            onAuthenticatedAccount(uid, mail, "Account created (${mail ?: "synced"}) — vault linked!")
          }
          .onFailure { err ->
            _ui.update { it.copy(toastMessage = err.localizedMessage ?: "Sign-up failed.") }
          }
      } finally {
        _ui.update { it.copy(authBusy = false) }
      }
    }
  }

  fun signInWithGoogle(activity: Activity) {
    if (_ui.value.authBusy) return
    viewModelScope.launch {
      _ui.update { it.copy(authBusy = true) }
      try {
        repo.signInWithGoogle(activity)
          .onSuccess { (uid, mail) ->
            onAuthenticatedAccount(uid, mail, "Signed in with Google (${mail ?: "synced"}). ")
          }
          .onFailure { err ->
            _ui.update { it.copy(toastMessage = err.localizedMessage ?: "Google sign-in failed.") }
          }
      } finally {
        _ui.update { it.copy(authBusy = false) }
      }
    }
  }

  private suspend fun onAuthenticatedAccount(uid: String, email: String?, message: String) {
    // Re-stamp any non-private local items so existing captures sync into the linked account
    val restamped = _ui.value.memories.map { m ->
      if (m.privacyMode == "only_on_device") m else m.copy(uid = uid, syncStatus = "pending")
    }
    _ui.update {
      it.copy(
        uid = uid,
        accountEmail = email,
        memories = restamped,
        toastMessage = message,
      )
    }
    attachCloudListeners(uid)
    flushPendingMemories(uid)
    persistCurrent()
  }

  fun signOutAccount() {
    viewModelScope.launch {
      cloudListeners.forEach { it.remove() }
      cloudListeners = emptyList()
      val guestUid = repo.signOutToGuest()
      _ui.update {
        it.copy(
          uid = guestUid,
          accountEmail = null,
          toastMessage = "Signed out to local guest session.",
        )
      }
      if (guestUid != null) attachCloudListeners(guestUid)
    }
  }

  fun checkForAppUpdate() {
    if (_ui.value.updateCheckBusy) return
    viewModelScope.launch {
      _ui.update { it.copy(updateCheckBusy = true) }
      try {
        val rc = repo.fetchRemoteConfig(forceFresh = true)
        val hasUpdate = rc.latestAppVersionCode > BuildConfig.VERSION_CODE && rc.apkDownloadUrl.isNotBlank()
        _ui.update {
          it.copy(
            remoteConfig = rc,
            updateAvailable = hasUpdate,
            toastMessage = if (hasUpdate) {
              "Update v${rc.latestAppVersionName} is available! Tap Download Update."
            } else {
              "You're on the latest MEMORA version (v${BuildConfig.VERSION_NAME})."
            },
          )
        }
      } finally {
        _ui.update { it.copy(updateCheckBusy = false) }
      }
    }
  }

  fun downloadAndInstallUpdate() {
    if (_ui.value.updateDownloading) return
    val rc = _ui.value.remoteConfig
    viewModelScope.launch {
      _ui.update { it.copy(updateDownloading = true, updateDownloadProgress = 0) }
      try {
        AppUpdater.downloadAndInstallWithProgress(
          context = getApplication(),
          apkUrl = rc.apkDownloadUrl,
          versionName = rc.latestAppVersionName,
          onProgress = { pct -> _ui.update { it.copy(updateDownloadProgress = pct) } },
          onStatus = { msg -> _ui.update { it.copy(toastMessage = msg) } },
        )
      } finally {
        _ui.update { it.copy(updateDownloading = false) }
      }
    }
  }

  fun scanMediaNow(silent: Boolean = false) {
    if (_ui.value.autoIndexBusy) return
    if (!mediaIndexer.hasMediaPermission()) {
      if (!silent) {
        _ui.update { it.copy(toastMessage = "Grant file access permission first.") }
      }
      return
    }
    viewModelScope.launch {
      _ui.update { it.copy(autoIndexBusy = true) }
      try {
        val state = _ui.value
        val privacyMode = if (state.autoIndexLocalOnly) "only_on_device" else state.defaultPrivacyMode
        val includeFiles = state.autoIndexScope == "everything"
        var addedCount = 0
        var detectedReminders = 0
        var rounds = 0

        // Work through the backlog in small rounds so a large phone library indexes steadily.
        while (rounds < 8) {
          rounds += 1
          val imageBatch = mediaIndexer.findNewCandidates(
            scope = state.autoIndexScope,
            alreadyIndexedKeys = indexedMediaKeys,
            maxBatch = 4,
          )
          val fileBatch = if (includeFiles) fileLibrary.findNewFiles(indexedMediaKeys, 4) else emptyList()
          if (imageBatch.isEmpty() && fileBatch.isEmpty()) break

          for (cand in imageBatch) {
            val bmp = mediaIndexer.decodeDownsampledBitmap(cand.contentUri)
            if (bmp == null) {
              indexedMediaKeys.add(cand.dedupeKey)
              continue
            }
            val result = repo.captureAndUnderstand(
              rawText = "",
              url = null,
              bitmap = bmp,
              kindHint = if (cand.isScreenshot) "screenshot" else "photo",
              privacyMode = privacyMode,
              sourceApp = "Auto-Indexed · ${cand.bucketName}",
              uid = _ui.value.uid,
              remoteConfig = _ui.value.remoteConfig,
            )
            detectedReminders += commitIndexed(result, cand.dedupeKey)
            addedCount += 1
          }

          for (file in fileBatch) {
            val extracted = fileLibrary.extract(file)
            if (extracted == null) {
              indexedMediaKeys.add(file.dedupeKey)
              continue
            }
            val result = repo.captureAndUnderstand(
              rawText = extracted.text,
              url = null,
              bitmap = extracted.bitmap,
              kindHint = extracted.kindHint,
              privacyMode = privacyMode,
              sourceApp = "Auto-Indexed · ${file.source}",
              uid = _ui.value.uid,
              remoteConfig = _ui.value.remoteConfig,
            )
            detectedReminders += commitIndexed(result, file.dedupeKey)
            addedCount += 1
          }
        }

        if (addedCount > 0) {
          val noun = if (includeFiles) "file" else "image"
          val note = if (detectedReminders > 0) {
            "Auto-indexed $addedCount new $noun${if (addedCount == 1) "" else "s"} · $detectedReminders reminder detected"
          } else {
            "Auto-indexed $addedCount new $noun${if (addedCount == 1) "" else "s"} privately"
          }
          _ui.update { it.copy(toastMessage = note) }
        } else if (!silent) {
          _ui.update { it.copy(toastMessage = "Everything on this phone is already indexed.") }
        }
      } finally {
        _ui.update { it.copy(autoIndexBusy = false) }
      }
    }
  }

  private suspend fun commitIndexed(
    result: Triple<MemoryItem, List<ReminderItem>, BenchmarkEntry>,
    dedupeKey: String,
  ): Int {
    val (mem, newRems, bench) = result
    indexedMediaKeys.add(dedupeKey)

    val nextMems = sortMemories(listOf(mem) + _ui.value.memories.filter { it.id != mem.id })
    val nextRems = repo.annotateConflicts(newRems + _ui.value.reminders)
    val nextThreads = repo.reconcileThreads(nextMems, _ui.value.threads, _ui.value.uid)
    nextThreads.forEach { repo.syncThreadToCloud(it, _ui.value.uid) }

    _ui.update {
      it.copy(
        memories = nextMems,
        reminders = nextRems,
        threads = nextThreads,
        benchmarks = listOf(bench) + it.benchmarks,
        indexedMediaCount = indexedMediaKeys.size,
      )
    }
    persistCurrent()
    return newRems.size
  }

  fun onFolderConnected(treeUri: Uri) {
    fileLibrary.connectFolder(treeUri)
    _ui.update {
      it.copy(
        connectedFolderCount = fileLibrary.connectedFolders().size,
        toastMessage = "Folder connected — documents inside will be indexed privately.",
      )
    }
    if (_ui.value.autoIndexEnabled) scanMediaNow(silent = false)
  }

  fun disconnectAllFolders() {
    fileLibrary.disconnectAllFolders()
    _ui.update { it.copy(connectedFolderCount = 0, toastMessage = "All connected folders disconnected.") }
  }

  fun selectTab(tab: MemoraTab) = _ui.update { it.copy(activeTab = tab) }
  fun toggleTheme() = _ui.update { it.copy(darkMode = !it.darkMode) }
  fun setFilter(filter: String) = _ui.update { it.copy(activeFilter = filter) }
  fun setQuickQuery(q: String) = _ui.update { it.copy(quickQuery = q) }
  fun openCapture(open: Boolean) = _ui.update {
    it.copy(captureSheetOpen = open, incomingShare = if (open) it.incomingShare else null)
  }
  fun openTelemetry(open: Boolean) = _ui.update {
    it.copy(telemetrySheetOpen = open, online = repo.isOnline())
  }
  fun openMemory(memory: MemoryItem?) = _ui.update { it.copy(selectedMemory = memory) }
  fun setDefaultPrivacyMode(mode: String) = _ui.update { it.copy(defaultPrivacyMode = mode) }
  fun clearToast() = _ui.update { it.copy(toastMessage = null) }

  fun handleSharedText(text: String) {
    val trimmed = text.trim()
    val urlRegex = Regex("https?://\\S+")
    val foundUrl = urlRegex.find(trimmed)?.value
    _ui.update {
      it.copy(
        incomingShare = IncomingSharePayload(
          text = trimmed,
          url = foundUrl,
          kindHint = if (foundUrl != null) "link" else "note",
          sourceApp = "Android Share Sheet",
        ),
        captureSheetOpen = true,
      )
    }
  }

  fun handleSharedImageUri(uri: Uri) {
    viewModelScope.launch {
      val bmp = decodeBitmapFromUri(uri)
      if (bmp != null) {
        _ui.update {
          it.copy(
            incomingShare = IncomingSharePayload(
              bitmap = bmp,
              kindHint = "screenshot",
              sourceApp = "Android Share Sheet",
            ),
            captureSheetOpen = true,
          )
        }
      }
    }
  }

  suspend fun decodeBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
      getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
      }
    }.getOrNull()
  }

  fun submitCapture(
    rawText: String,
    url: String?,
    bitmap: Bitmap?,
    kindHint: String,
    privacyMode: String,
  ) {
    if (_ui.value.captureBusy) return
    if (rawText.isBlank() && url.isNullOrBlank() && bitmap == null) return

    viewModelScope.launch {
      _ui.update { it.copy(captureBusy = true, online = repo.isOnline()) }
      try {
        val state = _ui.value
        val (mem, newRems, bench) = repo.captureAndUnderstand(
          rawText = rawText,
          url = url,
          bitmap = bitmap,
          kindHint = kindHint,
          privacyMode = privacyMode,
          sourceApp = state.incomingShare?.sourceApp,
          uid = state.uid,
          remoteConfig = state.remoteConfig,
        )

        val nextMemories = sortMemories(listOf(mem) + state.memories.filter { it.id != mem.id })
        val nextReminders = repo.annotateConflicts(newRems + state.reminders)
        val nextThreads = repo.reconcileThreads(nextMemories, state.threads, state.uid)
        nextThreads.forEach { repo.syncThreadToCloud(it, state.uid) }

        val srcLabel = when (mem.inferenceSource) {
          "on_device" -> "On-Device Gemini Nano"
          "in_cloud" -> "Cloud Gemini"
          else -> "Local Offline Parser"
        }
        val msg = if (newRems.isNotEmpty()) {
          "Saved via $srcLabel (${mem.inferenceLatencyMs}ms) · ${newRems.size} reminder detected"
        } else {
          "Saved via $srcLabel (${mem.inferenceLatencyMs}ms)"
        }

        _ui.update {
          it.copy(
            memories = nextMemories,
            reminders = nextReminders,
            threads = nextThreads,
            benchmarks = listOf(bench) + it.benchmarks,
            captureBusy = false,
            captureSheetOpen = false,
            incomingShare = null,
            toastMessage = msg,
          )
        }
        persistCurrent()
      } finally {
        _ui.update { it.copy(captureBusy = false) }
      }
    }
  }

  fun runSearch(query: String) {
    val trimmed = query.trim()
    if (trimmed.isBlank() || _ui.value.searchBusy) return
    viewModelScope.launch {
      _ui.update { it.copy(searchBusy = true, activeTab = MemoraTab.SEARCH) }
      try {
        val state = _ui.value
        val ans = repo.aiEngine.searchMemories(
          query = trimmed,
          memories = state.memories,
          privacyMode = state.defaultPrivacyMode,
          remoteConfig = state.remoteConfig,
        )
        val bench = BenchmarkEntry(
          id = UUID.randomUUID().toString(),
          operation = "natural_search",
          source = ans.source,
          modelUsed = ans.modelUsed,
          latencyMs = ans.latencyMs,
          timestamp = System.currentTimeMillis(),
        )
        _ui.update {
          it.copy(
            searchHistory = listOf(ans) + it.searchHistory,
            benchmarks = listOf(bench) + it.benchmarks,
            searchBusy = false,
          )
        }
        persistCurrent()
      } finally {
        _ui.update { it.copy(searchBusy = false) }
      }
    }
  }

  fun synthesizeThread(thread: MemoryThread) {
    if (_ui.value.synthesizingThreadId != null) return
    viewModelScope.launch {
      _ui.update { it.copy(synthesizingThreadId = thread.id) }
      try {
        val state = _ui.value
        val linked = state.memories.filter { it.id in thread.memoryIds }
        val res = repo.aiEngine.synthesizeThread(
          title = thread.title,
          memories = linked,
          privacyMode = state.defaultPrivacyMode,
          remoteConfig = state.remoteConfig,
        )
        val updated = repo.syncThreadToCloud(
          thread.copy(
            summary = res.summary,
            keyInsights = res.keyInsights,
            nextActions = res.nextActions,
            inferenceSource = res.inferenceSource,
            updatedAt = System.currentTimeMillis(),
          ),
          state.uid,
        )
        val bench = BenchmarkEntry(
          id = UUID.randomUUID().toString(),
          operation = "thread_synthesis",
          source = res.inferenceSource,
          modelUsed = res.modelUsed,
          latencyMs = res.latencyMs,
          timestamp = System.currentTimeMillis(),
        )
        _ui.update {
          it.copy(
            threads = it.threads.map { t -> if (t.id == updated.id) updated else t },
            benchmarks = listOf(bench) + it.benchmarks,
            synthesizingThreadId = null,
            toastMessage = "Thread synthesized (${res.latencyMs}ms)",
          )
        }
        persistCurrent()
      } finally {
        _ui.update { it.copy(synthesizingThreadId = null) }
      }
    }
  }

  fun confirmReminder(rem: ReminderItem) {
    viewModelScope.launch {
      val updated = repo.syncReminderToCloud(rem.copy(status = "confirmed"), _ui.value.uid)
      ReminderScheduler.fireImmediatePreviewNotification(getApplication(), updated)
      _ui.update {
        it.copy(
          reminders = repo.annotateConflicts(it.reminders.map { r -> if (r.id == updated.id) updated else r }),
          toastMessage = "Local notification scheduled for ${updated.dueDateIso}",
        )
      }
      persistCurrent()
    }
  }

  fun completeReminder(rem: ReminderItem) {
    viewModelScope.launch {
      val updated = repo.syncReminderToCloud(rem.copy(status = "completed"), _ui.value.uid)
      _ui.update {
        it.copy(
          reminders = repo.annotateConflicts(it.reminders.map { r -> if (r.id == updated.id) updated else r }),
        )
      }
      persistCurrent()
    }
  }

  fun snoozeReminder(rem: ReminderItem) {
    viewModelScope.launch {
      val nextIso = runCatching { LocalDate.parse(rem.dueDateIso).plusDays(1).toString() }
        .getOrElse { LocalDate.now().plusDays(1).toString() }
      val updated = repo.syncReminderToCloud(
        rem.copy(dueDateIso = nextIso, status = "snoozed"),
        _ui.value.uid,
      )
      _ui.update {
        it.copy(
          reminders = repo.annotateConflicts(it.reminders.map { r -> if (r.id == updated.id) updated else r }),
          toastMessage = "Snoozed to $nextIso",
        )
      }
      persistCurrent()
    }
  }

  fun deleteReminder(id: String) {
    viewModelScope.launch {
      repo.deleteReminderFromCloud(id, _ui.value.uid)
      _ui.update {
        it.copy(reminders = repo.annotateConflicts(it.reminders.filter { r -> r.id != id }))
      }
      persistCurrent()
    }
  }

  fun createManualReminder(title: String, dueDateIso: String, dueTime: String, venue: String, category: String) {
    viewModelScope.launch {
      val now = System.currentTimeMillis()
      val rem = ReminderItem(
        id = "rem-${UUID.randomUUID()}",
        uid = _ui.value.uid ?: "local",
        title = title.trim(),
        dueDateIso = dueDateIso,
        dueTime = dueTime.trim().ifBlank { null },
        venue = venue.trim().ifBlank { null },
        category = category,
        priority = "medium",
        status = "confirmed",
        createdAt = now,
        updatedAt = now,
      )
      val saved = repo.syncReminderToCloud(rem, _ui.value.uid)
      ReminderScheduler.fireImmediatePreviewNotification(getApplication(), saved)
      _ui.update {
        it.copy(
          reminders = repo.annotateConflicts(listOf(saved) + it.reminders),
          toastMessage = "Reminder scheduled for $dueDateIso",
        )
      }
      persistCurrent()
    }
  }

  fun togglePin(memory: MemoryItem) {
    viewModelScope.launch {
      val updated = repo.syncMemoryToCloud(memory.copy(pinned = !memory.pinned), _ui.value.uid)
      _ui.update {
        it.copy(
          memories = sortMemories(it.memories.map { m -> if (m.id == updated.id) updated else m }),
          selectedMemory = if (it.selectedMemory?.id == updated.id) updated else it.selectedMemory,
        )
      }
      persistCurrent()
    }
  }

  fun toggleFavorite(memory: MemoryItem) {
    viewModelScope.launch {
      val updated = repo.syncMemoryToCloud(memory.copy(favorite = !memory.favorite), _ui.value.uid)
      _ui.update {
        it.copy(
          memories = sortMemories(it.memories.map { m -> if (m.id == updated.id) updated else m }),
          selectedMemory = if (it.selectedMemory?.id == updated.id) updated else it.selectedMemory,
        )
      }
      persistCurrent()
    }
  }

  fun savePersonalNote(memory: MemoryItem, note: String) {
    viewModelScope.launch {
      val updated = repo.syncMemoryToCloud(memory.copy(personalNote = note.trim()), _ui.value.uid)
      _ui.update {
        it.copy(
          memories = sortMemories(it.memories.map { m -> if (m.id == updated.id) updated else m }),
          selectedMemory = updated,
          toastMessage = "Annotation saved",
        )
      }
      persistCurrent()
    }
  }

  fun deleteMemory(id: String) {
    viewModelScope.launch {
      val target = _ui.value.memories.firstOrNull { it.id == id }
      repo.localStore.deleteLocalMedia(target?.mediaLocalKey)
      repo.deleteMemoryFromCloud(id, _ui.value.uid)
      val next = _ui.value.memories.filter { it.id != id }
      val nextThreads = repo.reconcileThreads(next, _ui.value.threads, _ui.value.uid)
      _ui.update {
        it.copy(
          memories = next,
          threads = nextThreads,
          selectedMemory = null,
          toastMessage = "Memory deleted",
        )
      }
      persistCurrent()
    }
  }

  fun loadDemoMemories() {
    viewModelScope.launch {
      val uid = _ui.value.uid
      val (demoMems, demoRems) = repo.buildDemoBundle(uid)
      val syncedMems = demoMems.map { repo.syncMemoryToCloud(it, uid) }
      val syncedRems = demoRems.map { repo.syncReminderToCloud(it, uid) }
      val mergedMems = sortMemories((syncedMems + _ui.value.memories).distinctBy { it.id })
      val mergedRems = repo.annotateConflicts((syncedRems + _ui.value.reminders).distinctBy { it.id })
      val nextThreads = repo.reconcileThreads(mergedMems, _ui.value.threads, uid)
      nextThreads.forEach { repo.syncThreadToCloud(it, uid) }
      _ui.update {
        it.copy(
          memories = mergedMems,
          reminders = mergedRems,
          threads = nextThreads,
          toastMessage = "Loaded demo memories, Visual Intelligence Reminders & Threads",
        )
      }
      persistCurrent()
    }
  }

  fun syncNow() {
    viewModelScope.launch {
      val online = repo.isOnline()
      val uid = _ui.value.uid ?: repo.ensureSignedInUid()
      if (uid != null && online) {
        flushPendingMemories(uid)
        _ui.update { it.copy(uid = uid, online = true, toastMessage = "Vault synced with Firebase") }
      } else {
        _ui.update { it.copy(online = online, toastMessage = "Offline — memories queued safely on device") }
      }
    }
  }

  fun runSideBySideBenchmark() {
    if (_ui.value.benchmarkBusy) return
    viewModelScope.launch {
      val online = repo.isOnline()
      _ui.update { it.copy(benchmarkBusy = true, online = online) }
      try {
        val result = repo.aiEngine.runSideBySideBenchmark(
          remoteConfig = _ui.value.remoteConfig,
          isOnline = online,
        )
        val newEntries = mutableListOf<BenchmarkEntry>()
        val now = System.currentTimeMillis()
        result.onDevice.latencyMs?.let { ms ->
          newEntries.add(
            BenchmarkEntry(
              id = UUID.randomUUID().toString(),
              operation = "side_by_side_benchmark",
              source = "on_device",
              modelUsed = result.onDevice.modelUsed,
              latencyMs = ms,
              timestamp = now,
            ),
          )
        }
        result.inCloud.latencyMs?.let { ms ->
          newEntries.add(
            BenchmarkEntry(
              id = UUID.randomUUID().toString(),
              operation = "side_by_side_benchmark",
              source = "in_cloud",
              modelUsed = result.inCloud.modelUsed,
              latencyMs = ms,
              timestamp = now + 1L,
            ),
          )
        }
        _ui.update {
          it.copy(
            benchmarks = newEntries + it.benchmarks,
            lastSideBySideBenchmark = result,
            benchmarkBusy = false,
            toastMessage = result.verdict,
          )
        }
        persistCurrent()
      } finally {
        _ui.update { it.copy(benchmarkBusy = false) }
      }
    }
  }

  fun speakText(text: String) {
    if (!ttsReady) return
    val clean = text.replace(Regex("[*#`]"), "").take(700)
    tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "memora-recap")
  }

  fun stopSpeaking() {
    tts?.stop()
  }

  private fun sortMemories(list: List<MemoryItem>): List<MemoryItem> =
    list.sortedWith(compareByDescending<MemoryItem> { it.pinned }.thenByDescending { it.createdAt })

  override fun onCleared() {
    super.onCleared()
    mediaIndexer.stopObserving()
    cloudListeners.forEach { it.remove() }
    tts?.stop()
    tts?.shutdown()
  }
}
