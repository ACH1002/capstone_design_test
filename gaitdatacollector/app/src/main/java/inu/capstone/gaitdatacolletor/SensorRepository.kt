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

/**
 * SensorRepository 클래스
 * 이 클래스는 가속도계, 자기계, GPS 센서의 데이터를 수집하고 처리하며, CSV 파일로 저장하는 기능을 제공합니다.
 * @param context 애플리케이션 컨텍스트
 */
class SensorRepository(private val context: Context) {
    // 시스템 서비스 초기화
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // 센서 데이터를 저장할 MutableStateFlow 객체들
    private val _accelerometerData = MutableStateFlow(listOf(0f, 0f, 0f))
    private val _magnetometerData = MutableStateFlow(listOf(0f, 0f, 0f))
    private val _gpsData = MutableStateFlow(Pair(0.0, 0.0))

    // 이동 평균 필터를 위한 버퍼
    private val accelerometerBuffer = ArrayDeque<List<Float>>()
    private val magnetometerBuffer = ArrayDeque<List<Float>>()
    private val bufferSize = 10 // 이동 평균을 위한 버퍼 크기

    // 로우 패스 필터 계수
    private val alpha = 0.8f // 로우 패스 필터 계수 (0에서 1 사이, 1에 가까울수록 필터링 효과가 강해짐)

    // 임계값 설정
    private val accelerometerThreshold = 0.2f
    private val magnetometerThreshold = 0.7f

    // 이전 센서 읽기 값 저장
    private var lastAccelerometerValue = listOf(0f, 0f, 0f)
    private var lastMagnetometerValue = listOf(0f, 0f, 0f)

    // CSV 데이터 저장을 위한 변수들
    private val dataList = mutableListOf<List<String>>()
    private var isCollecting = false
    private var startTime: Long = 0
    private var dataCount = 0


    private var lastKnownLocation: Location? = null


    private val _allSensorData = MutableStateFlow<List<SensorData>>(emptyList())
    val allSensorData = _allSensorData.asStateFlow()

    private val _measurementComplete = MutableStateFlow(false)
    val measurementComplete = _measurementComplete.asStateFlow()
    /**
     * 데이터 수집을 시작합니다.
     */
    fun startDataCollection() {
        isCollecting = true
        startTime = System.currentTimeMillis()
        dataList.clear()
        dataList.add(listOf("Time", "AccX", "AccY", "AccZ", "MagX", "MagY", "MagZ", "Latitude", "Longitude"))
        dataCount = 0
        _allSensorData.value = emptyList()
        _measurementComplete.value = false
    }


    /**
     * 데이터 수집을 중지하고 CSV 파일로 저장합니다.
     */
    fun stopDataCollection() {
        isCollecting = false
        _measurementComplete.value = true
    }

    fun resetMeasurement() {
        _allSensorData.value = emptyList()
        _measurementComplete.value = false
        dataList.clear()
        dataCount = 0
    }

    /**
     * 수집된 데이터를 CSV 파일로 저장합니다.
     */
    fun saveDataToCSV() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "sensor_data_$timestamp.csv"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveFileUsingMediaStore(fileName)
        } else {
            saveFileToDownloads(fileName)
        }
    }

    /**
     * Android 10 이상에서 MediaStore API를 사용하여 파일을 저장합니다.
     * @param fileName 저장할 파일 이름
     */
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

    /**
     * Android 9 이하에서 직접 다운로드 폴더에 파일을 저장합니다.
     * @param fileName 저장할 파일 이름
     */
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

    /**
     * 가속도계 리스너
     * 가속도계 데이터를 처리하고 필요한 경우 데이터를 수집합니다.
     */
    private val accelerometerListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val values = event.values.toList()
            val filteredValues = lowPassFilter(values, lastAccelerometerValue)
            val thresholdFiltered = applyThresholdFilter(filteredValues, lastAccelerometerValue, accelerometerThreshold)

            if (thresholdFiltered != null) {
                lastAccelerometerValue = thresholdFiltered
                accelerometerBuffer.addLast(thresholdFiltered)
                if (accelerometerBuffer.size > bufferSize) {
                    accelerometerBuffer.removeFirst()
                }
                _accelerometerData.value = calculateAverage(accelerometerBuffer)

                if (isCollecting && dataCount < 500) {
                    val currentTime = System.currentTimeMillis() - startTime
                    val currentSensorData = SensorData(
                        _accelerometerData.value,
                        _magnetometerData.value,
                        _gpsData.value
                    )
                    _allSensorData.value += currentSensorData
                    val data = listOf(
                        currentTime.toString(),
                        _accelerometerData.value[0].toString(),
                        _accelerometerData.value[1].toString(),
                        _accelerometerData.value[2].toString(),
                        _magnetometerData.value[0].toString(),
                        _magnetometerData.value[1].toString(),
                        _magnetometerData.value[2].toString(),
                        _gpsData.value.first.toString(),
                        _gpsData.value.second.toString()
                    )
                    dataList.add(data)
                    dataCount++

                    if (dataCount >= 500 || currentTime > 20000) {
                        stopDataCollection()
                    }
                }
            } else {
                lastAccelerometerValue = filteredValues
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    /**
     * 자기계 리스너
     * 자기계 데이터를 처리합니다.
     */
    private val magnetometerListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val values = event.values.toList()
            val filteredValues = lowPassFilter(values, lastMagnetometerValue)
            val thresholdFiltered = applyThresholdFilter(filteredValues, lastMagnetometerValue, magnetometerThreshold)

            if (thresholdFiltered != null) {
                lastMagnetometerValue = thresholdFiltered
                magnetometerBuffer.addLast(thresholdFiltered)
                if (magnetometerBuffer.size > bufferSize) {
                    magnetometerBuffer.removeFirst()
                }
                _magnetometerData.value = calculateAverage(magnetometerBuffer)
            } else {
                lastMagnetometerValue = filteredValues
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    /**
     * GPS 리스너
     * 위치 데이터를 업데이트합니다.
     */
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
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            // 이 메소드는 더 이상 사용되지 않지만, 이전 버전과의 호환성을 위해 구현합니다.
        }
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

    /**
     * 이동 평균을 계산합니다.
     * @param buffer 데이터 버퍼
     * @return 평균값 리스트
     */
    private fun calculateAverage(buffer: ArrayDeque<List<Float>>): List<Float> {
        val sum = buffer.fold(listOf(0f, 0f, 0f)) { acc, values ->
            acc.zip(values) { a, b -> a + b }
        }
        return sum.map { it / buffer.size }
    }

    /**
     * 로우 패스 필터를 적용합니다.
     * @param input 입력 데이터
     * @param output 이전 출력 데이터
     * @return 필터링된 데이터
     */
    private fun lowPassFilter(input: List<Float>, output: List<Float>): List<Float> {
        return input.zip(output) { current, last ->
            last + alpha * (current - last)
        }
    }

    /**
     * 센서 측정을 시작합니다.
     */
    @SuppressLint("MissingPermission")
    fun startMeasurement() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val samplingPeriodUs = (1000000 / 50) // 1초에 50번 측정 (마이크로초 단위)
        sensorManager.registerListener(accelerometerListener, accelerometer, samplingPeriodUs)
        sensorManager.registerListener(magnetometerListener, magnetometer, samplingPeriodUs)

        requestLastKnownLocation()
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, locationListener)
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1f, locationListener)
        startDataCollection()
    }

    /**
     * 센서 측정을 중지합니다.
     */
    fun stopMeasurement() {
        sensorManager.unregisterListener(accelerometerListener)
        sensorManager.unregisterListener(magnetometerListener)
        locationManager.removeUpdates(locationListener)
        stopDataCollection()
    }



    /**
     * 현재 센서 데이터를 반환합니다.
     * @return SensorData 객체
     */
    fun getSensorData(): SensorData {
        return SensorData(
            _accelerometerData.value.map { roundToDecimalPlaces(it, 2) },
            _magnetometerData.value.map { roundToDecimalPlaces(it, 2) },
            Pair(
                roundToDecimalPlaces(_gpsData.value.first, 6),
                roundToDecimalPlaces(_gpsData.value.second, 6)
            )
        )
    }

    /**
     * Float 값을 지정된 소수점 자리수로 반올림합니다.
     * @param value 반올림할 값
     * @param decimalPlaces 소수점 자리수
     * @return 반올림된 값
     */
    private fun roundToDecimalPlaces(value: Float, decimalPlaces: Int): Float {
        val factor = 10.0f.pow(decimalPlaces)
        return round(value * factor) / factor
    }

    /**
     * Double 값을 지정된 소수점 자리수로 반올림합니다.
     * @param value 반올림할 값
     * @param decimalPlaces 소수점 자리수
     * @return 반올림된 값
     */
    private fun roundToDecimalPlaces(value: Double, decimalPlaces: Int): Double {
        val factor = 10.0.pow(decimalPlaces)
        return round(value * factor) / factor
    }

    /**
     * 임계값 필터를 적용합니다.
     * @param current 현재 값
     * @param last 이전 값
     * @param threshold 임계값
     * @return 임계값을 넘은 경우 현재 값, 그렇지 않으면 null
     */
    private fun applyThresholdFilter(current: List<Float>, last: List<Float>, threshold: Float): List<Float>? {
        val changes = current.zip(last) { a, b -> kotlin.math.abs(a - b) }
        return if (changes.any { it > threshold }) {
            current
        } else {
            null
        }
    }
}