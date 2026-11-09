package com.pharmtrack.reflex.core.App

import com.pharmtrack.reflex.core.Domain.MedicalData
import com.pharmtrack.reflex.core.Domain.ScheduledNotification

/**
 * Effects are the only way a reducer reaches the outside world.
 *
 * `dispatch` and `deliver` are serviced IMMEDIATELY and synchronously, in order, before the
 * step record is emitted — that is what makes `markTaken` mutate `data` and consume its id
 * before any flush (medication_lifecycle asserts both right after `send`). Everything else
 * is deferred onto one of two FIFO queues and only runs on `flush`.
 */
sealed class Effect {

    data class Dispatch(val action: Action) : Effect()

    data class Deliver(val message: Message) : Effect()

    data object StoreLoad : Effect()

    data class StoreSave(val data: MedicalData) : Effect()

    data object StoreClear : Effect()

    data class KvRead(val key: String) : Effect()

    data class KvWrite(val key: String, val value: Boolean) : Effect()

    data class ReplaceAll(val notifications: List<ScheduledNotification>) : Effect()

    data class After(val seconds: Double, val key: String?, val message: Message) : Effect()

    val isImmediate: Boolean get() = this is Dispatch || this is Deliver

    /** Timers drain on `flush: scheduler`; service calls drain on `flush: store`. */
    val isTimer: Boolean get() = this is After
}
