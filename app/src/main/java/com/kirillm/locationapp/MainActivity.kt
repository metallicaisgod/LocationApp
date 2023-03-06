package com.kirillm.locationapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import java.text.DateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        const val CHECK_SETTINGS_CODE = 111
        const val REQUEST_LOCATION_PERMISSION = 222
    }

    private lateinit var startLocationUpdatesButton: Button
    private lateinit var stopLocationUpdatesButton: Button
    private lateinit var locationTextView: TextView
    private lateinit var locationUpdateTimeTextView: TextView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var settingsClient: SettingsClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationSettingsRequest: LocationSettingsRequest
    private lateinit var locationCallback: LocationCallback
    private var currentLocation: Location? = null

    private var isLocationUpdatesActive = false
    //private late init var locationUpdateTime: String

//    val startForResult =
//        registerForActivityResult(
//            ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
//        if (result.resultCode == Activity.RESULT_OK) {
//            val intent = result.data
//
//        }
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startLocationUpdatesButton = findViewById(R.id.startLocationUpdatesButton)
        stopLocationUpdatesButton = findViewById(R.id.stopLocationUpdatesButton)
        locationTextView = findViewById(R.id.locationTextView)
        locationUpdateTimeTextView = findViewById(R.id.locationUpdateTimeTextView)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        settingsClient = LocationServices.getSettingsClient(this)

        startLocationUpdatesButton.setOnClickListener {
            startLocationUpdate()
        }
        stopLocationUpdatesButton.setOnClickListener {
            stopLocationUpdate()
        }

        buildLocationRequest()
        buildLocationCallback()
        buildLocationSettingsRequest()
    }

    private fun stopLocationUpdate() {

        if (isLocationUpdatesActive) {
            return
        }

        fusedLocationClient.removeLocationUpdates(locationCallback)
            .addOnCompleteListener(this) {
                isLocationUpdatesActive = false
                startLocationUpdatesButton.isEnabled = true
                stopLocationUpdatesButton.isEnabled = false
            }

    }

    private fun startLocationUpdate() {

        isLocationUpdatesActive = true
        startLocationUpdatesButton.isEnabled = false
        stopLocationUpdatesButton.isEnabled = true

        settingsClient.checkLocationSettings(locationSettingsRequest)
            .addOnSuccessListener(
                this
            ) {
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@addOnSuccessListener
                }
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.myLooper()
                )
            }
            .addOnFailureListener(this) {
                when ((it as ApiException).statusCode) {
                    LocationSettingsStatusCodes
                        .RESOLUTION_REQUIRED -> {
                        try {
                            val resolvableApiException =
                                (it as ResolvableApiException)
                            resolvableApiException.startResolutionForResult(
                                this@MainActivity,
                                CHECK_SETTINGS_CODE
                            )
                        } catch (s: IntentSender.SendIntentException) {
                            s.printStackTrace()
                        }
                    }
                    LocationSettingsStatusCodes
                        .SETTINGS_CHANGE_UNAVAILABLE -> {
                        val message = "Adjust location settings on your device"
                        Toast.makeText(
                            this@MainActivity, message, Toast.LENGTH_LONG
                        ).show()
                        isLocationUpdatesActive = false
                        startLocationUpdatesButton.isEnabled = true
                        stopLocationUpdatesButton.isEnabled = false
                    }
                }

                updateLocationUi()
            }

    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            CHECK_SETTINGS_CODE -> {
                when (requestCode) {
                    Activity.RESULT_OK -> {
                        Log.d(
                            "MainActivity",
                            "User has agreed to change location permissions"
                        )
                        startLocationUpdate()
                    }
                    Activity.RESULT_CANCELED -> {
                        Log.d(
                            "MainActivity",
                            "User has not agreed to change location permissions"
                        )
                        isLocationUpdatesActive = false
                        startLocationUpdatesButton.isEnabled = true
                        stopLocationUpdatesButton.isEnabled = false
                        updateLocationUi()
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdate()
    }

    override fun onResume() {
        super.onResume()
        if (isLocationUpdatesActive && checkLocationPermissions()) {
            startLocationUpdate()
        } else if (!checkLocationPermissions()) {
            requestLocationPermissions()
        }
    }

    private fun requestLocationPermissions() {
        val shouldProvideRationaleFine = shouldShowRequestPermissionRationale(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val shouldProvideRationaleCoarse = shouldShowRequestPermissionRationale(
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (shouldProvideRationaleFine && shouldProvideRationaleCoarse) {
            Snackbar.make(
                findViewById(android.R.id.content),
                "Location permissions are needed for app functionality",
                Snackbar.LENGTH_INDEFINITE
            ).setAction("Ok") {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ),
                    REQUEST_LOCATION_PERMISSION
                )
            }.show()
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQUEST_LOCATION_PERMISSION
            )
        }


    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isEmpty()) {
                Log.d("onRequestPermissions", "Request was cancelled")
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED
            ) {
                if (isLocationUpdatesActive)
                    startLocationUpdate()
            } else {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Turn on location in settings",
                    Snackbar.LENGTH_INDEFINITE
                ).setAction("Settings") {
                    val intent = Intent()
                    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    val uri = Uri.fromParts(
                        "package",
                        packageName,
                        null
                    )
                    intent.data = uri
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                }.show()
            }

        }

    }

    private fun checkLocationPermissions(): Boolean {
        val permissionFineState = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val permissionCoarseState = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return (permissionFineState == PackageManager.PERMISSION_GRANTED &&
                permissionCoarseState == PackageManager.PERMISSION_GRANTED)
    }

    private fun buildLocationSettingsRequest() {
        locationSettingsRequest =
            LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest).build()
    }

    private fun buildLocationCallback() {

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)
                currentLocation = p0.lastLocation!!

                updateLocationUi()
            }
        }

    }

    private fun updateLocationUi() {
        if (currentLocation != null) {
            locationTextView.text = getString(
                R.string.coordinates,
                currentLocation!!.latitude,
                currentLocation!!.longitude
            )
            locationUpdateTimeTextView.text = DateFormat.getTimeInstance().format(Date())
        }
    }

    private fun buildLocationRequest() {
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 3000
        ).setMinUpdateIntervalMillis(1000)
            .build()
    }
}