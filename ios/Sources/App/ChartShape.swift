import SwiftUI

/// Presentation geometry only, ported from VitalsView.swift:350-401 (DynamicChartPath).
/// The VALUES come from the machines (`chartValues`); this shape merely maps them to points.
struct TrendChartShape: Shape {
    var closed: Bool = false
    var data: [Double]

    func path(in rect: CGRect) -> Path {
        var path = Path()

        guard data.count > 1 else {
            path.move(to: CGPoint(x: 0, y: rect.height * 0.5))
            path.addLine(to: CGPoint(x: rect.width, y: rect.height * 0.5))
            if closed {
                path.addLine(to: CGPoint(x: rect.width, y: rect.height))
                path.addLine(to: CGPoint(x: 0, y: rect.height))
                path.closeSubpath()
            }
            return path
        }

        let maxVal = data.max() ?? 100
        let minVal = data.min() ?? 0
        let range = maxVal - minVal
        let adjustedRange = range == 0 ? 1.0 : range
        let stepX = rect.width / CGFloat(data.count - 1)

        for (index, value) in data.enumerated() {
            let normalizedY: CGFloat = range == 0 ? 0.5 : 1.0 - CGFloat((value - minVal) / adjustedRange)
            let y = normalizedY * (rect.height * 0.8) + (rect.height * 0.1)
            let x = CGFloat(index) * stepX
            if index == 0 {
                path.move(to: CGPoint(x: x, y: y))
            } else {
                path.addLine(to: CGPoint(x: x, y: y))
            }
        }

        if closed {
            path.addLine(to: CGPoint(x: rect.width, y: rect.height))
            path.addLine(to: CGPoint(x: 0, y: rect.height))
            path.closeSubpath()
        }

        return path
    }
}