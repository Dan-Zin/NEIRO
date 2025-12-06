package com.example.neirotech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class SessionsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_sessions, container, false)
        view.findViewById<View>(R.id.btnStartNewSession)?.setOnClickListener {
            startActivity(Intent(requireContext(), SessionSetupActivity::class.java))
        }
        view.findViewById<View>(R.id.btnScanDevice)?.setOnClickListener {
            startActivity(Intent(requireContext(), DeviceScanActivity::class.java))
        }
        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)

        view.findViewById<View>(R.id.btnDebugBrainBit)?.setOnClickListener {
            val fakeMetrics = prefs.getBoolean(SessionSetupActivity.PREF_FAKE_METRICS, false)
            val intent = Intent(requireContext(), LiveMonitoringActivity::class.java).apply {
                putExtra(SessionSetupActivity.EXTRA_NAME, "Отладка BrainBit")
                putExtra(SessionSetupActivity.EXTRA_SOURCE, "debug")
                putExtra(SessionSetupActivity.EXTRA_URI, null as String?)
                putExtra(SessionSetupActivity.EXTRA_YOUTUBE, null as String?)
                putStringArrayListExtra(SessionSetupActivity.EXTRA_TAGS, arrayListOf<String>())
                putExtra(SessionSetupActivity.EXTRA_AUTOSAVE, false)
                putExtra(SessionSetupActivity.EXTRA_FAKE_METRICS, fakeMetrics)
            }
            startActivity(intent)
        }
        return view
    }
}

