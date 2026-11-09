import Foundation

/// Pure text/number helpers shared by the machines. No locale, no formatter objects —
/// every function here must produce the identical string on iOS and Android.
public enum ValueFormat {
    /// Swift's `Double(String)`: no surrounding whitespace, accepts exponent, hex-float,
    /// "inf", "nan" and a leading "+". Kotlin must reproduce this acceptance set exactly
    /// (Q7 / numeric_leniency); `toDoubleOrNull` differs on hex and on whitespace.
    public static func parseDouble(_ text: String) -> Double? { Double(text) }

    /// Swift's `Int(String)`: strict, no whitespace, no decimal point, no exponent (Q6).
    public static func parseInt(_ text: String) -> Int? { Int(text) }

    /// D10 numeric_prefill — the shortest round-trip decimal form, NOT the display format.
    /// 78.2 -> "78.2", 120.0 -> "120". Exponent spelling is normalized so Swift's "1e+20"
    /// and Kotlin's "1.0E20" both become "1e20".
    public static func shortestDecimal(_ value: Double) -> String {
        if value == value.rounded(.towardZero), abs(value) < 1e15 {
            return String(Int(value))
        }
        guard value.isFinite else {
            if value.isNaN { return "nan" }
            return value < 0 ? "-inf" : "inf"
        }
        var text = String(value)
        if let index = text.firstIndex(where: { $0 == "e" || $0 == "E" }) {
            var mantissa = String(text[text.startIndex..<index])
            var exponent = String(text[text.index(after: index)...])
            if mantissa.hasSuffix(".0") { mantissa.removeLast(2) }
            var negative = false
            if exponent.hasPrefix("-") { negative = true; exponent.removeFirst() }
            if exponent.hasPrefix("+") { exponent.removeFirst() }
            while exponent.count > 1, exponent.hasPrefix("0") { exponent.removeFirst() }
            text = mantissa + "e" + (negative ? "-" : "") + exponent
        }
        return text
    }

    /// D14 display rounding. Darwin's printf rounds the EXACT binary value half-to-even;
    /// Kotlin's String.format(Locale.ROOT, ...) rounds half-up and would disagree on ties
    /// such as 76.25. The Kotlin port must use
    /// BigDecimal(value).setScale(n, RoundingMode.HALF_EVEN).toPlainString().
    public static func fixed(_ value: Double, decimals: Int) -> String {
        String(format: "%.\(decimals)f", value)
    }

    /// `Int(Double)` truncates toward zero (120.7 -> 120, 80.9 -> 80). The legacy initializer
    /// traps on nan/infinity/out-of-range; this clamps instead so a leniently-accepted value
    /// (numeric_leniency) can never crash a render. No journey reaches the clamp.
    public static func truncatingInt(_ value: Double) -> Int {
        guard value.isFinite else { return value.isNaN ? 0 : (value < 0 ? Int.min : Int.max) }
        let truncated = value.rounded(.towardZero)
        if let exact = Int(exactly: truncated) { return exact }
        return truncated < 0 ? Int.min : Int.max
    }

    /// D14. No unit inside — the caller appends the unit exactly once.
    public static func vitalValue(_ vital: Vital) -> String {
        switch vital.type {
        case .weight:
            return fixed(vital.value, decimals: 1)
        case .bloodSugar:
            return fixed(vital.value, decimals: 0)
        case .bloodPressure:
            return "\(truncatingInt(vital.value))/\(truncatingInt(vital.value2 ?? 0))"
        }
    }

    public static func unit(for type: VitalType) -> String {
        switch type {
        case .weight: return "kg"
        case .bloodPressure: return "mmHg"
        case .bloodSugar: return "mg/dL"
        }
    }

    /// The Vitals tab and Vitals Detail headline: latest reading plus unit, "--" when empty.
    public static func reading(_ vital: Vital?) -> String {
        guard let vital else { return "--" }
        return vitalValue(vital) + " " + unit(for: vital.type)
    }

    /// Q12. First character of the first two space-separated tokens, uppercased. Repeated
    /// and leading spaces collapse because the split drops empty subsequences.
    /// "Dr. Sarah Jenkins" -> "DS", "Madonna" -> "M", "   " -> "?".
    public static func initials(_ name: String) -> String {
        let tokens = name.split(separator: " ")
        let letters = tokens.prefix(2).compactMap { $0.first }.map { String($0).uppercased() }
        let joined = letters.joined()
        return joined.isEmpty ? "?" : joined
    }

    /// No night branch and no name — hours 0-11 are morning (DashboardView.swift:49-54).
    public static func greeting(hour: Int) -> String {
        if hour < 12 { return "Good morning" }
        if hour < 18 { return "Good afternoon" }
        return "Good evening"
    }

    /// D11. Nearest week, ties away from zero: -4 days -> -1, -3 days -> 0, 43 days -> 6.
    public static func weekOffset(days: Int) -> Int {
        Int((Double(days) / 7.0).rounded())
    }
}