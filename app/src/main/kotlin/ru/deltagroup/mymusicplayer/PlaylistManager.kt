package ru.deltagroup.mymusicplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File
import java.io.FileOutputStream

object PlaylistManager {
    var songs: List<Song> = emptyList()
    var selectedTrackIndex: Int = -1 

    // Кэшируем URI, чтобы не создавать файл каждый раз
    private var defaultArtworkUri: Uri? = null

    /**
     * Создает реальный файл в кэше из drawable и возвращает file:// URI.
     * Вызывается только один раз за время жизни процесса.
     */
    private fun getDefaultArtworkUri(context: Context): Uri {
        if (defaultArtworkUri != null) return defaultArtworkUri!!

        val fileName = "default_track_art.png"
        val file = File(context.cacheDir, fileName)

        if (!file.exists()) {
            try {
                // Получаем bitmap из нашего drawable
                val drawable = ContextCompat.getDrawable(context, R.drawable.ic_not_image)
                val bitmap = (drawable as BitmapDrawable).bitmap
                
                // Сохраняем его во временную папку приложения как PNG
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        defaultArtworkUri = Uri.fromFile(file)
        return defaultArtworkUri!!
    }

    fun getMediaItems(context: Context): List<MediaItem> {
        // Получаем надежный file:// URI для нашей заглушки
        val fallbackUri = getDefaultArtworkUri(context)
        
        return songs.mapNotNull { song ->
            song.fileUri?.let { uriString ->
                // Если у трека есть обложка – берем её, если нет – подставляем наш файл
                val artworkUri = if (!song.albumArtUri.isNullOrBlank()) {
                    Uri.parse(song.albumArtUri)
                } else {
                    fallbackUri
                }

                MediaItem.Builder()
                    .setUri(Uri.parse(uriString))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(artworkUri)
                            .build()
                    )
                    .build()
            }
        }
    }
}