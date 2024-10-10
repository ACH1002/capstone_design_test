package inu.capstone.gaitdatacolletor

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.round
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.math.PI

/**
 * SensorRepository 클래스
 * 이 클래스는 가속도계, 자기계, GPS 센서의 데이터를 수집하고 처리하며, CSV 파일로 저장하는 기능을 제공합니다.
 * @param context 애플리케이션 컨텍스트
 */
class SensorRepository(private val context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _accelerometerData = MutableStateFlow(listOf(0f, 0f, 0f))
    private val _gyroscopeData = MutableStateFlow(listOf(0f, 0f, 0f))
    private val _magnetometerData = MutableStateFlow(listOf(0f, 0f, 0f))
    private val _angularVelocityData = MutableStateFlow(listOf(0f, 0f, 0f))
    private val _angleData = MutableStateFlow(listOf(0f, 0f, 0f)) // pitch, roll, yaw
    private val _gpsData = MutableStateFlow(Pair(0.0, 0.0))

    private val dataList = mutableListOf<List<String>>()
    private var isCollecting = false
    private var startTime: Long = 0
    private var lastGyroscopeTimestamp: Long = 0

    private var lastKnownLocation: Location? = null

    private val _allSensorData = MutableStateFlow<List<SensorData>>(emptyList())
    val allSensorData = _allSensorData.asStateFlow()

    private val _measurementComplete = MutableStateFlow(false)
    val measurementComplete = _measurementComplete.asStateFlow()

    private var pitch = 0.0
    private var roll = 0.0
    private var yaw = 0.0

    private val NS2S = 1.0f / 1000000000.0f
    private val RAD2DEG = 180 / PI.toFloat()

    fun startDataCollection() {
        isCollecting = true
        startTime = System.nanoTime()
        lastGyroscopeTimestamp = startTime
        dataList.clear()
        dataList.add(listOf("Time", "AccX", "AccY", "AccZ", "GyroX", "GyroY", "GyroZ", "MagX", "MagY", "MagZ", "AngVelX", "AngVelY", "AngVelZ", "Pitch", "Roll", "Yaw", "Latitude", "Longitude"))
        _allSensorData.value = emptyList()
        _measurementComplete.value = false
        pitch = 0.0
        roll = 0.0
        yaw = 0.0
    }

    fun stopDataCollection() {
        isCollecting = false
        _measurementComplete.value = true
    }

    fun resetMeasurement() {
        _allSensorData.value = emptyList()
        _measurementComplete.value = false
        dataList.clear()
    }

    fun saveDataToCSV(fileName: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fullFileName = "${fileName}_$timestamp.csv"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveFileUsingMediaStore(fullFileName)
        } else {
            saveFileToDownloads(fullFileName)
        }
    }

    @SuppressLint("InlinedApi")
    private fun saveFileUsingMediaStore(fileName: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val uri: Uri? = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                csvWriter().writeAll(dataList, outputStream)
            }
        }
    }

    private fun saveFileToDownloads(fileName: String) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)

        try {
            FileOutputStream(file).use { outputStream ->
                csvWriter().writeAll(dataList, outputStream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private val accelerometerListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val values = event.values.toList()
            _accelerometerData.value = values

            if (isCollecting) {
                updateSensorData()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    private val gyroscopeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val values = event.values.toList()
            _gyroscopeData.value = values

            // 각속도 및 각도 계산
            val currentTimestamp = event.timestamp
            if (lastGyroscopeTimestamp != 0L) {
                val dt = (currentTimestamp - lastGyroscopeTimestamp) * NS2S

                val gyroX = event.values[0]
                val gyroY = event.values[1]
                val gyroZ = event.values[2]

                pitch += gyroY * dt
                roll += gyroX * dt
                yaw += gyroZ * dt

                _angleData.value = listOf(
                    (pitch * RAD2DEG).toFloat(),
                    (roll * RAD2DEG).toFloat(),
                    (yaw * RAD2DEG).toFloat()
                )

                _angularVelocityData.value = listOf(gyroX, gyroY, gyroZ)
            }
            lastGyroscopeTimestamp = currentTimestamp
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    private val magnetometerListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val values = event.values.toList()
            _magnetometerData.value = values
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastKnownLocation = location
            _gpsData.value = Pair(location.latitude, location.longitude)
        }

        override fun onProviderEnabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                requestLastKnownLocation()
            }
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                _gpsData.value = Pair(0.0, 0.0)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    @SuppressLint("MissingPermission")
    private fun requestLastKnownLocation() {
        val providers = locationManager.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            val location = locationManager.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                bestLocation = location
            }
        }
        bestLocation?.let {
            lastKnownLocation = it
            _gpsData.value = Pair(it.latitude, it.longitude)
        }
    }

    @SuppressLint("MissingPermission")
    fun startMeasurement() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val samplingPeriodUs = SensorManager.SENSOR_DELAY_GAME
        sensorManager.registerListener(accelerometerListener, accelerometer, samplingPeriodUs)
        sensorManager.registerListener(gyroscopeListener, gyroscope, samplingPeriodUs)
        sensorManager.registerListener(magnetometerListener, magnetometer, samplingPeriodUs)

        requestLastKnownLocation()
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, locationListener)
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1f, locationListener)
        startDataCollection()
    }

    fun stopMeasurement() {
        sensorManager.unregisterListener(accelerometerListener)
        sensorManager.unregisterListener(gyroscopeListener)
        sensorManager.unregisterListener(magnetometerListener)
        locationManager.removeUpdates(locationListener)
        stopDataCollection()
    }

    private fun updateSensorData() {
        val currentTime = (System.nanoTime() - startTime) * NS2S
        val currentSensorData = SensorData(
            _accelerometerData.value,
            _gyroscopeData.value,
            _magnetometerData.value,
            _angularVelocityData.value,
            _angleData.value,
            _gpsData.value
        )
        _allSensorData.value += currentSensorData
        val data = listOf(
            currentTime.toString(),
            _accelerometerData.value[0].toString(),
            _accelerometerData.value[1].toString(),
            _accelerometerData.value[2].toString(),
            _gyroscopeData.value[0].toString(),
            _gyroscopeData.value[1].toString(),
            _gyroscopeData.value[2].toString(),
            _magnetometerData.value[0].toString(),
            _magnetometerData.value[1].toString(),
            _magnetometerData.value[2].toString(),
            _angularVelocityData.value[0].toString(),
            _angularVelocityData.value[1].toString(),
            _angularVelocityData.value[2].toString(),
            _angleData.value[0].toString(),
            _angleData.value[1].toString(),
            _angleData.value[2].toString(),
            _gpsData.value.first.toString(),
            _gpsData.value.second.toString()
        )
        dataList.add(data)

        if (currentTime >= 30f) { // 30 seconds
            stopDataCollection()
        }
    }

    fun getSensorData(): SensorData {
        return SensorData(
            _accelerometerData.value,
            _gyroscopeData.value,
            _magnetometerData.value,
            _angularVelocityData.value,
            _angleData.value,
            _gpsData.value
        )
    }
}



data class SensorData(

    val accelerometer: List<Float> = listOf(0f, 0f, 0f),
    val gyroscope: List<Float> = listOf(0f, 0f, 0f),
    val magnetometer: List<Float> = listOf(0f, 0f, 0f),
    val angularVelocity: List<Float> = listOf(0f, 0f, 0f),
    val angle: List<Float> = listOf(0f, 0f, 0f),
    val gps: Pair<Double, Double>
)

