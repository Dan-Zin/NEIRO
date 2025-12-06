package com.example.neirotech

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import com.neurotech.emstartifcats.ArtifactDetectSetting
import com.neurotech.emstartifcats.EmotionalMath
import com.neurotech.emstartifcats.MathLibSetting
import com.neurotech.emstartifcats.MentalAndSpectralSetting
import com.neurotech.emstartifcats.RawChannels
import com.neurotech.emstartifcats.ShortArtifactDetectSetting
import com.neurosdk2.neuro.BrainBit
import com.neurosdk2.neuro.Scanner
import com.neurosdk2.neuro.types.BrainBitResistData
import com.neurosdk2.neuro.types.BrainBitSignalData
import com.neurosdk2.neuro.types.SensorCommand
import com.neurosdk2.neuro.types.SensorFamily
import com.neurosdk2.neuro.types.SensorInfo
import com.neurosdk2.neuro.types.SensorState
import com.neurosdk2.neuro.Sensor
import com.neurosdk2.neuro.interfaces.BrainBitSignalDataReceived
import com.neurosdk2.neuro.interfaces.BrainBitResistDataReceived
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * LiveMonitoringActivity — экран мониторинга EEG данных в реальном времени.
 * 
 * Использует BrainBit SDK2 (neurosdk2) для работы с устройством:
 * - Scanner для поиска и подключения к устройству
 * - BrainBit sensor для получения сигнала и сопротивления
 * - EmotionalMath для анализа данных EEG
 * 
 * Поддерживает:
 * - Работу с реальным устройством BrainBit
 * - Режим фейковых метрик для отладки
 * - Воспроизведение видео (локальное/YouTube)
 */
class LiveMonitoringActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LiveMonitoringActivity"
        private const val SAMPLING_FREQUENCY = 250 // BrainBit sampling frequency Hz
        private const val CALIBRATION_LENGTH_SEC = 6
        private const val RESISTANCE_CHECK_INTERVAL_MS = 30_000L
        private const val RESISTANCE_MEASUREMENT_DURATION_MS = 3_000L
    }

    // Video player
    private var player: ExoPlayer? = null

    // SDK2 components
    private var scanner: Scanner? = null
    private var brainBitSensor: BrainBit? = null

    // MathLib for EEG analysis
    private var emotionalMath: EmotionalMath? = null

    // Session state
    private var fakeMetrics: Boolean = false
    private var fakeJob: Job? = null
    private var resistanceJob: Job? = null
    private var isResistanceMode = false
    private var calibrationComplete = false
    private var dataPacketsCount = 0
    
    // Сглаживание значений (Exponential Moving Average)
    // Коэффициент: 0.1 = очень плавно, 0.3 = умеренно, 0.5 = быстро реагирует
    private val SMOOTHING_FACTOR = 0.15  // Чем меньше - тем плавнее
    private var smoothedAlpha = 0.0
    private var smoothedBeta = 0.0
    private var smoothedTheta = 0.0
    private var smoothedAttention = 0.0
    private var smoothedRelaxation = 0.0
    private var isFirstReading = true
    
    // Контроль частоты обновления UI (троттлинг)
    private val UI_UPDATE_INTERVAL_MS = 1000L  // Обновлять UI раз в секунду
    private var lastUiUpdateTime = 0L

    // UI
    private var signalQualityView: TextView? = null
    private var waveTextView: TextView? = null
    private var engagementLevelView: TextView? = null
    private var channelMetricsView: TextView? = null
    private var peaksInfoView: TextView? = null
    private var artifactsInfoView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)

        val source = intent.getStringExtra(SessionSetupActivity.EXTRA_SOURCE) ?: "n/a"
        fakeMetrics = intent.getBooleanExtra(SessionSetupActivity.EXTRA_FAKE_METRICS, false)

        // Выбор layout в зависимости от источника
        if (source == "debug") {
            setContentView(R.layout.activity_live_monitoring_debug)
        } else {
            setContentView(R.layout.activity_live_monitoring)
        }

        // Проверка подключения если не фейк режим
        if (!fakeMetrics && !ConnectionManager.isConnected()) {
            Toast.makeText(
                this,
                "Нет подключенного BrainBit. Подключите в главном экране.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        initViews()
        setupUi(source)

        if (fakeMetrics) {
            startFakeMetricsGeneration()
        } else {
            startBrainBitMonitoring()
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
        stopBrainBitMonitoring()
        stopFakeMetrics()
    }

    // region Initialization

    private fun initViews() {
        signalQualityView = findViewById(R.id.signalQuality)
        waveTextView = findViewById(R.id.waveText)
        engagementLevelView = findViewById(R.id.engagementLevel)
        channelMetricsView = findViewById(R.id.channelMetrics)
        peaksInfoView = findViewById(R.id.peaksInfo)
        artifactsInfoView = findViewById(R.id.artifactsInfo)
    }

    private fun setupUi(source: String) {
        // Session info header
        val sessionInfo = buildSessionInfoText()
        findViewById<TextView>(R.id.sessionInfo)?.text = sessionInfo

        // Setup video player if not debug mode
        if (source != "debug") {
            setupVideoPlayer(source)
            BottomSheetBehavior.from(findViewById(R.id.bottomSheet)).state =
                BottomSheetBehavior.STATE_COLLAPSED
        }

        // Control buttons
        setupControlButtons()
    }

    private fun buildSessionInfoText(): String {
        val sessionName = intent.getStringExtra(SessionSetupActivity.EXTRA_NAME) ?: "Сессия"
        val tags = intent.getStringArrayListExtra(SessionSetupActivity.EXTRA_TAGS) ?: arrayListOf()
        val autoSave = intent.getBooleanExtra(SessionSetupActivity.EXTRA_AUTOSAVE, true)
        val source = intent.getStringExtra(SessionSetupActivity.EXTRA_SOURCE) ?: "n/a"

        return buildString {
            append("Сессия: $sessionName")
            append(" • Источник: $source")
            append(" • Метки: ${if (tags.isEmpty()) "нет" else tags.joinToString()}")
            append(" • Автосохранение: ${if (autoSave) "вкл" else "выкл"}")
        }
    }

    private fun setupControlButtons() {
        // Stop button
        findViewById<ImageButton?>(R.id.btnStop)?.setOnClickListener {
            navigateToAnalysis()
        }

        // Pause button
        findViewById<ImageButton?>(R.id.btnPause)?.setOnClickListener {
            togglePlayback()
        }

        // Add mark button
        findViewById<Button?>(R.id.btnAddMark)?.setOnClickListener {
            addSessionMark()
        }

        // Snapshot button  
        findViewById<Button?>(R.id.btnSnapshot)?.setOnClickListener {
            saveSnapshot()
        }

        // Statistics button
        findViewById<Button?>(R.id.btnStatistics)?.setOnClickListener {
            navigateToAnalysis()
        }

        // Calibrate button
        findViewById<Button?>(R.id.btnCalibrate)?.setOnClickListener {
            startCalibration()
        }
    }

    // endregion

    // region Video Player

    private fun setupVideoPlayer(source: String) {
        val uriString = intent.getStringExtra(SessionSetupActivity.EXTRA_URI)
        val youtubeUrl = intent.getStringExtra(SessionSetupActivity.EXTRA_YOUTUBE)
        val playerView = findViewById<PlayerView>(R.id.playerView)
        val statusView = findViewById<TextView>(R.id.playerStatus)

        when (source) {
            "debug" -> {
                statusView?.text = "Режим отладки BrainBit: видео не используется"
                playerView?.player = null
            }
            "youtube" -> {
                statusView?.text = "YouTube: ${youtubeUrl ?: "не задана"} (откройте внешним плеером)"
                playerView?.player = null
            }
            else -> {
        val uri = uriString?.let { Uri.parse(it) }
        if (uri == null) {
                    statusView?.text = "Не выбран файл"
            return
                }
                initializeExoPlayer(playerView, uri, statusView)
            }
        }
        }

    private fun initializeExoPlayer(playerView: PlayerView?, uri: Uri, statusView: TextView?) {
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView?.player = exo
            exo.setMediaItem(MediaItem.fromUri(uri))
            exo.prepare()
            exo.playWhenReady = true
            statusView?.text = "Воспроизведение..."
        }
    }

    private fun togglePlayback() {
        player?.let { exo ->
            val shouldPlay = !exo.isPlaying
            exo.playWhenReady = shouldPlay
            findViewById<TextView?>(R.id.playerStatus)?.text =
                if (shouldPlay) "Воспроизведение..." else "Пауза"
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    // endregion

    // region BrainBit SDK2 Integration

    /**
     * Запускает мониторинг данных с BrainBit устройства.
     * Использует сохранённый SensorInfo из ConnectionManager для быстрого подключения.
     */
    private fun startBrainBitMonitoring() {
        val address = ConnectionManager.getAddress()
        val name = ConnectionManager.getName()
        val savedInfo = ConnectionManager.getSensorInfo()

        Log.d(TAG, "Starting BrainBit monitoring: $name ($address)")

        lifecycleScope.launch(Dispatchers.IO) {
            if (address.isNullOrBlank()) {
                showError("Нет адреса устройства")
                return@launch
            }

            // Попытка использовать сохранённый SensorInfo
            if (savedInfo != null && savedInfo.address.equals(address, ignoreCase = true)) {
                if (connectUsingSensorInfo(savedInfo)) return@launch
            }

            // Полное сканирование и подключение
            scanAndConnect(address, name)
        }
    }

    /**
     * Подключение к устройству используя сохранённый SensorInfo.
     * Согласно документации SDK2, createSensor - блокирующий метод.
     */
    private suspend fun connectUsingSensorInfo(info: SensorInfo): Boolean {
        return try {
            Log.d(TAG, "Attempting direct connection using saved SensorInfo")
            val sc = Scanner(info.sensFamily)
            scanner = sc
            sc.start()
            delay(150)

            val sensor = sc.createSensor(info) as? BrainBit
            if (sensor != null) {
                brainBitSensor = sensor
                setupBrainBitSensor(sensor)
                true
            } else {
                sc.stop()
                sc.close()
                scanner = null
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct connection failed: ${e.message}", e)
            false
        }
    }

    /**
     * Сканирование и подключение к устройству по адресу.
     * Согласно документации SDK2:
     * 1. Создаём Scanner с указанием SensorFamily
     * 2. Подписываемся на sensorsChanged callback
     * 3. Запускаем сканирование через start()
     * 4. При нахождении устройства создаём sensor через createSensor()
     */
    private suspend fun scanAndConnect(address: String, name: String?) {
        var connected = false

        try {
            val sc = Scanner(SensorFamily.SensorLEBrainBit)
            scanner = sc

            // Подписка на найденные устройства (согласно документации SDK2)
            sc.sensorsChanged = Scanner.ScannerCallback { _, sensors ->
                sensors.forEach { sensorInfo ->
                    Log.d(TAG, "Found sensor: ${sensorInfo.name} (${sensorInfo.address})")
                }
            }

            sc.start()
            Log.d(TAG, "Scanner started for SensorLEBrainBit")

            // Опрос найденных устройств
            repeat(8) { iteration ->
                if (connected) return@repeat
                delay(300)

                val sensors = sc.sensors
                val foundSensor = sensors?.firstOrNull { 
                    it.address.equals(address, ignoreCase = true) 
                }

                if (foundSensor != null) {
                    Log.d(TAG, "Target device found: ${foundSensor.name}")
                    connected = tryCreateSensor(sc, foundSensor)
                }
            }

            sc.stop()
            sc.sensorsChanged = null

            if (!connected) {
                sc.close()
                scanner = null
                showError("BrainBit не найден${if (!name.isNullOrBlank()) " ($name)" else ""}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Scan error: ${e.message}", e)
            showError("Ошибка сканирования: ${e.message}")
        }
    }

    /**
     * Создание сенсора из найденного устройства.
     * Согласно документации SDK2, createSensor автоматически подключается к устройству.
     */
    @Synchronized
    private fun tryCreateSensor(sc: Scanner, info: SensorInfo): Boolean {
        if (brainBitSensor != null) return true

        return try {
            Log.d(TAG, "Creating BrainBit sensor...")
            val sensor = sc.createSensor(info) as? BrainBit
                ?: return false.also { Log.e(TAG, "Cannot cast to BrainBit") }

            brainBitSensor = sensor
            setupBrainBitSensor(sensor)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create sensor: ${e.message}", e)
            runOnUiThread {
                signalQualityView?.text = "Ошибка: ${e.message}"
            }
            false
        }
    }

    /**
     * Настройка сенсора BrainBit.
     * Согласно документации SDK2:
     * - Подписываемся на brainBitSignalDataReceived для получения сигнала
     * - Подписываемся на brainBitResistDataReceived для получения сопротивления
     * - Выполняем execCommand(StartSignal) для начала получения данных
     */
    private fun setupBrainBitSensor(sensor: BrainBit) {
        initEmotionalMath()

        try {
            // Подписка на данные сигнала
            sensor.brainBitSignalDataReceived = BrainBitSignalDataReceived { data ->
                if (data != null && data.isNotEmpty()) {
                    handleSignalData(data)
                }
            }

            // Подписка на данные сопротивления
            sensor.brainBitResistDataReceived = BrainBitResistDataReceived { data ->
                if (data != null) {
                    handleResistanceData(data)
                }
            }

            // Подписка на изменение состояния подключения
            sensor.sensorStateChanged = Sensor.SensorStateChanged { state ->
                Log.d(TAG, "Sensor state changed: $state")
                if (state == SensorState.StateOutOfRange) {
                    runOnUiThread {
                        signalQualityView?.text = "Устройство отключено"
                        Toast.makeText(
                            this@LiveMonitoringActivity,
                            "BrainBit отключён. Проверьте устройство.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            // Запуск получения сигнала
            sensor.execCommand(SensorCommand.StartSignal)
            Log.d(TAG, "Signal streaming started")

            // Запуск периодической проверки сопротивления
            startPeriodicResistanceCheck()

            runOnUiThread {
                signalQualityView?.text = "Подключено • получаю данные..."
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up BrainBit: ${e.message}", e)
            runOnUiThread {
                signalQualityView?.text = "Ошибка: ${e.message}"
            }
        }
    }

    /**
     * Обработка данных сигнала EEG.
     * BrainBit имеет 4 канала: O1, O2, T3, T4
     */
    private fun handleSignalData(data: Array<BrainBitSignalData>) {
        dataPacketsCount++
        val lastSample = data.lastOrNull() ?: return

        try {
            // Вычисление биполярных каналов для MathLib
            val bipolarSamples = data.map { sample ->
                val ch1 = sample.t3 - sample.o1
                val ch2 = sample.t4 - sample.o2
                RawChannels(ch1, ch2)
            }.toTypedArray()

            // Передача данных в MathLib
            emotionalMath?.pushData(bipolarSamples)
            emotionalMath?.processDataArr()

            // Чтение результатов анализа
            processAnalysisResults()

        } catch (e: Exception) {
            Log.e(TAG, "Error processing signal data: ${e.message}", e)
        }

        // Обновление UI с данными каналов
        updateChannelDisplay(lastSample)
    }

    /**
     * Обработка данных сопротивления электродов.
     */
    private fun handleResistanceData(data: BrainBitResistData) {
        val channels = mapOf(
            "O1" to data.o1,
            "O2" to data.o2,
            "T3" to data.t3,
            "T4" to data.t4
        )

        Log.d(TAG, "Resistance: ${channels.entries.joinToString { "${it.key}=${formatResistance(it.value)}" }}")

        val displayText = if (channels.values.all { it.isInfinite() }) {
            "Нет контакта: подключите электроды к коже головы"
        } else {
            channels.entries.joinToString(" • ") { (name, value) ->
                val quality = getResistanceQualityIcon(value)
                val displayValue = formatResistance(value)
                "$name: $displayValue$quality"
            }
        }

        runOnUiThread {
            artifactsInfoView?.text = displayText
        }
    }

    private fun formatResistance(value: Double): String {
        return when {
            value.isInfinite() -> "∞"
            value.isNaN() -> "?"
            else -> "${"%.0f".format(value / 1000.0)}k"
        }
    }

    private fun getResistanceQualityIcon(resistOhm: Double): String {
        return when {
            resistOhm.isInfinite() || resistOhm.isNaN() -> "✗"
            resistOhm < 50_000 -> "✓"      // Отличный контакт
            resistOhm < 200_000 -> "○"     // Хороший контакт
            resistOhm < 500_000 -> "◐"     // Удовлетворительный
            resistOhm < 2_000_000 -> "●"   // Плохой контакт
            else -> "✗"                     // Нет контакта
        }
    }

    private fun updateChannelDisplay(sample: BrainBitSignalData) {
        val channels = listOf(
            "O1" to sample.o1,
            "O2" to sample.o2,
            "T3" to sample.t3,
            "T4" to sample.t4
        )

        val channelText = channels.joinToString(" • ") { (name, value) ->
            "$name: ${"%.6f".format(value)} V"
        }

        val peakValue = channels.maxOfOrNull { it.second } ?: 0.0

        runOnUiThread {
            channelMetricsView?.text = "Каналы: $channelText"
            signalQualityView?.text = "Пакет ${sample.packNum} • 4 канала • #$dataPacketsCount"
            peaksInfoView?.text = "Пики: ${"%.6f".format(peakValue)} V"
        }
    }

    /**
     * Периодическая проверка сопротивления электродов.
     * BrainBit не может одновременно передавать сигнал и измерять сопротивление.
     */
    private fun startPeriodicResistanceCheck() {
        stopPeriodicResistanceCheck()

        resistanceJob = lifecycleScope.launch(Dispatchers.IO) {
            delay(10_000) // Начальная задержка

            while (isActive && brainBitSensor != null) {
                if (!isResistanceMode) {
                    measureResistance()
                }
                delay(RESISTANCE_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun stopPeriodicResistanceCheck() {
        resistanceJob?.cancel()
        resistanceJob = null
        isResistanceMode = false
    }

    /**
     * Измерение сопротивления электродов.
     * Требует переключения режима устройства.
     */
    private suspend fun measureResistance() {
        val sensor = brainBitSensor ?: return
        if (isResistanceMode) return

        try {
            isResistanceMode = true

            // Останавливаем сигнал
            sensor.execCommand(SensorCommand.StopSignal)
            delay(500)

            // Запускаем измерение сопротивления
            sensor.execCommand(SensorCommand.StartResist)
            delay(RESISTANCE_MEASUREMENT_DURATION_MS)

            // Останавливаем измерение сопротивления
            sensor.execCommand(SensorCommand.StopResist)
            delay(500)

            // Возобновляем сигнал
            sensor.execCommand(SensorCommand.StartSignal)

        } catch (e: Exception) {
            Log.e(TAG, "Error during resistance measurement: ${e.message}", e)
            // Попытка восстановить режим сигнала
            try {
                sensor.execCommand(SensorCommand.StopResist)
                delay(300)
                sensor.execCommand(SensorCommand.StartSignal)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to restore signal mode: ${ex.message}")
            }
        } finally {
            isResistanceMode = false
        }
    }

    /**
     * Остановка мониторинга BrainBit.
     * Согласно документации SDK2, нужно:
     * 1. Остановить команды
     * 2. Отключить callback'и
     * 3. Отключиться от устройства
     * 4. Освободить ресурсы
     */
    private fun stopBrainBitMonitoring() {
        Log.d(TAG, "Stopping BrainBit monitoring...")

        stopPeriodicResistanceCheck()

        brainBitSensor?.let { sensor ->
            try {
                sensor.execCommand(SensorCommand.StopSignal)
                sensor.execCommand(SensorCommand.StopResist)
                sensor.brainBitSignalDataReceived = null
                sensor.brainBitResistDataReceived = null
                sensor.sensorStateChanged = null
                sensor.disconnect()
                sensor.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping sensor: ${e.message}")
            }
        }
        brainBitSensor = null

        scanner?.let { sc ->
            try {
                sc.stop()
                sc.sensorsChanged = null
                sc.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scanner: ${e.message}")
            }
        }
        scanner = null

        emotionalMath = null
        calibrationComplete = false
        dataPacketsCount = 0
    }

    // endregion

    // region EmotionalMath (MathLib)

    /**
     * Инициализация библиотеки анализа EEG.
     */
    private fun initEmotionalMath() {
        try {
            val mathLibSettings = MathLibSetting(
                SAMPLING_FREQUENCY,  // samplingFrequency
                25,                  // processWindowSize
                1_000,               // fftWindowSize
                4,                   // nFirstSecSkipped
                true,                // bipolarMode
                4,                   // channelsNumber
                0                    // reserved
            )

            val artifactSettings = ArtifactDetectSetting(
                110,                 // ampl_art
                70,                  // susp_delta_art
                800_000,             // step_art
                (40 * 1e7).toInt(),  // art_ext_val
                4,                   // susp_ext_cnt
                true,                // hanning
                false,               // hamming
                true,                // blackman
                125                  // reserved
            )

            val shortArtifactSettings = ShortArtifactDetectSetting(
                200,                 // amplArt
                200,                 // stepArt
                25                   // shortArtPeriod
            )

            val mentalSettings = MentalAndSpectralSetting(
                4,                   // nSecForInstantEstimation
                2                    // nSecForAveraging
            )

            emotionalMath = EmotionalMath(
                mathLibSettings,
                artifactSettings,
                shortArtifactSettings,
                mentalSettings
            ).apply {
                setCallibrationLength(CALIBRATION_LENGTH_SEC)
                setMentalEstimationMode(false)
                setSkipWinsAfterArtifact(10)
                setZeroSpectWaves(true, 0, 1, 1, 1, 0)
                setSpectNormalizationByBandsWidth(true)
            }

            calibrationComplete = false
            dataPacketsCount = 0
            Log.d(TAG, "EmotionalMath initialized")

        } catch (e: Exception) {
            Log.e(TAG, "EmotionalMath init error: ${e.message}", e)
        }
    }

    /**
     * Обработка результатов анализа EEG.
     */
    private fun processAnalysisResults() {
        val math = emotionalMath ?: return

        try {
            val isArtifacted = math.isBothSidesArtifacted() || math.isArtifactedSequence()
            val calibrationPercent = math.callibrationPercents

            val isCalibrationFinished = try {
                math.calibrationFinished()
            } catch (e: Exception) {
                calibrationPercent >= 100
            }

            if (isCalibrationFinished && !calibrationComplete) {
                calibrationComplete = true
                Log.d(TAG, "✅ Calibration completed!")
            }

            // Чтение ментальных данных
            val mentalData = math.readMentalDataArr()
            val lastMental = mentalData.lastOrNull()

            // Чтение спектральных данных
            val spectralData = readSpectralData(math)
            
            // Применяем сглаживание (Exponential Moving Average)
            // Сглаживание происходит при каждом получении данных для накопления
            val rawAlpha = spectralData.alpha
            val rawBeta = spectralData.beta
            val rawTheta = spectralData.theta
            val rawAttention = lastMental?.relAttention ?: 0.0
            val rawRelaxation = lastMental?.relRelaxation ?: 0.0
            
            if (isFirstReading) {
                // Первое чтение - инициализируем сглаженные значения
                smoothedAlpha = rawAlpha
                smoothedBeta = rawBeta
                smoothedTheta = rawTheta
                smoothedAttention = rawAttention
                smoothedRelaxation = rawRelaxation
                isFirstReading = false
            } else {
                // EMA: smoothed = factor * new + (1 - factor) * old
                smoothedAlpha = smoothValue(rawAlpha, smoothedAlpha)
                smoothedBeta = smoothValue(rawBeta, smoothedBeta)
                smoothedTheta = smoothValue(rawTheta, smoothedTheta)
                smoothedAttention = smoothValue(rawAttention, smoothedAttention)
                smoothedRelaxation = smoothValue(rawRelaxation, smoothedRelaxation)
            }

            // Троттлинг: обновляем UI только раз в UI_UPDATE_INTERVAL_MS
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUiUpdateTime < UI_UPDATE_INTERVAL_MS) {
                return // Пропускаем обновление UI, но сглаживание уже применено
            }
            lastUiUpdateTime = currentTime

            // Обновление UI со сглаженными значениями
            updateAnalysisDisplay(
                isArtifacted = isArtifacted,
                calibrationPercent = calibrationPercent,
                attention = smoothedAttention,
                relaxation = smoothedRelaxation,
                alpha = smoothedAlpha,
                beta = smoothedBeta,
                theta = smoothedTheta
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error processing analysis results: ${e.message}", e)
        }
    }

    private data class SpectralData(
        val alpha: Double,
        val beta: Double,
        val theta: Double
    )

    private fun readSpectralData(math: EmotionalMath): SpectralData {
        var alpha = 0.0
        var beta = 0.0
        var theta = 0.0

        try {
            // Прямой вызов метода библиотеки
            val spectralArr = math.readSpectralDataPercentsArr()
            
            if (spectralArr != null && spectralArr.isNotEmpty()) {
                val last = spectralArr.last()
                
                // Читаем поля через рефлексию, так как структура может отличаться в разных версиях
                val clazz = last.javaClass
                
                // Пробуем получить значения из полей
                alpha = getFieldValue(last, "Alpha", "alpha", "alphaPercent", "alphaProcent")
                beta = getFieldValue(last, "Beta", "beta", "betaPercent", "betaProcent")
                theta = getFieldValue(last, "Theta", "theta", "thetaPercent", "thetaProcent")
                
                // Если не нашли через прямые имена, логируем все доступные поля для диагностики
                if (alpha == 0.0 && beta == 0.0 && theta == 0.0) {
                    Log.d(TAG, "SpectralData class: ${clazz.name}")
                    clazz.declaredFields.forEach { field ->
                        field.isAccessible = true
                        val value = field.get(last)
                        Log.d(TAG, "  Field: ${field.name} = $value (${field.type.simpleName})")
                    }
                    // Также проверяем методы-геттеры
                    clazz.methods.filter { it.name.startsWith("get") && it.parameterCount == 0 }.forEach { method ->
                        try {
                            val value = method.invoke(last)
                            Log.d(TAG, "  Method: ${method.name}() = $value")
                        } catch (e: Exception) { /* ignore */ }
                    }
                }
            } else {
                Log.d(TAG, "No spectral data available yet")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading spectral data: ${e.message}", e)
        }

        // Нормализация если значения в процентах (>1 означает проценты, не доли)
        if (alpha > 1) alpha /= 100.0
        if (beta > 1) beta /= 100.0
        if (theta > 1) theta /= 100.0

        return SpectralData(
            alpha = alpha.coerceIn(0.0, 1.0),
            beta = beta.coerceIn(0.0, 1.0),
            theta = theta.coerceIn(0.0, 1.0)
        )
    }
    
    /**
     * Получает значение поля из объекта, пробуя разные имена полей.
     */
    private fun getFieldValue(obj: Any, vararg fieldNames: String): Double {
        val clazz = obj.javaClass
        
        for (fieldName in fieldNames) {
            try {
                // Пробуем найти поле напрямую
                val field = clazz.declaredFields.firstOrNull { 
                    it.name.equals(fieldName, ignoreCase = true) 
                }
                if (field != null) {
                    field.isAccessible = true
                    val value = field.get(obj)
                    if (value is Number) {
                        return value.toDouble()
                    }
                }
                
                // Пробуем найти геттер
                val getterName = "get${fieldName.replaceFirstChar { it.uppercase() }}"
                val getter = clazz.methods.firstOrNull { 
                    it.name.equals(getterName, ignoreCase = true) && it.parameterCount == 0 
                }
                if (getter != null) {
                    val value = getter.invoke(obj)
                    if (value is Number) {
                        return value.toDouble()
                    }
                }
            } catch (e: Exception) {
                // Продолжаем со следующим именем
            }
        }
        
        return 0.0
    }
    
    /**
     * Применяет экспоненциальное сглаживание (EMA).
     * Формула: smoothed = factor * newValue + (1 - factor) * previousValue
     * 
     * @param newValue новое значение
     * @param previousSmoothed предыдущее сглаженное значение
     * @return сглаженное значение
     */
    private fun smoothValue(newValue: Double, previousSmoothed: Double): Double {
        return SMOOTHING_FACTOR * newValue + (1.0 - SMOOTHING_FACTOR) * previousSmoothed
    }

    private fun updateAnalysisDisplay(
        isArtifacted: Boolean,
        calibrationPercent: Int,
        attention: Double,
        relaxation: Double,
        alpha: Double,
        beta: Double,
        theta: Double
    ) {
        val alphaPercent = (alpha * 100.0).coerceIn(0.0, 100.0)
        val betaPercent = (beta * 100.0).coerceIn(0.0, 100.0)
        val thetaPercent = (theta * 100.0).coerceIn(0.0, 100.0)
        val attentionPercent = (attention.coerceIn(0.0, 1.0) * 100.0)

        runOnUiThread {
            // Артефакты/сопротивление (не перезаписываем если показываем сопротивление)
            if (!isResistanceMode && artifactsInfoView?.text?.contains("контакт") != true) {
                artifactsInfoView?.text = if (isArtifacted) {
                    "⚠️ Обнаружены артефакты"
                } else {
                    "✓ Сигнал чистый"
                }
            }

            // Спектральные данные
            waveTextView?.text = buildString {
                append("Альфа: ${"%.1f".format(alphaPercent)}%\n")
                append("Бета: ${"%.1f".format(betaPercent)}%\n")
                append("Индекс: ${"%.1f".format(thetaPercent)}%")
            }

            // Внимание/калибровка
            engagementLevelView?.text = when {
                calibrationPercent in 1..99 -> "🔄 Калибровка: $calibrationPercent%"
                calibrationComplete -> "Внимание: ${"%.1f".format(attentionPercent)}%"
                else -> "⏳ Ожидание калибровки..."
            }
        }
    }

    // endregion

    // region Fake Metrics (Debug Mode)

    // Сглаженные значения для фейкового режима
    private var fakeAlpha = 50.0
    private var fakeBeta = 40.0
    private var fakeTheta = 30.0
    private var fakeEngagement = 50.0
    
    private fun startFakeMetricsGeneration() {
        signalQualityView?.text = "Фейк-данные • без устройства"

        fakeJob?.cancel()
        fakeJob = lifecycleScope.launch {
            while (isActive) {
                // Генерируем случайные целевые значения
                val targetAlpha = Random.nextDouble(20.0, 90.0)
                val targetBeta = Random.nextDouble(10.0, 80.0)
                val targetTheta = Random.nextDouble(10.0, 80.0)
                val targetEngagement = Random.nextDouble(20.0, 90.0)
                
                // Применяем сглаживание для плавных переходов
                // Используем больший коэффициент (0.2) для более заметных изменений в фейк-режиме
                val fakeSmoothFactor = 0.2
                fakeAlpha = fakeSmoothFactor * targetAlpha + (1.0 - fakeSmoothFactor) * fakeAlpha
                fakeBeta = fakeSmoothFactor * targetBeta + (1.0 - fakeSmoothFactor) * fakeBeta
                fakeTheta = fakeSmoothFactor * targetTheta + (1.0 - fakeSmoothFactor) * fakeTheta
                fakeEngagement = fakeSmoothFactor * targetEngagement + (1.0 - fakeSmoothFactor) * fakeEngagement
                
                val volts = List(4) { Random.nextDouble(20.0, 120.0) / 1e6 }
                val names = listOf("O1", "O2", "T3", "T4")

                val channelText = volts.mapIndexed { idx, v ->
                    "${names[idx]}: ${"%.6f".format(v)} V"
                }.joinToString(" • ")

                val peak = volts.maxOrNull() ?: 0.0

                waveTextView?.text = buildString {
                    append("Альфа: ${"%.1f".format(fakeAlpha)}%\n")
                    append("Бета: ${"%.1f".format(fakeBeta)}%\n")
                    append("Индекс: ${"%.1f".format(fakeTheta)}%")
                }

                engagementLevelView?.text = "Внимание: ${"%.1f".format(fakeEngagement)}%"
                channelMetricsView?.text = "Каналы: $channelText"
                peaksInfoView?.text = "Пики: ${"%.6f".format(peak)} V"
                artifactsInfoView?.text = "Фейковый режим"

                delay(UI_UPDATE_INTERVAL_MS) // Обновляем раз в секунду
            }
        }
    }

    private fun stopFakeMetrics() {
        fakeJob?.cancel()
        fakeJob = null
    }

    // endregion

    // region UI Actions

    private fun navigateToAnalysis() {
        startActivity(Intent(this, SessionAnalysisActivity::class.java))
        finish()
    }

    private fun addSessionMark() {
        engagementLevelView?.text = "Добавлена метка"
        Toast.makeText(this, "Метка добавлена", Toast.LENGTH_SHORT).show()
    }

    private fun saveSnapshot() {
        engagementLevelView?.text = "Снимок сохранён"
        Toast.makeText(this, "Снимок сохранён", Toast.LENGTH_SHORT).show()
    }

    private fun startCalibration() {
        if (fakeMetrics) {
            Toast.makeText(this, "Фейковые метрики: калибровка не требуется", Toast.LENGTH_SHORT).show()
            return
        }

        if (brainBitSensor == null || brainBitSensor?.state != SensorState.StateInRange) {
            Toast.makeText(this, "Устройство не подключено", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            emotionalMath?.startCalibration()
            calibrationComplete = false
            engagementLevelView?.text = "🔄 Калибровка: 0%"
            Log.d(TAG, "Calibration started manually")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start calibration: ${e.message}", e)
            Toast.makeText(this, "Ошибка запуска калибровки: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun showError(message: String) {
        withContext(Dispatchers.Main) {
            signalQualityView?.text = message
            Toast.makeText(this@LiveMonitoringActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    // endregion
}
