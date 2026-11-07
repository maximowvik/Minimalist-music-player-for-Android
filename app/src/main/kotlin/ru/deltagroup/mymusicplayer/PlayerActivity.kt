package ru.deltagroup.mymusicplayer

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale

class PlayerActivity : AppCompatActivity() {
    private lateinit var mediaController: MediaController
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val handler = Handler(Looper.getMainLooper())

    // Парсим форматированную строку "H:MM:SS" или "M:SS" обратно в миллисекунды
    private fun parseDurationString(durationStr: String?): Long {
        if (durationStr.isNullOrBlank() || durationStr == "--:--") return 0L
        return try {
            val parts = durationStr.split(":").map { it.trim().toLong() }
            when (parts.size) {
                3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
                2 -> (parts[0] * 60 + parts[1]) * 1000L
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private val updateProgress = object : Runnable {
        override fun run() {
            if (!::mediaController.isInitialized) return

            val seekBar = findViewById<SeekBar>(R.id.seekBar)
            val tvCurrent = findViewById<TextView>(R.id.tvCurrentTime)
            val tvTotal = findViewById<TextView>(R.id.tvTotalTime)

            val position = mediaController.currentPosition.coerceAtLeast(0)

            // Берем длительность ИМЕННО ИЗ ОБЪЕКТА SONG (как в адаптере)
            val currentIndex = mediaController.currentMediaItemIndex
            val currentSong = PlaylistManager.songs.getOrNull(currentIndex)
            val safeDurationMs = parseDurationString(currentSong?.duration)

            if (safeDurationMs > 0 && safeDurationMs <= Int.MAX_VALUE.toLong()) {
                if (seekBar.max != safeDurationMs.toInt()) {
                    seekBar.max = safeDurationMs.toInt()
                    seekBar.isEnabled = true
                }

                if (!seekBar.isPressed) {
                    seekBar.progress = position.toInt().coerceIn(0, safeDurationMs.toInt())
                }

                tvTotal.text = currentSong?.duration ?: "--:--"
            } else {
                // Fallback на плеер
                val playerDur = mediaController.duration
                if (playerDur > 0 && playerDur != C.TIME_UNSET && playerDur <= Int.MAX_VALUE.toLong()) {
                    if (seekBar.max != playerDur.toInt()) {
                        seekBar.max = playerDur.toInt()
                        seekBar.isEnabled = true
                    }
                    if (!seekBar.isPressed) {
                        seekBar.progress = position.toInt().coerceIn(0, playerDur.toInt())
                    }
                    tvTotal.text = formatTime(playerDur)
                } else {
                    seekBar.isEnabled = false
                    tvTotal.text = "--:--"
                }
            }

            tvCurrent.text = formatTime(position)
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<ImageButton>(R.id.btnPlay).setOnClickListener {
            if (::mediaController.isInitialized) {
                if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
            }
        }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener {
            if (::mediaController.isInitialized) mediaController.seekToNextMediaItem()
        }
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener {
            if (::mediaController.isInitialized) mediaController.seekToPreviousMediaItem()
        }

        // ИСПРАВЛЕННЫЙ СЛУШАТЕЛЬ SEEKBAR
        findViewById<SeekBar>(R.id.seekBar).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Обновляем только текст времени при перетаскивании
                    findViewById<TextView>(R.id.tvCurrentTime).text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                // Останавливаем автообновление прогресса, пока пользователь держит ползунок
                handler.removeCallbacks(updateProgress)
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                if (::mediaController.isInitialized && sb != null) {
                    val targetPosition = sb.progress.toLong()

                    // ВАЖНО: Используем seekTo с явным указанием индекса трека
                    // Это надежнее для проблемных файлов FLAC/диктофона
                    mediaController.seekTo(
                        mediaController.currentMediaItemIndex,
                        targetPosition
                    )

                    // Мгновенная обратная связь интерфейсу
                    findViewById<TextView>(R.id.tvCurrentTime).text = formatTime(targetPosition)
                }

                // Возобновляем автообновление
                handler.post(updateProgress)
            }
        })
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            setupUI()
            handler.post(updateProgress)

            mediaController.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlayButton()
                override fun onMediaMetadataChanged(m: MediaMetadata) = updateTrackInfo()
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    PlaylistManager.selectedTrackIndex = mediaController.currentMediaItemIndex.takeIf { it >= 0} ?: -1
                    updateTrackInfo()
                    setupUI()
                }
            })
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateProgress)
        MediaController.releaseFuture(controllerFuture)
    }

    private fun setupUI() {
        updateTrackInfo()
        updatePlayButton()
        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        seekBar.progress = 0
        seekBar.isEnabled = false
    }

    private fun updateTrackInfo() {
        if (!::mediaController.isInitialized) return
        val m = mediaController.mediaMetadata
        findViewById<TextView>(R.id.tvTitle).text = m.title ?: "Неизвестно"
        findViewById<TextView>(R.id.tvArtist).text = m.artist ?: ""
        findViewById<ImageView>(R.id.imgAlbum).load(m.artworkUri) {
            crossfade(true)
            placeholder(R.drawable.ic_not_image)
            error(R.drawable.ic_not_image)
            transformations(RoundedCornersTransformation(16f * resources.displayMetrics.density))
        }
    }

    private fun updatePlayButton() {
        if (!::mediaController.isInitialized) return
        findViewById<ImageButton>(R.id.btnPlay).setImageResource(
            if (mediaController.isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_arrow_24
        )
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0 || ms == C.TIME_UNSET) return "--:--"
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