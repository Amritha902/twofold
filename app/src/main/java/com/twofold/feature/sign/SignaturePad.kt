package com.twofold.feature.sign

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.twofold.core.design.LocalTwofoldColors

/** One continuous pen-down-to-pen-up stroke, in pad-local coordinates. */
typealias Stroke = List<Offset>

/**
 * The signature surface, shown on the client's half.
 *
 * Note on coordinates: this pane is rendered inside a 180°-rotated container so it reads correctly
 * to the person opposite. Compose hit-tests *through* graphicsLayer transforms, so the offsets
 * arriving here are already in this composable's local space and need no manual un-rotation.
 * Applying one would double-transform and put the ink where the finger isn't — verify on hardware
 * before touching this.
 */
@Composable
fun SignaturePad(
    signerName: String,
    modifier: Modifier = Modifier,
    onStrokesChanged: (List<Stroke>) -> Unit = {},
) {
    val colors = LocalTwofoldColors.current
    val strokes = remember { mutableStateListOf<Stroke>() }
    val current = remember { mutableStateListOf<Offset>() }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paperRaised)
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Text(
            text = "Please sign below",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.ink,
        )

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            current.clear()
                            current.add(offset)
                        },
                        onDrag = { change, _ ->
                            current.add(change.position)
                            change.consume()
                        },
                        onDragEnd = {
                            if (current.size > 1) {
                                strokes.add(current.toList())
                                onStrokesChanged(strokes.toList())
                            }
                            current.clear()
                        },
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                (strokes + listOf(current.toList())).forEach { stroke ->
                    for (i in 1 until stroke.size) {
                        drawLine(
                            color = colors.ink,
                            start = stroke[i - 1],
                            end = stroke[i],
                            strokeWidth = STROKE_WIDTH_PX,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }

        // The signature line, in the one accent colour. Everything else on this half is paper.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
        ) {
            drawLine(
                color = colors.seal,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = size.height.coerceAtLeast(1f),
                cap = StrokeCap.Square,
            )
        }

        Text(
            text = signerName,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private const val STROKE_WIDTH_PX = 4f
