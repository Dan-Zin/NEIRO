package com.example.neirotech

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SessionAnalysisActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        setContentView(R.layout.activity_session_analysis)

        val sessions = SessionStorage.getSessions(this)
        val total = sessions.size
        val last = sessions.maxByOrNull { it.startTime }

        val title = findViewById<TextView?>(R.id.analysisTitle)
        val subtitle = findViewById<TextView?>(R.id.analysisSubtitle)
        val overall = findViewById<TextView?>(R.id.textOverall)
        val timeline = findViewById<TextView?>(R.id.textTimeline)
        val peaks = findViewById<TextView?>(R.id.textPeaks)
        val drops = findViewById<TextView?>(R.id.textDrops)

        title?.text = "Анализ сессии"
        subtitle?.text = last?.let { "${it.name} • ${formatDate(it.startTime)}" } ?: "Нет данных — завершите хотя бы одну сессию"

        if (last == null) {
            overall?.text = "Общий показатель вовлеченности: нет данных"
            timeline?.text = "Нет данных"
            peaks?.text = "Нет данных"
            drops?.text = "Нет данных"
        } else {
            val overallScore = (last.avgAttention ?: last.lastAttention ?: 0.0).coerceAtLeast(0.0)
            val overallLabel = labelScore(overallScore * 3)
            overall?.text = "Общий показатель вовлеченности: ${fmtPercent(overallScore * 3)} ($overallLabel)"

            val durationText = last.durationMs?.let { "Длительность: ${fmtDuration(it)}" } ?: "Длительность: —"
            timeline?.text = durationText

            peaks?.text = buildPeaks(last)
            drops?.text = buildDrops(last)
        }

        findViewById<Button>(R.id.btnOpenReport).setOnClickListener {
            startActivity(ExportReportActivity.intent(this))
        }
        findViewById<Button>(R.id.btnOpenHistory).setOnClickListener {
            val targetId = last?.id
            startActivity(SessionDetailActivity.intent(this, targetId))
        }
    }

    private fun buildPeaks(rec: SessionRecord?): String {
        if (rec == null) return "—"
        val events = rec.events.filter { it.attention != null }.sortedByDescending { it.attention }.take(3)
        if (events.isEmpty()) {
            rec.maxAttention?.let { return "• ${fmtPercent(it)} @ ${fmtTime(rec.maxAttentionTs)}" }
            return "Нет данных"
        }
        return events.mapIndexed { idx, ev ->
            val time = fmtRelative(rec, ev.timestamp)
            "${idx + 1}) $time — ${fmtPercent(ev.attention)}"
        }.joinToString("\n")
    }

    private fun buildDrops(rec: SessionRecord?): String {
        if (rec == null) return "—"
        val events = rec.events.filter { it.attention != null }.sortedBy { it.attention }
        if (events.isNotEmpty()) {
            val first = events.first()
            val time = fmtRelative(rec, first.timestamp)
            return "• $time — ${fmtPercent(first.attention)}"
        }
        rec.minAttention?.let { return "• ${fmtPercent(it)} @ ${fmtTime(rec.minAttentionTs)}" }
        return "Нет данных"
    }

    private fun labelScore(v: Double): String = when {
        v >= 60 -> "хорошо"
        v >= 30 -> "средне"
        else -> "низко"
    }

    private fun formatDate(ts: Long): String {
        val sdf = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ts))
    }

    private fun formatTime(ts: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss dd.MM", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ts))
    }

    private fun fmtRelative(rec: SessionRecord, ts: Long): String {
        val delta = ts - rec.startTime
        if (delta < 0) return formatTime(ts)
        val totalSec = delta / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun fmtTime(ts: Long?): String = ts?.let { formatTime(it) } ?: "—"
    private fun fmtPercent(v: Double?): String = if (v != null) "${"%.1f".format(v)}%" else "—"
    private fun fmtDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%d:%02d", min, sec)
    }
}

