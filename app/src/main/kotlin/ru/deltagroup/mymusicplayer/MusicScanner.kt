package ru.deltagroup.mymusicplayer

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.C
import java.util.Locale

object MusicScanner {
    data class ScanResult(val songs: List<Song>, val totalSizeBytes: Long)

    fun scan(context: Context): ScanResult {
        val songs = mutableListOf<Song>()
        var totalSize = 0L
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000"

        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val title = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown"
                val artist = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown"
                val album = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unknown"
                val durationMs = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                val sizeBytes = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE))
                val filePath = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                val albumId = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))

                val artUri = if (albumId > 0) {
                    ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), 
                        albumId
                    ).toString()
                } else {
                    null
                }

                val safeDuration = calculateSafeDuration(durationMs, filePath, sizeBytes)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                
                songs.add(Song(
                    title = title,
                    artist = artist,
                    album = album,
                    duration = formatTime(safeDuration),
                    fileUri = uri.toString(),
                    albumArtUri = artUri
                ))
                totalSize += sizeBytes
            }
        }
        return ScanResult(songs, totalSize)
    }

    private fun calculateSafeDuration(metadataDuration: Long, path: String?, size: Long): Long {
        if (metadataDuration > 0 && metadataDuration != C.TIME_UNSET) return metadataDuration
        if (path != null && size > 0) {
            try {
                val bitrate = if (path.endsWith(".flac", true)) 768_000L else 192_000L
                val durationMs = (size * 8L * 1000L) / bitrate
                if (durationMs in 1_000..86_400_000L) return durationMs
            } catch (_: Exception) {}
        }
        return 0L
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "--:--"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) 
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        else 
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}