package com.example.hanotifier

import android.graphics.Color
import android.content.Intent
import android.net.Uri
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

class NotificationAdapter(
    private var items: List<NotificationEntity>,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    private val selectedIds = mutableSetOf<Long>()
    private val timeFormat = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvTitle)
        val message: TextView = view.findViewById(R.id.tvMessage)
        val time: TextView = view.findViewById(R.id.tvTime)
        val cameraLink: TextView = view.findViewById(R.id.tvCameraLink)
        val cameraLink2: TextView = view.findViewById(R.id.tvCameraLink2)
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

        if (!item.cameraUrl.isNullOrBlank()) {
            holder.cameraLink.visibility = View.VISIBLE
            holder.cameraLink.text = item.cameraName?.takeIf { it.isNotBlank() } ?: "📷 Ver câmera"
            holder.cameraLink.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.cameraUrl))
                holder.itemView.context.startActivity(intent)
            }
        } else {
            holder.cameraLink.visibility = View.GONE
            holder.cameraLink.setOnClickListener(null)
        }

        if (!item.cameraUrl2.isNullOrBlank()) {
            holder.cameraLink2.visibility = View.VISIBLE
            holder.cameraLink2.text = item.cameraName2?.takeIf { it.isNotBlank() } ?: "📷 Ver câmera 2"
            holder.cameraLink2.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.cameraUrl2))
                holder.itemView.context.startActivity(intent)
            }
        } else {
            holder.cameraLink2.visibility = View.GONE
            holder.cameraLink2.setOnClickListener(null)
        }

        if (!item.imageUrl.isNullOrBlank()) {
            holder.image.visibility = View.VISIBLE
            Glide.with(holder.image.context)
                .load(item.imageUrl)
                .into(holder.image)
        } else {
            holder.image.visibility = View.GONE
        }

        val isSelected = selectedIds.contains(item.id)

        if (isSelected) {
            holder.itemView.setBackgroundResource(R.drawable.item_selected_bg)
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
        }

        holder.itemView.setOnLongClickListener {
            toggleSelection(item.id)
            true
        }

        holder.itemView.setOnClickListener {
            if (selectedIds.isNotEmpty()) {
                toggleSelection(item.id)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<NotificationEntity>) {
        items = newItems

        val validIds = newItems.map { it.id }.toSet()
        selectedIds.retainAll(validIds)

        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun getSelectedIds(): List<Long> {
        return selectedIds.toList()
    }

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    private fun toggleSelection(id: Long) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }

        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }
}
