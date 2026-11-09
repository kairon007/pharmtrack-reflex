package com.pharmtrack.reflex.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pharmtrack.reflex.core.App.Clock
import com.pharmtrack.reflex.core.App.IDCancellation
import com.pharmtrack.reflex.core.App.IDGenerator
import com.pharmtrack.reflex.core.App.KeyValueStore
import com.pharmtrack.reflex.core.App.MedicalStore
import com.pharmtrack.reflex.core.App.Message
import com.pharmtrack.reflex.core.App.NodeID
import com.pharmtrack.reflex.core.App.NotificationScheduler
import com.pharmtrack.reflex.core.App.Scheduler
import com.pharmtrack.reflex.core.Domain.MedicalData
import com.pharmtrack.reflex.core.Domain.ScheduledNotification
import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.MedicalCodec
import java.io.File
import java.util.UUID

// Phase 4 live adapters for the six Core ports (contracts/services.yaml). Core never
// learns a file path, a SharedPreferences key or an alarm manager — all of that lives here.
// The fixture adapters in RunnerKit remain the test surface.

class LiveClock : Clock {
    override fun now(): Instant = Instant(System.currentTimeMillis() / 1000.0)
}

class LiveIDGenerator : IDGenerator {
    override fun next(): UUID = UUID.randomUUID()
}

/**
 * Fires delivered messages on the main looper. A non-nil key replaces the pending entry
 * with that key; a nil key stacks (services.yaml#Scheduler).
 */
class HandlerScheduler(private val onFire: (Message) -> Unit) : Scheduler {

    private class Entry(val runnable: Runnable, val key: String?)

    private val handler = Handler(Looper.getMainLooper())
    private val entries = mutableMapOf<Int, Entry>()
    private val keyed = mutableMapOf<String, Int>()
    private var nextToken = 0

    override fun after(seconds: Double, key: String?, node: NodeID, message: Message): IDCancellation {
        val token = nextToken
        nextToken += 1

        if (key != null) {
            keyed[key]?.let { previous -> entries.remove(previous)?.let { handler.removeCallbacks(it.runnable) } }
            keyed[key] = token
        }

        val runnable = Runnable {
            entries.remove(token)
            if (key != null && keyed[key] == token) keyed.remove(key)
            onFire(message)
        }
        entries[token] = Entry(runnable, key)
        handler.postDelayed(runnable, (seconds * 1000.0).toLong())
        return IDCancellation(token)
    }

    override fun cancel(handle: IDCancellation) {
        val entry = entries.remove(handle.token) ?: return
        handler.removeCallbacks(entry.runnable)
        if (entry.key != null && keyed[entry.key] == handle.token) keyed.remove(entry.key)
    }
}

class PrefsKeyValueStore(context: Context) : KeyValueStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun bool(key: String): Boolean? =
        if (prefs.contains(key)) prefs.getBoolean(key, false) else null

    override fun setBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    companion object {
        private const val PREFS_NAME = "reflex.keyvalue"
    }
}

/**
 * medical_data.json in the app's private files directory — the Android counterpart of the
 * iOS Documents slot (persistence.yaml#file). Encoding goes through the same MedicalCodec
 * the fixture adapter round-trips, so leniency and nil-omission are identical.
 */
class FileMedicalStore(context: Context) : MedicalStore {

    private val file = File(context.filesDir, FILE_NAME)

    override suspend fun load(): MedicalData? {
        if (!file.exists()) return null
        return MedicalCodec.decode(file.readBytes())
    }

    override suspend fun save(data: MedicalData) {
        val bytes = MedicalCodec.encode(data)
        val temporary = File(file.parentFile, "$FILE_NAME.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(file)) {
            file.writeBytes(bytes)
            temporary.delete()
        }
    }

    /** Idempotent: clearing an absent file is success, not an error. */
    override suspend fun clear() {
        if (file.exists()) file.delete()
    }

    companion object {
        private const val FILE_NAME = "medical_data.json"
    }
}

/** The four channels the planner names. The id IS the planner's channel string. */
object NotificationChannels {

    const val MEDICATION_REMINDERS = "medication_reminders"
    const val LOW_STOCK_ALERTS = "low_stock_alerts"
    const val APPOINTMENT_REMINDERS = "appointment_reminders"
    const val CONSULTATION_REMINDERS = "consultation_reminders"

    private val CHANNELS = listOf(
        MEDICATION_REMINDERS to "Medication reminders",
        LOW_STOCK_ALERTS to "Low stock alerts",
        APPOINTMENT_REMINDERS to "Appointment reminders",
        CONSULTATION_REMINDERS to "Consultation reminders"
    )

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        for ((id, name) in CHANNELS) {
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)
            channel.enableVibration(true)
            manager.createNotificationChannel(channel)
        }
    }
}

/**
 * Clear-all-then-reschedule is canonical (D1). The planner's list is the complete desired
 * set: every pending alarm of the previous set is cancelled first. A nil triggerAt (low
 * stock) posts immediately instead of arming an alarm.
 */
class AlarmNotificationScheduler(private val context: Context) : NotificationScheduler {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)
    private val registry = NotificationRegistry(context) { identifier -> cancelAlarm(identifier) }

    init {
        NotificationChannels.ensure(context)
    }

    override suspend fun requestAuthorization(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    override suspend fun replaceAll(notifications: List<ScheduledNotification>) {
        registry.clear()
        for (notification in notifications) {
            val trigger = notification.triggerAt
            if (trigger == null) {
                notificationManager.notify(
                    notification.identifier.hashCode(),
                    buildNotification(notification.identifier, notification.channel, notification.title, notification.body)
                )
            } else {
                val pendingIntent = alarmIntent(notification)
                val atMillis = (trigger.secondsSince1970 * 1000.0).toLong()
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
                registry.add(notification.identifier)
            }
        }
    }

    private fun alarmIntent(notification: ScheduledNotification): PendingIntent {        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = NotificationAlarmReceiver.ACTION_POST
            putExtra(NotificationAlarmReceiver.EXTRA_IDENTIFIER, notification.identifier)
            putExtra(NotificationAlarmReceiver.EXTRA_CHANNEL, notification.channel)
            putExtra(NotificationAlarmReceiver.EXTRA_TITLE, notification.title)
            putExtra(NotificationAlarmReceiver.EXTRA_BODY, notification.body)
        }
        return PendingIntent.getBroadcast(
            context,
            notification.identifier.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelAlarm(identifier: String) {
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = NotificationAlarmReceiver.ACTION_POST
        }
        val existing = PendingIntent.getBroadcast(
            context,
            identifier.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(existing)
        existing.cancel()
    }

    private fun buildNotification(
        identifier: String,
        channel: String,
        title: String,
        body: String
    ) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_notifications)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
}

/**
 * The identifiers of the currently armed alarms. Kept in SharedPreferences so a
 * replaceAll after a process restart can still cancel what the previous set armed.
 */
private class NotificationRegistry(
    context: Context,
    private val cancelAlarm: (String) -> Unit
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun add(identifier: String) {
        prefs.edit().putStringSet(KEY, prefs.getStringSet(KEY, emptySet())!! + identifier).apply()
    }

    fun clear() {
        val identifiers = prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        identifiers.forEach(cancelAlarm)
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val PREFS_NAME = "reflex.notifications"
        private const val KEY = "armed"
    }
}

/** Posts the notification an armed alarm was scheduled for. */
class NotificationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val identifier = intent.getStringExtra(EXTRA_IDENTIFIER) ?: return
        val channel = intent.getStringExtra(EXTRA_CHANNEL) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: return

        NotificationChannels.ensure(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(identifier.hashCode(), notification)
    }

    companion object {
        const val ACTION_POST = "com.pharmtrack.reflex.app.POST_NOTIFICATION"
        const val EXTRA_IDENTIFIER = "identifier"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
    }
}
