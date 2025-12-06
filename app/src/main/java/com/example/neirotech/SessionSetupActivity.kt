package com.example.neirotech

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SessionSetupActivity : AppCompatActivity() {
    private var selectedSource: String? = null
    private var selectedUri: Uri? = null
    private var youtubeLink: String? = null

    private val pickLocalVideo =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                takePersistable(it)
                updateSelection("local", it, null)
            }
        }

    private val pickWifiFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                takePersistable(it)
                updateSelection("wifi", it, null)
            }
        }

    private val captureVideo =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) updateSelection("camera", uri, null)
                else Toast.makeText(this, "Видео не получено", Toast.LENGTH_SHORT).show()
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingAction?.invoke()
            } else {
                Toast.makeText(this, "Нет разрешения для медиа", Toast.LENGTH_SHORT).show()
            }
            pendingAction = null
        }

    private var pendingAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        setContentView(R.layout.activity_session_setup)

        val preview = findViewById<TextView>(R.id.videoPreview)
        val sessionName = findViewById<EditText>(R.id.inputSessionName)
        val startButton = findViewById<Button>(R.id.btnStartRecording)
        val autoSave = findViewById<Switch>(R.id.switchAutosave)
        val checkScenes = findViewById<CheckBox>(R.id.checkScenes)
        val checkMusic = findViewById<CheckBox>(R.id.checkMusic)
        val checkText = findViewById<CheckBox>(R.id.checkText)
        val checkTransitions = findViewById<CheckBox>(R.id.checkTransitions)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val fakeMetricsEnabled = prefs.getBoolean(PREF_FAKE_METRICS, false)

        fun updatePreview() {
            val labelUi = when (selectedSource) {
                "local" -> "Локальное видео"
                "wifi" -> "Файл (Wi‑Fi/облако)"
                "camera" -> "Видео с камеры"
                "youtube" -> "YouTube ссылка"
                else -> "Не выбрано"
            }
            val detail = when {
                youtubeLink != null -> youtubeLink
                selectedUri != null -> selectedUri.toString()
                else -> ""
            }
            preview.text = "Выбрано: $labelUi ${if (detail.isNullOrBlank()) "" else "\n$detail"}"
        }

        findViewById<Button>(R.id.btnLocalVideo).setOnClickListener {
            ensureMediaPermission {
                pickLocalVideo.launch(arrayOf("video/*"))
            }
            selectedSource = "local"
        }

        findViewById<Button>(R.id.btnWifiUpload).setOnClickListener {
            ensureMediaPermission {
                pickWifiFile.launch(arrayOf("*/*"))
            }
            selectedSource = "wifi"
        }

        findViewById<Button>(R.id.btnCamera).setOnClickListener {
            ensureCameraPermission {
                val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                captureVideo.launch(intent)
                selectedSource = "camera"
            }
        }

        findViewById<Button>(R.id.btnYoutube).setOnClickListener {
            showYoutubeDialog { link ->
                youtubeLink = link
                selectedUri = null
                selectedSource = "youtube"
                updatePreview()
            }
        }

        startButton.setOnClickListener {
            val name = sessionName.text.toString().ifBlank { "Новая сессия" }
            val source = selectedSource
            if (source == null) {
                Toast.makeText(this, "Выберите источник видео", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (source != "youtube" && selectedUri == null) {
                Toast.makeText(this, "Выберите файл/запишите видео", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (source == "youtube" && youtubeLink.isNullOrBlank()) {
                Toast.makeText(this, "Введите ссылку YouTube", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val tags = arrayListOf<String>().apply {
                if (checkScenes.isChecked) add("Сцены")
                if (checkMusic.isChecked) add("Музыка")
                if (checkText.isChecked) add("Текст/титры")
                if (checkTransitions.isChecked) add("Переходы")
            }

            if (!fakeMetricsEnabled && !ConnectionManager.isConnected()) {
                Toast.makeText(this, "Подключите BrainBit или включите фейковые метрики в настройках", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val launchIntent = Intent(this, LiveMonitoringActivity::class.java).apply {
                putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_SOURCE, source)
                putExtra(EXTRA_URI, selectedUri?.toString())
                putExtra(EXTRA_YOUTUBE, youtubeLink)
                putStringArrayListExtra(EXTRA_TAGS, tags)
                putExtra(EXTRA_AUTOSAVE, autoSave.isChecked)
                putExtra(EXTRA_FAKE_METRICS, fakeMetricsEnabled)
                // пробрасываем выбранное устройство в мониторинг, если есть в текущем intent
                putExtra(EXTRA_DEVICE_NAME, this@SessionSetupActivity.intent.getStringExtra(EXTRA_DEVICE_NAME))
                putExtra(EXTRA_DEVICE_ADDRESS, this@SessionSetupActivity.intent.getStringExtra(EXTRA_DEVICE_ADDRESS))
            }
            startActivity(launchIntent)
        }

        fun refresh() = updatePreview()
        refresh()
    }

    private fun ensureMediaPermission(action: () -> Unit) {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, perm) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(perm)
        }
    }

    private fun ensureCameraPermission(action: () -> Unit) {
        val perm = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, perm) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(perm)
        }
    }

    private fun showYoutubeDialog(onConfirm: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "https://youtu.be/..."
        }
        AlertDialog.Builder(this)
            .setTitle("YouTube ссылка")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val text = input.text.toString().trim()
                if (text.startsWith("http")) {
                    onConfirm(text)
                } else {
                    Toast.makeText(this, "Введите корректную ссылку", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateSelection(type: String, uri: Uri?, link: String?) {
        selectedSource = type
        selectedUri = uri
        youtubeLink = link
        val preview = findViewById<TextView>(R.id.videoPreview)
        val labelUi = when (type) {
            "local" -> "Локальное видео"
            "wifi" -> "Файл (Wi‑Fi/облако)"
            "camera" -> "Видео с камеры"
            else -> "YouTube ссылка"
        }
        val detail = when {
            link != null -> link
            uri != null -> uri.toString()
            else -> ""
        }
        preview.text = "Выбрано: $labelUi ${if (detail.isBlank()) "" else "\n$detail"}"
    }

    private fun takePersistable(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Ignore if not persistable
        }
    }

    companion object {
        const val EXTRA_NAME = "session_name"
        const val EXTRA_SOURCE = "session_source"
        const val EXTRA_URI = "session_uri"
        const val EXTRA_YOUTUBE = "session_youtube"
        const val EXTRA_TAGS = "session_tags"
        const val EXTRA_AUTOSAVE = "session_autosave"
        const val EXTRA_DEVICE_NAME = "session_device_name"
        const val EXTRA_DEVICE_ADDRESS = "session_device_address"
        const val EXTRA_FAKE_METRICS = "session_fake_metrics"
        const val PREF_FAKE_METRICS = "pref_fake_metrics"
    }
}

