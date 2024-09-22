package inu.capstone.gaitdatacolletor

import android.content.pm.PackageManager
import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat




private const val LOCATION_PERMISSION_REQUEST_CODE = 1

class MainActivity : ComponentActivity() {

    private lateinit var sensorRepository: SensorRepository
    private lateinit var viewModel: SensorViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorRepository = SensorRepository(this)
        viewModel = SensorViewModel(sensorRepository)

        // 권한 확인 및 요청
        checkAndRequestLocationPermission()

        setContent {
            SensorDataScreen(viewModel)
        }
    }

    private fun checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 권한이 없는 경우 요청
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            // 이미 권한이 있는 경우
            // 센서 측정 시작 등의 작업 수행
        }
    }


}
