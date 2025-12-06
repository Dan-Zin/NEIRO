package com.example.neirotech

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * LiveMonitoringActivity — только UI/видео, работа с BrainBit вынесена в BrainBitController.
 */
class LiveMonitoringActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var brainBitController: BrainBitController
    private var fakeMetrics: Boolean = false
    private var fakeJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)

        val source = intent.getStringExtra(SessionSetupActivity.EXTRA_SOURCE) ?: "n/a"
        fakeMetrics = intent.getBooleanExtra(SessionSetupActivity.EXTRA_FAKE_METRICS, false)
        if (source == "debug") {
            setContentView(R.layout.activity_live_monitoring_debug)
        } else {
            setContentView(R.layout.activity_live_monitoring)
        }

        if (!fakeMetrics && !ConnectionManager.isConnected()) {
            Toast.makeText(
                this,
                "Нет подключенного BrainBit. Подключите в главном экране.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        bindUi(source)

        if (fakeMetrics) {
            startFakeMetrics()
        } else {
            brainBitController = BrainBitController(this)
            brainBitController.start(
                ConnectionManager.getAddress(),
                ConnectionManager.getName(),
                ConnectionManager.getSensorInfo()
            )
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
        if (::brainBitController.isInitialized) brainBitController.stop()
        fakeJob?.cancel()
    }

    // region UI
    private fun bindUi(source: String) {
        val infoView = findViewById<TextView>(R.id.sessionInfo)
        val sessionName = intent.getStringExtra(SessionSetupActivity.EXTRA_NAME) ?: "Сессия"
        val tags = intent.getStringArrayListExtra(SessionSetupActivity.EXTRA_TAGS) ?: arrayListOf()
        val autoSave = intent.getBooleanExtra(SessionSetupActivity.EXTRA_AUTOSAVE, true)
        infoView.text =
            "Сессия: $sessionName • Источник: $source • Метки: ${if (tags.isEmpty()) "нет" else tags.joinToString()} • Автосохранение: ${if (autoSave) "вкл" else "выкл"}"

        if (source != "debug") {
            setupPlayer(source)
            BottomSheetBehavior.from(findViewById(R.id.bottomSheet)).state =
                BottomSheetBehavior.STATE_COLLAPSED
        }

        findViewById<ImageButton?>(R.id.btnStop)?.setOnClickListener {
            startActivity(Intent(this, SessionAnalysisActivity::class.java))
            finish()
        }
        findViewById<ImageButton?>(R.id.btnPause)?.setOnClickListener {
            player?.let { exo ->
                val shouldPlay = !exo.isPlaying
                exo.playWhenReady = shouldPlay
                findViewById<TextView?>(R.id.playerStatus)?.text =
                    if (shouldPlay) "Воспроизведение..." else "Пауза"
            }
        }
        findViewById<Button?>(R.id.btnAddMark)?.setOnClickListener {
            findViewById<TextView?>(R.id.engagementLevel)?.text = "Добавлена метка"
        }
        findViewById<Button?>(R.id.btnSnapshot)?.setOnClickListener {
            findViewById<TextView?>(R.id.engagementLevel)?.text = "Снимок сохранён"
        }
        findViewById<Button?>(R.id.btnStatistics)?.setOnClickListener {
            startActivity(Intent(this, SessionAnalysisActivity::class.java))
        }
        findViewById<Button?>(R.id.btnCalibrate)?.setOnClickListener {
            if (fakeMetrics) {
                Toast.makeText(this, "Фейковые метрики: калибровка не требуется", Toast.LENGTH_SHORT).show()
            } else if (::brainBitController.isInitialized) {
                brainBitController.startCalibrationManual()
            }
        }
    }

    private fun setupPlayer(source: String) {
        val uriString = intent.getStringExtra(SessionSetupActivity.EXTRA_URI)
        val youtube = intent.getStringExtra(SessionSetupActivity.EXTRA_YOUTUBE)
        val playerView = findViewById<PlayerView>(R.id.playerView)
        val status = findViewById<TextView>(R.id.playerStatus)

        when (source) {
            "debug" -> {
                status.text = "Режим отладки BrainBit: видео не используется"
                playerView.player = null
                return
            }
            "youtube" -> {
                status.text = "YouTube: ${youtube ?: "не задана"} (откройте внешним плеером)"
                playerView.player = null
                return
            }
        }

        val uri = uriString?.let { Uri.parse(it) }
        if (uri == null) {
            status.text = "Не выбран файл"
            return
        }

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(uri))
            exo.prepare()
            exo.playWhenReady = true
            status.text = "Воспроизведение..."
        }
    }
    // endregion

    private fun startFakeMetrics() {
        findViewById<TextView>(R.id.signalQuality)?.text = "Фейк-данные • без устройства"
        fakeJob?.cancel()
        fakeJob = lifecycleScope.launch {
            while (isActive) {
                val alpha = Random.nextDouble(20.0, 90.0)
                val beta = Random.nextDouble(10.0, 80.0)
                val index = Random.nextDouble(10.0, 80.0)
                val engagement = Random.nextDouble(20.0, 90.0)
                val volts = List(4) { Random.nextDouble(20.0, 120.0) / 1e6 }
                val names = listOf("O1", "O2", "T3", "T4")
                val chText = volts.mapIndexed { idx, v -> "${names[idx]}: ${"%.6f".format(v)} V" }
                    .joinToString(" • ")
                val peak = volts.maxOrNull() ?: 0.0

                findViewById<TextView>(R.id.waveText)?.text =
                    "Альфа: ${"%.1f".format(alpha)}%\n" +
                        "Бета: ${"%.1f".format(beta)}%\n" +
                        "Индекс: ${"%.1f".format(index)}%"
                findViewById<TextView>(R.id.engagementLevel)?.text =
                    "Внимание: ${"%.1f".format(engagement)}%"
                findViewById<TextView>(R.id.channelMetrics)?.text = "Каналы: $chText"
                findViewById<TextView>(R.id.peaksInfo)?.text = "Пики: ${"%.6f".format(peak)} V"
                findViewById<TextView>(R.id.artifactsInfo)?.text = "Фейковый режим"

                delay(1000)
            }
        }
    }

    companion object {
        private const val TAG = "LiveMonitoringActivity"
    }
}