package inu.capstone.gaitdatacolletor

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


@Composable
fun SensorDataScreen(viewModel: SensorViewModel) {
    val currentSensorData by viewModel.currentSensorData.collectAsState()
    val allSensorData by viewModel.allSensorData.collectAsState()
    val measurementStatus by viewModel.measurementStatus.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (measurementStatus) {
            MeasurementStatus.WAITING -> {
                Text("측정 대기")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.startMeasurement() }) {
                    Text("측정 시작")
                }
            }
            MeasurementStatus.MEASURING -> {
                Text("측정 중...")
                Spacer(modifier = Modifier.height(16.dp))
                Text("현재 데이터:")
                Text("가속도계: ${currentSensorData.accelerometer}")
                Text("지자기계: ${currentSensorData.magnetometer}")
                Text("GPS: ${currentSensorData.gps}")
            }
            MeasurementStatus.COMPLETED -> {
                Text("측정 완료")
                Spacer(modifier = Modifier.height(16.dp))
                Text("모든 측정 데이터:")
                SensorDataTable(allSensorData)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { viewModel.saveData() }) {
                        Text("저장하기")
                    }
                    Button(onClick = { viewModel.resetMeasurement() }) {
                        Text("다시 측정하기")
                    }
                }
            }
        }
    }
}


@Composable
fun SensorDataTable(data: List<SensorData>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
    ) {
        item {
            TableHeader()
        }
        itemsIndexed(data) { index, item ->
            TableRow(index, item)
        }
    }
}

@Composable
fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(8.dp)
    ) {
        listOf("No.", "AccX", "AccY", "AccZ", "MagX", "MagY", "MagZ", "Lat", "Long").forEach { header ->
            Text(
                text = header,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TableRow(index: Int, data: SensorData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (index % 2 == 0) Color.White else Color.LightGray)
            .padding(8.dp)
    ) {
        Text(text = "${index + 1}", modifier = Modifier.weight(1f))
        data.accelerometer.forEach { value ->
            Text(text = String.format("%.2f", value), modifier = Modifier.weight(1f))
        }
        data.magnetometer.forEach { value ->
            Text(text = String.format("%.2f", value), modifier = Modifier.weight(1f))
        }
        Text(text = String.format("%.6f", data.gps.first), modifier = Modifier.weight(1f))
        Text(text = String.format("%.6f", data.gps.second), modifier = Modifier.weight(1f))
    }
}