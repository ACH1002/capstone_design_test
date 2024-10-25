package inu.capstone.gaitdatacolletor

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Card
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
    var stepCount by remember { mutableStateOf("") }
    val currentSensorData by viewModel.currentSensorData.collectAsState()
    val measurementStatus by viewModel.measurementStatus.collectAsState()
    val allSensorData by viewModel.allSensorData.collectAsState()

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
        when (measurementStatus) {
            MeasurementStatus.WAITING -> {
                Text(
                    "측정 대기 중",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    "볼륨 UP 버튼을 눌러 측정을 시작하세요",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            MeasurementStatus.MEASURING -> {
                Text(
                    "측정 중...",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    "볼륨 DOWN 버튼을 눌러 측정을 종료하세요",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text("현재 데이터:")
                Text("가속도계: ${currentSensorData.accelerometer.joinToString(", ")}")
                Text("자이로스코프: ${currentSensorData.gyroscope.joinToString(", ")}")
                Text("자기계: ${currentSensorData.magnetometer.joinToString(", ")}")
                Text("GPS: ${currentSensorData.gps}")
            }
            MeasurementStatus.COMPLETED -> {
                Text(
                    "측정 완료",
                    style = MaterialTheme.typography.headlineMedium
                )

                // 데이터 테이블
                DataTable(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface),
                    allSensorData = allSensorData
                )

                // 저장 관련 컨트롤
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = stepCount,
                        onValueChange = { stepCount = it },
                        label = { Text("걸음 수") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                if (stepCount.isNotEmpty()) {
                                    viewModel.saveData(stepCount)
                                }
                            },
                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                        ) {
                            Text("저장하기")
                        }
                        Button(
                            onClick = {
                                viewModel.resetMeasurement()
                                stepCount = ""
                            },
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("다시 측정하기")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DataTable(
    modifier: Modifier = Modifier,
    allSensorData: List<SensorData>
) {
    val headers = listOf(
        "Time",
        "AccX", "AccY", "AccZ",
        "GyroX", "GyroY", "GyroZ",
        "MagX", "MagY", "MagZ",
        "Latitude", "Longitude"
    )

    LazyColumn(
        modifier = modifier
    ) {
        // 헤더 행
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(4.dp)
            ) {
                headers.forEach { header ->
                    Text(
                        text = header,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1
                    )
                }
            }
        }

        // 데이터 행들
        items(allSensorData.size) { index ->
            val data = allSensorData[index]
            val timeSeconds = index * 0.1f // 100ms 간격으로 측정했으므로

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp)
            ) {
                // Time
                TableCell(text = String.format("%.1f", timeSeconds))

                // Accelerometer
                data.accelerometer.forEach { value ->
                    TableCell(text = String.format("%.3f", value))
                }

                // Gyroscope
                data.gyroscope.forEach { value ->
                    TableCell(text = String.format("%.3f", value))
                }

                // Magnetometer
                data.magnetometer.forEach { value ->
                    TableCell(text = String.format("%.3f", value))
                }

                // GPS
                TableCell(text = String.format("%.6f", data.gps.first))
                TableCell(text = String.format("%.6f", data.gps.second))
            }
        }
    }
}

@Composable
private fun RowScope.TableCell(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1
    )
}

fun vibrateDevice(vibrator: Vibrator) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(500)
    }
}