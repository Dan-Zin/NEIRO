package com.example.neirotech

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {
    private var statusView: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        statusView = view.findViewById(R.id.cardDeviceStatus)

        view.findViewById<TextView>(R.id.cardQuickStartAction).setOnClickListener {
            startActivity(Intent(requireContext(), SessionSetupActivity::class.java))
        }
        view.findViewById<TextView>(R.id.cardDeviceStatus).setOnClickListener {
            startActivity(Intent(requireContext(), DeviceScanActivity::class.java))
        }
        view.findViewById<TextView>(R.id.cardLastSessionAction).setOnClickListener {
            startActivity(Intent(requireContext(), SessionAnalysisActivity::class.java))
        }
        view.findViewById<TextView>(R.id.cardRecommendationsAction).setOnClickListener {
            startActivity(Intent(requireContext(), SessionAnalysisActivity::class.java))
        }
        view.findViewById<TextView>(R.id.cardStatsAction).setOnClickListener {
            startActivity(Intent(requireContext(), SessionAnalysisActivity::class.java))
        }
        view.findViewById<View>(R.id.btnNotifications).setOnClickListener {
            startActivity(Intent(requireContext(), ExportReportActivity::class.java))
        }
        updateStatus()
        return view
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val connected = ConnectionManager.isConnected()
        val name = ConnectionManager.getName() ?: "не подключено"
        statusView?.text = if (connected) "Статус: $name" else "Статус: не подключено"
    }
}

