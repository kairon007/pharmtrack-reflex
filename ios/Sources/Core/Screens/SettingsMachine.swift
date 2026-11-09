import Foundation

public struct SettingsState: Equatable {
    public var draft: NotificationSettings

    public init(draft: NotificationSettings = .default) {
        self.draft = draft
    }
}

public enum SettingsAction {
    case setAllEnabled(Bool)
    case setMedicationsEnabled(Bool)
    case setAppointmentsEnabled(Bool)
    case setConsultationsEnabled(Bool)
    case setLowStockEnabled(Bool)
    case setLowStockThreshold(Int)
    case setMedicationReminderTime(ReminderTime)
    case setAppointmentReminderTime(ReminderTime)
    case save
    case cancel
    /// Internal: app_root.presentSettings reseeds the draft from the committed data.
    case seedDraft(NotificationSettings)

    public static func decode(_ name: String, _ args: ActionArguments) throws -> SettingsAction? {
        switch name {
        case "setAllEnabled":
            return .setAllEnabled(try args.bool("value"))
        case "setMedicationsEnabled":
            return .setMedicationsEnabled(try args.bool("value"))
        case "setAppointmentsEnabled":
            return .setAppointmentsEnabled(try args.bool("value"))
        case "setConsultationsEnabled":
            return .setConsultationsEnabled(try args.bool("value"))
        case "setLowStockEnabled":
            return .setLowStockEnabled(try args.bool("value"))
        case "setLowStockThreshold":
            return .setLowStockThreshold(try args.int("value"))
        case "setMedicationReminderTime":
            return .setMedicationReminderTime(try args.enumeration("value", ReminderTime.self))
        case "setAppointmentReminderTime":
            return .setAppointmentReminderTime(try args.enumeration("value", ReminderTime.self))
        case "save":
            return .save
        case "cancel":
            return .cancel
        default:
            return nil
        }
    }
}

public enum SettingsMachine {
    public static let thresholdOptions: [Int] = [3, 5, 10]

    public static func reduce(
        _ state: SettingsState,
        _ action: SettingsAction,
        _ context: MachineContext
    ) -> (SettingsState, [Effect]) {
        var state = state
        switch action {
        case let .setAllEnabled(value):
            state.draft.allNotificationsEnabled = value
        case let .setMedicationsEnabled(value):
            state.draft.medicationsEnabled = value
        case let .setAppointmentsEnabled(value):
            state.draft.appointmentsEnabled = value
        case let .setConsultationsEnabled(value):
            state.draft.consultationsEnabled = value
        case let .setLowStockEnabled(value):
            state.draft.lowStockEnabled = value
        case let .setLowStockThreshold(value):
            state.draft.lowStockThreshold = value
        case let .setMedicationReminderTime(value):
            state.draft.medicationReminderTime = value
        case let .setAppointmentReminderTime(value):
            state.draft.appointmentReminderTime = value
        case let .seedDraft(settings):
            state.draft = settings
            return (state, [])

        case .save:
        // Q14. No dirty check — saving unchanged settings still writes and reschedules.
        // Hiding a section never reset its values, so the whole draft commits verbatim.
            var data = context.data
            data.notificationSettings = state.draft
            return (state, [
                .dispatch(.appRoot(.commitData(data, .settings))),
                .dispatch(.appRoot(.dismissSettings)),
            ])

        case .cancel:
            return (state, [.dispatch(.appRoot(.dismissSettings))])
        }
        // Every control binds to the draft. app_root.data is untouched until save.
        return (state, [])
    }

    public static func emit(_ state: SettingsState, _ context: MachineContext) -> ReflexValue {
        let draft = state.draft
        var fields: [String: ReflexValue] = [:]
        fields["draft"] = draft.reflexValue
        fields["sectionsVisible"] = .bool(draft.allNotificationsEnabled)
        fields["medicationTimeVisible"] = .bool(draft.allNotificationsEnabled && draft.medicationsEnabled)
        fields["thresholdVisible"] = .bool(draft.allNotificationsEnabled && draft.lowStockEnabled)
        fields["appointmentTimeVisible"] = .bool(draft.allNotificationsEnabled && draft.appointmentsEnabled)
        fields["thresholdOptions"] = .array(thresholdOptions.map { .int($0) })
        fields["reminderOptions"] = .array(ReminderTime.allCases.map { .string($0.rawValue) })
        return .object(fields)
    }
}