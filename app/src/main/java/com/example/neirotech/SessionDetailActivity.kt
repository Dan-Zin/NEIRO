package com.example.neirotech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SessionDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        setContentView(R.layout.activity_session_detail)

        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        val sessions = SessionStorage.getSessions(this)
        val record = when {
            sessionId != null -> SessionStorage.getSession(this, sessionId)
            else -> sessions.maxByOrNull { it.startTime }
        }

        val header = findViewById<TextView?>(resources.getIdentifier("textSessionHeader", "id", packageName))
        val listView = findViewById<ListView?>(resources.getIdentifier("listSessionEvents", "id", packageName))

        if (record == null) {
            header?.text = "Сессий пока нет"
            return
        }

        header?.text = buildString {
            append("Сессия: ${record.name}\n")
            append("Источник: ${record.source}\n")
            append("Начало: ${formatTime(record.startTime)}\n")
            record.endTime?.let { append("Конец: ${formatTime(it)}\n") }
            record.durationMs?.let { append("Длительность: ${fmtDuration(it)}\n") }
            append("Средн. внимание: ${fmtPercent(record.avgAttention)}\n")
            append("Средн. релаксация: ${fmtPercent(record.avgRelaxation)}\n")
            append("Макс внимание: ${fmtPercent(record.maxAttention)} @ ${fmtTime(record.maxAttentionTs)}\n")
            append("Макс релаксация: ${fmtPercent(record.maxRelaxation)} @ ${fmtTime(record.maxRelaxationTs)}\n")
            append("Метки: ${record.events.count { it.type == SessionEventType.MARK }} • Снимки: ${record.events.count { it.type == SessionEventType.SNAPSHOT }}")
        }

        val rows = record.events.sortedBy { it.timestamp }.map { ev ->
            val label = if (ev.type == SessionEventType.MARK) "Метка" else "Снимок"
            val att = ev.attention?.let { "Att ${"%.1f".format(it)}%" } ?: ""
            val rel = ev.relaxation?.let { "Rel ${"%.1f".format(it)}%" } ?: ""
            val time = formatTime(ev.timestamp)
            "$time • $label $att $rel"
        }

        if (listView != null) {
            listView.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        }
    }

    private fun formatTime(ts: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss dd.MM", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ts))
    }

    private fun fmtTime(ts: Long?): String = ts?.let { formatTime(it) } ?: "—"
    private fun fmtPercent(v: Double?): String = if (v != null) "${"%.1f".format(v)}%" else "—"
    private fun fmtDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%d:%02d", min, sec)
    }

    companion object {
        private const val EXTRA_SESSION_ID = "extra_session_id"
        fun intent(context: Context, sessionId: String? = null): Intent =
            Intent(context, SessionDetailActivity::class.java).apply {
                if (sessionId != null) putExtra(EXTRA_SESSION_ID, sessionId)
            }
    }
}

