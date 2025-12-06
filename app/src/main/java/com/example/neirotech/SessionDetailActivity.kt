package com.example.neirotech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SessionDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        setContentView(R.layout.activity_session_detail)
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, SessionDetailActivity::class.java)
    }
}

