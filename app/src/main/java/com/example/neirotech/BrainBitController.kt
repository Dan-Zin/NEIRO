package com.example.neirotech

import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.neurotech.emstartifcats.ArtifactDetectSetting
import com.neurotech.emstartifcats.EmotionalMath
import com.neurotech.emstartifcats.MathLibSetting
import com.neurotech.emstartifcats.MentalAndSpectralSetting
import com.neurotech.emstartifcats.RawChannels
import com.neurotech.emstartifcats.ShortArtifactDetectSetting
import com.neurosdk2.neuro.BrainBit
import com.neurosdk2.neuro.Scanner
import com.neurosdk2.neuro.interfaces.BrainBitResistDataReceived
import com.neurosdk2.neuro.interfaces.BrainBitSignalDataReceived
import com.neurosdk2.neuro.types.BrainBitResistData
import com.neurosdk2.neuro.types.BrainBitSignalData
import com.neurosdk2.neuro.types.SensorCommand
import com.neurosdk2.neuro.types.SensorFamily
import com.neurosdk2.neuro.types.SensorInfo
import com.neurosdk2.neuro.types.SensorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Инкапсулирует всю работу с BrainBit/MathLib, чтобы LiveMonitoringActivity занималась только UI.
 */
class BrainBitController(private val activity: LiveMonitoringActivity) {

    companion object {
        private const val TAG = "LiveMonitoringActivity"
    }

    // SDK2
    private var scanner: Scanner? = null
    private var brainBit: BrainBit? = null

    // MathLib
    private var emotionalMath: EmotionalMath? = null
    private var channelNames: List<String> = listOf("Ch1", "Ch2", "Ch3", "Ch4")

    // Resistance monitoring
    private var lastResistanceData: BrainBitResistData? = null
    private var periodicResistanceJob: Job? = null
    private var isInResistanceMode = false

    // Calibration / data state
    private var calibrationStarted = false
    private var calibrationComplete = false
    private var dataPacketsReceived = 0

    fun start(address: String?, name: String?, savedInfo: SensorInfo?) {
        startBrainBitStream(address, name, savedInfo)
    }

    fun stop() {
        stopBrainBitStream()
    }

    fun startCalibrationManual() {
        if (!isSensorConnected()) {
            activity.runOnUiThread {
                Toast.makeText(activity, "Устройство не подключено", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (emotionalMath == null) return

        try {
            emotionalMath?.startCalibration()
            calibrationStarted = true
            calibrationComplete = false
            Log.d(TAG, "Calibration started (manual)")
            activity.runOnUiThread {
                activity.findViewById<TextView>(R.id.engagementLevel)?.text = "Калибровка: 0%"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start calibration: ${e.message}", e)
            activity.runOnUiThread {
                Toast.makeText(activity, "Ошибка запуска калибровки: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startBrainBitStream(address: String?, name: String?, savedInfo: SensorInfo?) {
        Log.d(TAG, "startBrainBitStream: Connecting to $name ($address)")
        activity.lifecycleScope.launch(Dispatchers.IO) {
            if (address.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    activity.findViewById<TextView>(R.id.signalQuality).text = "Нет адреса устройства"
                    Toast.makeText(
                        activity,
                        "Нет адреса устройства, переподключите через главный экран",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            if (savedInfo != null && savedInfo.address.equals(address, ignoreCase = true)) {
                if (savedInfo.sensFamily == SensorFamily.SensorLEBrainBit && tryConnectUsingSavedInfo(savedInfo)) return@launch
            }

            val families = listOf(SensorFamily.SensorLEBrainBit)

            var connected = false
            for (family in families) {
                if (connected) break
                try {
                    val sc = (scanner ?: Scanner(family)).also { scanner = it }
                    // Перезапускаем поиск на уже существующем сканере
                    try {
                        sc.stop()
                    } catch (_: Exception) {}
                    sc.start()

                    // Быстрый опрос: до ~1.5 сек, шаг 250 мс
                    repeat(6) {
                        if (connected) return@repeat
                        delay(250)
                        val sensors = sc.sensors
                        val found = sensors?.firstOrNull { it.address.equals(address, true) }
                        if (found != null) {
                            connected = tryConnectToSensor(sc, found)
                        }
                    }

                    // Останавливаем поиск как только попытались подключиться
                    try { sc.stop() } catch (_: Exception) {}

                    if (!connected) {
                        sc.close()
                        scanner = null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Scan error: ${e.message}", e)
                }
            }

            if (!connected) {
                withContext(Dispatchers.Main) {
                    activity.findViewById<TextView>(R.id.signalQuality).text =
                        "BrainBit не найден${if (name.isNullOrBlank()) "" else " ($name)"}"
                    Toast.makeText(
                        activity,
                        "Устройство не найдено. Убедитесь, что BrainBit включён и рядом.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun tryConnectUsingSavedInfo(info: SensorInfo): Boolean {
        return try {
            val sc = Scanner(info.sensFamily)
            scanner = sc
            sc.start()
            Thread.sleep(150)
            tryConnectToSensor(sc, info)
        } catch (e: Exception) {
            Log.e(TAG, "Direct connect failed: ${e.message}", e)
            false
        }
    }

    @Synchronized
    private fun tryConnectToSensor(sc: Scanner, info: SensorInfo): Boolean {
        if (brainBit != null) return true
        return try {
            when (info.sensFamily) {
                SensorFamily.SensorLEBrainBit -> {
                    val sensor = sc.createSensor(info) as? BrainBit
                        ?: return false.also { Log.e(TAG, "Cannot cast to BrainBit") }
                    brainBit = sensor
                    setupBrainBit(sensor)
                }
                else -> false.also { Log.e(TAG, "Unsupported sensor family: ${info.sensFamily}") }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}", e)
            activity.runOnUiThread {
                activity.findViewById<TextView>(R.id.signalQuality)?.text = "Ошибка: ${e.message}"
            }
            false
        }
    }

    fun stopBrainBitStream() {
        Log.d(TAG, "Stopping BrainBit stream...")

        brainBit?.let { sensor ->
            try {
                sensor.execCommand(SensorCommand.StopSignal)
                sensor.execCommand(SensorCommand.StopResist)
                sensor.brainBitSignalDataReceived = null
                sensor.brainBitResistDataReceived = null
                sensor.disconnect()
                sensor.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping sensor: ${e.message}")
            }
        }

        brainBit = null
        emotionalMath = null
        calibrationStarted = false
        calibrationComplete = false
        dataPacketsReceived = 0

        scanner?.let { sc ->
            try {
                sc.stop()
                sc.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scanner: ${e.message}")
            }
        }
        scanner = null

        stopPeriodicResistanceCheck()
    }

    private fun setupBrainBit(sensor: BrainBit) {
        channelNames = listOf("O1", "O2", "T3", "T4")
        initMath()

        try {
            sensor.brainBitSignalDataReceived = BrainBitSignalDataReceived { data ->
                if (data.isNotEmpty()) {
                    Log.d(TAG, "BrainBit Signal: ${data.size} samples received")
                    handleBrainBitSignalData(data)
                }
            }

            sensor.brainBitResistDataReceived = BrainBitResistDataReceived { data ->
                Log.d(TAG, "Resistance callback triggered with data: ${data}")
                handleBrainBitResistData(data)
            }

            sensor.execCommand(SensorCommand.StartSignal)
            startPeriodicResistanceCheck()

            activity.runOnUiThread {
                activity.findViewById<TextView>(R.id.signalQuality)?.text = "Подключено • получаю данные..."
            }

        } catch (e: Exception) {
            Log.e(TAG, "setupBrainBit error: ${e.message}", e)
            activity.runOnUiThread {
                activity.findViewById<TextView>(R.id.signalQuality)?.text = "Ошибка: ${e.message}"
            }
        }
    }

    private fun handleBrainBitSignalData(data: Array<BrainBitSignalData>) {
        dataPacketsReceived++
        val last = data.lastOrNull() ?: return

        try {
            val firstSample = data.firstOrNull()
            val bipolars = Array(data.size) { i ->
                val rawO1 = data[i].o1
                val rawO2 = data[i].o2
                val rawT3 = data[i].t3
                val rawT4 = data[i].t4


                val ch1 = (rawT3 - rawO1)
                val ch2 = (rawT4 - rawO2)
                RawChannels(ch1, ch2)
            }

            if (dataPacketsReceived <= 3 || dataPacketsReceived % 10 == 0) {
                firstSample?.let {
                    val maxAbs = maxOf(
                        kotlin.math.abs(it.o1),
                        kotlin.math.abs(it.o2),
                        kotlin.math.abs(it.t3),
                        kotlin.math.abs(it.t4)
                    )

                    val ch1Val = (it.t3 - it.o1)
                    val ch2Val = (it.t4 - it.o2)
                    Log.w(TAG, "BIPOLAR: samples=${bipolars.size} ch1=${"%.2f".format(ch1Val)}µV ch2=${"%.2f".format(ch2Val)}µV )")
                }
            }

            emotionalMath?.pushData(bipolars)
            emotionalMath?.processDataArr()
            readMathResults()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing BrainBit data: ${e.message}", e)
        }

        // Отображаем каналы в Вольтах (сырые значения из SDK)
        val volts = listOf(last.o1, last.o2, last.t3, last.t4)
        val peak = volts.maxOrNull() ?: 0.0
        val names = listOf("O1", "O2", "T3", "T4")
        val chText = volts.mapIndexed { idx, v -> "${names[idx]}: ${"%.6f".format(v)} V" }
            .joinToString(" • ")
        activity.runOnUiThread {
            activity.findViewById<TextView>(R.id.channelMetrics)?.text = "Каналы: $chText"
            activity.findViewById<TextView>(R.id.signalQuality)?.text =
                "Пакет ${last.packNum} • 4 канала • #$dataPacketsReceived"
            activity.findViewById<TextView>(R.id.peaksInfo)?.text =
                "Пики: ${"%.6f".format(peak)} V"
        }
    }

    private fun handleBrainBitResistData(data: BrainBitResistData) {
        lastResistanceData = data

        val samples = listOf(data.o1, data.o2, data.t3, data.t4)
        val names = listOf("O1", "O2", "T3", "T4")

        val logMessage = buildString {
            append("Resistance data received: ")
            samples.forEachIndexed { idx, value ->
                val valueStr = when {
                    value.isInfinite() -> "INF"
                    value.isNaN() -> "NaN"
                    else -> String.format("%.0f", value)
                }
                append("${names[idx]}=${valueStr}Ω ")
            }
        }
        Log.d(TAG, logMessage)

        val allInf = samples.all { it.isInfinite() }

        val text = if (allInf) {
            "Нет контакта: подключите электроды к коже головы"
        } else {
            samples.mapIndexed { idx, v ->
                val quality = getResistQuality(v)
                val displayValue = when {
                    v.isInfinite() -> "∞"
                    v.isNaN() -> "?"
                    else -> "${"%.0f".format(v / 1000.0)}k"
                }
                "${names[idx]}: $displayValue$quality"
            }.joinToString(" • ")
        }

        activity.runOnUiThread {
            updateResistanceDisplay(text)
        }
    }

    private fun updateResistanceDisplay(resistanceText: String) {
        val artifactsView = activity.findViewById<TextView>(R.id.artifactsInfo)
        if (artifactsView != null) {
            // Показываем только актуальные данные сопротивления, без накопления старых значений
            artifactsView.text = resistanceText
        }
    }

    private fun getResistQuality(resistOhm: Double): String {
        return when {
            resistOhm.isInfinite() || resistOhm.isNaN() -> "✗"
            resistOhm < 50_000 -> "✓"
            resistOhm < 200_000 -> "○"
            resistOhm < 500_000 -> "◐"
            resistOhm < 2_000_000 -> "●"
            else -> "✗"
        }
    }

    private fun readMathResults() {
        try {
            val math = emotionalMath ?: return

            val isArtifacted = math.isBothSidesArtifacted() || math.isArtifactedSequence()
            val calib = math.callibrationPercents

            val isCalibFinished = try {
                math.calibrationFinished()
            } catch (e: Exception) {
                calib >= 100
            }

            if (isCalibFinished && !calibrationComplete) {
                calibrationComplete = true
                Log.d(TAG, "✅ Calibration completed!")
            }

            val mentalDataArr = math.readMentalDataArr()
            val lastMind = mentalDataArr.lastOrNull()

            val spectral = readSpectralTriple(math)

            val alphaPercent = (spectral.first * 100.0).coerceIn(0.0, 100.0)
            val betaPercent = (spectral.second * 100.0).coerceIn(0.0, 100.0)
            val thetaPercent = (spectral.third * 100.0).coerceIn(0.0, 100.0)

            val attention = (lastMind?.relAttention ?: 0.0).coerceIn(0.0, 1.0)
            val relaxation = (lastMind?.relRelaxation ?: 0.0).coerceIn(0.0, 1.0)
            val engagementPercent = attention * 100.0
            val relaxationPercent = relaxation * 100.0

            activity.runOnUiThread {
                if (isArtifacted) {
                    activity.findViewById<TextView>(R.id.artifactsInfo)?.text = "⚠️ Обнаружены артефакты"
                } else {
                    activity.findViewById<TextView>(R.id.artifactsInfo)?.text = "✓ Сигнал чистый"
                }

                // Показываем числовые значения без «прогресс-баров»
                activity.findViewById<TextView>(R.id.waveText)?.text =
                    "Альфа: ${"%.1f".format(alphaPercent)}%\n" +
                    "Бета: ${"%.1f".format(betaPercent)}%\n" +
                    "Индекс: ${"%.1f".format(thetaPercent)}%"

                if (calib in 1..99) {
                    activity.findViewById<TextView>(R.id.engagementLevel)?.text = "🔄 Калибровка: $calib%"
                } else if (calibrationComplete) {
                    activity.findViewById<TextView>(R.id.engagementLevel)?.text =
                        "Внимание: ${"%.1f".format(engagementPercent)}%"
                } else {
                    activity.findViewById<TextView>(R.id.engagementLevel)?.text = "⏳ Ожидание калибровки..."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Math read error: ${e.message}", e)
        }
    }

    private fun readSpectralTriple(math: EmotionalMath): Triple<Double, Double, Double> {
        var alpha = 0.0
        var beta = 0.0
        var theta = 0.0

        try {
            val spectralArrayMethod = math.javaClass.methods.firstOrNull {
                it.name == "readSpectralDataPercentsArr"
            }

            if (spectralArrayMethod != null) {
                val arr = spectralArrayMethod.invoke(math) as? List<*>
                val last = arr?.lastOrNull()
                if (last != null) {
                    val fields = last.javaClass.declaredFields
                    for (field in fields) {
                        field.isAccessible = true
                        when (field.name.lowercase()) {
                            "alpha", "alphap", "alphaprocent" ->
                                alpha = (field.get(last) as? Number)?.toDouble() ?: 0.0
                            "beta", "betap", "betaprocent" ->
                                beta = (field.get(last) as? Number)?.toDouble() ?: 0.0
                            "theta", "thetap", "index", "indexp" ->
                                theta = (field.get(last) as? Number)?.toDouble() ?: 0.0
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading spectral data: ${e.message}")
        }

        if (alpha > 1) alpha /= 100.0
        if (beta > 1) beta /= 100.0
        if (theta > 1) theta /= 100.0

        return Triple(alpha.coerceIn(0.0, 1.0),
            beta.coerceIn(0.0, 1.0),
            theta.coerceIn(0.0, 1.0))
    }

    private fun getProgressBar(percent: Int, maxLength: Int = 5): String {
        val filled = (percent / 100.0 * maxLength).toInt().coerceIn(0, maxLength)
        val empty = maxLength - filled
        return "▓".repeat(filled) + "░".repeat(empty)
    }

    private fun initMath() {
        try {
            val sf = 250 // Sampling frequency for BrainBit
            val mls = MathLibSetting(
                sf, 25, 1_000, 4, true, 4, 0
            )
            val ads = ArtifactDetectSetting(
                110,
                70,
                800_000,
                (40 * 1e7).toInt(),
                4,
                true,
                false,
                true,
                125
            )
            val sads = ShortArtifactDetectSetting(200, 200, 25)
            val mss = MentalAndSpectralSetting(
                4,
                2
            )

            emotionalMath = EmotionalMath(mls, ads, sads, mss)

            emotionalMath?.setCallibrationLength(6)
            emotionalMath?.setMentalEstimationMode(false)
            emotionalMath?.setSkipWinsAfterArtifact(10)
            emotionalMath?.setZeroSpectWaves(true, 0, 1, 1, 1, 0)
            emotionalMath?.setSpectNormalizationByBandsWidth(true)
            calibrationStarted = false
            calibrationComplete = false
            dataPacketsReceived = 0
            Log.d(TAG, "EmotionalMath initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Math init error: ${e.message}", e)
        }
    }

    private fun isSensorConnected(): Boolean {
        return brainBit != null && brainBit?.state == SensorState.StateInRange
    }

    private fun startResistanceMeasurement() {
        val sensor = brainBit ?: return

        if (isInResistanceMode) {
            Log.d(TAG, "Already in resistance mode")
            return
        }

        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                isInResistanceMode = true

                Log.d(TAG, "Stopping signal for resistance measurement...")
                sensor.execCommand(SensorCommand.StopSignal)
                delay(500)

                Log.d(TAG, "Starting resistance measurement...")
                sensor.execCommand(SensorCommand.StartResist)

                delay(3_000)

                Log.d(TAG, "Stopping resistance measurement...")
                sensor.execCommand(SensorCommand.StopResist)
                delay(500)

                Log.d(TAG, "Restarting signal...")
                sensor.execCommand(SensorCommand.StartSignal)

            } catch (e: Exception) {
                Log.e(TAG, "Error during resistance measurement: ${e.message}", e)
                try {
                    sensor.execCommand(SensorCommand.StopResist)
                    delay(500)
                    sensor.execCommand(SensorCommand.StartSignal)
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to restore signal mode: ${ex.message}")
                }
            } finally {
                isInResistanceMode = false
            }
        }
    }

    private fun startPeriodicResistanceCheck() {
        stopPeriodicResistanceCheck()

        periodicResistanceJob = activity.lifecycleScope.launch(Dispatchers.IO) {
            delay(10_000)

            while (brainBit != null) {
                if (!isInResistanceMode) {
                    startResistanceMeasurement()
                    delay(4_500)
                }
                delay(30_000)
            }
        }
    }

    private fun stopPeriodicResistanceCheck() {
        periodicResistanceJob?.cancel()
        periodicResistanceJob = null
        isInResistanceMode = false
    }
}

