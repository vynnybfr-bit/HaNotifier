package com.example.hanotifier

import android.graphics.Color
import android.content.Intent
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log

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
        val imageContainer: View = view.findViewById(R.id.imageContainer)
        val imageStatus: TextView = view.findViewById(R.id.tvImageStatus)
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

        // Reset the image area first because RecyclerView reuses these views.
        holder.imageContainer.visibility = View.GONE
        holder.image.visibility = View.VISIBLE
        holder.imageStatus.visibility = View.GONE
        holder.imageStatus.text = ""
        holder.image.contentDescription = null
        Glide.with(holder.image.context).clear(holder.image)

        if (!item.imageUrl.isNullOrBlank()) {
            holder.imageContainer.visibility = View.VISIBLE
            holder.imageStatus.visibility = View.VISIBLE
            holder.imageStatus.text = "Carregando foto..."
            holder.image.setOnClickListener {
                showImageFullscreen(holder.image.context, item.imageUrl)
            }

            Glide.with(holder.image.context)
                .load(item.imageUrl)
                .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<android.graphics.drawable.Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        val reason = e?.rootCauses?.firstOrNull()?.message
                            ?: e?.message
                            ?: "erro desconhecido"
                        holder.image.setImageDrawable(null)
                        holder.imageStatus.visibility = View.VISIBLE
                        holder.imageStatus.text = "❌ ERRO AO CARREGAR FOTO\n$reason"
                        holder.image.contentDescription = "Erro ao carregar foto: $reason"
                        Log.e("HaNotifierImage", "Falha ao carregar imagem: $model", e)
                        return false
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        model: Any,
                        target: Target<android.graphics.drawable.Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        holder.imageStatus.visibility = View.GONE
                        holder.image.contentDescription = null
                        Log.d("HaNotifierImage", "Imagem carregada: $model | origem=$dataSource")
                        return false
                    }
                })
                .into(holder.image)
        } else {
            holder.imageContainer.visibility = View.VISIBLE
            holder.image.visibility = View.GONE
            holder.imageStatus.visibility = View.VISIBLE
            holder.imageStatus.text = "⚠️ SEM FOTO\nA notificação não recebeu image_url"
            holder.image.setOnClickListener(null)
            Log.w("HaNotifierImage", "Notificação ${item.id} sem imageUrl")
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
    private fun showImageFullscreen(context: android.content.Context, imageUrl: String) {
        val dialog = Dialog(context)
        val imageView = ImageView(context)
        imageView.setBackgroundColor(Color.BLACK)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER

        Glide.with(context)
            .load(imageUrl)
            .into(imageView)

        imageView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(imageView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        dialog.window?.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT
        )
        dialog.show()
        dialog.window?.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT
        )
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
