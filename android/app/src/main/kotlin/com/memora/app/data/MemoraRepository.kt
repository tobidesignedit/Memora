package com.memora.app.data

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.memora.app.reminders.ReminderScheduler
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.UUID

/**
 * Offline-first repository coordinating:
 * 1) App-private local vault (`LocalVaultStore`)
 * 2) Live Cloud Firestore sync (`users/{uid}/memories`, `/reminders`, `/threads`)
 * 3) Firebase AI Logic Hybrid Inference (`HybridAiEngine`)
 * 4) Firebase Remote Config dynamic model routing
 * 5) Android local notification scheduling (`ReminderScheduler`)
 */
class MemoraRepository(private val context: Context) {
  val localStore = LocalVaultStore(context)
  val aiEngine = HybridAiEngine()
  private val auth = Firebase.auth
  private val db = Firebase.firestore

  fun isOnline(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  private fun parseDisplayName(rawDisplayName: String?, fallbackEmail: String?): Pair<String?, String?> {
    val raw = rawDisplayName?.trim().orEmpty()
    if (raw.isNotEmpty()) {
      val match = Regex("^(.*?)\\s*\\(@([^)]+)\\)$").find(raw)
      if (match != null) {
        val parsedName = match.groupValues[1].trim().ifBlank { null }
        val parsedUser = match.groupValues[2].trim().ifBlank { null }
        return parsedName to parsedUser
      }
      val fallbackUser = fallbackEmail?.substringBefore('@')?.trim()?.takeIf { it.isNotBlank() }
      return raw to fallbackUser
    }
    return null to null
  }

  fun currentAccountEmail(): String? = auth.currentUser?.takeIf { !it.isAnonymous }?.email

  fun currentAccountInfo(): AuthAccountInfo? {
    val u = auth.currentUser?.takeIf { !it.isAnonymous } ?: return null
    val (name, username) = parseDisplayName(u.displayName, u.email)
    return AuthAccountInfo(
      uid = u.uid,
      email = u.email,
      name = name,
      username = username,
    )
  }

  suspend fun ensureSignedInUid(): String? {
    auth.currentUser?.uid?.let { return it }
    if (!isOnline()) return null
    return runCatching {
      auth.signInAnonymously().await().user?.uid
    }.getOrNull()
  }

  suspend fun signInWithEmail(email: String, password: String): Result<AuthAccountInfo> =
    runCatching {
      val res = auth.signInWithEmailAndPassword(email.trim(), password).await()
      val u = res.user ?: error("Sign-in did not return a user.")
      runCatching { u.reload().await() }
      val refreshed = auth.currentUser ?: u
      val (name, username) = parseDisplayName(refreshed.displayName, refreshed.email)
      AuthAccountInfo(
        uid = refreshed.uid,
        email = refreshed.email,
        name = name,
        username = username,
      )
    }

  suspend fun signUpWithEmail(
    name: String,
    username: String,
    email: String,
    password: String,
  ): Result<AuthAccountInfo> =
    runCatching {
      val cleanName = name.trim()
      val cleanUsername = username.trim().removePrefix("@").trim()
      val cleanEmail = email.trim()
      val current = auth.currentUser
      val user = if (current != null && current.isAnonymous) {
        val cred = EmailAuthProvider.getCredential(cleanEmail, password)
        runCatching { current.linkWithCredential(cred).await().user }
          .getOrNull() ?: auth.createUserWithEmailAndPassword(cleanEmail, password).await().user
      } else {
        auth.createUserWithEmailAndPassword(cleanEmail, password).await().user
      } ?: error("Account creation failed.")

      val formattedDisplay = when {
        cleanName.isNotEmpty() && cleanUsername.isNotEmpty() -> "$cleanName (@$cleanUsername)"
        cleanName.isNotEmpty() -> cleanName
        cleanUsername.isNotEmpty() -> "@$cleanUsername"
        else -> ""
      }
      if (formattedDisplay.isNotEmpty()) {
        runCatching {
          user.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(formattedDisplay).build(),
          ).await()
        }
      }

      AuthAccountInfo(
        uid = user.uid,
        email = user.email ?: cleanEmail,
        name = cleanName.ifBlank { null },
        username = cleanUsername.ifBlank { null },
      )
    }

  suspend fun signInWithGoogle(activity: Activity): Result<AuthAccountInfo> =
    runCatching {
      val webClientId = runCatching {
        context.getString(com.memora.app.R.string.default_web_client_id)
      }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: "67062354466-94nj28uj11qeubv10t0g5j4v70i3s64u.apps.googleusercontent.com"

      var googleDisplayName: String? = null
      val credUser = try {
        val credentialManager = CredentialManager.create(activity)
        val googleIdOption = GetGoogleIdOption.Builder()
          .setFilterByAuthorizedAccounts(false)
          .setServerClientId(webClientId)
          .setAutoSelectEnabled(false)
          .build()
        val request = GetCredentialRequest.Builder()
          .addCredentialOption(googleIdOption)
          .build()
        val credResult = credentialManager.getCredential(request = request, context = activity)
        val googleIdTokenCred = GoogleIdTokenCredential.createFrom(credResult.credential.data)
        googleDisplayName = googleIdTokenCred.displayName
        val firebaseCred = GoogleAuthProvider.getCredential(googleIdTokenCred.idToken, null)
        val current = auth.currentUser
        if (current != null && current.isAnonymous) {
          runCatching { current.linkWithCredential(firebaseCred).await().user }
            .getOrNull() ?: auth.signInWithCredential(firebaseCred).await().user
        } else {
          auth.signInWithCredential(firebaseCred).await().user
        }
      } catch (cancel: GetCredentialCancellationException) {
        throw IllegalStateException("Google sign-in cancelled.", cancel)
      } catch (_: Exception) {
        // Fallback to Firebase OAuthProvider web flow when Credential Manager has no local Google account
        val provider = OAuthProvider.newBuilder("google.com").build()
        auth.startActivityForSignInWithProvider(activity, provider).await().user
      }

      val u = credUser ?: error("Google sign-in did not return a user.")
      val (parsedName, parsedUsername) = parseDisplayName(
        u.displayName?.takeIf { it.isNotBlank() } ?: googleDisplayName,
        u.email,
      )
      AuthAccountInfo(
        uid = u.uid,
        email = u.email,
        name = parsedName,
        username = parsedUsername,
      )
    }

  suspend fun signOutToGuest(): String? {
    runCatching { auth.signOut() }
    return ensureSignedInUid()
  }

  suspend fun fetchRemoteConfig(forceFresh: Boolean = false): RemoteConfigSnapshot {
    return runCatching {
      val rc = Firebase.remoteConfig
      rc.setConfigSettingsAsync(
        remoteConfigSettings { minimumFetchIntervalInSeconds = if (forceFresh) 0L else 30L },
      ).await()
      rc.setDefaultsAsync(
        mapOf(
          "cloud_model_fast" to "gemini-3.5-flash-lite",
          "cloud_model_pro" to "gemini-3.8-flash",
          "tts_model" to "gemini-3.1-flash-tts-preview",
          "default_inference_mode" to "prefer_on_device",
          "max_context_memories" to 24L,
          "latest_app_version_code" to 2L,
          "latest_app_version_name" to "1.1.0",
          "apk_download_url" to "",
          "apk_release_notes" to "",
        ),
      ).await()
      if (isOnline()) {
        runCatching { rc.fetchAndActivate().await() }
      }
      RemoteConfigSnapshot(
        cloudModelFast = rc.getString("cloud_model_fast").ifBlank { "gemini-3.5-flash-lite" },
        cloudModelPro = rc.getString("cloud_model_pro").ifBlank { "gemini-3.8-flash" },
        ttsModel = rc.getString("tts_model").ifBlank { "gemini-3.1-flash-tts-preview" },
        defaultInferenceMode = rc.getString("default_inference_mode").ifBlank { "prefer_on_device" },
        maxContextMemories = rc.getLong("max_context_memories").toInt().coerceIn(8, 50),
        latestAppVersionCode = rc.getLong("latest_app_version_code").toInt().coerceAtLeast(1),
        latestAppVersionName = rc.getString("latest_app_version_name").ifBlank { "1.1.0" },
        apkDownloadUrl = rc.getString("apk_download_url").trim(),
        apkReleaseNotes = rc.getString("apk_release_notes").trim(),
      )
    }.getOrElse { RemoteConfigSnapshot() }
  }

  suspend fun loadLocal(): VaultSnapshot = localStore.loadSnapshot()

  suspend fun saveLocal(snapshot: VaultSnapshot) = localStore.saveSnapshot(snapshot)

  fun subscribeCloud(
    uid: String,
    onMemories: (List<MemoryItem>) -> Unit,
    onReminders: (List<ReminderItem>) -> Unit,
    onThreads: (List<MemoryThread>) -> Unit,
  ): List<ListenerRegistration> {
    val regs = mutableListOf<ListenerRegistration>()
    regs += db.collection("users").document(uid).collection("memories")
      .addSnapshotListener { snap, _ ->
        if (snap == null) return@addSnapshotListener
        val items = snap.documents.mapNotNull { doc ->
          runCatching {
            val entitiesMap = doc.get("entities") as? Map<*, *>
            MemoryItem(
              id = doc.id,
              uid = doc.getString("uid") ?: uid,
              kind = doc.getString("kind") ?: "note",
              title = doc.getString("title") ?: "",
              summary = doc.getString("summary") ?: "",
              rawText = doc.getString("rawText") ?: "",
              extractedText = doc.getString("extractedText") ?: "",
              url = doc.getString("url"),
              sourceApp = doc.getString("sourceApp"),
              mediaMimeType = doc.getString("mediaMimeType"),
              mediaFileName = doc.getString("mediaFileName"),
              mediaLocalKey = doc.getString("mediaLocalKey"),
              mediaThumbnail = doc.getString("mediaThumbnail"),
              tags = (doc.get("tags") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
              category = doc.getString("category") ?: "Personal Vault",
              entities = ExtractedEntities(
                people = listFromMap(entitiesMap, "people"),
                organizations = listFromMap(entitiesMap, "organizations"),
                dates = listFromMap(entitiesMap, "dates"),
                amounts = listFromMap(entitiesMap, "amounts"),
                locations = listFromMap(entitiesMap, "locations"),
                contacts = listFromMap(entitiesMap, "contacts"),
              ),
              actionItems = (doc.get("actionItems") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
              personalNote = doc.getString("personalNote"),
              threadId = doc.getString("threadId"),
              threadTopic = doc.getString("threadTopic"),
              importance = doc.getString("importance") ?: "normal",
              language = doc.getString("language") ?: "en",
              inferenceSource = doc.getString("inferenceSource") ?: "in_cloud",
              inferenceModel = doc.getString("inferenceModel"),
              inferenceLatencyMs = doc.getLong("inferenceLatencyMs") ?: 0L,
              privacyMode = doc.getString("privacyMode") ?: "prefer_on_device",
              syncStatus = "synced",
              pinned = doc.getBoolean("pinned") ?: false,
              favorite = doc.getBoolean("favorite") ?: false,
              archived = doc.getBoolean("archived") ?: false,
              createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
              updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
            )
          }.getOrNull()
        }
        onMemories(items)
      }

    regs += db.collection("users").document(uid).collection("reminders")
      .addSnapshotListener { snap, _ ->
        if (snap == null) return@addSnapshotListener
        val items = snap.documents.mapNotNull { doc ->
          runCatching {
            ReminderItem(
              id = doc.id,
              uid = doc.getString("uid") ?: uid,
              memoryId = doc.getString("memoryId"),
              memoryTitle = doc.getString("memoryTitle"),
              memoryThumbnail = doc.getString("memoryThumbnail"),
              title = doc.getString("title") ?: "",
              dueDateIso = doc.getString("dueDateIso") ?: LocalDate.now().toString(),
              dueTime = doc.getString("dueTime"),
              venue = doc.getString("venue"),
              organizer = doc.getString("organizer"),
              category = doc.getString("category") ?: "event",
              priority = doc.getString("priority") ?: "medium",
              status = doc.getString("status") ?: "suggested",
              recurrence = doc.getString("recurrence") ?: "none",
              conflictWithId = doc.getString("conflictWithId"),
              notes = doc.getString("notes"),
              createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
              updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
            )
          }.getOrNull()
        }
        onReminders(items)
      }

    regs += db.collection("users").document(uid).collection("threads")
      .addSnapshotListener { snap, _ ->
        if (snap == null) return@addSnapshotListener
        val items = snap.documents.mapNotNull { doc ->
          runCatching {
            MemoryThread(
              id = doc.id,
              uid = doc.getString("uid") ?: uid,
              title = doc.getString("title") ?: "",
              topic = doc.getString("topic") ?: "",
              summary = doc.getString("summary") ?: "",
              memoryIds = (doc.get("memoryIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
              keyInsights = (doc.get("keyInsights") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
              nextActions = (doc.get("nextActions") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
              autoGenerated = doc.getBoolean("autoGenerated") ?: true,
              inferenceSource = doc.getString("inferenceSource"),
              updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
            )
          }.getOrNull()
        }
        onThreads(items)
      }
    return regs
  }

  suspend fun syncMemoryToCloud(memory: MemoryItem, uid: String?): MemoryItem {
    if (memory.privacyMode == "only_on_device" || uid == null) {
      return memory.copy(syncStatus = if (memory.privacyMode == "only_on_device") "local_only" else "pending")
    }
    val prepared = memory.copy(uid = uid, syncStatus = "synced", updatedAt = System.currentTimeMillis())
    return runCatching {
      db.collection("users").document(uid).collection("memories").document(prepared.id)
        .set(memoryToMap(prepared), SetOptions.merge()).await()
      prepared
    }.getOrElse {
      prepared.copy(syncStatus = "pending")
    }
  }

  suspend fun deleteMemoryFromCloud(id: String, uid: String?) {
    if (uid == null) return
    runCatching {
      db.collection("users").document(uid).collection("memories").document(id).delete().await()
    }
  }

  suspend fun syncReminderToCloud(reminder: ReminderItem, uid: String?): ReminderItem {
    val prepared = reminder.copy(uid = uid ?: reminder.uid, updatedAt = System.currentTimeMillis())
    if (prepared.status == "confirmed" || prepared.status == "snoozed") {
      ReminderScheduler.scheduleReminder(context, prepared)
    } else if (prepared.status == "completed") {
      ReminderScheduler.cancelReminder(context, prepared.id)
    }
    if (uid != null) {
      runCatching {
        db.collection("users").document(uid).collection("reminders").document(prepared.id)
          .set(reminderToMap(prepared), SetOptions.merge()).await()
      }
    }
    return prepared
  }

  suspend fun deleteReminderFromCloud(id: String, uid: String?) {
    ReminderScheduler.cancelReminder(context, id)
    if (uid == null) return
    runCatching {
      db.collection("users").document(uid).collection("reminders").document(id).delete().await()
    }
  }

  suspend fun syncThreadToCloud(thread: MemoryThread, uid: String?): MemoryThread {
    val prepared = thread.copy(uid = uid ?: thread.uid, updatedAt = System.currentTimeMillis())
    if (uid != null) {
      runCatching {
        db.collection("users").document(uid).collection("threads").document(prepared.id)
          .set(threadToMap(prepared), SetOptions.merge()).await()
      }
    }
    return prepared
  }

  suspend fun captureAndUnderstand(
    rawText: String,
    url: String?,
    bitmap: Bitmap?,
    kindHint: String,
    privacyMode: String,
    sourceApp: String?,
    uid: String?,
    remoteConfig: RemoteConfigSnapshot,
  ): Triple<MemoryItem, List<ReminderItem>, BenchmarkEntry> {
    val id = "mem-${UUID.randomUUID()}"
    var localKey: String? = null
    var thumbDataUrl: String? = null
    if (bitmap != null) {
      val (k, t) = localStore.saveLocalImage(id, bitmap)
      localKey = k
      thumbDataUrl = t
    }

    val aiRes = aiEngine.understandCapture(
      rawText = rawText,
      url = url,
      bitmap = bitmap,
      kindHint = kindHint,
      privacyMode = privacyMode,
      remoteConfig = remoteConfig,
    )

    val now = System.currentTimeMillis()
    val mem = MemoryItem(
      id = id,
      uid = uid ?: "local",
      kind = aiRes.kind,
      title = aiRes.title,
      summary = aiRes.summary,
      rawText = rawText,
      extractedText = aiRes.extractedText,
      url = url?.takeIf { it.startsWith("http://") || it.startsWith("https://") },
      sourceApp = sourceApp,
      mediaMimeType = if (bitmap != null) "image/jpeg" else null,
      mediaLocalKey = localKey,
      mediaThumbnail = thumbDataUrl,
      tags = aiRes.tags,
      category = aiRes.category,
      entities = aiRes.entities,
      actionItems = aiRes.actionItems,
      threadTopic = aiRes.threadTopic,
      importance = aiRes.importance,
      inferenceSource = aiRes.inferenceSource,
      inferenceModel = aiRes.modelUsed,
      inferenceLatencyMs = aiRes.latencyMs,
      privacyMode = privacyMode,
      syncStatus = if (privacyMode == "only_on_device") "local_only" else "pending",
      createdAt = now,
      updatedAt = now,
    )

    val syncedMem = syncMemoryToCloud(mem, uid)
    val reminders = aiRes.reminders.map { cand ->
      syncReminderToCloud(
        ReminderItem(
          id = "rem-${UUID.randomUUID()}",
          uid = uid ?: "local",
          memoryId = syncedMem.id,
          memoryTitle = syncedMem.title,
          memoryThumbnail = syncedMem.mediaThumbnail,
          title = cand.title,
          dueDateIso = cand.dueDateIso,
          dueTime = cand.dueTime,
          venue = cand.venue,
          organizer = cand.organizer,
          category = cand.category,
          priority = cand.priority,
          status = "suggested",
          notes = cand.notes,
          createdAt = now,
          updatedAt = now,
        ),
        uid,
      )
    }

    val bench = BenchmarkEntry(
      id = UUID.randomUUID().toString(),
      operation = "capture_understand",
      source = aiRes.inferenceSource,
      modelUsed = aiRes.modelUsed,
      latencyMs = aiRes.latencyMs,
      timestamp = now,
    )
    return Triple(syncedMem, reminders, bench)
  }

  fun reconcileThreads(memories: List<MemoryItem>, existing: List<MemoryThread>, uid: String?): List<MemoryThread> {
    val active = memories.filter { !it.archived }
    val buckets = linkedMapOf<String, MutableList<MemoryItem>>()
    for (m in active) {
      val raw = (m.threadTopic ?: m.category).trim()
      val slug = raw.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
      if (slug.isNotEmpty()) {
        buckets.getOrPut(slug) { mutableListOf() }.add(m)
      }
    }
    val existingMap = existing.associateBy { it.id }
    val out = mutableListOf<MemoryThread>()
    for ((slug, items) in buckets) {
      if (items.size < 2) continue
      val tid = "thread-$slug"
      val prev = existingMap[tid]
      val ids = items.map { it.id }
      val title = items.first().threadTopic ?: items.first().category
      if (prev != null && prev.memoryIds.size == ids.size && prev.memoryIds.containsAll(ids)) {
        out.add(prev)
      } else {
        out.add(
          MemoryThread(
            id = tid,
            uid = uid ?: "local",
            title = title,
            topic = slug,
            summary = prev?.summary ?: "${items.size} connected memories relating to $title.",
            memoryIds = ids,
            keyInsights = prev?.keyInsights?.takeIf { it.isNotEmpty() } ?: items.take(3).map { it.summary },
            nextActions = prev?.nextActions?.takeIf { it.isNotEmpty() }
              ?: items.flatMap { it.actionItems }.distinct().take(4),
            autoGenerated = true,
            inferenceSource = prev?.inferenceSource,
            updatedAt = System.currentTimeMillis(),
          ),
        )
      }
    }
    return out.sortedByDescending { it.updatedAt }
  }

  fun annotateConflicts(reminders: List<ReminderItem>): List<ReminderItem> {
    val active = reminders.filter { it.status != "completed" }
    return reminders.map { rem ->
      if (rem.status == "completed") {
        rem.copy(conflictWithId = null)
      } else {
        val hit = active.firstOrNull {
          it.id != rem.id &&
            it.dueDateIso == rem.dueDateIso &&
            !it.dueTime.isNullOrBlank() &&
            it.dueTime == rem.dueTime
        }
        rem.copy(conflictWithId = hit?.id)
      }
    }
  }

  fun buildDemoBundle(uid: String?): Pair<List<MemoryItem>, List<ReminderItem>> {
    val now = System.currentTimeMillis()
    val owner = uid ?: "local"
    val examDate = LocalDate.now().plusDays(3).toString()
    val billDate = LocalDate.now().plusDays(2).toString()
    val meetupDate = LocalDate.now().plusDays(5).toString()

    val mems = listOf(
      MemoryItem(
        id = "demo-exam-timetable",
        uid = owner,
        kind = "timetable",
        title = "CSC 412 Distributed Systems Exam",
        summary = "Final exam on $examDate at 09:00 in Engineering Hall B. Bring student ID.",
        rawText = "CSC 412 Distributed Systems Exam — $examDate 09:00, Engineering Hall B.",
        extractedText = "EXAM TIMETABLE | CSC 412 Distributed Systems | Date: $examDate | Time: 09:00 | Venue: Engineering Hall B",
        tags = listOf("exam", "university", "timetable", "distributed-systems"),
        category = "Study & Exams",
        entities = ExtractedEntities(
          organizations = listOf("Computer Science Dept"),
          dates = listOf(examDate),
          locations = listOf("Engineering Hall B"),
        ),
        actionItems = listOf("Review Raft consensus & vector clocks", "Pack Student ID"),
        threadTopic = "Semester Finals",
        importance = "high",
        inferenceSource = "on_device",
        inferenceModel = "gemini-nano-4 (on-device)",
        inferenceLatencyMs = 290,
        privacyMode = "prefer_on_device",
        syncStatus = "synced",
        pinned = true,
        favorite = true,
        createdAt = now - 3_600_000L * 2,
        updatedAt = now - 3_600_000L * 2,
      ),
      MemoryItem(
        id = "demo-study-voice",
        uid = owner,
        kind = "voice",
        title = "Voice Note: Finals Revision Plan",
        summary = "Spoken revision reminder covering Raft leader election and meeting at the library at 4 PM.",
        rawText = "Focus on Raft leader election and vector clocks. Meet at Main Library tomorrow at 4pm for 2024 past questions.",
        extractedText = "Focus on Raft leader election and vector clocks. Meet at Main Library tomorrow at 4pm for 2024 past questions.",
        tags = listOf("voice", "study", "revision", "finals"),
        category = "Study & Exams",
        entities = ExtractedEntities(
          dates = listOf(LocalDate.now().plusDays(1).toString()),
          locations = listOf("Main Library"),
        ),
        actionItems = listOf("Solve 2024 past questions at 4 PM"),
        threadTopic = "Semester Finals",
        inferenceSource = "on_device",
        inferenceModel = "gemini-nano-4 (on-device)",
        inferenceLatencyMs = 235,
        privacyMode = "prefer_on_device",
        syncStatus = "synced",
        createdAt = now - 3_600_000L * 4,
        updatedAt = now - 3_600_000L * 4,
      ),
      MemoryItem(
        id = "demo-payment-notice",
        uid = owner,
        kind = "receipt",
        title = "Fiber Internet Renewal — $58.00 Due",
        summary = "Invoice #INV-88412 for $58.00 due by $billDate. Stored privately in Local Vault.",
        rawText = "MetroFiber Internet Invoice #INV-88412. Amount Due: $58.00. Due by $billDate.",
        extractedText = "PAYMENT NOTICE | Fiber Internet Renewal | Amount: $58.00 | Due: $billDate | Ref #INV-88412",
        tags = listOf("receipt", "payment", "bill", "local-vault"),
        category = "Finance",
        entities = ExtractedEntities(
          organizations = listOf("MetroFiber ISP"),
          dates = listOf(billDate),
          amounts = listOf("$58.00"),
        ),
        actionItems = listOf("Pay $58.00 before $billDate"),
        threadTopic = "Monthly Bills",
        importance = "high",
        inferenceSource = "on_device",
        inferenceModel = "gemini-nano-4 (on-device)",
        inferenceLatencyMs = 264,
        privacyMode = "only_on_device",
        syncStatus = "local_only",
        createdAt = now - 3_600_000L * 6,
        updatedAt = now - 3_600_000L * 6,
      ),
      MemoryItem(
        id = "demo-builders-flyer",
        uid = owner,
        kind = "flyer",
        title = "Hybrid AI Builders Meetup Flyer",
        summary = "Developer meetup on $meetupDate at 18:00 at Civic Innovation Hub.",
        rawText = "Hybrid AI Builders Meetup — $meetupDate at 18:00, Civic Innovation Hub.",
        extractedText = "EVENT FLYER | Hybrid AI Builders Meetup | $meetupDate 18:00 | Civic Innovation Hub",
        tags = listOf("event", "meetup", "firebase", "ai"),
        category = "Events",
        entities = ExtractedEntities(
          organizations = listOf("Civic Innovation Hub"),
          dates = listOf(meetupDate),
          locations = listOf("Civic Innovation Hub"),
        ),
        actionItems = listOf("RSVP and prep 2-min live MEMORA demo"),
        threadTopic = "MEMORA Launch",
        inferenceSource = "in_cloud",
        inferenceModel = "gemini-3.5-flash-lite",
        inferenceLatencyMs = 820,
        privacyMode = "prefer_on_device",
        syncStatus = "synced",
        favorite = true,
        createdAt = now - 3_600_000L * 10,
        updatedAt = now - 3_600_000L * 10,
      ),
    )

    val rems = listOf(
      ReminderItem(
        id = "rem-demo-payment",
        uid = owner,
        memoryId = "demo-payment-notice",
        memoryTitle = "Fiber Internet Renewal — $58.00 Due",
        title = "Pay Fiber Internet Invoice ($58.00)",
        dueDateIso = billDate,
        dueTime = "17:00",
        organizer = "MetroFiber ISP",
        category = "payment",
        priority = "high",
        status = "suggested",
        notes = "Detected from saved payment notice (#INV-88412).",
        createdAt = now - 3_600_000L * 6,
        updatedAt = now - 3_600_000L * 6,
      ),
      ReminderItem(
        id = "rem-demo-exam",
        uid = owner,
        memoryId = "demo-exam-timetable",
        memoryTitle = "CSC 412 Distributed Systems Exam",
        title = "CSC 412 Distributed Systems Exam",
        dueDateIso = examDate,
        dueTime = "09:00",
        venue = "Engineering Hall B",
        category = "exam",
        priority = "high",
        status = "confirmed",
        notes = "Bring Student ID.",
        createdAt = now - 3_600_000L * 2,
        updatedAt = now - 3_600_000L * 2,
      ),
      ReminderItem(
        id = "rem-demo-meetup",
        uid = owner,
        memoryId = "demo-builders-flyer",
        memoryTitle = "Hybrid AI Builders Meetup Flyer",
        title = "Hybrid AI Builders Meetup",
        dueDateIso = meetupDate,
        dueTime = "18:00",
        venue = "Civic Innovation Hub",
        category = "event",
        priority = "medium",
        status = "suggested",
        notes = "Detected automatically from saved flyer.",
        createdAt = now - 3_600_000L * 10,
        updatedAt = now - 3_600_000L * 10,
      ),
    )
    return mems to rems
  }

  private fun listFromMap(map: Map<*, *>?, key: String): List<String> {
    return (map?.get(key) as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
  }

  private fun memoryToMap(m: MemoryItem): Map<String, Any?> = buildMap {
    put("id", m.id)
    put("uid", m.uid)
    put("kind", m.kind)
    put("title", m.title)
    put("summary", m.summary)
    put("rawText", m.rawText)
    put("extractedText", m.extractedText)
    m.url?.let { put("url", it) }
    m.sourceApp?.let { put("sourceApp", it) }
    m.mediaMimeType?.let { put("mediaMimeType", it) }
    m.mediaFileName?.let { put("mediaFileName", it) }
    m.mediaLocalKey?.let { put("mediaLocalKey", it) }
    m.mediaThumbnail?.let { put("mediaThumbnail", it) }
    put("tags", m.tags)
    put("category", m.category)
    put(
      "entities",
      mapOf(
        "people" to m.entities.people,
        "organizations" to m.entities.organizations,
        "dates" to m.entities.dates,
        "amounts" to m.entities.amounts,
        "locations" to m.entities.locations,
        "contacts" to m.entities.contacts,
      ),
    )
    put("actionItems", m.actionItems)
    m.personalNote?.let { put("personalNote", it) }
    m.threadId?.let { put("threadId", it) }
    m.threadTopic?.let { put("threadTopic", it) }
    put("importance", m.importance)
    m.language?.let { put("language", it) }
    put("inferenceSource", m.inferenceSource)
    m.inferenceModel?.let { put("inferenceModel", it) }
    put("inferenceLatencyMs", m.inferenceLatencyMs)
    put("privacyMode", m.privacyMode)
    put("syncStatus", m.syncStatus)
    put("pinned", m.pinned)
    put("favorite", m.favorite)
    put("archived", m.archived)
    put("createdAt", m.createdAt)
    put("updatedAt", m.updatedAt)
  }

  private fun reminderToMap(r: ReminderItem): Map<String, Any?> = buildMap {
    put("id", r.id)
    put("uid", r.uid)
    r.memoryId?.let { put("memoryId", it) }
    r.memoryTitle?.let { put("memoryTitle", it) }
    r.memoryThumbnail?.let { put("memoryThumbnail", it) }
    put("title", r.title)
    put("dueDateIso", r.dueDateIso)
    r.dueTime?.let { put("dueTime", it) }
    r.venue?.let { put("venue", it) }
    r.organizer?.let { put("organizer", it) }
    put("category", r.category)
    put("priority", r.priority)
    put("status", r.status)
    put("recurrence", r.recurrence)
    r.conflictWithId?.let { put("conflictWithId", it) }
    r.notes?.let { put("notes", it) }
    put("createdAt", r.createdAt)
    put("updatedAt", r.updatedAt)
  }

  private fun threadToMap(t: MemoryThread): Map<String, Any?> = buildMap {
    put("id", t.id)
    put("uid", t.uid)
    put("title", t.title)
    put("topic", t.topic)
    put("summary", t.summary)
    put("memoryIds", t.memoryIds)
    put("keyInsights", t.keyInsights)
    put("nextActions", t.nextActions)
    put("autoGenerated", t.autoGenerated)
    t.inferenceSource?.let { put("inferenceSource", it) }
    put("updatedAt", t.updatedAt)
  }
}
