package ru.deltagroup.mymusicplayer

import android.content.ComponentName
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale

class PlayerActivity : AppCompatActivity() {

    private lateinit var mediaController: MediaController
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    // Views (Обновлены типы под Material 3)
    private lateinit var btnBack: ImageButton
    private lateinit var btnPlayPause: FloatingActionButton // FAB
    private lateinit var btnNext: MaterialButton          // MaterialButton
    private lateinit var btnPrev: MaterialButton          // MaterialButton
    private lateinit var seekBar: Slider
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var imgAlbum: ImageView

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    
    // Таймер для обновления прогресса
    private val updateProgressTask = object : Runnable {
        override fun run() {
            if (::mediaController.isInitialized) {
                val position = mediaController.currentPosition
                val duration = mediaController.duration

                if (duration > 0 && duration != androidx.media3.common.C.TIME_UNSET) {
                    tvCurrentTime.text = formatDuration(position)
                    tvTotalTime.text = formatDuration(duration)
                    
                    // Обновляем Slider (масштаб 0-1000)
                    val progress = (position.toFloat() / duration.toFloat()) * 1000f
                    seekBar.value = progress.coerceIn(0f, 1000f)
                }
            }
            handler.postDelayed(this, 500) // Обновляем каждые 500 мс
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Инициализация Views
        btnBack = findViewById(R.id.btnBack)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        tvTitle = findViewById(R.id.tvTitle)
        tvArtist = findViewById(R.id.tvArtist)
        imgAlbum = findViewById(R.id.imgAlbum)

        // Настройка кнопок
        btnBack.setOnClickListener { finish() }

        btnPlayPause.setOnClickListener {
            if (::mediaController.isInitialized) {
                if (mediaController.isPlaying) {
                    mediaController.pause()
                } else {
                    mediaController.play()
                }
            }
        }

        btnNext.setOnClickListener {
            if (::mediaController.isInitialized) {
                mediaController.seekToNext()
            }
        }

        btnPrev.setOnClickListener {
            if (::mediaController.isInitialized) {
                mediaController.seekToPrevious()
            }
        }

        // Настройка Slider (Материал Дизайна)
        seekBar.addOnChangeListener { _, value, fromUser ->
            if (fromUser && ::mediaController.isInitialized) {
                val duration = mediaController.duration
                if (duration > 0) {
                    val newPosition = (value / 1000f * duration).toLong()
                    tvCurrentTime.text = formatDuration(newPosition)
                }
            }
        }

        seekBar.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                // Пользователь коснулся ползунка
            }

            override fun onStopTrackingTouch(slider: Slider) {
                if (::mediaController.isInitialized) {
                    val duration = mediaController.duration
                    if (duration > 0) {
                        val newPosition = (slider.value / 1000f * duration).toLong()
                        mediaController.seekTo(newPosition)
                    }
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        controllerFuture.addListener({
            mediaController = controllerFuture.get()

            // Обновляем UI при подключении
            updateUI()
            handler.post(updateProgressTask)

            // Слушаем изменения состояния плеера
            mediaController.addListener(playerListener)

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateProgressTask)
        if (::mediaController.isInitialized) {
            mediaController.removeListener(playerListener)
        }
        MediaController.releaseFuture(controllerFuture)
    }

    // Выносим слушатель в переменную, чтобы корректно его удалять
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseButton()
        }

        override fun onMediaMetadataChanged(metadata: MediaMetadata) {
            updateUI()
        }
    }

    private fun updateUI() {
        if (!::mediaController.isInitialized) return

        val currentIndex = mediaController.currentMediaItemIndex.takeIf { it >= 0 } ?: -1
        val song = PlaylistManager.songs.getOrNull(currentIndex)
        val meta = mediaController.mediaMetadata

        val title = song?.title ?: meta.title ?: "Unknown Title"
        val artist = song?.artist ?: meta.artist ?: "Unknown Artist"
        val artworkUri = song?.albumArtUri ?: meta.artworkUri?.toString()

        tvTitle.text = title
        tvArtist.text = artist

        imgAlbum.load(artworkUri) {
            crossfade(true)
            placeholder(R.drawable.ic_not_image)
            error(R.drawable.ic_not_image)
            allowHardware(false)
            transformations(RoundedCornersTransformation(24f * resources.displayMetrics.density))
        }

        updatePlayPauseButton()
    }

    private fun updatePlayPauseButton() {
        if (::mediaController.isInitialized) {
            // setImageResource работает и для FloatingActionButton
            btnPlayPause.setImageResource(
                if (mediaController.isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_arrow_24
            )
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}