package com.memora.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.memora.app.ui.MemoraRootScreen
import com.memora.app.ui.MemoraTab
import com.memora.app.ui.MemoraViewModel
import com.memora.app.ui.theme.MEMORATheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
  private val vm: MemoraViewModel by viewModels()

  private var capturedPhoto by mutableStateOf<Bitmap?>(null)
  private var voiceCaptureText by mutableStateOf("")
  private var voiceSearchText by mutableStateOf("")
  private var pendingPhotoUri: Uri? = null

  private val galleryPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) {
      lifecycleScope.launch { capturedPhoto = vm.decodeBitmapFromUri(uri) }
    }
  }

  private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
    val uri = pendingPhotoUri
    if (saved && uri != null) {
      lifecycleScope.launch { capturedPhoto = vm.decodeBitmapFromUri(uri) }
    }
  }

  private val cameraPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
    if (ok) openCameraCapture()
  }

  private val notifPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

  private val mediaPermLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
    // Images are the core permission; audio/video are optional extras.
    val imagesGranted = results[vm.mediaIndexer.requiredPermission()] == true ||
      vm.mediaIndexer.hasMediaPermission()
    vm.onMediaPermissionResult(imagesGranted)
  }

  private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    if (uri != null) vm.onFolderConnected(uri)
  }

  private val voiceCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
    val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
    if (!spoken.isNullOrBlank()) voiceCaptureText = spoken
  }

  private val voiceSearchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
    val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
    if (!spoken.isNullOrBlank()) {
      voiceSearchText = spoken
      vm.runSearch(spoken)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    requestNotificationPermissionIfNeeded()
    consumeLaunchIntent(intent)

    setContent {
      val uiState by vm.ui.collectAsStateWithLifecycle()
      MEMORATheme(darkTheme = uiState.darkMode) {
        MemoraRootScreen(
          viewModel = vm,
          pickedBitmap = capturedPhoto,
          voiceCaptureDraft = voiceCaptureText,
          voiceSearchDraft = voiceSearchText,
          onLaunchCamera = ::triggerCamera,
          onLaunchGallery = { galleryPicker.launch("image/*") },
          onLaunchVoiceCapture = { launchSpeechRecognizer(forSearch = false) },
          onLaunchVoiceSearch = { launchSpeechRecognizer(forSearch = true) },
          onRequestAutoIndexToggle = { enable ->
            if (enable && !vm.mediaIndexer.hasMediaPermission()) {
              mediaPermLauncher.launch(vm.fileLibrary.requiredPermissions())
            } else {
              vm.setAutoIndexEnabled(enable)
            }
          },
          onConnectFolder = { folderPicker.launch(null) },
          onClearPickedMedia = {
            capturedPhoto = null
            voiceCaptureText = ""
          },
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    vm.onAppResumed()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    consumeLaunchIntent(intent)
  }

  private fun consumeLaunchIntent(launchIntent: Intent?) {
    if (launchIntent == null) return

    when (launchIntent.getStringExtra("open_tab")) {
      "search" -> vm.selectTab(MemoraTab.SEARCH)
      "threads" -> vm.selectTab(MemoraTab.THREADS)
      "reminders" -> vm.selectTab(MemoraTab.REMINDERS)
      "vault" -> vm.selectTab(MemoraTab.VAULT)
    }
    if (launchIntent.getBooleanExtra("open_capture", false)) {
      vm.openCapture(true)
    }

    if (launchIntent.action == Intent.ACTION_SEND) {
      val mime = launchIntent.type.orEmpty()
      if (mime.startsWith("text/")) {
        launchIntent.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.handleSharedText(it) }
      } else if (mime.startsWith("image/")) {
        @Suppress("DEPRECATION")
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          launchIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
          launchIntent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        uri?.let { vm.handleSharedImageUri(it) }
      }
    }
  }

  private fun triggerCamera() {
    val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
      PackageManager.PERMISSION_GRANTED
    if (granted) openCameraCapture() else cameraPermLauncher.launch(Manifest.permission.CAMERA)
  }

  private fun openCameraCapture() {
    val dir = File(cacheDir, "camera").apply { mkdirs() }
    val photoFile = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
    pendingPhotoUri = uri
    cameraLauncher.launch(uri)
  }

  private fun launchSpeechRecognizer(forSearch: Boolean) {
    val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(
        RecognizerIntent.EXTRA_PROMPT,
        if (forSearch) "Ask MEMORA anything you saved" else "Speak your memory or reminder",
      )
    }
    runCatching {
      if (forSearch) voiceSearchLauncher.launch(speechIntent) else voiceCaptureLauncher.launch(speechIntent)
    }
  }

  private fun requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }
}
