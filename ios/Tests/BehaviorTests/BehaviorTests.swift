import Core
import Foundation
import RunnerKit
import XCTest

/// The Phase 2 exit gate, headless: the frozen suite runs against Core with the fixture service
/// graph. No simulator, no filesystem, no clock, no notification permission.
///
/// These tests use the SAME executor and the SAME canonicalization as `reflex-runner`, so a green
/// `swift test` and a green runner are the same claim, not two approximations of it.
final class BehaviorTests: XCTestCase {
    private func specs() throws -> Specs {
        try Specs.locate()
    }

    func testEveryJourneyParses() throws {
        let specs = try specs()
        let files = try specs.journeyFiles(matching: "*.yaml")
        XCTAssertFalse(files.isEmpty, "no journeys found")
        var caseCount = 0
        for file in files {
            let text = try String(contentsOf: file, encoding: .utf8)
            let journey = try JourneyParser.parse(text, fileName: file.lastPathComponent)
            XCTAssertEqual(journey.id, file.deletingPathExtension().lastPathComponent,
                           "journey id must be the filename stem")
            XCTAssertFalse(journey.cases.isEmpty, "\(journey.id) has no cases")
            caseCount += journey.cases.count
        }
        XCTAssertGreaterThan(caseCount, 0)
    }

    func testSuitePasses() async throws {
        let specs = try specs()
        let out = NDJSON.inMemory()
        let code = try await Executor(specs: specs, out: out).run(glob: "*.yaml", caseFilter: nil)
        if code != 0 {
            XCTFail(Self.failureReport(out.lines))
        }
    }

    /// runner.yaml#determinism: two runs of the same binary with the same spec must produce
    /// byte-identical output. This is the property the whole cross-platform diff rests on.
    func testStreamIsDeterministic() async throws {
        let specs = try specs()
        let first = NDJSON.inMemory()
        let second = NDJSON.inMemory()
        _ = try await Executor(specs: specs, out: first).run(glob: "*.yaml", caseFilter: nil)
        _ = try await Executor(specs: specs, out: second).run(glob: "*.yaml", caseFilter: nil)

        XCTAssertEqual(first.lines.count, second.lines.count, "record count differs between runs")
        for (index, pair) in zip(first.lines, second.lines).enumerated() where pair.0 != pair.1 {
            XCTFail("line \(index) differs between runs")
            break
        }
    }

    func testUnknownCaseIsAUsageError() async throws {
        let specs = try specs()
        do {
            _ = try await Executor(specs: specs, out: .inMemory())
                .run(glob: "*.yaml", caseFilter: "no_such_case_anywhere")
            XCTFail("an unknown case id must be rejected")
        } catch let error as RunnerError {
            XCTAssertEqual(error.exitCode, 2)
        }
    }

    func testInternalActionsAreNotSendable() {
        // commitData and friends are reachable only from another reducer's effect list
        // (nodes.yaml). The executor validates against `actions`, so an overlap would let a
        // journey drive an internal transition directly and mask a missing public path.
        for schema in NodeRegistry.schemas {
            for name in schema.internalActions {
                XCTAssertFalse(schema.actions.contains(name),
                               "\(schema.id.rawValue).\(name) must not be a public action")
            }
        }
        XCTAssertEqual(NodeRegistry.schemas.count, NodeID.allCases.count)
    }

    /// runner.yaml#failure_report: the journey, the case, the step and the PATH. Never a whole
    /// state object — those are large and the path is what a human needs.
    private static func failureReport(_ lines: [String]) -> String {
        var journey = ""
        var caseID = ""
        var report: [String] = []
        for line in lines {
            guard let record = try? ReflexValue.parseJSON(Data(line.utf8)).objectValue else { continue }
            switch record["type"]?.stringValue {
            case "case_start":
                journey = record["journey"]?.stringValue ?? ""
                caseID = record["case"]?.stringValue ?? ""
            case "assertion" where record["passed"]?.boolValue == false:
                let step = record["step"]?.intValue ?? -1
                let path = record["path"]?.stringValue ?? ""
                let op = record["operator"]?.stringValue ?? ""
                report.append("\(journey)#\(caseID) step \(step): \(path) \(op)")
            default:
                continue
            }
        }
        return report.isEmpty
            ? "the suite failed without recording a failing assertion"
            : report.prefix(40).joined(separator: "\n")
    }
}