package com.pomodorotimer.app.service

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
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
import com.pomodorotimer.app.MainActivity
import com.pomodorotimer.app.R
import com.pomodorotimer.app.notifications.NotificationChannels
import com.pomodorotimer.app.data.PlaybackStartPolicy
import com.pomodorotimer.app.data.MusicQueue
import com.pomodorotimer.app.data.MusicRepository
import com.pomodorotimer.app.data.WorkspaceStore
import com.pomodorotimer.app.domain.MusicMode
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
    private var resumeOnFocusGain = false
    private var playOrder: List<Int> = emptyList()
    private var orderMode: MusicMode? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationSettingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val channel = intent?.getStringExtra(NotificationManager.EXTRA_NOTIFICATION_CHANNEL_ID)
            if (intent?.action == NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED || channel == NotificationChannels.MUSIC) {
                refreshNotificationVisibility()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        NotificationChannels.ensure(this)
        runningService = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val filter = IntentFilter().apply {
                addAction(NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED)
                addAction(NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(notificationSettingsReceiver, filter, Context.RECEIVER_EXPORTED)
            else registerReceiver(notificationSettingsReceiver, filter)
        }
        session = MediaSession(this, "PomodoroMusic").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onSkipToNext() = next()
                override fun onSkipToPrevious() = previous()
                override fun onStop() = stopSelf()
            })
            isActive = NotificationChannels.musicNotificationsEnabled(this@MusicService)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A media foreground service must promote itself before preparing audio.
        if (intent?.action != ACTION_STOP) {
            startForegroundCompat(notification(titles.getOrNull(index).orEmpty()))
        }
        if (paths.isEmpty() && intent != null && intent.action != ACTION_PLAY) {
            MusicRepository.loadQueue(this)?.let { saved ->
                paths = saved.paths
                titles = saved.titles
                index = if (paths.isEmpty()) 0 else getSharedPreferences("music_playback", MODE_PRIVATE).getInt("index", 0).coerceIn(0, paths.lastIndex)
            }
        }
        when (intent?.action) {
            ACTION_PLAY -> {
                val newPaths = intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty()
                val newTitles = intent.getStringArrayListExtra(EXTRA_TITLES).orEmpty()
                if (newPaths.isNotEmpty()) {
                    paths = newPaths
                    titles = newTitles
                    index = intent.getIntExtra(EXTRA_START, 0).coerceIn(0, newPaths.lastIndex)
                    orderMode = null
                    startTrack()
                } else resume()
            }
            ACTION_ENSURE_PLAYING -> {
                when (PlaybackStartPolicy.decide(player != null, playing)) {
                    PlaybackStartPolicy.Action.KEEP_PLAYING -> Unit
                    PlaybackStartPolicy.Action.RESUME -> resume()
                    PlaybackStartPolicy.Action.START_QUEUE -> {
                        val newPaths = intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty()
                        val newTitles = intent.getStringArrayListExtra(EXTRA_TITLES).orEmpty()
                        if (newPaths.isNotEmpty()) {
                            paths = newPaths
                            titles = newTitles
                            index = intent.getIntExtra(EXTRA_START, 0).coerceIn(0, paths.lastIndex)
                            orderMode = null
                            startTrack()
                        } else { stopForegroundCompat(); stopSelf() }
                    }
                }
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_NEXT -> next()
            ACTION_PREVIOUS -> previous()
            ACTION_STOP -> { releasePlayer(); stopForegroundCompat(); stopSelf() }
            null -> {
                val saved = MusicRepository.loadQueue(this)
                val prefs = getSharedPreferences("music_playback", MODE_PRIVATE)
                if (prefs.getBoolean("playing", false) && saved != null && saved.paths.isNotEmpty()) {
                    paths = saved.paths
                    titles = saved.titles
                    index = prefs.getInt("index", 0).coerceIn(0, paths.lastIndex)
                    startTrack()
                } else { stopForegroundCompat(); stopSelf() }
            }
        }
        return START_STICKY
    }

    private fun currentMode(): MusicMode =
        WorkspaceStore(this).load(null)?.state?.settings?.musicMode ?: MusicMode.SINGLE

    private fun syncQueue() {
        val saved = MusicRepository.loadQueue(this) ?: return
        if (saved.paths == paths && saved.titles == titles) return
        val current = paths.getOrNull(index)
        paths = saved.paths
        titles = saved.titles
        index = paths.indexOf(current).takeIf { it >= 0 } ?: 0
        orderMode = null
    }

    private fun prepareOrder(mode: MusicMode) {
        if (orderMode != mode || playOrder.size != paths.size) {
            playOrder = MusicQueue.order(paths.size, mode)
            orderMode = mode
        }
    }

    private fun startTrack(failures: Int = 0) {
        releasePlayer()
        if (paths.isEmpty() || failures >= paths.size) {
            getSharedPreferences("music_playback", MODE_PRIVATE).edit().putBoolean("playing", false).apply()
            stopForegroundCompat(); stopSelf(); return
        }
        val path = paths.getOrNull(index)
        if (path == null || !File(path).isFile) {
            index = (index + 1) % paths.size
            mainHandler.post { startTrack(failures + 1) }
            return
        }
        if (!requestFocus()) { playing = false; publish(); return }
        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build())
            mediaPlayer.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            mediaPlayer.setDataSource(path)
            mediaPlayer.setOnCompletionListener { next(automatic = true) }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                index = (index + 1) % paths.size
                mainHandler.post { startTrack(failures + 1) }
                true
            }
            mediaPlayer.prepare()
            mediaPlayer.start()
            player = mediaPlayer
            playing = true
            resumeOnFocusGain = false
            publish()
        } catch (_: Exception) {
            runCatching { mediaPlayer.release() }
            index = (index + 1) % paths.size
            mainHandler.post { startTrack(failures + 1) }
        }
    }

    private fun resume() {
        val current = player ?: run { startTrack(); return }
        if (!requestFocus()) return
        if (runCatching { current.start() }.isSuccess) {
            playing = true
            resumeOnFocusGain = false
            publish()
        }
    }

    private fun pause(transient: Boolean = false) {
        runCatching { player?.pause() }
        playing = false
        resumeOnFocusGain = transient
        publish()
        if (!transient) abandonFocus()
    }

    private fun next(automatic: Boolean = false) {
        syncQueue()
        if (paths.isEmpty()) return
        val mode = currentMode()
        prepareOrder(mode)
        index = MusicQueue.nextIndex(index, playOrder, mode, automatic)
        startTrack()
    }

    private fun previous() {
        syncQueue()
        if (paths.isEmpty()) return
        val mode = currentMode()
        prepareOrder(mode)
        val position = playOrder.indexOf(index).coerceAtLeast(0)
        index = playOrder[MusicQueue.previous(position, playOrder, if (mode == MusicMode.SINGLE) MusicMode.SEQUENCE else mode)]
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

    private fun refreshNotificationVisibility() {
        session?.isActive = NotificationChannels.musicNotificationsEnabled(this)
        if (paths.isNotEmpty()) publish()
    }

    private fun publish() {
        session?.isActive = NotificationChannels.musicNotificationsEnabled(this)
        val title = titles.getOrNull(index) ?: ""
        MusicPlayback.title = title
        MusicPlayback.path = paths.getOrNull(index).orEmpty()
        MusicPlayback.playing = playing
        getSharedPreferences("music_playback", MODE_PRIVATE).edit().putBoolean("playing", playing || resumeOnFocusGain).putInt("index", index).apply()
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
        val builder = Notification.Builder(this, NotificationChannels.MUSIC)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title.ifBlank { "Pomodoro" })
            .setContentText("背景音乐")
            .setContentIntent(content)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
        if (NotificationChannels.musicNotificationsEnabled(this)) {
            builder.addAction(action(ACTION_PREVIOUS, "上一首", 11))
                .addAction(action(toggle, toggleLabel, 12))
                .addAction(action(ACTION_NEXT, "下一首", 13))
                .setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(0, 1, 2))
        } else {
            // Android still requires a foreground-service notification, but an inactive media
            // session and a non-media notification do not request an exempt media control card.
            builder.setCategory(Notification.CATEGORY_SERVICE)
        }
        return builder.build()
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

    private fun requestFocus(): Boolean {
        val manager = audioManager ?: return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS -> pause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> if (playing) pause(transient = true)
                        AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocusGain) {
                            resumeOnFocusGain = false
                            if (runCatching { player?.start() }.isSuccess && player != null) { playing = true; publish() }
                        }
                    }
                }.build().also { focusRequest = it }
            return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        @Suppress("DEPRECATION")
        return manager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focusRequest?.let { manager.abandonAudioFocusRequest(it) }
        else @Suppress("DEPRECATION") manager.abandonAudioFocus(null)
    }

    override fun onDestroy() {
        if (runningService === this) runningService = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { unregisterReceiver(notificationSettingsReceiver) }
        mainHandler.removeCallbacksAndMessages(null)
        MusicPlayback.playing = false
        MusicPlayback.title = ""
        MusicPlayback.path = ""
        releasePlayer()
        abandonFocus()
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        const val MUSIC_ID = 2001
        @Volatile private var runningService: MusicService? = null

        fun refreshNotificationSettings() {
            runningService?.let { service -> service.mainHandler.post { service.refreshNotificationVisibility() } }
        }
        private const val ACTION_PLAY = "com.pomodorotimer.app.music.PLAY"
        private const val ACTION_ENSURE_PLAYING = "com.pomodorotimer.app.music.ENSURE_PLAYING"
        private const val ACTION_PAUSE = "com.pomodorotimer.app.music.PAUSE"
        private const val ACTION_RESUME = "com.pomodorotimer.app.music.RESUME"
        private const val ACTION_NEXT = "com.pomodorotimer.app.music.NEXT"
        private const val ACTION_PREVIOUS = "com.pomodorotimer.app.music.PREVIOUS"
        private const val ACTION_STOP = "com.pomodorotimer.app.music.STOP"
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

        /** Resume an existing player in place; load the queue only when no player exists. */
        fun ensurePlaying(context: Context, paths: List<String>, titles: List<String>, startIndex: Int) {
            if (paths.isEmpty()) return
            val intent = Intent(context, MusicService::class.java)
                .setAction(ACTION_ENSURE_PLAYING)
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
