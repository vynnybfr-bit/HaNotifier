package com.example.hanotifier

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(private var items: List<NotificationEntity>) :
    RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvTitle)
        val message: TextView = view.findViewById(R.id.tvMessage)
        val time: TextView = view.findViewById(R.id.tvTime)
        val image: ImageView = view.findViewById(R.id.ivImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.message.text = item.message
        holder.time.text = timeFormat.format(Date(item.timestamp))

        if (!item.imageUrl.isNullOrBlank()) {
            holder.image.visibility = View.VISIBLE
            Glide.with(holder.image.context)
                .load(item.imageUrl)
                .into(holder.image)
        } else {
            holder.image.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<NotificationEntity>) {
        items = newItems
        notifyDataSetChanged()
    }
}
