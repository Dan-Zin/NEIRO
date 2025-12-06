package com.example.neirotech

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionActivity : AppCompatActivity() {

    private lateinit var bluetoothStatus: TextView
    private lateinit var storageStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var continueButton: Button
    private var navigating = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updateStatuses()
            if (PermissionHelper.allGranted(this)) {
                goToMain()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        setContentView(R.layout.activity_permission)

        bluetoothStatus = findViewById(R.id.bluetoothStatus)
        storageStatus = findViewById(R.id.storageStatus)
        notificationStatus = findViewById(R.id.notificationStatus)
        continueButton = findViewById(R.id.btnContinue)

        findViewById<Button>(R.id.btnBluetooth).setOnClickListener { requestBluetooth() }
        findViewById<Button>(R.id.btnFiles).setOnClickListener { requestFiles() }
        findViewById<Button>(R.id.btnNotifications).setOnClickListener { requestNotifications() }
        continueButton.setOnClickListener { goToMain() }

        updateStatuses()
    }

    override fun onResume() {
        super.onResume()
        if (PermissionHelper.allGranted(this)) {
            goToMain()
        }
    }

    private fun requestBluetooth() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            // Для API < 31 BLE-сканирование требует ACCESS_FINE_LOCATION
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (perms.isEmpty()) {
            Toast.makeText(this, "Bluetooth разрешение не требуется", Toast.LENGTH_SHORT).show()
            return
        }
        permissionLauncher.launch(perms)
    }

    private fun requestFiles() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(perms)
    }

    private fun requestNotifications() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

        if (perms.isEmpty()) {
            // На API < 33 разрешение не требуется — просто обновим статус
            updateStatuses()
            return
        }
        permissionLauncher.launch(perms)
    }

    private fun updateStatuses() {
        bluetoothStatus.text = "Bluetooth: ${statusForBluetooth()}"
        storageStatus.text = "Файлы: ${statusForStorage()}"
        notificationStatus.text = "Уведомления: ${statusForNotifications()}"

        val enabled = PermissionHelper.allGranted(this)
        continueButton.isEnabled = enabled
        continueButton.alpha = if (enabled) 1f else 0.6f
        if (enabled) {
            goToMain()
        }
    }

    private fun statusForBluetooth(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isGranted(Manifest.permission.BLUETOOTH_SCAN) &&
                isGranted(Manifest.permission.BLUETOOTH_CONNECT)
            ) "разрешено" else "нужно разрешить"
        } else {
            if (isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) "разрешено" else "нужно разрешить"
        }
    }

    private fun statusForStorage(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isGranted(Manifest.permission.READ_MEDIA_VIDEO)) "разрешено" else "нужно разрешить"
        } else {
            if (isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) "разрешено" else "нужно разрешить"
        }
    }

    private fun statusForNotifications(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isGranted(Manifest.permission.POST_NOTIFICATIONS)) "разрешено" else "нужно разрешить"
        } else {
            "не требуется"
        }
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun goToMain() {
        if (navigating) return
        navigating = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

