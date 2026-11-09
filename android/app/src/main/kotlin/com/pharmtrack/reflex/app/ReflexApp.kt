package com.pharmtrack.reflex.app

import android.content.Context
import com.pharmtrack.reflex.core.App.Action
import com.pharmtrack.reflex.core.App.MachineContext
import com.pharmtrack.reflex.core.App.Message
import com.pharmtrack.reflex.core.App.RootSession
import com.pharmtrack.reflex.core.App.Services
import com.pharmtrack.reflex.core.App.AppState
import com.pharmtrack.reflex.core.Domain.MedicalData
import com.pharmtrack.reflex.core.Screens.AppRootAction
import com.pharmtrack.reflex.core.Support.Instant
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The live host: owns one RootSession, republishes its AppState after every reduction and
 * every flushed service effect, and fires live scheduler messages back into the session.
 * Composables only read `state` / the typed screen data and call `send(action)`.
 */
class ReflexApp(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val clock = LiveClock()
    private val scheduler = HandlerScheduler { message -> receive(message) }
    private val session: RootSession
    private val _state: MutableStateFlow<AppState>

    val state: StateFlow<AppState> get() = _state

    private var draining = false
    private var started = false

    init {
        // Instant.Zone models a fixed offset; the live host pins the device's current one.
        val now = clock.now()
        val offsetSeconds = TimeZone.getDefault().getOffset((now.secondsSince1970 * 1000.0).toLong()) / 1000
        val zone = Instant.Zone(offsetSeconds)
        val services = Services(
            clock = clock,
            ids = LiveIDGenerator(),
            scheduler = scheduler,
            keyValue = PrefsKeyValueStore(context),
            store = FileMedicalStore(context),
            notifications = AlarmNotificationScheduler(context)
        )
        session = RootSession(now, zone, services)
        _state = MutableStateFlow(session.state)
    }

    val context: MachineContext get() = session.context

    val data: MedicalData get() = session.state.appRoot.data

    /** Entry point from the app shell's first composition. */
    fun start() {
        if (started) return
        started = true
        send(Action.ToAppRoot(AppRootAction.OnAppear))
        scope.launch { session.services.notifications.requestAuthorization() }
    }

    fun send(action: Action) {
        session.send(action)
        publish()
        drain()
    }

    private fun receive(message: Message) {
        session.deliver(message)
        publish()
        drain()
    }

    private fun publish() {
        _state.value = session.state
    }

    /** One drain at a time; the session's FIFO loop picks up effects queued mid-flush. */
    private fun drain() {
        if (draining) return
        draining = true
        scope.launch {
            session.flushStore()
            draining = false
            publish()
        }
    }
}
