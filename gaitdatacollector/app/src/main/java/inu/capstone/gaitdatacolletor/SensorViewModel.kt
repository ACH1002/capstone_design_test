package inu.capstone.gaitdatacolletor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class SensorViewModel(private val repository: SensorRepository) : ViewModel() {

    private val _measurementStatus = MutableStateFlow(MeasurementStatus.WAITING)
    val measurementStatus = _measurementStatus.asStateFlow()

    private val _currentSensorData = MutableStateFlow(SensorData(
        listOf(0f, 0f, 0f),
        listOf(0f, 0f, 0f),
        listOf(0f, 0f, 0f),
        listOf(0f, 0f, 0f),
        listOf(0f, 0f, 0f),
        Pair(0.0, 0.0)
    ))
    val currentSensorData = _currentSensorData.asStateFlow()

    val allSensorData = repository.allSensorData
    val measurementComplete = repository.measurementComplete

    private var walkingStyle: String = ""
    private var walkingState: String = ""
    private var measurementJob: Job? = null

    fun startMeasurement(style: String, state: String) {
        walkingStyle = style
        walkingState = state
        _measurementStatus.value = MeasurementStatus.MEASURING
        repository.startMeasurement()
        measurementJob = viewModelScope.launch {
            while (!measurementComplete.value) {
                _currentSensorData.value = repository.getSensorData()
                kotlinx.coroutines.delay(100) // 100ms 간격으로 업데이트
            }
            _measurementStatus.value = MeasurementStatus.COMPLETED
        }
    }

    fun cancelMeasurement() {
        measurementJob?.cancel()
        repository.stopMeasurement()
        _measurementStatus.value = MeasurementStatus.WAITING
        resetMeasurement()
    }

    fun saveData(stepCount: String) {
        val fileName = "${walkingStyle}_${walkingState}_${stepCount}걸음"
        repository.saveDataToCSV(fileName)
        resetMeasurement()
    }

    fun stopMeasurement() {
        _measurementStatus.value = MeasurementStatus.COMPLETED
        repository.stopMeasurement()
    }

    fun resetMeasurement() {
        _measurementStatus.value = MeasurementStatus.WAITING
        _currentSensorData.value = SensorData(
            listOf(0f, 0f, 0f),
            listOf(0f, 0f, 0f),
            listOf(0f, 0f, 0f),
            listOf(0f, 0f, 0f),
            listOf(0f, 0f, 0f),
            Pair(0.0, 0.0)
        )
        walkingStyle = ""
        walkingState = ""
        repository.resetMeasurement()
    }
}

enum class MeasurementStatus {
    WAITING, MEASURING, COMPLETED
}