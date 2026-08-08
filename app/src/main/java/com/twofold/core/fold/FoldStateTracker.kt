package com.twofold.core.fold

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowPreview
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Turns raw posture and sensor signals into a [FoldState].
 *
 * Two things here are less obvious than they look, and both were deliberate:
 *
 * 1. **Flat does not mean "on a table."** [FoldingFeature.State.FLAT] is also reported when the
 *    device is fully open and held in two hands to read. Entering two-sided mode then would show
 *    the agent's private notes to the room, upside down, for no reason. So Twofold additionally
 *    requires gravity to say the device is lying roughly face-up.
 *
 * 2. **Posture must be debounced.** Physically opening a foldable emits a burst of intermediate
 *    states. Reacting to each one makes the UI thrash at exactly the moment someone is watching.
 */
class FoldStateTracker(private val activity: Activity) {

    private val sensorManager =
        activity.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    @OptIn(FlowPreview::class)
    fun foldState(): Flow<FoldState> =
        combine(
            postureFlow().debounce(POSTURE_SETTLE_MS).distinctUntilChanged(),
            lyingFlatFlow(),
            hingeAngleFlow(),
        ) { posture, lyingFlat, hingeAngle ->
            posture.toFoldState(lyingFlat, hingeAngle)
        }.distinctUntilChanged()

    // region posture

    private data class Posture(
        val state: FoldingFeature.State?,
        val isHorizontal: Boolean,
        val creaseFraction: Float?,
        val creaseThickness: Int,
    )

    private fun postureFlow(): Flow<Posture> =
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { layoutInfo ->
                val fold = layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()
                    ?: return@map Posture(null, false, null, 0)

                val windowHeight = WindowMetricsCalculator.getOrCreate()
                    .computeCurrentWindowMetrics(activity)
                    .bounds
                    .height()

                val horizontal = fold.orientation == FoldingFeature.Orientation.HORIZONTAL

                // Only a horizontal crease splits the window into a near half and a far half.
                // A vertical crease is a book, not a table, and Twofold mode does not apply.
                val fraction = if (horizontal && windowHeight > 0) {
                    fold.bounds.centerY().toFloat() / windowHeight
                } else {
                    null
                }

                Posture(
                    state = fold.state,
                    isHorizontal = horizontal,
                    creaseFraction = fraction?.coerceIn(0f, 1f),
                    creaseThickness = if (horizontal) fold.bounds.height() else 0,
                )
            }

    private fun Posture.toFoldState(lyingFlat: Boolean, hingeAngle: Float?): FoldState {
        val mode = when {
            state == null || !isHorizontal -> DeviceMode.PREPARE
            state == FoldingFeature.State.FLAT && lyingFlat -> DeviceMode.TWOFOLD
            state == FoldingFeature.State.HALF_OPENED -> DeviceMode.PRESENT
            else -> DeviceMode.PREPARE
        }
        return FoldState(
            mode = mode,
            creaseFraction = creaseFraction,
            creaseThickness = creaseThickness,
            hingeAngle = hingeAngle,
        )
    }

    // endregion

    // region sensors

    /**
     * True when gravity points almost straight through the back of the device — i.e. it is lying
     * face-up on a surface rather than being held up to read.
     */
    private fun lyingFlatFlow(): Flow<Boolean> {
        val gravity = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: return flowOf(false)

        return sensorManager.readings(gravity)
            .map { values -> values[Z] > FACE_UP_GRAVITY_THRESHOLD }
            .distinctUntilChanged()
    }

    /** Continuous hinge angle where available. Absent on non-foldables, and that is fine. */
    private fun hingeAngleFlow(): Flow<Float?> {
        val hinge = sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
            ?: return flowOf(null)

        return sensorManager.readings(hinge).map { it.firstOrNull() }
    }

    private fun SensorManager.readings(sensor: Sensor): Flow<FloatArray> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values.copyOf())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { unregisterListener(listener) }
    }

    // endregion

    private companion object {
        const val Z = 2

        /**
         * Out of ~9.81 m/s². Generous enough to tolerate a slightly uneven table, tight enough to
         * exclude a device being held at a reading angle.
         */
        const val FACE_UP_GRAVITY_THRESHOLD = 8.5f

        /** Long enough to swallow the intermediate states of a physical unfold. */
        const val POSTURE_SETTLE_MS = 250L
    }
}
