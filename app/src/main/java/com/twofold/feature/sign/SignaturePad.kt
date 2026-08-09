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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.twofold.R
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
    /** Strokes plus the pad size they were drawn in — the exporter needs both to scale correctly. */
    onSignatureChanged: (List<Stroke>, Size) -> Unit = { _, _ -> },
) {
    val colors = LocalTwofoldColors.current
    val strokes = remember { mutableStateListOf<Stroke>() }
    val current = remember { mutableStateListOf<Offset>() }
    var padSize by remember { mutableStateOf(Size.Zero) }

    // Resolved here rather than inside semantics {}, which is not a composable scope.
    val signatureAreaDescription = stringResource(R.string.signature_area_description)

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paperRaised)
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Text(
            text = stringResource(R.string.sign_prompt),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.ink,
        )

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                // Without this the signature area is silent — a screen-reader user is asked to sign
                // with no indication of where, on the one screen where getting it wrong means
                // signing nothing at all.
                .semantics {
                    contentDescription = signatureAreaDescription
                }
                .onSizeChanged { padSize = Size(it.width.toFloat(), it.height.toFloat()) }
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
                                onSignatureChanged(strokes.toList(), padSize)
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
