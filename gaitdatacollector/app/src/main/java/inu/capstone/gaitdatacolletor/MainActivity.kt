package inu.capstone.gaitdatacolletor

import android.content.pm.PackageManager
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.KeyEvent
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

        checkAndRequestLocationPermission()

        setContent {
            SensorDataScreen(viewModel)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (viewModel.measurementStatus.value == MeasurementStatus.WAITING) {
                    viewModel.startMeasurement("", "")
                    return true
                }
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (viewModel.measurementStatus.value == MeasurementStatus.MEASURING) {
                    viewModel.stopMeasurement()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }
}
