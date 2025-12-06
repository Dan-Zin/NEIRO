package com.example.neirotech

import android.os.Bundle
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        val nightSwitch = view.findViewById<Switch>(R.id.switchNightMode)
        val paletteGroup = view.findViewById<RadioGroup>(R.id.radioPalette)
        val radioNeon = view.findViewById<RadioButton>(R.id.radioNeon)
        val radioOcean = view.findViewById<RadioButton>(R.id.radioOcean)
        val radioSunset = view.findViewById<RadioButton>(R.id.radioSunset)
        val fakeSwitch = view.findViewById<Switch>(R.id.switchFakeMetricsGlobal)

        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

        nightSwitch.isChecked = ThemeManager.isNight(ctx)
        when (ThemeManager.currentPalette(ctx)) {
            "sunset" -> radioSunset.isChecked = true
            "neon" -> radioNeon.isChecked = true
            else -> radioOcean.isChecked = true
        }
        fakeSwitch.isChecked = prefs.getBoolean(SessionSetupActivity.PREF_FAKE_METRICS, false)

        nightSwitch.setOnCheckedChangeListener { _, checked ->
            ThemeManager.setNightMode(ctx, checked)
            requireActivity().recreate()
        }

        paletteGroup.setOnCheckedChangeListener { _, checkedId ->
            val palette = when (checkedId) {
                R.id.radioSunset -> "sunset"
                R.id.radioNeon -> "neon"
                else -> "ocean"
            }
            ThemeManager.setPalette(ctx, palette)
            requireActivity().recreate()
        }

        fakeSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(SessionSetupActivity.PREF_FAKE_METRICS, checked).apply()
        }

        return view
    }
}

