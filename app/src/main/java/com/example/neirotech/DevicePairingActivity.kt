package com.example.neirotech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Activity для подтверждения выбора BrainBit устройства.
 * НЕ подключается к устройству - только сохраняет информацию.
 * Реальное подключение происходит в LiveMonitoringActivity.
 */
class DevicePairingActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "DevicePairingActivity"
        private const val EXTRA_NAME = "device_name"
        private const val EXTRA_ADDRESS = "device_address"
        private const val EXTRA_RSSI = "device_rssi"

        fun intent(context: Context, name: String, address: String, rssi: Int): Intent =
            Intent(context, DevicePairingActivity::class.java).apply {
                putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_RSSI, rssi)
            }
    }

    private var address: String? = null
    private var name: String? = null
    private var rssi: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        setContentView(R.layout.activity_device_pairing)

        address = intent.getStringExtra(EXTRA_ADDRESS)
        name = intent.getStringExtra(EXTRA_NAME)
        rssi = intent.getIntExtra(EXTRA_RSSI, -80)

        val stageText = findViewById<TextView>(R.id.pairingStage)
        val stageProgress = findViewById<ProgressBar>(R.id.pairingProgress)
        val signalIndicator = findViewById<TextView>(R.id.signalIndicator)
        val button = findViewById<Button>(R.id.btnStartSession)

        // Проверяем, есть ли SensorInfo
        val hasSensorInfo = ConnectionManager.hasSensorInfo()
        val sensorInfo = ConnectionManager.getSensorInfo()
        
        if (hasSensorInfo && sensorInfo != null) {
            // Устройство найдено через SDK2 Scanner - готово к использованию
            Log.d(TAG, "Device ready: ${sensorInfo.name} (${sensorInfo.address}), family: ${sensorInfo.sensFamily}")
            
            signalIndicator.text = "Устройство: ${name ?: "Неизвестно"}\nТип: ${sensorInfo.sensFamily.name.removePrefix("SensorLE")}"
            stageText.text = "Устройство готово"
            stageProgress.progress = 100
            
            // Сохраняем статус подключения
            ConnectionManager.setConnected(name, address, sensorInfo)
            
            button.isEnabled = true
            button.text = "Готово"
            button.setOnClickListener {
                Toast.makeText(this, "Устройство выбрано. Запустите сессию.", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            // Нет SensorInfo - что-то пошло не так
            Log.w(TAG, "No SensorInfo available for device $name ($address)")
            
            signalIndicator.text = "Устройство: ${name ?: "Неизвестно"}\nОшибка: информация не найдена"
            stageText.text = "Ошибка"
            stageProgress.progress = 0
            
            button.isEnabled = true
            button.text = "Назад"
            button.setOnClickListener {
                finish()
            }
            
            Toast.makeText(this, "Пересканируйте устройства", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasConnectPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(
            this,
            perm
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

