package com.pharmtrack.reflex.runner

import com.pharmtrack.reflex.core.App.Clock
import com.pharmtrack.reflex.core.App.IDCancellation
import com.pharmtrack.reflex.core.App.IDGenerator
import com.pharmtrack.reflex.core.App.KeyValueStore
import com.pharmtrack.reflex.core.App.MedicalStore
import com.pharmtrack.reflex.core.App.Message
import com.pharmtrack.reflex.core.App.NodeID
import com.pharmtrack.reflex.core.App.NotificationScheduler
import com.pharmtrack.reflex.core.App.Scheduler
import com.pharmtrack.reflex.core.App.ServiceError
import com.pharmtrack.reflex.core.App.Services
import com.pharmtrack.reflex.core.Domain.MedicalData
import com.pharmtrack.reflex.core.Domain.ScheduledNotification
import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.MedicalCodec
import com.pharmtrack.reflex.core.Support.parseUUID
import java.util.UUID

/** The pinned clock. `advance` is driven by the spec, never by Core (services.yaml#Clock). */
class FixtureClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant

    fun advance(seconds: Double) {
        instant = instant.addingSeconds(seconds)
    }

    fun reset(value: Instant) {
        this.instant = value
    }
}

/**
 * Hands out determinism.yaml#ids.sequence in order. Exhaustion is recorded, never wrapped;
 * the executor turns it into exit 3. `next()` stays non-throwing so reducers remain total.
 */
class FixtureIDGenerator(private val sequence: List<UUID>) : IDGenerator {

    private var index = 0

    val handedOut = mutableListOf<UUID>()
    var consumed = 0
        private set
    var isExhausted = false
        private set

    override fun next(): UUID {
        if (index >= sequence.size) {
            isExhausted = true
            return EXHAUSTED_SENTINEL
        }
        val id = sequence[index]
        index += 1
        consumed += 1
        handedOut.add(id)
        return id
    }

    /**
     * SCHEMA.md#setup: counters are zeroed at the end of setup. The sequence position is NOT
     * rewound, so a journey never sees an id that setup already handed out.
     */
    fun resetCounters() {
        consumed = 0
        handedOut.clear()
    }

    companion object {
        val EXHAUSTED_SENTINEL: UUID = parseUUID("00000000-0000-4000-8000-00000000DEAD")!!
    }
}

data class TimerEntry(
    val token: Int,
    val seconds: Double,
    val key: String?,
    val message: Message
)

/**
 * Queues after() entries; nothing fires until drain(). A non-nil key REPLACES the pending
 * entry with that key, which is how the onboarding reveal cancels its own predecessor
 * instead of stacking uncancelled timers the way MainTabView.swift:107-118 did.
 */
class FixtureScheduler : Scheduler {

    private val entries = mutableListOf<TimerEntry>()
    private var nextToken = 0

    var registeredCount = 0
        private set

    override fun after(seconds: Double, key: String?, node: NodeID, message: Message): IDCancellation {
        if (key != null) {
            entries.removeAll { it.key == key }
        }
        nextToken += 1
        registeredCount += 1
        entries.add(TimerEntry(token = nextToken, seconds = seconds, key = key, message = message))
        return IDCancellation(nextToken)
    }

    override fun cancel(handle: IDCancellation) {
        entries.removeAll { it.token == handle.token }
    }

    /**
     * Fires every pending entry in insertion order and hands the messages back. The
     * executor delivers them, which may register new entries; it drains to fixed point.
     */
    fun drain(): List<Message> {
        val fired = entries.map { it.message }
        entries.clear()
        return fired
    }

    val pendingCount: Int get() = entries.size

    fun reset() {
        entries.clear()
        nextToken = 0
        registeredCount = 0
    }
}

class FixtureKeyValueStore(val values: MutableMap<String, Boolean>) : KeyValueStore {
    override fun bool(key: String): Boolean? = values[key]

    override fun setBool(key: String, value: Boolean) {
        values[key] = value
    }
}

/**
 * The medical_data.json slot. save() runs the platform's REAL encoder and then the REAL
 * decoder, so `saved` is a genuine decode(encode(x)) round-trip (runner.yaml#fixture_adapters).
 */
class FixtureMedicalStore(slot: ByteArray?, var mode: Mode = Mode.NORMAL) : MedicalStore {

    enum class Mode {
        NORMAL, ABSENT, UNREADABLE
    }

    var slot: ByteArray? = slot
        private set

    var saved: MedicalData? = null
        private set

    var writeCount = 0
        private set

    private var armedFailure: String? = null

    val filePresent: Boolean
        get() = when (mode) {
            Mode.ABSENT -> false
            Mode.UNREADABLE -> true
            Mode.NORMAL -> slot != null
        }

    /**
     * SCHEMA.md's `inject: { service: store, fail_next: <error> }` names no operation, so the
     * armed failure is consumed by whichever store operation runs next.
     */
    fun armFailure(error: String) {
        armedFailure = error
    }

    private fun consumeFailure() {
        val name = armedFailure ?: return
        armedFailure = null
        throw ServiceError.Injected(name)
    }

    override suspend fun load(): MedicalData? {
        consumeFailure()
        return when (mode) {
            Mode.ABSENT -> null
            Mode.UNREADABLE -> throw ServiceError.DecodeFailed
            Mode.NORMAL -> {
                val bytes = slot ?: return null
                MedicalCodec.decode(bytes)
            }
        }
    }

    override suspend fun save(data: MedicalData) {
        consumeFailure()
        val bytes = MedicalCodec.encode(data)
        slot = bytes
        mode = Mode.NORMAL
        saved = MedicalCodec.decode(bytes)
        writeCount += 1
    }

    /** Idempotent. A removal is not a write, so writeCount is untouched. */
    override suspend fun clear() {
        consumeFailure()
        slot = null
        mode = Mode.NORMAL
    }

    fun resetCounters() {
        writeCount = 0
    }
}

/** Records the last replaceAll argument and counts invocations. Never delivers anything. */
class FixtureNotificationScheduler : NotificationScheduler {

    var scheduled: List<ScheduledNotification> = emptyList()
        private set

    var replaceCount = 0
        private set

    var authorized = true

    override suspend fun requestAuthorization(): Boolean = authorized

    override suspend fun replaceAll(notifications: List<ScheduledNotification>) {
        scheduled = notifications
        replaceCount += 1
    }

    fun resetCounters() {
        replaceCount = 0
    }
}

/** The whole fixture graph for one case. Cases never share state (SCHEMA.md#given). */
class FixtureServices(
    now: Instant,
    idSequence: List<UUID>,
    keyValues: Map<String, Boolean>,
    slot: ByteArray?,
    mode: FixtureMedicalStore.Mode
) {
    val clock = FixtureClock(now)
    val ids = FixtureIDGenerator(idSequence)
    val scheduler = FixtureScheduler()
    val keyValue = FixtureKeyValueStore(keyValues.toMutableMap())
    val store = FixtureMedicalStore(slot, mode)
    val notifications = FixtureNotificationScheduler()

    val ports: Services
        get() = Services(
            clock = clock,
            ids = ids,
            scheduler = scheduler,
            keyValue = keyValue,
            store = store,
            notifications = notifications
        )

    fun resetCounters() {
        ids.resetCounters()
        store.resetCounters()
        notifications.resetCounters()
    }
}
