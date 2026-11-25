package com.memora.app.data

import android.graphics.Bitmap
import android.os.SystemClock
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.InferenceSource
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class DetectedReminderDraft(
  val title: String,
  val dueDateIso: String,
  val dueTime: String? = null,
  val venue: String? = null,
  val organizer: String? = null,
  val category: String = "event",
  val priority: String = "medium",
  val notes: String? = null,
)

data class CaptureAiResult(
  val kind: String,
  val title: String,
  val summary: String,
  val extractedText: String,
  val category: String,
  val tags: List<String>,
  val entities: ExtractedEntities,
  val actionItems: List<String>,
  val reminders: List<DetectedReminderDraft>,
  val threadTopic: String,
  val importance: String,
  val inferenceSource: String,
  val modelUsed: String,
  val latencyMs: Long,
)

data class ThreadSynthesisAiResult(
  val summary: String,
  val keyInsights: List<String>,
  val nextActions: List<String>,
  val inferenceSource: String,
  val modelUsed: String,
  val latencyMs: Long,
)

data class SideBySideLegResult(
  val status: String, // "ok" | "unavailable" | "offline" | "error"
  val latencyMs: Long? = null,
  val modelUsed: String,
  val detail: String,
  val preview: String? = null,
)

data class SideBySideBenchmarkResult(
  val ranAt: Long,
  val promptLabel: String,
  val onDevice: SideBySideLegResult,
  val inCloud: SideBySideLegResult,
  val verdict: String,
)

/**
 * Core Firebase AI Logic Hybrid Inference Engine for MEMORA Android.
 *
 * Routes prompts through `Firebase.ai(backend = GenerativeBackend.googleAI())`
 * with `OnDeviceConfig(mode = ...)`:
 * - On AICore / Gemini Nano 4 flagships (e.g., Pixel 9/10): runs locally on-device (`ON_DEVICE`).
 * - On everyday devices without AICore (e.g., Samsung Galaxy S10 / A06): seamlessly falls back
 *   to Cloud Gemini (`IN_CLOUD`) with zero code branching or crashes.
 * - When offline without on-device Nano: deterministic local extractor keeps capture & search alive.
 */
@OptIn(PublicPreviewAPI::class)
class HybridAiEngine {

  private fun mapMode(privacyMode: String): InferenceMode = when (privacyMode) {
    "only_on_device" -> InferenceMode.ONLY_ON_DEVICE
    "prefer_in_cloud" -> InferenceMode.PREFER_IN_CLOUD
    else -> InferenceMode.PREFER_ON_DEVICE
  }

  private fun stripJsonFences(raw: String): String {
    val trimmed = raw.trim()
    val fenceRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
    val match = fenceRegex.find(trimmed)
    if (match != null) return match.groupValues[1].trim()
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
  }

  private fun jsonArrayToStrings(arr: JSONArray?, limit: Int = 12): List<String> {
    if (arr == null) return emptyList()
    val out = mutableListOf<String>()
    for (i in 0 until minOf(arr.length(), limit)) {
      val s = arr.optString(i, "").trim()
      if (s.isNotEmpty()) out.add(s)
    }
    return out
  }

  suspend fun understandCapture(
    rawText: String,
    url: String?,
    bitmap: Bitmap?,
    kindHint: String,
    privacyMode: String,
    remoteConfig: RemoteConfigSnapshot,
  ): CaptureAiResult = withContext(Dispatchers.IO) {
    val started = SystemClock.elapsedRealtime()
    val todayIso = LocalDate.now().toString()
    val cloudModel = if (bitmap != null) remoteConfig.cloudModelFast else remoteConfig.cloudModelFast

    val prompt = """
      You are MEMORA, a private personal memory AI. Today is $todayIso.
      Analyze the captured text, URL, or image and return STRICT JSON ONLY (no markdown outside JSON):
      {
        "kind": "screenshot|photo|voice|note|link|document|receipt|flyer|ticket|timetable",
        "title": "Concise human title (max 60 chars)",
        "summary": "Clear 1-2 sentence summary",
        "extractedText": "Full OCR text or transcript",
        "category": "Finance|Events|Study & Exams|Travel|Work & Projects|Reading & Links|Health & Care|Shopping|Personal Vault",
        "tags": ["3 to 6 short lowercase tags"],
        "threadTopic": "2-3 word topic/project name to cluster related memories",
        "importance": "high|normal|low",
        "entities": {
          "people": [],
          "organizations": [],
          "dates": [],
          "amounts": [],
          "locations": [],
          "contacts": []
        },
        "actionItems": ["concrete tasks found"],
        "reminders": [
          {
            "title": "Specific reminder title",
            "dueDateIso": "YYYY-MM-DD",
            "dueTime": "HH:mm or empty",
            "venue": "location or empty",
            "organizer": "org/person or empty",
            "category": "event|exam|payment|travel|meeting|deadline|personal",
            "priority": "high|medium|low",
            "notes": "why this matters"
          }
        ]
      }
      Only include upcoming reminders on or after $todayIso. Never fabricate dates.
      Text: ${rawText.ifBlank { "(none)" }}
      URL: ${url ?: "(none)"}
    """.trimIndent()

    try {
      val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = cloudModel,
        generationConfig = generationConfig {
          temperature = 0.2f
        },
        onDeviceConfig = OnDeviceConfig(mode = mapMode(privacyMode)),
      )

      val response = withTimeout(12_000L) {
        model.generateContent(
          content {
            if (bitmap != null) image(bitmap)
            text(prompt)
          },
        )
      }

      val latencyMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(1L)
      val source = when (response.inferenceSource) {
        InferenceSource.ON_DEVICE -> "on_device"
        InferenceSource.IN_CLOUD -> "in_cloud"
        else -> "in_cloud"
      }
      val modelUsed = if (source == "on_device") "gemini-nano-4 (on-device)" else cloudModel
      val rawOut = response.text ?: throw IllegalStateException("Empty AI output")
      val obj = JSONObject(stripJsonFences(rawOut))
      val ent = obj.optJSONObject("entities")

      val reminders = mutableListOf<DetectedReminderDraft>()
      val remArr = obj.optJSONArray("reminders")
      if (remArr != null) {
        for (i in 0 until remArr.length()) {
          val r = remArr.optJSONObject(i) ?: continue
          val due = r.optString("dueDateIso", "").take(10)
          if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(due)) continue
          if (due < todayIso) continue
          reminders.add(
            DetectedReminderDraft(
              title = r.optString("title", obj.optString("title", "Upcoming Reminder")).trim(),
              dueDateIso = due,
              dueTime = r.optString("dueTime", "").trim().ifBlank { null },
              venue = r.optString("venue", "").trim().ifBlank { null },
              organizer = r.optString("organizer", "").trim().ifBlank { null },
              category = normalizeReminderCategory(r.optString("category", "event")),
              priority = r.optString("priority", "medium").takeIf { it in listOf("high", "medium", "low") } ?: "medium",
              notes = r.optString("notes", "").trim().ifBlank { null },
            ),
          )
        }
      }

      CaptureAiResult(
        kind = normalizeKind(obj.optString("kind", kindHint), kindHint),
        title = obj.optString("title", "Saved Memory").trim().take(80).ifBlank { "Saved Memory" },
        summary = obj.optString("summary", rawText.ifBlank { "Saved in MEMORA." }).trim(),
        extractedText = obj.optString("extractedText", rawText).trim(),
        category = obj.optString("category", "Personal Vault").trim().ifBlank { "Personal Vault" },
        tags = jsonArrayToStrings(obj.optJSONArray("tags"), 6),
        entities = ExtractedEntities(
          people = jsonArrayToStrings(ent?.optJSONArray("people")),
          organizations = jsonArrayToStrings(ent?.optJSONArray("organizations")),
          dates = jsonArrayToStrings(ent?.optJSONArray("dates")),
          amounts = jsonArrayToStrings(ent?.optJSONArray("amounts")),
          locations = jsonArrayToStrings(ent?.optJSONArray("locations")),
          contacts = jsonArrayToStrings(ent?.optJSONArray("contacts")),
        ),
        actionItems = jsonArrayToStrings(obj.optJSONArray("actionItems")),
        reminders = reminders,
        threadTopic = obj.optString("threadTopic", obj.optString("category", "Personal Vault")).trim(),
        importance = obj.optString("importance", "normal").takeIf { it in listOf("high", "normal", "low") } ?: "normal",
        inferenceSource = source,
        modelUsed = modelUsed,
        latencyMs = latencyMs,
      )
    } catch (_: Throwable) {
      offlineDeterministicUnderstand(rawText, url, kindHint, started)
    }
  }

  suspend fun searchMemories(
    query: String,
    memories: List<MemoryItem>,
    privacyMode: String,
    remoteConfig: RemoteConfigSnapshot,
  ): SearchAnswer = withContext(Dispatchers.IO) {
    val started = SystemClock.elapsedRealtime()
    val tokens = query.lowercase()
      .replace(Regex("[^a-z0-9\\s]"), " ")
      .split(Regex("\\s+"))
      .filter { it.length >= 2 }

    val ranked = memories
      .filter { !it.archived }
      .map { m ->
        val hay = buildString {
          append(m.title).append(' ')
          append(m.summary).append(' ')
          append(m.extractedText).append(' ')
          append(m.category).append(' ')
          append(m.tags.joinToString(" ")).append(' ')
          append(m.entities.amounts.joinToString(" ")).append(' ')
          append(m.entities.dates.joinToString(" ")).append(' ')
          append(m.entities.organizations.joinToString(" "))
        }.lowercase()
        var score = 0
        for (t in tokens) if (hay.contains(t)) score += 3
        if (hay.contains(query.lowercase())) score += 6
        if (m.pinned) score += 1
        m to score
      }
      .sortedWith(compareByDescending<Pair<MemoryItem, Int>> { it.second }.thenByDescending { it.first.createdAt })

    val localFallback = {
      val matches = ranked.filter { it.second > 0 }.take(5).map { it.first }
      val elapsed = (SystemClock.elapsedRealtime() - started).coerceAtLeast(3L)
      if (matches.isEmpty()) {
        SearchAnswer(
          query = query,
          answer = "No exact local matches found yet. Try broader keywords or capture a note, flyer, or voice memo first.",
          citations = emptyList(),
          suggestedFollowUps = listOf("Show upcoming deadlines", "Find saved receipts", "Summarize recent captures"),
          source = "offline_heuristic",
          modelUsed = "local-semantic-index",
          latencyMs = elapsed,
        )
      } else {
        val body = matches.mapIndexed { idx, m -> "${idx + 1}. ${m.title} (${m.category}): ${m.summary}" }
          .joinToString("\n\n")
        SearchAnswer(
          query = query,
          answer = "Found ${matches.size} relevant memories in your vault:\n\n$body",
          citations = matches.map { SearchCitation(it.id, it.title, it.summary) },
          suggestedFollowUps = listOf("Turn these into a checklist", "Any deadlines across these?"),
          source = "offline_heuristic",
          modelUsed = "local-semantic-index",
          latencyMs = elapsed,
        )
      }
    }

    val safeContext = ranked
      .take(remoteConfig.maxContextMemories)
      .map { it.first }
      .filter { privacyMode == "only_on_device" || it.privacyMode != "only_on_device" }

    if (safeContext.isEmpty()) return@withContext localFallback()

    try {
      val contextArr = JSONArray()
      for (m in safeContext) {
        contextArr.put(
          JSONObject().apply {
            put("id", m.id)
            put("kind", m.kind)
            put("title", m.title)
            put("category", m.category)
            put("summary", m.summary)
            put("extractedText", m.extractedText.take(400))
            put("tags", JSONArray(m.tags))
            put("dates", JSONArray(m.entities.dates))
            put("amounts", JSONArray(m.entities.amounts))
            put("actionItems", JSONArray(m.actionItems))
          },
        )
      }

      val prompt = """
        You are MEMORA, the user's personal memory assistant.
        Answer using ONLY the saved memories JSON below. Cite exact memory IDs and titles.
        Return STRICT JSON ONLY:
        {
          "answer": "Clear direct answer",
          "citations": [{"memoryId": "id", "title": "title", "excerpt": "relevant snippet"}],
          "suggestedFollowUps": ["2 or 3 natural follow-up questions"]
        }
        Question: "$query"
        Memories: $contextArr
      """.trimIndent()

      val chosenCloud = if (query.length > 75 || safeContext.size > 10) {
        remoteConfig.cloudModelPro
      } else {
        remoteConfig.cloudModelFast
      }

      val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = chosenCloud,
        generationConfig = generationConfig { temperature = 0.25f },
        onDeviceConfig = OnDeviceConfig(mode = mapMode(privacyMode)),
      )
      val response = withTimeout(12_000L) { model.generateContent(prompt) }
      val latencyMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(1L)
      val source = if (response.inferenceSource == InferenceSource.ON_DEVICE) "on_device" else "in_cloud"
      val modelUsed = if (source == "on_device") "gemini-nano-4 (on-device)" else chosenCloud

      val obj = JSONObject(stripJsonFences(response.text ?: "{}"))
      val validIds = safeContext.map { it.id }.toSet()
      val cits = mutableListOf<SearchCitation>()
      val citArr = obj.optJSONArray("citations")
      if (citArr != null) {
        for (i in 0 until citArr.length()) {
          val c = citArr.optJSONObject(i) ?: continue
          val mid = c.optString("memoryId", "")
          if (mid in validIds) {
            cits.add(SearchCitation(mid, c.optString("title", ""), c.optString("excerpt", "")))
          }
        }
      }

      SearchAnswer(
        query = query,
        answer = obj.optString("answer", "Here is what I found in your saved memories."),
        citations = cits,
        suggestedFollowUps = jsonArrayToStrings(obj.optJSONArray("suggestedFollowUps"), 3),
        source = source,
        modelUsed = modelUsed,
        latencyMs = latencyMs,
      )
    } catch (_: Throwable) {
      localFallback()
    }
  }

  suspend fun synthesizeThread(
    title: String,
    memories: List<MemoryItem>,
    privacyMode: String,
    remoteConfig: RemoteConfigSnapshot,
  ): ThreadSynthesisAiResult = withContext(Dispatchers.IO) {
    val started = SystemClock.elapsedRealtime()
    try {
      val itemsJson = JSONArray()
      for (m in memories) {
        itemsJson.put(
          JSONObject().apply {
            put("kind", m.kind)
            put("title", m.title)
            put("summary", m.summary)
            put("actionItems", JSONArray(m.actionItems))
          },
        )
      }
      val prompt = """
        Synthesize the connected MEMORA Memory Thread "$title". Return STRICT JSON ONLY:
        {
          "summary": "2-3 sentence synthesis connecting all memories",
          "keyInsights": ["3 to 4 key connections"],
          "nextActions": ["3 to 4 concrete next steps"]
        }
        Memories: $itemsJson
      """.trimIndent()

      val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = remoteConfig.cloudModelPro,
        generationConfig = generationConfig { temperature = 0.3f },
        onDeviceConfig = OnDeviceConfig(mode = mapMode(privacyMode)),
      )
      val response = withTimeout(12_000L) { model.generateContent(prompt) }
      val latencyMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(1L)
      val source = if (response.inferenceSource == InferenceSource.ON_DEVICE) "on_device" else "in_cloud"
      val obj = JSONObject(stripJsonFences(response.text ?: "{}"))
      ThreadSynthesisAiResult(
        summary = obj.optString("summary", "Connected thread of ${memories.size} memories about $title."),
        keyInsights = jsonArrayToStrings(obj.optJSONArray("keyInsights"), 4),
        nextActions = jsonArrayToStrings(obj.optJSONArray("nextActions"), 4),
        inferenceSource = source,
        modelUsed = if (source == "on_device") "gemini-nano-4 (on-device)" else remoteConfig.cloudModelPro,
        latencyMs = latencyMs,
      )
    } catch (_: Throwable) {
      ThreadSynthesisAiResult(
        summary = "Connected thread spanning ${memories.size} memories around $title.",
        keyInsights = memories.take(3).map { "${it.title}: ${it.summary}" },
        nextActions = memories.flatMap { it.actionItems }.distinct().take(4),
        inferenceSource = "offline_heuristic",
        modelUsed = "local-thread-aggregator",
        latencyMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(3L),
      )
    }
  }

  private fun offlineDeterministicUnderstand(
    rawText: String,
    url: String?,
    kindHint: String,
    started: Long,
  ): CaptureAiResult {
    val combined = listOfNotNull(rawText.takeIf { it.isNotBlank() }, url).joinToString("\n")
    val lower = combined.lowercase()
    val kind = when {
      lower.contains("receipt") || lower.contains("invoice") -> "receipt"
      lower.contains("exam") || lower.contains("timetable") -> "timetable"
      lower.contains("ticket") || lower.contains("flight") -> "ticket"
      lower.contains("flyer") || lower.contains("meetup") || lower.contains("summit") -> "flyer"
      !url.isNullOrBlank() -> "link"
      else -> kindHint
    }

    val amountRegex = Regex("(?:[\\$€£₦]|USD|NGN|EUR|GBP)\\s?\\d[\\d,]*(?:\\.\\d{2})?", RegexOption.IGNORE_CASE)
    val amounts = amountRegex.findAll(combined).map { it.value }.distinct().take(5).toList()

    val isoDateRegex = Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b")
    val dates = isoDateRegex.findAll(combined).map { it.value }.distinct().take(5).toList()
    val timeRegex = Regex("\\b\\d{1,2}:\\d{2}(?:\\s?(?:AM|PM))?\\b", RegexOption.IGNORE_CASE)
    val firstTime = timeRegex.find(combined)?.value

    val title = combined.lineSequence().map { it.trim() }.firstOrNull { it.length > 2 }
      ?.take(65) ?: "Captured $kind"
    val summary = if (combined.length > 160) combined.take(157) + "..." else combined.ifBlank {
      "Saved privately to your offline MEMORA vault."
    }

    val todayIso = LocalDate.now().toString()
    val reminders = mutableListOf<DetectedReminderDraft>()
    for (d in dates) {
      if (d >= todayIso) {
        reminders.add(
          DetectedReminderDraft(
            title = title,
            dueDateIso = d,
            dueTime = firstTime,
            category = when (kind) {
              "timetable" -> "exam"
              "receipt" -> "payment"
              "ticket" -> "travel"
              else -> "event"
            },
            priority = if (kind in listOf("receipt", "timetable")) "high" else "medium",
            notes = "Detected offline by MEMORA local date parser.",
          ),
        )
      }
    }
    if (reminders.isEmpty() && Regex("\\b(tomorrow|deadline|due|remind|exam|meeting|flight)\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined)) {
      reminders.add(
        DetectedReminderDraft(
          title = title,
          dueDateIso = LocalDate.now().plusDays(1).toString(),
          dueTime = firstTime ?: "09:00",
          category = if (lower.contains("pay") || lower.contains("bill")) "payment" else "personal",
          priority = "medium",
          notes = "Detected offline — tap Confirm to schedule local notification.",
        ),
      )
    }

    val category = when (kind) {
      "receipt" -> "Finance"
      "timetable" -> "Study & Exams"
      "ticket" -> "Travel"
      "flyer" -> "Events"
      "voice" -> "Voice Notes"
      "link" -> "Reading & Links"
      else -> "Personal Vault"
    }

    val words = lower.replace(Regex("[^a-z0-9\\s]"), " ")
      .split(Regex("\\s+"))
      .filter { it.length >= 4 && it !in setOf("https", "about", "there", "their", "would", "could", "tomorrow") }
      .groupingBy { it }.eachCount()
      .entries.sortedByDescending { it.value }
      .take(4).map { it.key }

    return CaptureAiResult(
      kind = kind,
      title = title,
      summary = summary,
      extractedText = combined,
      category = category,
      tags = (listOf(kind) + words).distinct().take(5),
      entities = ExtractedEntities(dates = dates, amounts = amounts),
      actionItems = if (reminders.isNotEmpty()) listOf("Follow up: $title") else emptyList(),
      reminders = reminders,
      threadTopic = words.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: category,
      importance = if (reminders.isNotEmpty()) "high" else "normal",
      inferenceSource = "offline_heuristic",
      modelUsed = "local-offline-parser",
      latencyMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(4L),
    )
  }

  private fun normalizeKind(value: String, fallback: String): String {
    val allowed = setOf("screenshot", "photo", "voice", "note", "link", "document", "receipt", "flyer", "ticket", "timetable")
    val v = value.lowercase().trim()
    return if (v in allowed) v else fallback
  }

  private fun normalizeReminderCategory(value: String): String {
    val allowed = listOf("event", "exam", "payment", "travel", "meeting", "deadline", "personal")
    val v = value.lowercase().trim()
    return allowed.firstOrNull { v.contains(it) } ?: "event"
  }

  private fun summarizeBenchmarkPreview(raw: String): String {
    return runCatching {
      val obj = JSONObject(stripJsonFences(raw))
      listOf(
        obj.optString("title", "").takeIf { it.isNotBlank() },
        obj.optString("dueDateIso", "").takeIf { it.isNotBlank() }?.let { "Due $it" },
        obj.optString("amount", "").takeIf { it.isNotBlank() },
      ).filterNotNull().joinToString(" · ").take(90)
    }.getOrNull().orEmpty().ifBlank {
      raw.replace(Regex("\\s+"), " ").trim().take(90)
    }
  }

  /**
   * Runs the exact same structured flyer-extraction prompt back-to-back through
   * ONLY_ON_DEVICE (Android AICore / Gemini Nano 4) and ONLY_IN_CLOUD (Cloud Gemini)
   * via Firebase AI Logic to measure real latency on this exact device.
   */
  suspend fun runSideBySideBenchmark(
    remoteConfig: RemoteConfigSnapshot,
    isOnline: Boolean,
  ): SideBySideBenchmarkResult = withContext(Dispatchers.IO) {
    val samplePrompt = """
      Extract structured memory JSON from this captured flyer notice. Return STRICT JSON ONLY:
      {"title":"...","category":"...","dueDateIso":"YYYY-MM-DD","amount":"..."}.
      Notice: "AI Builders Summit 2026 — Nov 28, 2026 at 10:00 AM, Landmark Centre. Early-bird pass $45 due by Nov 27, 2026."
    """.trimIndent()

    // 1. On-Device Leg (ONLY_ON_DEVICE)
    val onDeviceStart = SystemClock.elapsedRealtime()
    val onDeviceLeg = try {
      val localModel = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = remoteConfig.cloudModelFast,
        generationConfig = generationConfig { temperature = 0.1f },
        onDeviceConfig = OnDeviceConfig(mode = InferenceMode.ONLY_ON_DEVICE),
      )
      val response = localModel.generateContent(samplePrompt)
      val latencyMs = (SystemClock.elapsedRealtime() - onDeviceStart).coerceAtLeast(1L)
      val preview = summarizeBenchmarkPreview(response.text.orEmpty())
      SideBySideLegResult(
        status = "ok",
        latencyMs = latencyMs,
        modelUsed = "gemini-nano-4 (on-device)",
        detail = "0 KB uploaded · \$0.00 cost",
        preview = preview,
      )
    } catch (e: Exception) {
      val msg = e.message.orEmpty()
      SideBySideLegResult(
        status = "unavailable",
        latencyMs = null,
        modelUsed = "gemini-nano-4 (on-device)",
        detail = if (msg.contains("AICore", ignoreCase = true) ||
          msg.contains("available", ignoreCase = true) ||
          msg.contains("device", ignoreCase = true) ||
          msg.isBlank()
        ) {
          "AICore / Gemini Nano unavailable on this phone — PREFER_ON_DEVICE auto-routes to Cloud."
        } else {
          msg.take(110)
        },
      )
    }

    // 2. Cloud Leg (ONLY_IN_CLOUD)
    val cloudLeg = if (!isOnline) {
      SideBySideLegResult(
        status = "offline",
        latencyMs = null,
        modelUsed = remoteConfig.cloudModelFast,
        detail = "Offline / Airplane Mode — cloud unreachable.",
      )
    } else {
      val cloudStart = SystemClock.elapsedRealtime()
      try {
        val cloudModel = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
          modelName = remoteConfig.cloudModelFast,
          generationConfig = generationConfig { temperature = 0.1f },
          onDeviceConfig = OnDeviceConfig(mode = InferenceMode.ONLY_IN_CLOUD),
        )
        val response = cloudModel.generateContent(samplePrompt)
        val latencyMs = (SystemClock.elapsedRealtime() - cloudStart).coerceAtLeast(1L)
        val preview = summarizeBenchmarkPreview(response.text.orEmpty())
        SideBySideLegResult(
          status = "ok",
          latencyMs = latencyMs,
          modelUsed = remoteConfig.cloudModelFast,
          detail = "Firebase AI Logic · App Check secured",
          preview = preview,
        )
      } catch (e: Exception) {
        SideBySideLegResult(
          status = "error",
          latencyMs = null,
          modelUsed = remoteConfig.cloudModelFast,
          detail = e.message?.take(110) ?: "Cloud inference failed.",
        )
      }
    }

    val verdict = when {
      onDeviceLeg.status == "ok" && cloudLeg.status == "ok" &&
        onDeviceLeg.latencyMs != null && cloudLeg.latencyMs != null -> {
        if (onDeviceLeg.latencyMs <= cloudLeg.latencyMs) {
          val ratio = String.format(java.util.Locale.US, "%.1f", cloudLeg.latencyMs.toDouble() / onDeviceLeg.latencyMs.toDouble())
          "On-Device ran ${ratio}× faster (${onDeviceLeg.latencyMs}ms vs ${cloudLeg.latencyMs}ms) · 0 KB uploaded · \$0 cost."
        } else {
          val ratio = String.format(java.util.Locale.US, "%.1f", onDeviceLeg.latencyMs.toDouble() / cloudLeg.latencyMs.toDouble())
          "Cloud ran ${ratio}× faster (${cloudLeg.latencyMs}ms vs ${onDeviceLeg.latencyMs}ms); On-Device stayed 100% private at \$0."
        }
      }
      onDeviceLeg.status == "ok" && cloudLeg.status == "offline" ->
        "Airplane Mode Verified: On-Device succeeded in ${onDeviceLeg.latencyMs}ms while Cloud was unreachable."
      cloudLeg.status == "ok" && onDeviceLeg.status != "ok" ->
        "Cloud Fallback Verified: ${remoteConfig.cloudModelFast} completed in ${cloudLeg.latencyMs}ms on non-AICore hardware."
      else ->
        "Offline without local AICore Nano — MEMORA deterministic local parser keeps capture & reminders working."
    }

    SideBySideBenchmarkResult(
      ranAt = System.currentTimeMillis(),
      promptLabel = "Structured Event Flyer + Deadline Extraction",
      onDevice = onDeviceLeg,
      inCloud = cloudLeg,
      verdict = verdict,
    )
  }
}
