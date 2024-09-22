package inu.capstone.gaitdatacolletor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SensorData(
    val accelerometer: List<Float> = listOf(0f, 0f, 0f),
    val magnetometer: List<Float> = listOf(0f, 0f, 0f),
    val gps: Pair<Double, Double> = Pair(0.0, 0.0)
)

class SensorViewModel(private val repository: SensorRepository) : ViewModel() {

    private val _measurementStatus = MutableStateFlow(MeasurementStatus.WAITING)
    val measurementStatus = _measurementStatus.asStateFlow()

    private val _currentSensorData = MutableStateFlow(SensorData())
    val currentSensorData = _currentSensorData.asStateFlow()

    val allSensorData = repository.allSensorData
    val measurementComplete = repository.measurementComplete

    fun startMeasurement() {
        _measurementStatus.value = MeasurementStatus.MEASURING
        repository.startMeasurement()
        viewModelScope.launch {
            while (!measurementComplete.value) {
                _currentSensorData.value = repository.getSensorData()
                kotlinx.coroutines.delay(100) // 100ms 간격으로 업데이트
            }
            _measurementStatus.value = MeasurementStatus.COMPLETED
        }
    }

    fun saveData() {
        repository.saveDataToCSV()
        resetMeasurement()
    }

    fun stopMeasurement() {
        _measurementStatus.value = MeasurementStatus.COMPLETED
        repository.stopMeasurement()
    }

    fun resetMeasurement() {
        _measurementStatus.value = MeasurementStatus.WAITING
        _currentSensorData.value = SensorData()
        repository.resetMeasurement()
    }
}

enum class MeasurementStatus {
    WAITING, MEASURING, COMPLETED
}