package com.pharmtrack.reflex.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Presentation geometry only, ported from VitalsView.swift:350-401 (DynamicChartPath).
 * The VALUES come from the machines (`chartValues`); this shape merely maps them to points.
 */
object TrendChartGeometry {

    fun points(data: List<Double>, width: Float, height: Float): List<Offset> {
        if (data.size <= 1) {
            return listOf(Offset(0f, height * 0.5f), Offset(width, height * 0.5f))
        }
        val maxValue = data.max()
        val minValue = data.min()
        val range = maxValue - minValue
        val adjustedRange = if (range == 0.0) 1.0 else range
        val stepX = width / (data.size - 1)

        return data.mapIndexed { index, value ->
            val normalizedY = if (range == 0.0) 0.5f else (1.0 - (value - minValue) / adjustedRange).toFloat()
            val y = normalizedY * (height * 0.8f) + (height * 0.1f)
            Offset(index * stepX, y)
        }
    }

    fun path(data: List<Double>, width: Float, height: Float, closed: Boolean): Path {
        val path = Path()

        if (data.size <= 1) {
            path.moveTo(0f, height * 0.5f)
            path.lineTo(width, height * 0.5f)
            if (closed) {
                path.lineTo(width, height)
                path.lineTo(0f, height)
                path.close()
            }
            return path
        }

        for ((index, point) in points(data, width, height).withIndex()) {
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }

        if (closed) {
            path.lineTo(width, height)
            path.lineTo(0f, height)
            path.close()
        }

        return path
    }
}

@Composable
fun TrendChart(
    data: List<Double>,
    modifier: Modifier = Modifier,
    closed: Boolean = false,
    lineColor: Color = Theme.primary,
    fillBrush: Brush? = null
) {
    Canvas(modifier = modifier) {
        if (closed && fillBrush != null) {
            drawPath(
                path = TrendChartGeometry.path(data, size.width, size.height, true),
                brush = fillBrush
            )
        }
        // The line follows the data only; the closing edges belong to the fill.
        drawPath(
            path = TrendChartGeometry.path(data, size.width, size.height, false),
            color = lineColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}
