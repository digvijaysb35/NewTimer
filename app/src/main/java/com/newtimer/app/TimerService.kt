package com.newtimer.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.max

class TimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var endAt = 0L
    private var player: MediaPlayer? = null
    private var ringStopAt = 0L
    private val tick = object : Runnable {
        override fun run() {
            val remaining = endAt - System.currentTimeMillis()
            if (remaining <= 0L) {
                startRinging()
            } else {
                updateNotification("Time left: " + format(remaining))
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopAlarm(); stopSelf(); return START_NOT_STICKY }
            ACTION_START -> {
                val seconds = max(1, intent.getLongExtra(EXTRA_SECONDS, 60L))
                stopAlarm()
                endAt = System.currentTimeMillis() + seconds * 1000L
                startForeground(NOTIFICATION_ID, notification("Timer running"))
                handler.removeCallbacks(tick)
                handler.post(tick)
            }
        }
        return START_NOT_STICKY
    }

    private fun startRinging() {
        handler.removeCallbacks(tick)
        val prefs = getSharedPreferences("timer", MODE_PRIVATE)
        val duration = prefs.getInt("ring_duration", 60).coerceIn(5, 600)
        ringStopAt = System.currentTimeMillis() + duration * 1000L
        updateNotification("Ringing — press a volume key to stop")
        try {
            val custom = prefs.getString("ring_uri", null)?.let(Uri::parse)
            val uri = custom ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setDataSource(this@TimerService, uri)
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                isLooping = true
                val gain = prefs.getInt("ring_gain", 100).coerceIn(0, 100) / 100f
                setVolume(gain, gain)
                prepare()
                start()
            }
        } catch (_: Exception) {
            try {
                player = MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.apply { isLooping = true; start() }
            } catch (_: Exception) {}
        }
        handler.postDelayed({
            if (System.currentTimeMillis() >= ringStopAt) { stopAlarm(); stopSelf() }
        }, duration * 1000L)
    }

    private fun notification(text: String): Notification {
        val channelId = "timer_alarm"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(channelId, "Timer", NotificationManager.IMPORTANCE_HIGH))
        }
        val stop = PendingIntent.getBroadcast(this, 1, Intent(this, StopReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("NewTimer").setContentText(text).setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopAlarm()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.newtimer.START"
        const val ACTION_STOP = "com.newtimer.STOP"
        const val EXTRA_SECONDS = "seconds"
        const val NOTIFICATION_ID = 7
        fun stopAlarm() {
            // Player is owned by the service instance; ACTION_STOP handles the normal stop path.
        }
        fun stopFromVolumeKey(context: Context) {
            context.startService(Intent(context, TimerService::class.java).setAction(ACTION_STOP))
        }
        private fun format(ms: Long): String {
            val sec = max(0, ms / 1000)
            return "%02d:%02d".format(sec / 60, sec % 60)
        }
    }

    private fun stopAlarm() {
        handler.removeCallbacksAndMessages(null)
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }
}
