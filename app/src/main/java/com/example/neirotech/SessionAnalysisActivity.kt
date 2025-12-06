package com.example.neirotech

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SessionAnalysisActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        setContentView(R.layout.activity_session_analysis)

        findViewById<Button>(R.id.btnOpenReport).setOnClickListener {
            startActivity(ExportReportActivity.intent(this))
        }
        findViewById<Button>(R.id.btnOpenHistory).setOnClickListener {
            startActivity(SessionDetailActivity.intent(this))
        }
    }
}

