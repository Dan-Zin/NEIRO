package com.example.neirotech

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.neurosdk2.neuro.Scanner
import com.neurosdk2.neuro.types.SensorFamily
import com.neurosdk2.neuro.types.SensorInfo

/**
 * Activity для сканирования BrainBit устройств с использованием SDK2 Scanner.
 * Использует нативный SDK2 Scanner вместо стандартного Android BLE Scanner
 * для корректного определения типа устройства и получения SensorInfo.
 */
class DeviceScanActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "DeviceScanActivity"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanning = false
    
    // SDK2 Scanners для разных семейств устройств
    private val scanners = mutableListOf<Scanner>()
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!scanning) return
            val all = scanners.flatMap { it.sensors ?: emptyList() }
            handleSensorListUpdate(all)
            handler.postDelayed(this, 1_000)
        }
    }

    private val devices = LinkedHashMap<String, UiDevice>() // address -> device
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val allGranted = requiredPermissions().all { grants[it] == true }
            if (allGranted) startScan()
            else Toast.makeText(this, "Нет разрешений для сканирования", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        setContentView(R.layout.activity_device_scan)

        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bm.adapter

        listView = findViewById(R.id.deviceList)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val device = devices.values.elementAtOrNull(position) ?: return@setOnItemClickListener
            // Сохраняем SensorInfo для быстрого подключения в LiveMonitoringActivity
            ConnectionManager.setSensorInfo(device.sensorInfo)
            startActivity(
                DevicePairingActivity.intent(this, device.name, device.address, device.rssi)
            )
        }

        findViewById<Button>(R.id.btnRefreshScan).setOnClickListener {
            startScanWithPermission()
        }

        findViewById<Button>(R.id.btnToPairing).setOnClickListener {
            val first = devices.values.firstOrNull()
            if (first != null) {
                // Сохраняем SensorInfo для быстрого подключения
                ConnectionManager.setSensorInfo(first.sensorInfo)
                startActivity(
                    DevicePairingActivity.intent(this, first.name, first.address, first.rssi)
                )
            } else {
                Toast.makeText(this, "Сначала найдите устройство", Toast.LENGTH_SHORT).show()
            }
        }

        startScanWithPermission()
    }

    private fun startScanWithPermission() {
        val perms = requiredPermissions()
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            startScan()
        }
    }

    private fun startScan() {
        if (bluetoothAdapter?.isEnabled != true) {
            Toast.makeText(this, "Включите Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }
        if (scanning) return

        devices.clear()
        refreshList()
        scanning = true

        // Запускаем создание и старт сканеров вне UI-потока, чтобы исключить блокировки (ANR)
        lifecycleScope.launch(Dispatchers.Default) {
            // Сканируем все поддерживаемые BrainBit семейства
            val families = listOf(
                SensorFamily.SensorLEBrainBit,
                SensorFamily.SensorLEBrainBit2,
                SensorFamily.SensorLEBrainBitPro,
                SensorFamily.SensorLEBrainBitFlex
            )

            families.forEach { family ->
                try {
                    val scanner = Scanner(family)
                    scanners.add(scanner)

                    // Callback при обновлении списка устройств
                    scanner.sensorsChanged = Scanner.ScannerCallback { _, sensorList ->
                        handleSensorListUpdate(sensorList)
                    }

                    scanner.start()
                    Log.d(TAG, "Started scanner for family: $family")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start scanner for family $family: ${e.message}")
                }
            }

            // Периодически опрашиваем накопленный список устройств (документация: callback только для новых)
            handler.post(pollRunnable)

            // Таймер остановки сканирования ставим на UI-потоке
            withContext(Dispatchers.Main) {
                handler.postDelayed({
                    stopScan()
                    Toast.makeText(this@DeviceScanActivity, "Сканирование завершено", Toast.LENGTH_SHORT).show()
                }, 12_000) // увеличили время сканирования до 12 секунд
            }
        }
    }
    
    private fun handleSensorListUpdate(sensorList: List<SensorInfo>?) {
        if (sensorList.isNullOrEmpty()) return
        
        runOnUiThread {
            var listChanged = false
            for (info in sensorList) {
                val name = info.name
                val address = info.address
                if (name.isNullOrBlank() || address.isNullOrBlank()) continue

                // Определяем RSSI (в SDK2 может не быть напрямую, используем 0 как placeholder)
                val rssi = 0

                val item = UiDevice(name, address, rssi, info)
                val existed = devices.containsKey(address)
                devices[address] = item
                if (!existed) listChanged = true
                Log.d(TAG, "Found device: $name ($address), family: ${info.sensFamily}")
            }
            if (listChanged) {
                refreshList()
                // Больше не останавливаем сканирование по первому найденному устройству
            }
        }
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        handler.removeCallbacks(pollRunnable)
        
        for (scanner in scanners) {
            try {
                scanner.stop()
                scanner.sensorsChanged = null
                scanner.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scanner: ${e.message}")
            }
        }
        scanners.clear()
    }

    private fun refreshList() {
        val items = devices.values.map { 
            val familyStr = it.sensorInfo?.sensFamily?.name?.removePrefix("SensorLE") ?: "BLE"
            "${it.name} • $familyStr • ${it.address}"
        }
        adapter.clear()
        adapter.addAll(items)
        adapter.notifyDataSetChanged()
    }

    private fun requiredPermissions(): List<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return perms
    }

    override fun onDestroy() {
        stopScan()
        super.onDestroy()
    }

    /**
     * Представление устройства для UI с сохранённым SensorInfo для SDK2.
     */
    data class UiDevice(
        val name: String, 
        val address: String, 
        val rssi: Int,
        val sensorInfo: SensorInfo? = null
    )
}

