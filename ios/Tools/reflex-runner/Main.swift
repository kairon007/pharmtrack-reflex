import Core
import Foundation
import RunnerKit

/// runner.yaml#cli. This binary IS the iOS implementation, so it rejects --platform android
/// rather than silently emitting an iOS stream under an Android label.
@main
struct ReflexRunner {
    static func main() async {
        do {
            let options = try Options.parse(CommandLine.arguments)
            let specs = try Specs.locate()
            let out: NDJSON
            if let path = options.outputPath {
                out = try NDJSON.file(path)
            } else {
                out = NDJSON.standardOutput()
            }
            let executor = Executor(specs: specs, out: out)
            let code = try await executor.run(glob: options.spec, caseFilter: options.caseID)
            out.finish()
            exit(code)
        } catch let error as RunnerError {
            fail(error.description, code: error.exitCode)
        } catch let error as ActionError {
            fail(error.description, code: 2)
        } catch {
            fail(String(describing: error), code: 2)
        }
    }

    private static func fail(_ message: String, code: Int32) -> Never {
        FileHandle.standardError.write(Data(("reflex-runner: " + message + "\n").utf8))
        exit(code)
    }
}

struct Options {
    let spec: String
    let caseID: String?
    let outputPath: URL?

    static let usage = """
    usage: reflex-runner --platform ios --spec <glob> [--case <id>] [--out <file>]
      --platform  required, must be ios for this binary
      --spec      glob relative to reflex/specs/behavior/, e.g. "vital_*.yaml"
      --case      run only this case id
      --out       write NDJSON here instead of stdout
    Set REFLEX_SPECS_DIR to point at reflex/specs when running outside the repository.
    """

    static func parse(_ arguments: [String]) throws -> Options {
        var platform: String?
        var spec: String?
        var caseID: String?
        var outputPath: URL?

        var index = 1
        while index < arguments.count {
            let flag = arguments[index]
            switch flag {
            case "--platform", "--spec", "--case", "--out":
                guard index + 1 < arguments.count else {
                    throw RunnerError.usage("\(flag) needs a value\n\(usage)")
                }
                let value = arguments[index + 1]
                switch flag {
                case "--platform": platform = value
                case "--spec": spec = value
                case "--case": caseID = value
                default: outputPath = URL(fileURLWithPath: value)
                }
                index += 2
            case "--help", "-h":
                throw RunnerError.usage(usage)
            default:
                throw RunnerError.usage("unknown flag \(flag)\n\(usage)")
            }
        }

        guard let platform else { throw RunnerError.usage("--platform is required\n\(usage)") }
        guard platform == "ios" else {
            throw RunnerError.usage("this binary is the ios implementation, not \(platform)")
        }
        guard let spec else { throw RunnerError.usage("--spec is required\n\(usage)") }
        return Options(spec: spec, caseID: caseID, outputPath: outputPath)
    }
}