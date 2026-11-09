import Core
import Foundation
import Yams

/// YAML is a TOOL concern (runner.yaml#implementation_constraints.ios.yaml). Core receives
/// plain ReflexValues and never sees a YAML node, a Date or an NSNumber.
public enum YAML {
    public static func parse(_ text: String) throws -> ReflexValue {
        let object = try Yams.load(yaml: text)
        return convert(object)
    }

    public static func parse(file url: URL) throws -> ReflexValue {
        try parse(String(contentsOf: url, encoding: .utf8))
    }

    /// Yams hands back Foundation collection types. Ints and doubles must stay distinct here:
    /// ReflexValue compares them numerically but serializes them differently, and the stream
    /// is byte-compared.
    static func convert(_ object: Any?) -> ReflexValue {
        guard let object else { return .null }
        if object is NSNull { return .null }

        if let number = object as? NSNumber {
            if CFGetTypeID(number) == CFBooleanGetTypeID() { return .bool(number.boolValue) }
            let kind = String(cString: number.objCType)
            if kind == "d" || kind == "f" { return .double(number.doubleValue) }
            return .int(number.intValue)
        }
        if let text = object as? String { return .string(text) }
        if let date = object as? Date {
            // Defensive: every instant in the suite is quoted, but a YAML timestamp resolver
            // would otherwise smuggle a Date into a string field.
            return .string(Instant(secondsSince1970: date.timeIntervalSince1970).iso8601)
        }
        if let array = object as? [Any] { return .array(array.map(convert)) }
        if let dictionary = object as? [AnyHashable: Any] {
            var result: [String: ReflexValue] = [:]
            for (key, value) in dictionary {
                result[String(describing: key)] = convert(value)
            }
            return .object(result)
        }
        return .string(String(describing: object))
    }
}