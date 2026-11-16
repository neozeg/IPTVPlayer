package com.workbuddy.iptvplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChannelAdapter(
    private val onClick: (Channel) -> Unit,
    private val onToggleFavorite: (Channel) -> Unit,
    private val isFavorite: (Channel) -> Boolean
) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {

    private var items: List<Channel> = emptyList()

    fun submit(list: List<Channel>) {
        items = list
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.tv_number)
        val logo: TextView = view.findViewById(R.id.tv_logo)
        val name: TextView = view.findViewById(R.id.tv_name)
        val group: TextView = view.findViewById(R.id.tv_group)
        val fav: TextView = view.findViewById(R.id.tv_fav)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = items[position]
        holder.number.text = channel.number.toString()
        holder.logo.text = channel.name.firstOrNull()?.toString()?.uppercase() ?: "▶"
        holder.name.text = channel.name
        holder.group.text = channel.group
        holder.fav.text = if (isFavorite(channel)) "★" else "☆"

        holder.itemView.setOnClickListener { onClick(channel) }
        holder.itemView.setOnLongClickListener {
            onToggleFavorite(channel)
            true
        }
        holder.fav.setOnClickListener { onToggleFavorite(channel) }
    }

    override fun getItemCount(): Int = items.size
}
