package inu.capstone.gaitdatacolletor

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp


@Composable
fun SensorDataScreen(viewModel: SensorViewModel) {
    var selectedWalkingStyle by remember { mutableStateOf("") }
    var selectedWalkingState by remember { mutableStateOf("") }
    var stepCount by remember { mutableStateOf("") }

    val currentSensorData by viewModel.currentSensorData.collectAsState()
    val measurementStatus by viewModel.measurementStatus.collectAsState()

    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }

    LaunchedEffect(measurementStatus) {
        if (measurementStatus == MeasurementStatus.COMPLETED) {
            vibrateDevice(vibrator)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("보행 방식 선택", style = MaterialTheme.typography.headlineSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WalkingStyleButton("손에 쥐고", selectedWalkingStyle) { selectedWalkingStyle = it }
            WalkingStyleButton("보면서", selectedWalkingStyle) { selectedWalkingStyle = it }
            WalkingStyleButton("주머니에 넣고", selectedWalkingStyle) { selectedWalkingStyle = it }
        }

        Text("보행 상태 선택", style = MaterialTheme.typography.headlineSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WalkingStateButton("정상", selectedWalkingState) { selectedWalkingState = it }
            WalkingStateButton("비정상", selectedWalkingState) { selectedWalkingState = it }
        }

        when (measurementStatus) {
            MeasurementStatus.WAITING -> {
                Button(
                    onClick = {
                        if (selectedWalkingStyle.isNotEmpty() && selectedWalkingState.isNotEmpty()) {
                            viewModel.startMeasurement(selectedWalkingStyle, selectedWalkingState)
                        }
                    },
                    enabled = selectedWalkingStyle.isNotEmpty() && selectedWalkingState.isNotEmpty()
                ) {
                    Text("측정 시작")
                }
            }
            MeasurementStatus.MEASURING -> {
                Text("측정 중...")
                Text("현재 데이터:")
                Text("가속도계: ${currentSensorData.accelerometer.joinToString(", ")}")
                Text("자이로스코프: ${currentSensorData.gyroscope.joinToString(", ")}")
                Text("자기계: ${currentSensorData.magnetometer.joinToString(", ")}")
                Text("각속도: ${currentSensorData.angularVelocity.joinToString(", ")}")
                Text("각도 (Pitch, Roll, Yaw): ${currentSensorData.angle.joinToString(", ")}")
                Text("GPS: ${currentSensorData.gps}")

                Button(
                    onClick = { viewModel.cancelMeasurement() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("측정 취소")
                }
            }
            MeasurementStatus.COMPLETED -> {
                TextField(
                    value = stepCount,
                    onValueChange = { stepCount = it },
                    label = { Text("걸음 수") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(onClick = {
                    if (stepCount.isNotEmpty()) {
                        viewModel.saveData(stepCount)
                    }
                }) {
                    Text("저장하기")
                }
                Button(onClick = {
                    viewModel.resetMeasurement()
                    selectedWalkingStyle = ""
                    selectedWalkingState = ""
                    stepCount = ""
                }) {
                    Text("다시 측정하기")
                }
            }
        }
    }
}

@Composable
fun WalkingStyleButton(text: String, selectedStyle: String, onSelected: (String) -> Unit) {
    Button(
        onClick = { onSelected(text) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selectedStyle == text) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
        )
    ) {
        Text(text)
    }
}

@Composable
fun WalkingStateButton(text: String, selectedState: String, onSelected: (String) -> Unit) {
    Button(
        onClick = { onSelected(text) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selectedState == text) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
        )
    ) {
        Text(text)
    }
}

fun vibrateDevice(vibrator: Vibrator) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(500)
    }
}