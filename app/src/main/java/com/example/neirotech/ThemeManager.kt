package com.example.neirotech

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    private const val PREFS = "theme_prefs"
    private const val KEY_PALETTE = "palette" // "ocean" | "sunset" | "neon"
    private const val KEY_NIGHT = "night_mode" // true | false

    fun applyTheme(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val palette = prefs.getString(KEY_PALETTE, "ocean")
        val night = prefs.getBoolean(KEY_NIGHT, false)

        AppCompatDelegate.setDefaultNightMode(
            if (night) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        val themeRes = when (palette) {
            "sunset" -> R.style.Theme_NeiroTech_Sunset
            "neon" -> R.style.Theme_NeiroTech_Neon
            else -> R.style.Theme_NeiroTech_Ocean
        }
        activity.setTheme(themeRes)
    }

    fun setPalette(context: Context, palette: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PALETTE, palette)
            .apply()
    }

    fun setNightMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NIGHT, enabled)
            .apply()
    }

    fun currentPalette(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PALETTE, "ocean") ?: "ocean"

    fun isNight(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_NIGHT, false)
}

