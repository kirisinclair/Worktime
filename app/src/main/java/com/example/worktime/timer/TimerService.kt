package com.example.worktime.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.worktime.MainActivity
import com.example.worktime.R
import com.example.worktime.data.Fmt
import com.example.worktime.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Notifs {
    const val CH_ONGOING = "worktime_ongoing"
    const val CH_ALERT = "worktime_alert"
    const val ID_ONGOING = 1001
    const val ID_ALERT = 1002

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_ONGOING, "Running timer", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Timer finished", NotificationManager.IMPORTANCE_HIGH)
                .apply { enableVibration(true) }
        )
    }

    fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun alert(ctx: Context, title: String, text: String) {
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_ALERT, n)
        } catch (_: SecurityException) {
        }
    }
}

/**
 * Keeps the countdown alive and visible when the app is not on screen, and owns the
 * work to break transition. The UI does the same transition when it is visible; both
 * paths go through the idempotent Store calls, so a double fire is harmless.
 */
class TimerService : Service() {

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.init(this)
        Notifs.ensureChannels(this)
        startForegroundSafely(build(Store.timer.elapsed(System.currentTimeMillis())))
        if (job == null) {
            val s = CoroutineScope(SupervisorJob())
            scope = s
            job = s.launch { loop() }
        }
        return START_STICKY
    }

    private suspend fun loop() {
        while (true) {
            val t = Store.timer
            if (!t.running) {
                stopSelf()
                return
            }
            val now = System.currentTimeMillis()
            val elapsed = t.elapsed(now)

            if (t.isBreak) {
                if (elapsed >= t.targetMs) {
                    Store.endBreak()
                    Notifs.alert(this, "Break over", "Back to work")
                    stopSelf()
                    return
                }
            } else if (Store.settings.breaksEnabled) {
                if (elapsed >= t.targetMs) {
                    val long = Store.isLongBreakNext()
                    if (Store.completeWork()) {
                        Notifs.alert(
                            this,
                            "Session done",
                            (if (long) "Long break" else "Break") + " " +
                                    (Store.timer.targetMs / 60000) + " min"
                        )
                    }
                    if (Store.settings.autoStartBreak) {
                        Store.startBreak(System.currentTimeMillis())
                    } else {
                        stopSelf()
                        return
                    }
                }
            } else if (elapsed >= Store.MAX_SESSION_MS) {
                Store.autoStop()
                Notifs.alert(this, "Timer stopped", "Two hours reached")
                stopSelf()
                return
            }

            notify(build(Store.timer.elapsed(System.currentTimeMillis())))
            delay(1000)
        }
    }

    private fun build(elapsed: Long): Notification {
        val t = Store.timer
        val remaining = (t.targetMs - elapsed).coerceAtLeast(0L)
        val over = (elapsed - t.targetMs).coerceAtLeast(0L)
        val title = when {
            t.isBreak -> "Break"
            over > 0L -> "Overwork +" + Fmt.exact(over)
            else -> Store.project(t.projectId)?.name ?: "Working"
        }
        val body = if (over > 0L && !t.isBreak) Fmt.countdown(over)
        else Fmt.countdown(remaining) + " left"
        return NotificationCompat.Builder(this, Notifs.CH_ONGOING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(Notifs.openApp(this))
            .build()
    }

    private fun notify(n: Notification) {
        try {
            NotificationManagerCompat.from(this).notify(Notifs.ID_ONGOING, n)
        } catch (_: SecurityException) {
        }
    }

    private fun startForegroundSafely(n: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(Notifs.ID_ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(Notifs.ID_ONGOING, n)
            }
        } catch (_: Exception) {
            // Notifications are a convenience; the timer itself runs on wall clock.
        }
    }

    override fun onDestroy() {
        job?.cancel()
        scope?.cancel()
        job = null
        scope = null
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) {
            try {
                ctx.startForegroundService(Intent(ctx, TimerService::class.java))
            } catch (_: Exception) {
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, TimerService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
