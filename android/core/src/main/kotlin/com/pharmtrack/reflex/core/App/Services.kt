package com.pharmtrack.reflex.core.App

import com.pharmtrack.reflex.core.Domain.MedicalData
import com.pharmtrack.reflex.core.Domain.ScheduledNotification
import com.pharmtrack.reflex.core.Support.Instant
import java.util.UUID

/**
 * Service ports from specs/contracts/services.yaml. Core depends only on these; the live
 * adapters (Phase 4) and the fixture adapters (RunnerKit) live outside Core.
 *
 * The impure operations are suspend functions because the file and the notification center
 * are async boundaries. The fixture adapters complete without suspending, so the runtime's
 * FIFO drain is fully deterministic and the headless runner never observes wall-clock timing.
 */
interface Clock {
    fun now(): Instant
}

interface IDGenerator {
    /**
     * Non-throwing so reducers stay total ("reducers do not throw"). The fixture adapter
     * records exhaustion instead of wrapping, and the runtime turns that into exit code 3.
     */
    fun next(): UUID
}

class IDCancellation(val token: Int)

interface Scheduler {
    /**
     * A non-nil key REPLACES any pending entry with that key (the onboarding reveal).
     * A nil key stacks (the vitals_detail highlight clear, guarded by its carried id).
     */
    fun after(seconds: Double, key: String?, node: NodeID, message: Message): IDCancellation

    fun cancel(handle: IDCancellation)
}

interface KeyValueStore {
    fun bool(key: String): Boolean?

    fun setBool(key: String, value: Boolean)
}

interface MedicalStore {
    /** null when the file is absent; throws when present but unparseable. Never partial data. */
    suspend fun load(): MedicalData?

    suspend fun save(data: MedicalData)

    /** Idempotent — clearing an absent file is success, not an error. */
    suspend fun clear()
}

interface NotificationScheduler {
    suspend fun requestAuthorization(): Boolean

    /** CANONICAL: cancel every pending notification, then schedule the supplied set. */
    suspend fun replaceAll(notifications: List<ScheduledNotification>)
}

data class Services(
    val clock: Clock,
    val ids: IDGenerator,
    val scheduler: Scheduler,
    val keyValue: KeyValueStore,
    val store: MedicalStore,
    val notifications: NotificationScheduler
)

sealed class ServiceError(message: String) : Exception(message) {
    class Injected(val name: String) : ServiceError(name)

    object IdsExhausted : ServiceError("idsExhausted")

    object DecodeFailed : ServiceError("decodeFailed")
}

object KeyValueKey {
    /** The legacy key. Not renamed or namespaced on upgrade (persistence.yaml#key_value_store). */
    const val HAS_COMPLETED_ONBOARDING = "hasCompletedOnboarding"
}
