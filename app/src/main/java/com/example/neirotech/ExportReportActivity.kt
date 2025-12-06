package com.example.neirotech

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportReportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        setContentView(R.layout.activity_export_report)

        findViewById<Button>(R.id.btnGenerateShare).setOnClickListener {
            generateAndShare()
        }
    }

    private fun generateAndShare() {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(cacheDir, "report_$timestamp.txt")
        val content = buildString {
            appendLine("BrainFocus Report")
            appendLine("Дата: $timestamp")
            appendLine()
            appendLine("Сводка сессии (пример):")
            appendLine("Вовлеченность: 78%")
            appendLine("Пики: 02:15 94%, 04:30 89%, 01:00 87%")
            appendLine("Артефакты: 6%  Шум: 8%")
            appendLine("Источник: ${intent.getStringExtra(SessionSetupActivity.EXTRA_SOURCE) ?: "n/a"}")
            appendLine("Видео: ${intent.getStringExtra(SessionSetupActivity.EXTRA_URI) ?: intent.getStringExtra(SessionSetupActivity.EXTRA_YOUTUBE) ?: "n/a"}")
        }
        file.writeText(content)

        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Поделиться отчётом"))
        Toast.makeText(this, "Отчёт сформирован", Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, ExportReportActivity::class.java)
    }
}

