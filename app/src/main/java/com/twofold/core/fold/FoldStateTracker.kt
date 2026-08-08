package com.twofold.core.fold

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.window.layout.FoldingFeature
import com.twofold.BuildConfig
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
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

    fun foldState(): Flow<FoldState> =
        combine(
            postureFlow().debounce(FoldLogic.POSTURE_SETTLE_MS).distinctUntilChanged(),
            lyingFlatFlow(),
            hingeAngleFlow(),
        ) { posture, lyingFlat, hingeAngle ->
            posture.toFoldState(lyingFlat, hingeAngle).also { state ->
                if (BuildConfig.DEBUG) {
                    // Debug-only, and it earns its place: posture bugs are invisible from the UI —
                    // the app just sits in the wrong mode with no indication why.
                    Log.d(
                        TAG,
                        "hinge=${posture.hinge} horizontal=${posture.isHorizontal} " +
                            "lyingFlat=$lyingFlat angle=$hingeAngle " +
                            "crease=${posture.creaseFraction} -> ${state.mode}",
                    )
                }
            }
        }.distinctUntilChanged()

    // region posture

    private data class Posture(
        val hinge: HingeState,
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
                    ?: return@map Posture(HingeState.NONE, false, null, 0)

                val windowHeight = WindowMetricsCalculator.getOrCreate()
                    .computeCurrentWindowMetrics(activity)
                    .bounds
                    .height()

                // Only a horizontal crease splits the window into a near half and a far half.
                // A vertical crease is a book, not a table, and Twofold mode does not apply.
                val horizontal = fold.orientation == FoldingFeature.Orientation.HORIZONTAL

                Posture(
                    hinge = fold.state.toHingeState(),
                    isHorizontal = horizontal,
                    creaseFraction = if (horizontal) {
                        FoldLogic.creaseFraction(fold.bounds.centerY(), windowHeight)
                    } else {
                        null
                    },
                    creaseThickness = if (horizontal) fold.bounds.height() else 0,
                )
            }

    private fun FoldingFeature.State.toHingeState(): HingeState = when (this) {
        FoldingFeature.State.FLAT -> HingeState.FLAT
        FoldingFeature.State.HALF_OPENED -> HingeState.HALF_OPENED
        else -> HingeState.NONE
    }

    private fun Posture.toFoldState(lyingFlat: Boolean, hingeAngle: Float?) = FoldState(
        mode = FoldLogic.deriveMode(hinge, isHorizontal, lyingFlat),
        creaseFraction = creaseFraction,
        creaseThickness = creaseThickness,
        hingeAngle = hingeAngle,
    )

    // endregion

    // region sensors

    /**
     * True when gravity points almost straight through the back of the device — i.e. it is lying
     * face-up on a surface rather than being held up to read.
     */
    private fun lyingFlatFlow(): Flow<Boolean> {
        val sensors = sensorManager ?: return flowOf(false)
        val gravity = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: return flowOf(false)

        return sensors.readings(gravity)
            .map { values -> values[Z] > FoldLogic.FACE_UP_GRAVITY_THRESHOLD }
            .distinctUntilChanged()
    }

    /** Continuous hinge angle where available. Absent on non-foldables, and that is fine. */
    private fun hingeAngleFlow(): Flow<Float?> {
        val sensors = sensorManager ?: return flowOf(null)
        val hinge = sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) ?: return flowOf(null)

        return sensors.readings(hinge).map { it.firstOrNull() }
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
        const val TAG = "TwofoldPosture"

        /** Index of the z component in a TYPE_GRAVITY reading. */
        const val Z = 2
    }
}
