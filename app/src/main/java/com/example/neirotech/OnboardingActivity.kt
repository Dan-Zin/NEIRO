package com.example.neirotech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2

class OnboardingActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var indicatorLayout: LinearLayout
    private lateinit var nextButton: Button
    private lateinit var skipButton: TextView

    private val pages = listOf(
        OnboardingPage(
            title = "Отслеживайте вовлеченность мозга",
            subtitle = "Быстрые метрики внимания и фокуса в одном месте",
            imageRes = R.mipmap.ic_launcher
        ),
        OnboardingPage(
            title = "Подключите BrainBit за 60 секунд",
            subtitle = "Пошаговые подсказки для стабильного подключения",
            imageRes = R.mipmap.ic_launcher_round
        ),
        OnboardingPage(
            title = "Анализируйте видео в реальном времени",
            subtitle = "Видео + биосигналы синхронно, без постобработки",
            imageRes = R.mipmap.ic_launcher
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        setContentView(R.layout.activity_onboarding)

        pager = findViewById(R.id.onboardingPager)
        indicatorLayout = findViewById(R.id.indicatorLayout)
        nextButton = findViewById(R.id.btnNext)
        skipButton = findViewById(R.id.btnSkip)

        pager.adapter = OnboardingAdapter(pages)
        setupIndicators()
        setCurrentIndicator(0)

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                setCurrentIndicator(position)
                nextButton.text = if (position == pages.lastIndex) "Начать" else "Далее"
            }
        })

        nextButton.setOnClickListener {
            val nextIndex = pager.currentItem + 1
            if (nextIndex < pages.size) {
                pager.currentItem = nextIndex
            } else {
                markOnboardingDone()
                goNext()
            }
        }

        skipButton.setOnClickListener {
            markOnboardingDone()
            goNext()
        }
    }

    private fun goNext() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setupIndicators() {
        val indicators = Array(pages.size) { index ->
            TextView(this).apply {
                text = "•"
                textSize = 28f
                setTextColor(
                    ContextCompat.getColor(
                        this@OnboardingActivity,
                        if (index == 0) R.color.purple_500 else R.color.gray_inactive
                    )
                )
            }
        }
        indicators.forEach { indicatorLayout.addView(it) }
    }

    private fun setCurrentIndicator(position: Int) {
        for (i in 0 until indicatorLayout.childCount) {
            val view = indicatorLayout.getChildAt(i) as TextView
            val color = if (i == position) R.color.purple_500 else R.color.gray_inactive
            view.setTextColor(ContextCompat.getColor(this, color))
        }
    }

    private fun markOnboardingDone() {
        val prefs = getSharedPreferences(SplashActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(SplashActivity.KEY_ONBOARDING_COMPLETE, true).apply()
    }
}

