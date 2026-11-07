package ru.deltagroup.mymusicplayer

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation

// Класс данных для песни
data class Song(
    val title: String, 
    val artist: String, 
    val album: String, 
    val duration: String,
    val albumArtUri: String? = null,
    val fileUri: String? = null
)

class SongAdapter(
    private val songs: List<Song>, 
    private val onItemClick: (Song) -> Unit,
    private var currentPlayingIndex: Int = -1
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgAlbumArt: ImageView = view.findViewById(R.id.imgAlbumArt)
        val tvTitle: TextView = view.findViewById(R.id.tvSongTitle)
        val tvArtist: TextView = view.findViewById(R.id.tvArtist)
        val tvAlbum: TextView = view.findViewById(R.id.tvAlbum)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.tvTitle.text = song.title
        holder.tvArtist.text = song.artist
        holder.tvAlbum.text = song.album
        holder.tvDuration.text = song.duration
        
        // Получаем радиус скругления (должен совпадать с XML)
        val radius = 16f * holder.itemView.resources.displayMetrics.density
        
        // Получаем цвет фона из темы Material Design
        val cardColor = try {
            val typedValue = android.util.TypedValue()
            holder.itemView.context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurfaceVariant, 
                typedValue, 
                true
            )
            typedValue.data
        } catch (e: Exception) {
            Color.parseColor("#E0E0E0") // Запасной цвет, если атрибут не найден
        }

        if (position == currentPlayingIndex) {
            // --- АКТИВНЫЙ ТРЕК (Фон + Зеленая рамка) ---
            val strokeWidth = 6
            
            // Слой 1: Цветной фон
            val bgLayer = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radius
                setColor(cardColor)
            }
            
            // Слой 2: Рамка
            val borderLayer = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radius
                setStroke(strokeWidth, Color.parseColor("#4CAF50")) // Зеленый цвет рамки
                setColor(Color.TRANSPARENT)
            }
            
            // Объединяем слои
            holder.itemView.background = LayerDrawable(arrayOf(bgLayer, borderLayer))
            holder.itemView.alpha = 1.0f
            
        } else {
            // --- ОБЫЧНЫЙ ТРЕК (Только фон) ---
            val bgOnly = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radius
                setColor(cardColor)
            }
            
            holder.itemView.background = bgOnly
            holder.itemView.alpha = 0.8f
        }
        
        // Загрузка обложки
        holder.imgAlbumArt.load(song.albumArtUri) {
            crossfade(true)
            placeholder(R.drawable.ic_not_image)
            error(R.drawable.ic_not_image)
            allowHardware(false)
            transformations(RoundedCornersTransformation(8f * holder.itemView.resources.displayMetrics.density))
        }

        holder.itemView.setOnClickListener { onItemClick(song) }
    }

    override fun getItemCount() = songs.size
    
    // Метод для обновления выделения без пересоздания списка
    fun updatePlayingIndex(newIndex: Int) {
        val oldIndex = currentPlayingIndex
        currentPlayingIndex = newIndex
        if (oldIndex >= 0) notifyItemChanged(oldIndex)
        if (newIndex >= 0) notifyItemChanged(newIndex)
    }
}