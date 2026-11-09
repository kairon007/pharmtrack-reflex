package com.pharmtrack.reflex.core.App

/** The 14 machines. Raw values are the spec's node names and appear verbatim in the stream. */
enum class NodeID(val rawValue: String) {
    APP_ROOT("app_root"),
    ONBOARDING("onboarding"),
    DASHBOARD("dashboard"),
    CONSULTATIONS("consultations"),
    MEDICATION_TRACKER("medication_tracker"),
    APPOINTMENTS("appointments"),
    VITALS("vitals"),
    VITALS_DETAIL("vitals_detail"),
    ADD_VITAL("add_vital"),
    ADD_MEDICATION("add_medication"),
    ADD_APPOINTMENT("add_appointment"),
    CONSULTATION_FORM("consultation_form"),
    MEDICATION_PICKER("medication_picker"),
    SETTINGS("settings");

    /** Tab index for the five tab nodes, null for overlays, pushes and modals. */
    val tabIndex: Int?
        get() = when (this) {
            DASHBOARD -> 0
            CONSULTATIONS -> 1
            MEDICATION_TRACKER -> 2
            APPOINTMENTS -> 3
            VITALS -> 4
            else -> null
        }

    companion object {
        fun fromRaw(raw: String): NodeID? = entries.firstOrNull { it.rawValue == raw }
    }
}

enum class CommitReason(val rawValue: String) {
    ADD("add"), UPDATE("update"), DELETE("delete"), MARK_TAKEN("markTaken"), SETTINGS("settings"), SEED("seed")
}

enum class LoadPhase(val rawValue: String) {
    IDLE("idle"), LOADING("loading"), LOADED("loaded"), UNREADABLE("unreadable")
}

enum class FormMode(val rawValue: String) {
    CLOSED("closed"), ADD("add"), EDIT("edit")
}

enum class EditMode(val rawValue: String) {
    ADD("add"), EDIT("edit");

    companion object {
        fun fromRaw(raw: String): EditMode? = entries.firstOrNull { it.rawValue == raw }
    }
}
