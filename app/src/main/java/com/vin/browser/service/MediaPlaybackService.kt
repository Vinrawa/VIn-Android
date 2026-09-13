package com.vin.browser.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.vin.browser.MainActivity
import com.vin.browser.R

class MediaPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "vin_media_playback_channel"
        const val NOTIFICATION_ID = 2026
        const val ACTION_START = "com.vin.browser.action.START_MEDIA_PLAYBACK"
        const val ACTION_STOP = "com.vin.browser.action.STOP_MEDIA_PLAYBACK"
        const val ACTION_TOGGLE_PLAY = "com.vin.browser.action.TOGGLE_PLAY_MEDIA"
        const val ACTION_MEDIA_PLAYING = "com.vin.browser.action.MEDIA_PLAYING_STATE"
        const val ACTION_MEDIA_PAUSED = "com.vin.browser.action.MEDIA_PAUSED_STATE"
        const val EXTRA_TITLE = "extra_media_title"
        const val EXTRA_DOMAIN = "extra_media_domain"

        var isServiceRunning = false
            private set

        var isMediaPlaying = true
            private set

        var onMediaTogglePlayPauseRequested: (() -> Unit)? = null
        var onMediaPauseRequested: (() -> Unit)? = null

        fun start(context: Context, title: String, domain: String) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_DOMAIN, domain)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun notifyPlaying(context: Context, title: String, domain: String) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = ACTION_MEDIA_PLAYING
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_DOMAIN, domain)
            }
            context.startService(intent)
        }

        fun notifyPaused(context: Context) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = ACTION_MEDIA_PAUSED
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null
    private var currentTitle: String = "Playing in background"
    private var currentDomain: String = "ViN Browser"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "ViNMediaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    onMediaTogglePlayPauseRequested?.invoke()
                }

                override fun onPause() {
                    onMediaTogglePlayPauseRequested?.invoke()
                }

                override fun onStop() {
                    onMediaPauseRequested?.invoke()
                    stopPlaybackService()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_STOP -> {
                onMediaPauseRequested?.invoke()
                stopPlaybackService()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PLAY -> {
                onMediaTogglePlayPauseRequested?.invoke()
                return START_STICKY
            }
            ACTION_MEDIA_PLAYING -> {
                isMediaPlaying = true
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                currentDomain = intent.getStringExtra(EXTRA_DOMAIN) ?: currentDomain
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                updateNotification()
                return START_STICKY
            }
            ACTION_MEDIA_PAUSED -> {
                isMediaPlaying = false
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                updateNotification()
                return START_STICKY
            }
        }

        currentTitle = intent?.getStringExtra(EXTRA_TITLE) ?: "Playing in background"
        currentDomain = intent?.getStringExtra(EXTRA_DOMAIN) ?: "ViN Browser"
        isMediaPlaying = true

        acquireWakeLock()
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        val notification = buildNotification(currentTitle, currentDomain, isMediaPlaying)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        isServiceRunning = true
        return START_STICKY
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    private fun updateNotification() {
        val notification = buildNotification(currentTitle, currentDomain, isMediaPlaying)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VinBrowser:BackgroundMediaWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(3 * 60 * 60 * 1000L) // 3 hours max safety timeout
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun stopPlaybackService() {
        releaseWakeLock()
        isServiceRunning = false
        mediaSession?.isActive = false
        mediaSession?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(title: String, domain: String, isPlaying: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val togglePlayIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_TOGGLE_PLAY
        }
        val togglePlayPendingIntent = PendingIntent.getService(
            this, 1, togglePlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (isPlaying) "Pause" else "Play"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("Playing in background • $domain")
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseIcon, playPauseText, togglePlayPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)

        mediaSession?.let {
            builder.setStyle(
                MediaStyle()
                    .setMediaSession(it.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps audio and video playing when ViN Browser is in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        isServiceRunning = false
        mediaSession?.release()
        super.onDestroy()
    }
}
