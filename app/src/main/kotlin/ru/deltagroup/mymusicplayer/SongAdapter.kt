package ru.deltagroup.mymusicplayer

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.card.MaterialCardView

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
        
        // Приводим корневой элемент к MaterialCardView
        val card = holder.itemView as MaterialCardView
        val context = card.context
        
        // Получаем базовый цвет карточки из темы
        val cardColor = try {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurfaceContainerLow, 
                typedValue, 
                true
            )
            typedValue.data
        } catch (e: Exception) {
            // Fallback цвет, если атрибут не найден
            androidx.core.content.ContextCompat.getColor(context, android.R.color.system_neutral1_900)
        }

        if (position == currentPlayingIndex) {
            // --- АКТИВНЫЙ ТРЕК (Цветной фон + Яркая рамка Primary) ---
            card.setCardBackgroundColor(cardColor)
            card.strokeColor = context.getThemeColor(com.google.android.material.R.attr.colorPrimary)
            card.strokeWidth = 6 // Толстая рамка (≈ 2dp)
            card.alpha = 1.0f
        } else {
            // --- ОБЫЧНЫЙ ТРЕК (Цветной фон + Тонкая обводка Outline) ---
            card.setCardBackgroundColor(cardColor)
            card.strokeColor = context.getThemeColor(com.google.android.material.R.attr.colorOutlineVariant)
            card.strokeWidth = 3 // Тонкая рамка (≈ 1dp)
            card.alpha = 0.95f
        }
        
        // Загрузка обложки через Coil
        // Скругление уже задано в XML через shapeAppearanceOverlay, 
        // но дополнительная трансформация не помешает
        holder.imgAlbumArt.load(song.albumArtUri) {
            crossfade(true)
            placeholder(R.drawable.ic_not_image)
            error(R.drawable.ic_not_image)
            allowHardware(false)
            transformations(
                RoundedCornersTransformation(
                    8f * context.resources.displayMetrics.density
                )
            )
        }

        // Клик по всей карточке
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