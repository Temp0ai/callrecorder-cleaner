package com.callrecorder.cleaner.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.callrecorder.cleaner.R
import com.callrecorder.cleaner.model.Recording

class RecordingAdapter(
    private val recordings: MutableList<Recording>
) : RecyclerView.Adapter<RecordingAdapter.RecordingViewHolder>() {

    private val matchedUris = mutableSetOf<String>()

    class RecordingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.recordingName)
        val dateText: TextView = itemView.findViewById(R.id.recordingDate)
        val sizeText: TextView = itemView.findViewById(R.id.recordingSize)
        val matchedBadge: TextView = itemView.findViewById(R.id.matchedBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recording, parent, false)
        return RecordingViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
        val recording = recordings[position]

        holder.nameText.text = recording.displayName
        holder.dateText.text = recording.dateFormatted
        holder.sizeText.text = recording.fileSizeFormatted

        val isMatched = matchedUris.contains(recording.uri.toString())
        holder.matchedBadge.visibility = if (isMatched) View.VISIBLE else View.GONE

        holder.itemView.setBackgroundColor(
            if (isMatched) Color.parseColor("#FFF3CD") else Color.WHITE
        )
    }

    override fun getItemCount(): Int = recordings.size

    fun updateMatched(uris: List<String>) {
        matchedUris.clear()
        matchedUris.addAll(uris)
        notifyDataSetChanged()
    }
}
