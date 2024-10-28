package inu.capstone.gaitdatacolletor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class SensorViewModel(private val repository: SensorRepository) : ViewModel() {

    private val _measurementStatus = MutableStateFlow(MeasurementStatus.WAITING)
    val measurementStatus = _measurementStatus.asStateFlow()

    private val _currentSensorData = MutableStateFlow(SensorData(
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

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId = _selectedId.asStateFlow()

    private val _selectedPosition = MutableStateFlow<String?>(null)
    val selectedPosition = _selectedPosition.asStateFlow()

    private val _selectedDistance = MutableStateFlow<String?>(null)
    val selectedDistance = _selectedDistance.asStateFlow()

    fun setId(id: String) {
        _selectedId.value = id
    }

    fun setPosition(position: String) {
        _selectedPosition.value = position
    }

    fun setDistance(distance: String) {
        _selectedDistance.value = distance
    }

    fun startMeasurement(style: String = "", state: String = "") {
        walkingStyle = style
        walkingState = state
        _measurementStatus.value = MeasurementStatus.MEASURING
        repository.startMeasurement()
        measurementJob = viewModelScope.launch {
            while (!measurementComplete.value) {
                _currentSensorData.value = repository.getSensorData()
                kotlinx.coroutines.delay(100)
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
        val id = _selectedId.value ?: return
        val position = _selectedPosition.value ?: return
        val distance = _selectedDistance.value ?: return
        val currentDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

        val fileName = "${id}_${position}_${distance}_${stepCount}_${currentDate}"
        repository.saveDataToCSV(fileName)
        resetMeasurement()
    }

    fun stopMeasurement() {
        _measurementStatus.value = MeasurementStatus.COMPLETED
        repository.stopMeasurement()
    }

    fun resetMeasurement() {
        _measurementStatus.value = MeasurementStatus.WAITING
        _selectedId.value = null
        _selectedPosition.value = null
        _selectedDistance.value = null
        _currentSensorData.value = SensorData(
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