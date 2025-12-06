package com.example.neirotech

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val imageRes: Int
)

class OnboardingAdapter(
    private val items: List<OnboardingPage>
) : RecyclerView.Adapter<OnboardingAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.onboardingTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.onboardingSubtitle)
        private val image: ImageView = itemView.findViewById(R.id.onboardingImage)

        fun bind(item: OnboardingPage) {
            title.text = item.title
            subtitle.text = item.subtitle
            image.setImageResource(item.imageRes)
        }
    }
}

