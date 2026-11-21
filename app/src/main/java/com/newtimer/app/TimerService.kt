package com.newtimer.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.max

class TimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var endAt = 0L
    private var player: MediaPlayer? = null
    private var systemRingtone: Ringtone? = null
    private var ringStopAt = 0L
    private var wakeLock: PowerManager.WakeLock? = null

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
            ACTION_STOP -> {
                stopAlarm()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val seconds = max(1, intent.getLongExtra(EXTRA_SECONDS, 60L))
                stopAlarm()
                endAt = System.currentTimeMillis() + seconds * 1000L
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NewTimer:Countdown").apply {
                    val ringSeconds = getSharedPreferences("timer", MODE_PRIVATE)
                        .getInt("ring_duration", 60).coerceIn(5, 600)
                    acquire((seconds + ringSeconds + 120L) * 1000L)
                }
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

        val customUri = prefs.getString("ring_uri", null)?.let(Uri::parse)
        val gain = prefs.getInt("ring_gain", 100).coerceIn(0, 100) / 100f
        if (customUri != null) {
            playCustomSound(customUri, gain)
        } else {
            playSystemAlarm(gain)
        }

        handler.postDelayed({
            if (System.currentTimeMillis() >= ringStopAt) {
                stopAlarm()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, duration * 1000L)
    }

    private fun playSystemAlarm(gain: Float) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (uri == null) {
                updateNotification("No system alarm sound is set on this phone")
                return
            }
            systemRingtone = RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= 28) {
                    isLooping = true
                    volume = gain
                }
                play()
            }
            if (systemRingtone == null) updateNotification("Unable to load system alarm sound")
        } catch (_: Exception) {
            updateNotification("Unable to play system alarm sound")
        }
    }

    private fun playCustomSound(uri: Uri, gain: Float) {
        try {
            player = MediaPlayer().apply {
                setDataSource(this@TimerService, uri)
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                isLooping = true
                setVolume(gain, gain)
                prepare()
                start()
            }
        } catch (_: Exception) {
            updateNotification("Unable to play selected sound — choose it again")
        }
    }

    private fun notification(text: String): Notification {
        val prefs = getSharedPreferences("timer", MODE_PRIVATE)
        val silent = prefs.getBoolean("silent_notifications", false)
        val channelId = if (silent) CHANNEL_SILENT else CHANNEL_SOUND
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                val importance = if (silent) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(
                    channelId,
                    if (silent) "Timer (silent)" else "Timer",
                    importance
                ).apply {
                    description = if (silent) "Silent ongoing countdown status" else "Countdown status notifications"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
        val stop = PendingIntent.getBroadcast(
            this, 1, Intent(this, StopReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("NewTimer")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(silent)
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
        private const val CHANNEL_SOUND = "timer_status_sound_v2"
        private const val CHANNEL_SILENT = "timer_status_silent_v2"

        fun stopFromVolumeKey(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
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
        try { systemRingtone?.stop() } catch (_: Exception) {}
        systemRingtone = null
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }
}
