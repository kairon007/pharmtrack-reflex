import Foundation

/// A wall-clock instant, stored as seconds since the Unix epoch.
///
/// Formatting is implemented arithmetically (Howard Hinnant's civil-date algorithms) rather
/// than through `DateFormatter`/`ISO8601DateFormatter`, so output is byte-identical on every
/// platform and in every locale — runner.yaml#canonicalization requires exactly
/// `yyyy-MM-ddTHH:mm:ssZ` with no fractional part.
public struct Instant: Hashable, Comparable, Sendable {
    public let secondsSince1970: Double

    public init(secondsSince1970: Double) {
        self.secondsSince1970 = secondsSince1970
    }

    public init(secondsSince1970: Int) {
        self.secondsSince1970 = Double(secondsSince1970)
    }

    public static let epoch = Instant(secondsSince1970: 0)
    public static let distantFuture = Instant(secondsSince1970: 253_402_300_799)
    public static let distantPast = Instant(secondsSince1970: -62_135_596_800)

    public static func < (lhs: Instant, rhs: Instant) -> Bool {
        lhs.secondsSince1970 < rhs.secondsSince1970
    }

    public func addingSeconds(_ seconds: Double) -> Instant {
        Instant(secondsSince1970: secondsSince1970 + seconds)
    }

    public func addingDays(_ days: Int) -> Instant {
        addingSeconds(Double(days) * 86_400)
    }

    public func timeIntervalSince(_ other: Instant) -> Double {
        secondsSince1970 - other.secondsSince1970
    }

    /// The pinned zone for the suite (fixtures/determinism.yaml). Only a fixed offset is
    /// modelled — no DST rules are needed, and none may leak into a reducer.
    public struct Zone: Hashable, Sendable {
        public let offsetSeconds: Int
        public init(offsetSeconds: Int) { self.offsetSeconds = offsetSeconds }
        public static let utc = Zone(offsetSeconds: 0)
    }

    public func startOfDay(in zone: Zone) -> Instant {
        let shifted = secondsSince1970 + Double(zone.offsetSeconds)
        let dayStart = (shifted / 86_400).rounded(.down) * 86_400
        return Instant(secondsSince1970: dayStart - Double(zone.offsetSeconds))
    }

    /// Days since the epoch in the given zone — the key used to compare calendar days
    /// without ever consulting the host timezone.
    public func dayIndex(in zone: Zone) -> Int {
        Int(((secondsSince1970 + Double(zone.offsetSeconds)) / 86_400).rounded(.down))
    }

    public func hour(in zone: Zone) -> Int {
        let shifted = Int(secondsSince1970.rounded(.down)) + zone.offsetSeconds
        let secondOfDay = ((shifted % 86_400) + 86_400) % 86_400
        return secondOfDay / 3_600
    }

    public var iso8601: String {
        let whole = secondsSince1970.rounded(.down)
        let days = Int((whole / 86_400).rounded(.down))
        let secondOfDay = Int(whole) - days * 86_400

        let (year, month, day) = Instant.civilFromDays(days)
        let hour = secondOfDay / 3_600
        let minute = (secondOfDay % 3_600) / 60
        let second = secondOfDay % 60

        let y = year < 0 ? "-" + Instant.pad(-year, 4) : Instant.pad(year, 4)
        return "\(y)-\(Instant.pad(month, 2))-\(Instant.pad(day, 2))T"
            + "\(Instant.pad(hour, 2)):\(Instant.pad(minute, 2)):\(Instant.pad(second, 2))Z"
    }

    public static func parse(_ text: String) -> Instant? {
        let scalars = Array(text.unicodeScalars)
        guard scalars.count >= 20 else { return nil }

        func digits(_ range: Range<Int>) -> Int? {
            var value = 0
            for index in range {
                guard index < scalars.count else { return nil }
                let scalar = scalars[index]
                guard scalar.value >= 48, scalar.value <= 57 else { return nil }
                value = value * 10 + Int(scalar.value - 48)
            }
            return value
        }

        var yearNegative = false
        var cursor = 0
        if scalars[0] == "-" { yearNegative = true; cursor = 1 }
        guard let yearRaw = digits(cursor..<(cursor + 4)) else { return nil }
        cursor += 4
        guard scalars[cursor] == "-" else { return nil }
        cursor += 1
        guard let month = digits(cursor..<(cursor + 2)), month >= 1, month <= 12 else { return nil }
        cursor += 2
        guard scalars[cursor] == "-" else { return nil }
        cursor += 1
        guard let day = digits(cursor..<(cursor + 2)), day >= 1, day <= 31 else { return nil }
        cursor += 2
        guard cursor < scalars.count, scalars[cursor] == "T" || scalars[cursor] == "t"
            || scalars[cursor] == " " else { return nil }
        cursor += 1
        guard let hour = digits(cursor..<(cursor + 2)), hour <= 23 else { return nil }
        cursor += 2
        guard cursor < scalars.count, scalars[cursor] == ":" else { return nil }
        cursor += 1
        guard let minute = digits(cursor..<(cursor + 2)), minute <= 59 else { return nil }
        cursor += 2
        guard cursor < scalars.count, scalars[cursor] == ":" else { return nil }
        cursor += 1
        guard let second = digits(cursor..<(cursor + 2)), second <= 60 else { return nil }
        cursor += 2

        // Fractional seconds are accepted on read and dropped on write.
        if cursor < scalars.count, scalars[cursor] == "." {
            cursor += 1
            let start = cursor
            while cursor < scalars.count, scalars[cursor].value >= 48, scalars[cursor].value <= 57 {
                cursor += 1
            }
            guard cursor > start else { return nil }
        }

        var offsetSeconds = 0
        if cursor < scalars.count {
            let marker = scalars[cursor]
            if marker == "Z" || marker == "z" {
                cursor += 1
            } else if marker == "+" || marker == "-" {
                let sign = marker == "-" ? -1 : 1
                cursor += 1
                guard let offsetHour = digits(cursor..<(cursor + 2)) else { return nil }
                cursor += 2
                var offsetMinute = 0
                if cursor < scalars.count {
                    if scalars[cursor] == ":" { cursor += 1 }
                    guard let parsed = digits(cursor..<(cursor + 2)) else { return nil }
                    offsetMinute = parsed
                    cursor += 2
                }
                offsetSeconds = sign * (offsetHour * 3_600 + offsetMinute * 60)
            } else {
                return nil
            }
        }
        guard cursor == scalars.count else { return nil }

        let year = yearNegative ? -yearRaw : yearRaw
        let days = Instant.daysFromCivil(year: year, month: month, day: day)
        let seconds = days * 86_400 + hour * 3_600 + minute * 60 + second - offsetSeconds
        return Instant(secondsSince1970: seconds)
    }

    static func pad(_ value: Int, _ width: Int) -> String {
        var text = String(value)
        while text.count < width { text = "0" + text }
        return text
    }

    /// Howard Hinnant, "chrono-Compatible Low-Level Date Algorithms".
    static func civilFromDays(_ z0: Int) -> (year: Int, month: Int, day: Int) {
        let z = z0 + 719_468
        let era = (z >= 0 ? z : z - 146_096) / 146_097
        let doe = z - era * 146_097
        let yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        let y = yoe + era * 400
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let d = doy - (153 * mp + 2) / 5 + 1
        let m = mp + (mp < 10 ? 3 : -9)
        return (y + (m <= 2 ? 1 : 0), m, d)
    }

    static func daysFromCivil(year: Int, month: Int, day: Int) -> Int {
        let y = year - (month <= 2 ? 1 : 0)
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400
        let mp = month + (month > 2 ? -3 : 9)
        let doy = (153 * mp + 2) / 5 + day - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }
}

extension Instant: Codable {
    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let text = try container.decode(String.self)
        guard let parsed = Instant.parse(text) else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Expected ISO8601 UTC instant, got \(text)"
            )
        }
        self = parsed
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(iso8601)
    }
}