package com.callrecorder.cleaner.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.callrecorder.cleaner.R
import com.callrecorder.cleaner.model.Recording
import java.text.SimpleDateFormat
import java.util.*

class RecordingAdapter(
    private val recordings: MutableList<Recording>
) : RecyclerView.Adapter<RecordingAdapter.RecordingViewHolder>() {
    
    private val matchedPaths = mutableSetOf<String>()
    private val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

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
        holder.dateText.text = sdf.format(recording.date.time)
        holder.sizeText.text = recording.fileSize
        
        val isMatched = matchedPaths.contains(recording.file.absolutePath)
        holder.matchedBadge.visibility = if (isMatched) View.VISIBLE else View.GONE
        
        holder.itemView.setBackgroundColor(
            if (isMatched) Color.parseColor("#FFF3CD") else Color.WHITE
        )
    }

    override fun getItemCount(): Int = recordings.size

    fun updateMatched(paths: List<String>) {
        matchedPaths.clear()
        matchedPaths.addAll(paths)
        notifyDataSetChanged()
    }
}
