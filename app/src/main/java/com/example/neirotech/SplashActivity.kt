package com.example.neirotech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val navigateRunnable = Runnable { routeFromSplash() }
    private lateinit var logoView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        setContentView(R.layout.activity_splash)
        logoView = findViewById(R.id.logoView)
        Glide.with(this)
            .asGif()
            .load(R.raw.logo)
            .into(logoView)
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(navigateRunnable, 1200)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(navigateRunnable)
    }

    private fun routeFromSplash() {
        val destination = if (!isOnboardingFinished()) {
            OnboardingActivity::class.java
        } else {
            MainActivity::class.java
        }
        startActivity(Intent(this, destination))
        finish()
    }

    private fun isOnboardingFinished(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    }

    companion object {
        const val PREFS_NAME = "neirotech_prefs"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}

