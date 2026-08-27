package com.diabecarekids.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Hold-to-activate SOS button (REQ-SOS-001 / INV-003). The gesture layer ONLY
 * reports start/end (design decision #7): a 16ms tick loop in the UI drives
 * [onTick], and the shared [com.diabecarekids.app.sos.SosHoldStateMachine]
 * decides the transition from elapsed time.
 *
 * Cancellation semantics (non-obvious — cancellation resets, never triggers):
 * `waitForUpOrCancellation()` returns null when the gesture is cancelled (e.g.
 * the pointer is consumed by a scroll or the composable leaves composition),
 * and the `finally` block still runs [onHoldEnd] — which resets the machine to
 * Idle (0% progress) without firing (INV-003). Only a full 3.0s continuous hold
 * reaches the Arming → Triggered transition.
 *
 * The ring shows [progress] (0..1); a full ring at 1.0 is the visual "armed"
 * state before the confirmation swap to the screen-level "Alerta enviada".
 */
@Composable
fun EmergencySOSButton(
    progress: Float,
    onHoldStart: () -> Unit,
    onTick: () -> Unit,
    onHoldEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }

    // 16ms tick loop while the finger is down (design decision #7). Restarts on
    // the pressed edge; cancellation on release/gesture-cancel stops it.
    LaunchedEffect(pressed) {
        if (pressed) {
            while (true) {
                onTick()
                delay(TICK_MILLIS)
            }
        }
    }

    val armed = progress >= 1f

    // Read color scheme once (composable) before entering non-composable draw scope.
    val colors = MaterialTheme.colorScheme
    val surfaceColor = if (armed) colors.error else colors.surface
    val labelColor = if (armed) colors.onError else colors.onSurface
    val progressColor = if (armed) colors.error else colors.primary

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(SOS_BUTTON_SIZE_DP)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    onHoldStart()
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        pressed = false
                        onHoldEnd()
                    }
                }
            }
            .background(
                color = surfaceColor,
                shape = CircleShape,
            )
            .border(2.dp, colors.outline, CircleShape),
    ) {
        Canvas(Modifier.size(SOS_BUTTON_SIZE_DP)) {
            val strokeWidth = size.minDimension * 0.08f
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            // Background track.
            drawArc(
                color = colors.outlineVariant,
                startAngle = START_ANGLE,
                sweepAngle = FULL_SWEEP,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            // Active progress arc.
            drawArc(
                color = progressColor,
                startAngle = START_ANGLE,
                sweepAngle = FULL_SWEEP * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Text(
            text = when {
                armed -> "¡SOS!"
                pressed -> "Sosteniendo…"
                else -> "Mantener\npresionado"
            },
            color = labelColor,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private val SOS_BUTTON_SIZE_DP = 200.dp
private const val START_ANGLE = -90f
private const val FULL_SWEEP = 360f
private const val TICK_MILLIS = 16L
