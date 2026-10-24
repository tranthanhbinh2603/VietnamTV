package com.vietnam.tvlive.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.vietnam.tvlive.R
import com.vietnam.tvlive.databinding.ItemChannelHorizontalBinding
import com.vietnam.tvlive.model.Channel
import com.vietnam.tvlive.model.ChannelStatus

class ChannelHorizontalAdapter(
    private var allChannels: List<Channel>,
    private val onChannelClick: (Channel, Int) -> Unit
) : RecyclerView.Adapter<ChannelHorizontalAdapter.ChannelViewHolder>() {

    // Only non-dead channels are visible
    private var visibleChannels: MutableList<Channel> = mutableListOf()

    init {
        filterVisibleChannels()
    }

    private fun filterVisibleChannels() {
        visibleChannels = allChannels.filter { it.status != ChannelStatus.DEAD }.toMutableList()
    }

    fun setChannels(newChannels: List<Channel>) {
        this.allChannels = newChannels
        filterVisibleChannels()
        notifyDataSetChanged()
    }

    fun updateChannelStatus(updated: Channel) {
        if (updated.status == ChannelStatus.DEAD) {
            val index = visibleChannels.indexOfFirst { it.streamUrl == updated.streamUrl }
            if (index >= 0) {
                visibleChannels.removeAt(index)
                notifyItemRemoved(index)
            }
        } else {
            val index = visibleChannels.indexOfFirst { it.streamUrl == updated.streamUrl }
            if (index >= 0) {
                notifyItemChanged(index)
            } else {
                filterVisibleChannels()
                notifyDataSetChanged()
            }
        }
    }

    fun setSelectedChannel(streamUrl: String) {
        visibleChannels.forEach { it.isPlaying = (it.streamUrl == streamUrl) }
        notifyDataSetChanged()
    }

    fun getVisiblePosition(streamUrl: String): Int {
        return visibleChannels.indexOfFirst { it.streamUrl == streamUrl }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelHorizontalBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(visibleChannels[position])
    }

    override fun getItemCount(): Int = visibleChannels.size

    inner class ChannelViewHolder(private val binding: ItemChannelHorizontalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(channel: Channel) {
            val context = binding.root.context
            binding.tvName.text = channel.name

            // Background highlight if currently playing
            if (channel.isPlaying) {
                binding.cardContainer.setBackgroundResource(R.drawable.bg_channel_card_selected)
            } else {
                binding.cardContainer.setBackgroundResource(R.drawable.bg_channel_card)
            }

            // Channel logo
            if (channel.logoUrl.isNotEmpty()) {
                Glide.with(context)
                    .load(channel.logoUrl)
                    .placeholder(R.drawable.ic_tv)
                    .error(R.drawable.ic_tv)
                    .into(binding.imgLogo)
            } else {
                binding.imgLogo.setImageResource(R.drawable.ic_tv)
            }

            binding.root.setOnClickListener {
                onChannelClick(channel, bindingAdapterPosition)
            }
        }
    }
}
