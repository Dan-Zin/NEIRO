package com.example.neirotech

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
        view.findViewById<View>(R.id.btnDebugBrainBit)?.setOnClickListener {
            val intent = Intent(requireContext(), LiveMonitoringActivity::class.java).apply {
                putExtra(SessionSetupActivity.EXTRA_NAME, "Отладка BrainBit")
                putExtra(SessionSetupActivity.EXTRA_SOURCE, "debug")
                putExtra(SessionSetupActivity.EXTRA_URI, null as String?)
                putExtra(SessionSetupActivity.EXTRA_YOUTUBE, null as String?)
                putStringArrayListExtra(SessionSetupActivity.EXTRA_TAGS, arrayListOf<String>())
                putExtra(SessionSetupActivity.EXTRA_AUTOSAVE, false)
            }
            startActivity(intent)
        }
        return view
    }
}

