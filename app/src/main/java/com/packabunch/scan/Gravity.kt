package com.packabunch.scan

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlin.math.sqrt

/**
 * Which way is up, from the phone's gravity sensor, in the back camera's own axes.
 *
 * Used to check the card is lying flat. A card leant against a wall or held in the hand is still
 * a perfect rectangle to the camera, but it is not the table: measuring heights from it would
 * tilt everything. (The reference project's own photo has its card standing upright in a holder.)
 */
class Gravity(context: Context) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    @Volatile private var device: FloatArray? = null

    private val sensorOrientation: Int = runCatching {
        val cameras = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val back = cameras.cameraIdList.firstOrNull {
            cameras.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: cameras.cameraIdList.first()
        cameras.getCameraCharacteristics(back).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
    }.getOrDefault(90)

    fun start() { sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } }
    fun stop() = manager.unregisterListener(this)

    /** Up, unit length, in camera axes (x right, y down, z forward in the sensor picture), or null. */
    fun upInCamera(): DoubleArray? {
        val v = device ?: return null
        val n = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())
        if (n < 1.0) return null
        val x = v[0] / n; val y = v[1] / n; val z = v[2] / n
        // Device axes: x right, y up, z out of the screen. The back camera looks along -z, and its
        // picture is the device's turned by the sensor orientation.
        return when (sensorOrientation) {
            90 -> doubleArrayOf(-y, -x, -z)
            270 -> doubleArrayOf(y, x, -z)
            180 -> doubleArrayOf(-x, y, -z)
            else -> doubleArrayOf(x, -y, -z)
        }
    }

    /**
     * Up, unit, in the camera axes of the upright portrait picture (x right, y down, z forward).
     * The device's axes are x right, y up the screen, z out of it; the back camera looks along -z.
     */
    fun upUpright(): DoubleArray? {
        val v = device ?: return null
        val n = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())
        if (n < 1.0) return null
        return doubleArrayOf(v[0] / n, -v[1] / n, -v[2] / n)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val prev = device
        // Low-pass the raw accelerometer if that is all there is; the gravity sensor already is.
        device = if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && prev != null)
            FloatArray(3) { prev[it] * 0.85f + event.values[it] * 0.15f }
        else event.values.copyOf()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
