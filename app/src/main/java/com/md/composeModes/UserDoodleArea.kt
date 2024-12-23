package com.md.composeModes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

object UserDoodleArea {
    data class Edge(
        val start: Offset,
        val end: Offset,
    )

    @Composable
    fun DrawingCanvas(modifier: Modifier = Modifier.height(96.dp), edges: SnapshotStateList<Edge>) {
        Canvas(modifier = modifier.fillMaxWidth()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val edge = Edge(
                        start = change.position - dragAmount,
                        end = change.position
                    )
                    edges.add(edge)
                }
            }) {
            edges.forEach { line ->
                drawLine(
                    color = Color.White,
                    start = line.start,
                    end = line.end,
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}