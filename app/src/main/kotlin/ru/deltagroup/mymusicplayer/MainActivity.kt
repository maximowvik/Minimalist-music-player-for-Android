package ru.deltagroup.mymusicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : AppCompatActivity() {
    private lateinit var mediaController: MediaController
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    
    private lateinit var btnMiniPlayPause: ImageButton
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var imgMiniArt: ImageView
    private lateinit var progressBarMini: LinearProgressIndicator
    private lateinit var recyclerView: RecyclerView
    private var songAdapter: SongAdapter? = null
    
    private val miniProgressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    
    private val updateMiniProgress = object : Runnable {
        override fun run() {
            if (::mediaController.isInitialized) {
                val duration = mediaController.duration
                val position = mediaController.currentPosition

                if (duration > 1000L && position >= 0L) {
                    val fraction = position.toFloat() / duration.toFloat()
                    val safeFraction = fraction.coerceIn(0.0f, 1.0f)
                    val progress = (safeFraction * 100).toInt()
                    progressBarMini.progress = progress
                }
            }
            miniProgressHandler.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        btnMiniPlayPause = findViewById(R.id.ivMiniPlayPause)
        tvMiniTitle = findViewById(R.id.tvMiniTitle)
        tvMiniArtist = findViewById(R.id.tvMiniArtist)
        imgMiniArt = findViewById(R.id.imgMiniArt)
        progressBarMini = findViewById(R.id.progressBarMini)
        recyclerView = findViewById(R.id.recyclerView)

        checkPermissionAndScan()

        // Клик по мини-плееру (используем View, так как это MaterialCardView)
        findViewById<View>(R.id.miniPlayer).setOnClickListener {
            if (::mediaController.isInitialized && mediaController.mediaItemCount > 0) {
                startActivity(Intent(this, PlayerActivity::class.java))
            } else {
                Toast.makeText(this, "Сначала выберите песню из списка", Toast.LENGTH_SHORT).show()
            }
        }

        btnMiniPlayPause.setOnClickListener {
            if (::mediaController.isInitialized) {
                if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        
        // Очищаем очередь перед запуском
        miniProgressHandler.removeCallbacks(updateMiniProgress)
        
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            
            if (mediaController.playbackState != Player.STATE_IDLE && 
                mediaController.currentMediaItemIndex >= 0) {
                PlaylistManager.selectedTrackIndex = mediaController.currentMediaItemIndex
            }
            
            songAdapter?.updatePlayingIndex(PlaylistManager.selectedTrackIndex)
            updateMiniPlayerUI()
            miniProgressHandler.post(updateMiniProgress)
            
            if (PlaylistManager.songs.isNotEmpty()) {
                setupAdapter()
            }

            mediaController.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateMiniPlayerUI()
                }
                
                override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                    updateMiniPlayerUI()
                }
                
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    PlaylistManager.selectedTrackIndex = mediaController.currentMediaItemIndex.takeIf { it >= 0 } ?: -1
                    songAdapter?.updatePlayingIndex(PlaylistManager.selectedTrackIndex)
                    updateMiniPlayerUI()
                    
                    if (PlaylistManager.selectedTrackIndex >= 0) {
                        recyclerView.scrollToPosition(PlaylistManager.selectedTrackIndex)
                    }
                }
                
                // Исправление бага: обновляем прогресс, когда трек готов
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        updateMiniPlayerUI()
                        updateMiniProgress.run()
                    }
                }
            })
            
            recyclerView.post {
                if (PlaylistManager.selectedTrackIndex >= 0) {
                    recyclerView.scrollToPosition(PlaylistManager.selectedTrackIndex)
                }
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        super.onStop()
        miniProgressHandler.removeCallbacks(updateMiniProgress)
        MediaController.releaseFuture(controllerFuture)
    }

    private fun setupAdapter() {
        if (PlaylistManager.songs.isNotEmpty()) {
            songAdapter = SongAdapter(
                PlaylistManager.songs, 
                { clickedSong ->
                    val index = PlaylistManager.songs.indexOf(clickedSong)
                    if (index >= 0 && ::mediaController.isInitialized) {
                        PlaylistManager.selectedTrackIndex = index
                        val mediaItems = PlaylistManager.getMediaItems(this)
                        mediaController.setMediaItems(mediaItems)
                        mediaController.prepare()
                        mediaController.seekToDefaultPosition(index)
                        mediaController.play()
                        
                        songAdapter?.updatePlayingIndex(index)
                        recyclerView.smoothScrollToPosition(index)
                    }
                },
                PlaylistManager.selectedTrackIndex
            )
            recyclerView.adapter = songAdapter
            recyclerView.layoutManager = LinearLayoutManager(this)
        }
    }

    private fun updateMiniPlayerUI() {
        if (!::mediaController.isInitialized) return
        
        val currentIndex = mediaController.currentMediaItemIndex.takeIf { it >= 0 } ?: -1
        val song = PlaylistManager.songs.getOrNull(currentIndex)
        val meta = mediaController.mediaMetadata
        
        val title = song?.title ?: meta.title ?: "No track selected..."
        val artist = song?.artist ?: meta.artist ?: ""
        val artworkUri = song?.albumArtUri ?: meta.artworkUri?.toString()
    
        tvMiniTitle.text = title
        tvMiniArtist.text = artist
        
        imgMiniArt.load(artworkUri) {
            crossfade(true)
            placeholder(R.drawable.ic_not_image)
            error(R.drawable.ic_not_image)
            allowHardware(false)
            transformations(RoundedCornersTransformation(16f * resources.displayMetrics.density))
        }
        
        btnMiniPlayPause.setImageResource(
            if (mediaController.isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_arrow_24
        )
    }

    private fun checkPermissionAndScan() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            loadMusic()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(perm), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadMusic()
        }
    }

    private fun loadMusic() {
        Thread {
            val result = MusicScanner.scan(this)
            runOnUiThread {
                PlaylistManager.songs = result.songs
                findViewById<TextView>(R.id.tvSongCount).text = "Песен: ${result.songs.size}"
                findViewById<TextView>(R.id.tvTotalSize).text = "Вес: ${formatSize(result.totalSizeBytes)}"
                setupAdapter()
            }
        }.start()
    }

    private fun formatSize(bytes: Long): String = "%.1f MB".format(bytes / (1024.0 * 1024.0))
}