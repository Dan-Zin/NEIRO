package com.example.neirotech

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class HistoryFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        view.findViewById<View>(R.id.sampleSessionCard)?.setOnClickListener {
            startActivity(Intent(requireContext(), SessionDetailActivity::class.java))
        }
        return view
    }
}

