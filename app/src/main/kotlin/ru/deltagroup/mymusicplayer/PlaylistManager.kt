package ru.deltagroup.mymusicplayer

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

object PlaylistManager {
    var songs: List<Song> = emptyList()
    var selectedTrackIndex: Int = -1 

    fun getMediaItems(): List<MediaItem> = songs.mapNotNull { song ->
        song.fileUri?.let { uriString ->
            val artworkUri = if (!song.albumArtUri.isNullOrBlank()) {
                Uri.parse(song.albumArtUri)
            } else {
                null
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