package com.serotonin.pomodoro.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.serotonin.pomodoro.MainActivity
import com.serotonin.pomodoro.R
import com.serotonin.pomodoro.notifications.NotificationChannels
import java.io.File

/**
 * Background music.
 *
 * The queue is fixed when playback starts (paths + titles), so the service never needs the network
 * or the app process to move to the next track. A MediaSession gives the notification real system
 * media controls — lock screen, Bluetooth headset and the media panel all work.
 */
class MusicService : Service() {

    private var player: MediaPlayer? = null
    private var session: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private var paths: List<String> = emptyList()
    private var titles: List<String> = emptyList()
    private var index: Int = 0
    private var playing: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        session = MediaSession(this, "PomodoroMusic").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onSkipToNext() = next()
                override fun onSkipToPrevious() = previous()
                override fun onStop() = stopSelf()
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val newPaths = intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty()
                val newTitles = intent.getStringArrayListExtra(EXTRA_TITLES).orEmpty()
                val start = intent.getIntExtra(EXTRA_START, 0)
                if (newPaths.isNotEmpty()) {
                    paths = newPaths
                    titles = newTitles
                    index = start.coerceIn(0, newPaths.lastIndex)
                    startTrack()
                } else {
                    resume()
                }
            }

            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_NEXT -> next()
            ACTION_PREVIOUS -> previous()
            ACTION_STOP -> {
                releasePlayer()
                stopForegroundCompat()
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ------------------------------------------------------------ playback

    private fun startTrack() {
        releasePlayer()
        val path = paths.getOrNull(index) ?: return
        val file = File(path)
        if (!file.exists()) {
            next()
            return
        }
        val mediaPlayer = MediaPlayer()
        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build(),
        )
        mediaPlayer.setDataSource(path)
        mediaPlayer.setOnCompletionListener { next() }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            playing = false
            next()
            true
        }
        runCatching { mediaPlayer.prepare() }.onFailure {
            mediaPlayer.release()
            return
        }
        mediaPlayer.start()
        player = mediaPlayer
        playing = true
        requestFocus()
        publish()
    }

    private fun resume() {
        val current = player ?: run {
            startTrack()
            return
        }
        runCatching { current.start() }
        playing = true
        requestFocus()
        publish()
    }

    private fun pause() {
        runCatching { player?.pause() }
        playing = false
        publish()
        abandonFocus()
    }

    private fun next() {
        if (paths.isEmpty()) return
        index = if (index >= paths.lastIndex) 0 else index + 1
        startTrack()
    }

    private fun previous() {
        if (paths.isEmpty()) return
        index = if (index <= 0) paths.lastIndex else index - 1
        startTrack()
    }

    private fun releasePlayer() {
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
        playing = false
    }

    // ------------------------------------------------------------ publish state

    private fun publish() {
        val title = titles.getOrNull(index) ?: ""
        session?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Pomodoro")
                .build(),
        )
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP,
                )
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    (player?.currentPosition ?: 0).toLong(),
                    1f,
                )
                .build(),
        )
        startForegroundCompat(notification(title))
    }

    private fun notification(title: String): Notification {
        NotificationChannels.ensure(this)
        val toggle = if (playing) ACTION_PAUSE else ACTION_RESUME
        val toggleLabel = if (playing) "暂停" else "继续"
        val content = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, NotificationChannels.MUSIC)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title.ifBlank { "Pomodoro" })
            .setContentText("背景音乐")
            .setContentIntent(content)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .addAction(action(ACTION_PREVIOUS, "上一首", 11))
            .addAction(action(toggle, toggleLabel, 12))
            .addAction(action(ACTION_NEXT, "下一首", 13))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun action(action: String, label: String, code: Int): Notification.Action {
        val pending = PendingIntent.getService(
            this,
            code,
            Intent(this, MusicService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_timer)
        return Notification.Action.Builder(icon, label, pending).build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                MUSIC_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(MUSIC_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        session?.isActive = false
    }

    // ------------------------------------------------------------ audio focus

    private fun requestFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setOnAudioFocusChangeListener { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS -> pause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                        AudioManager.AUDIOFOCUS_GAIN -> resume()
                    }
                }
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(null)
        }
    }

    override fun onDestroy() {
        releasePlayer()
        abandonFocus()
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        const val MUSIC_ID = 2001
        private const val ACTION_PLAY = "com.serotonin.pomodoro.music.PLAY"
        private const val ACTION_PAUSE = "com.serotonin.pomodoro.music.PAUSE"
        private const val ACTION_RESUME = "com.serotonin.pomodoro.music.RESUME"
        private const val ACTION_NEXT = "com.serotonin.pomodoro.music.NEXT"
        private const val ACTION_PREVIOUS = "com.serotonin.pomodoro.music.PREVIOUS"
        private const val ACTION_STOP = "com.serotonin.pomodoro.music.STOP"
        private const val EXTRA_PATHS = "paths"
        private const val EXTRA_TITLES = "titles"
        private const val EXTRA_START = "start"

        fun play(context: Context, paths: List<String>, titles: List<String>, startIndex: Int) {
            if (paths.isEmpty()) return
            val intent = Intent(context, MusicService::class.java)
                .setAction(ACTION_PLAY)
                .putStringArrayListExtra(EXTRA_PATHS, ArrayList(paths))
                .putStringArrayListExtra(EXTRA_TITLES, ArrayList(titles))
                .putExtra(EXTRA_START, startIndex)
            context.startForegroundService(intent)
        }

        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun next(context: Context) = send(context, ACTION_NEXT)
        fun previous(context: Context) = send(context, ACTION_PREVIOUS)

        fun stop(context: Context) {
            context.stopService(Intent(context, MusicService::class.java))
        }

        private fun send(context: Context, action: String) {
            val intent = Intent(context, MusicService::class.java).setAction(action)
            runCatching { context.startForegroundService(intent) }
        }
    }
}
