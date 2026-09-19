package com.example.backgroundodometer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.*

class LocationTrackingService : Service() {

    companion object {

        const val ACTION_SPEED_UPDATE =
            "com.example.backgroundodometer.SPEED_UPDATE"

        const val EXTRA_SPEED =
            "speed"

        const val ACTION_STOP =
            "com.example.backgroundodometer.STOP"

        private const val CHANNEL_ID =
            "odometer_tracking"

        private const val NOTIFICATION_ID =
            1001

        private const val LOCATION_INTERVAL =
            2000L

        private const val FASTEST_INTERVAL =
            1500L

        private const val MIN_DISTANCE =
            3f

        private const val MAX_ACCURACY =
            40f

        private const val MAX_JUMP =
            300f

        private const val MAX_SPEED =
            160.0
    }

    private lateinit var fusedClient:
        FusedLocationProviderClient

    private lateinit var database:
        OdometerDatabaseHelper

    private var tripId:
        Long? = null

    private var lastLocation:
        Location? = null

    private var speedTotal =
        0.0

    private var speedCount =
        0

    private var maxSpeed =
        0.0

    private var tracking =
        false

    private val callback =
        object : LocationCallback() {

            override fun onLocationResult(
                result: LocationResult
            ) {

                for (
                    location in result.locations
                ) {
                    processLocation(
                        location
                    )
                }
            }
        }

    override fun onCreate() {

        super.onCreate()

        database =
            OdometerDatabaseHelper(
                applicationContext
            )

        fusedClient =
            LocationServices
                .getFusedLocationProviderClient(
                    applicationContext
                )

        createNotificationChannel()

        startForegroundServiceNotification()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (
            intent?.action ==
            ACTION_STOP
        ) {

            stopTracking()

            stopSelf()

            return START_NOT_STICKY
        }

        startTracking()

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {

        val notification =
            createNotification(
                "GPS tracking active"
            )

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo
                .FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    private fun createNotification(
        text: String
    ): Notification {

        return NotificationCompat
            .Builder(
                this,
                CHANNEL_ID
            )
            .setContentTitle(
                "Background Odometer"
            )
            .setContentText(
                text
            )
            .setSmallIcon(
                android.R.drawable
                    .ic_menu_mylocation
            )
            .setOngoing(true)
            .setCategory(
                NotificationCompat
                    .CATEGORY_SERVICE
            )
            .build()
    }

    private fun createNotificationChannel() {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Odometer tracking",
                NotificationManager
                    .IMPORTANCE_LOW
            )
        )
    }

    private fun isGpsEnabled():
        Boolean {

        val manager =
            getSystemService(
                LOCATION_SERVICE
            ) as LocationManager

        return try {

            manager.isProviderEnabled(
                LocationManager.GPS_PROVIDER
            )

        } catch (_: Exception) {

            false
        }
    }

    private fun startTracking() {

        if (tracking) {
            return
        }

        if (!isGpsEnabled()) {

            updateNotification(
                "GPS is OFF"
            )

            return
        }

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) !=
            PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val request =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                LOCATION_INTERVAL
            )
                .setMinUpdateIntervalMillis(
                    FASTEST_INTERVAL
                )
                .setMinUpdateDistanceMeters(
                    MIN_DISTANCE
                )
                .setWaitForAccurateLocation(
                    false
                )
                .build()

        try {

            fusedClient.requestLocationUpdates(
                request,
                callback,
                mainLooper
            )

            tracking = true

            lastLocation = null

            speedTotal = 0.0
            speedCount = 0
            maxSpeed = 0.0

            tripId =
                database.getActiveTrip()

            if (tripId == null) {

                tripId =
                    database.createTrip(
                        System.currentTimeMillis()
                    )
            }

            updateNotification(
                "GPS tracking active"
            )

        } catch (_: Exception) {

            tracking = false
        }
    }

    private fun processLocation(
        location: Location
    ) {

        if (!tracking) {
            return
        }

        if (!isGpsEnabled()) {

            stopTracking()

            return
        }

        if (!location.hasAccuracy()) {
            return
        }

        if (
            location.accuracy >
            MAX_ACCURACY
        ) {
            return
        }

        val previous =
            lastLocation

        var speedKmh =
            if (location.hasSpeed()) {

                maxOf(
                    0.0,
                    location.speed * 3.6
                )

            } else {
                0.0
            }

        if (previous != null) {

            val distance =
                previous.distanceTo(
                    location
                )

            val seconds =
                (
                    location.time -
                        previous.time
                    ) / 1000.0

            if (seconds > 0) {

                val calculatedSpeed =
                    distance /
                        seconds *
                        3.6

                if (
                    !location.hasSpeed()
                ) {
                    speedKmh =
                        calculatedSpeed
                }

                if (
                    distance >
                    MAX_JUMP
                ) {
                    return
                }

                if (
                    calculatedSpeed >
                    MAX_SPEED
                ) {
                    return
                }

                val threshold =
                    database
                        .getSpeedThreshold()

                if (
                    speedKmh >= threshold
                ) {

                    database.addToOdometer(
                        distance / 1000.0
                    )
                }
            }
        }

        val currentTrip =
            tripId ?: return

        database.addTrackPoint(
            currentTrip,
            location.latitude,
            location.longitude,
            location.time,
            speedKmh,
            location.accuracy.toDouble()
        )

        speedTotal +=
            speedKmh

        speedCount++

        if (
            speedKmh > maxSpeed
        ) {
            maxSpeed =
                speedKmh
        }

        lastLocation =
            Location(location)

        sendSpeedUpdate(
            speedKmh
        )

        updateNotification(
            "Speed %.1f km/h"
                .format(speedKmh)
        )
    }

    private fun stopTracking() {

        try {

            fusedClient
                .removeLocationUpdates(
                    callback
                )

        } catch (_: Exception) {
        }

        if (tracking) {

            val currentTrip =
                tripId

            if (
                currentTrip != null
            ) {

                val average =
                    if (speedCount > 0) {
                        speedTotal /
                            speedCount
                    } else {
                        0.0
                    }

                database.completeTrip(
                    currentTrip,
                    0.0,
                    average,
                    maxSpeed,
                    System.currentTimeMillis()
                )
            }
        }

        tracking = false

        tripId = null

        lastLocation = null

        updateNotification(
            "GPS tracking paused"
        )
    }

    private fun sendSpeedUpdate(
        speed: Double
    ) {

        sendBroadcast(
            Intent(
                ACTION_SPEED_UPDATE
            )
                .putExtra(
                    EXTRA_SPEED,
                    speed
                )
                .setPackage(
                    packageName
                )
        )
    }

    private fun updateNotification(
        text: String
    ) {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            createNotification(text)
        )
    }

    override fun onDestroy() {

        try {

            fusedClient
                .removeLocationUpdates(
                    callback
                )

        } catch (_: Exception) {
        }

        database.close()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
