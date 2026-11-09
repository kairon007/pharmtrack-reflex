import Foundation

/// The real platform codec. The fixture MedicalStore adapter encodes through this on save
/// and decodes through it on load, so `store.saved` is decode(encode(x)) — a genuine
/// round-trip, never the in-memory object handed to save (runner.yaml#fixture_adapters).
public enum MedicalCodec {
    /// Swift's DecodingError descriptions and Kotlin's SerializationException messages can
    /// never match, and `loadError` is part of the byte-compared stream. Both adapters emit
    /// this fixed token instead of a platform exception string.
    public static let decodeFailureToken = "medical_data.json could not be decoded"

    public static func encoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        return encoder
    }

    public static func decoder() -> JSONDecoder {
        JSONDecoder()
    }

    public static func encode(_ data: MedicalData) throws -> Data {
        try encoder().encode(data)
    }

    public static func decode(_ bytes: Data) throws -> MedicalData {
        try decoder().decode(MedicalData.self, from: bytes)
    }
}

extension ReflexValue {
    /// Parses JSON into the canonical tree, keeping integers and doubles distinct so
    /// canonicalization can honour runner.yaml's number rules.
    public static func parseJSON(_ bytes: Data) throws -> ReflexValue {
        let object = try JSONSerialization.jsonObject(with: bytes, options: [.fragmentsAllowed])
        return fromJSONObject(object)
    }

    public static func fromJSONObject(_ object: Any) -> ReflexValue {
        if object is NSNull { return .null }
        if let number = object as? NSNumber {
            if CFGetTypeID(number) == CFBooleanGetTypeID() { return .bool(number.boolValue) }
            let type = String(cString: number.objCType)
            if type == "d" || type == "f" { return .double(number.doubleValue) }
            return .int(number.intValue)
        }
        if let string = object as? String { return .string(string) }
        if let array = object as? [Any] { return .array(array.map(fromJSONObject)) }
        if let dictionary = object as? [String: Any] {
            return .object(dictionary.mapValues(fromJSONObject))
        }
        return .null
    }

    /// Re-serializes the tree. `canonicalJSON` is itself valid JSON, so the merged fixture
    /// bytes handed to the real decoder are deterministic on both platforms.
    public var jsonData: Data {
        Data(canonicalJSON.utf8)
    }
}