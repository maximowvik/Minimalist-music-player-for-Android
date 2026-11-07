package ru.deltagroup.mymusicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : AppCompatActivity() {
    private lateinit var mediaController: MediaController
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    
    // Ссылки на View для оптимизации
    private lateinit var btnMiniPlayPause: ImageButton
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var imgMiniArt: ImageView
    private lateinit var progressBarMini: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private var songAdapter: SongAdapter? = null
    
    private val miniProgressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    
    private val updateMiniProgress = object : Runnable {
        override fun run() {
            if (::mediaController.isInitialized) {
                val duration = mediaController.duration
                if (duration > 0 && duration != androidx.media3.common.C.TIME_UNSET) {
                    val position = mediaController.currentPosition.coerceAtLeast(0)
                    val progress = (position * 1000L / duration).toInt().coerceIn(0, 1000)
                    progressBarMini.progress = progress
                    progressBarMini.isIndeterminate = false
                } else {
                    progressBarMini.isIndeterminate = true
                }
                miniProgressHandler.postDelayed(this, 200)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Программная установка цвета статус-бара (гарантированно работает)
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        // Инициализация View
        btnMiniPlayPause = findViewById(R.id.ivMiniPlayPause)
        tvMiniTitle = findViewById(R.id.tvMiniTitle)
        tvMiniArtist = findViewById(R.id.tvMiniArtist)
        imgMiniArt = findViewById(R.id.imgMiniArt)
        progressBarMini = findViewById(R.id.progressBarMini)
        recyclerView = findViewById(R.id.recyclerView)

        checkPermissionAndScan()

        // Клик по мини-плееру открывает PlayerActivity
        findViewById<LinearLayout>(R.id.miniPlayer).setOnClickListener {
            if (::mediaController.isInitialized && mediaController.mediaItemCount > 0) {
                startActivity(Intent(this, PlayerActivity::class.java))
            } else {
                Toast.makeText(this, "Сначала выберите песню из списка", Toast.LENGTH_SHORT).show()
            }
        }

        // Кнопка Play/Pause в мини-плеере
        btnMiniPlayPause.setOnClickListener {
            if (::mediaController.isInitialized) {
                if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            //PlaylistManager.selectedTrackIndex = mediaController.currentMediaItemIndex.takeIf { it >=0 } ?: -1
            updateMiniPlayerUI()
            miniProgressHandler.post(updateMiniProgress)
            
            // Создаем адаптер один раз при подключении
            if (PlaylistManager.songs.isNotEmpty()) {
                setupAdapter()
            }

            // ГЛАВНЫЙ СЛУШАТЕЛЬ ДЛЯ СИНХРОНИЗАЦИИ
            mediaController.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateMiniPlayerUI()
                }
                
                override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                    updateMiniPlayerUI()
                }
                
                // Вызывается ПРИ КАЖДОМ переключении трека (автоматически или вручную)
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    // Синхронизируем индекс выбранного трека
                    PlaylistManager.selectedTrackIndex = mediaController.currentMediaItemIndex.takeIf { it >= 0 } ?: -1
                    
                    // Обновляем рамку в списке без пересоздания адаптера
                    songAdapter?.updatePlayingIndex(PlaylistManager.selectedTrackIndex)
                    
                    // Обновляем данные мини-плеера
                    updateMiniPlayerUI()
                }
            })
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
                        val mediaItems = PlaylistManager.getMediaItems()
                        mediaController.setMediaItems(mediaItems)
                        mediaController.prepare()
                        mediaController.seekToDefaultPosition(index)
                        mediaController.play()
                        
                        // Сразу обновляем рамку при клике
                        songAdapter?.updatePlayingIndex(index)
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
        
        val currentIndex = PlaylistManager.selectedTrackIndex
        val song = PlaylistManager.songs.getOrNull(currentIndex)
        
        val meta = mediaController.mediaMetadata
        
        // Приоритет данных: сначала из локального списка, потом из метаданных плеера
        val title = song?.title ?: meta.title ?: "Нет музыки"
        val artist = song?.artist ?: meta.artist ?: ""
        
        // Приоритет обложки: из Song (надежнее), иначе из метаданных
        val artworkUri = song?.albumArtUri ?: meta.artworkUri?.toString()

        tvMiniTitle.text = title
        tvMiniArtist.text = artist
        
        // Надежная загрузка обложки
        imgMiniArt.load(artworkUri) {
            crossfade(true)
            placeholder(R.drawable.ic_not_image)
            error(R.drawable.ic_not_image)
            allowHardware(false)
            transformations(coil.transform.RoundedCornersTransformation(16f * resources.displayMetrics.density))
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
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) loadMusic()
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