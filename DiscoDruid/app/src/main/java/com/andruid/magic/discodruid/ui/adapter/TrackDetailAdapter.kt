package com.andruid.magic.discodruid.ui.adapter

import android.util.Log
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.andruid.magic.discodruid.ui.viewholder.TrackDetailViewHolder
import com.andruid.magic.medialoader.model.Track

private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Track>() {
    override fun areItemsTheSame(oldItem: Track, newItem: Track) =
        oldItem.audioId == newItem.audioId

    override fun areContentsTheSame(oldItem: Track, newItem: Track) =
        oldItem == newItem
}

class TrackDetailAdapter : ListAdapter<Track, TrackDetailViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        TrackDetailViewHolder.from(parent)

    override fun onBindViewHolder(holder: TrackDetailViewHolder, position: Int) {
        getItem(position)?.let { track ->
            Log.d("queueLog", "binding ${track.title}")
            holder.bind(track) }
    }
}