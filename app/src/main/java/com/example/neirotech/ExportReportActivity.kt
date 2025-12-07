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
        // 1. Получаем реальные данные последней сессии
        val sessions = SessionStorage.getSessions(this)
        val lastSession = sessions.maxByOrNull { it.startTime }

        if (lastSession == null) {
            Toast.makeText(this, "Нет завершенных сессий для отчета", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Формируем имя файла и дату отчета
        val reportDate = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val fileName = "BrainFocus_Report_$reportDate.txt"
        val file = File(cacheDir, fileName)

        // 3. Собираем контент на основе lastSession
        val content = buildString {
            appendLine("=== ОТЧЕТ BRAINFOCUS ===")
            appendLine("Дата формирования: ${formatFullDate(System.currentTimeMillis())}")
            appendLine()

            appendLine("--- Информация о сессии ---")
            appendLine("Название: ${lastSession.name}")
            appendLine("Начало: ${formatFullDate(lastSession.startTime)}")
            val duration = lastSession.durationMs ?: 0L
            appendLine("Длительность: ${fmtDuration(duration)}")
            appendLine()

            appendLine("--- Статистика ---")
            // Используем avgAttention напрямую, без умножения (как договорились ранее)
            val avg = lastSession.avgAttention ?: 0.0
            appendLine("Средняя вовлеченность: ${fmtPercent(avg)}")
            appendLine("Максимум: ${fmtPercent(lastSession.maxAttention)}")
            appendLine("Минимум: ${fmtPercent(lastSession.minAttention)}")
            appendLine()

            appendLine("--- Пики внимания (Топ-3) ---")
            val peaks = lastSession.events
                .filter { it.attention != null }
                .sortedByDescending { it.attention }
                .take(3)

            if (peaks.isEmpty()) {
                appendLine("Нет данных")
            } else {
                peaks.forEachIndexed { index, event ->
                    val relativeTime = fmtRelative(lastSession.startTime, event.timestamp)
                    appendLine("${index + 1}. $relativeTime — ${fmtPercent(event.attention)}")
                }
            }
            appendLine()

            appendLine("--- Данные событий ---")
            appendLine("Всего записано точек: ${lastSession.events.size}")
            // Если нужно, можно добавить CSV-подобный дамп всех точек ниже
            // lastSession.events.forEach { ... }
        }

        // 4. Записываем в файл
        try {
            file.writeText(content)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка записи файла", Toast.LENGTH_SHORT).show()
            return
        }

        // 5. Шарим файл через FileProvider
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Отчет BrainFocus: ${lastSession.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Поделиться отчётом"))
    }

    // --- Вспомогательные методы форматирования (аналогичны SessionAnalysisActivity) ---

    private fun formatFullDate(ts: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    private fun fmtDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%d мин %02d сек", min, sec)
    }

    private fun fmtPercent(v: Double?): String {
        return if (v != null) "%.1f%%".format(v) else "—"
    }

    private fun fmtRelative(startTs: Long, currentTs: Long): String {
        val delta = currentTs - startTs
        if (delta < 0) return "00:00"
        val totalSec = delta / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, ExportReportActivity::class.java)
    }
}