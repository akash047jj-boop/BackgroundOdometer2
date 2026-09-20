package com.example.backgroundodometer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

        const val ACTION_STOP =
            "com.example.backgroundodometer.STOP"

        const val EXTRA_SPEED =
            "speed"

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

        private const val MAX_JUMP_METERS =
            300f

        private const val MAX_REASONABLE_SPEED =
            160.0
    }

    private lateinit var fusedClient:
        FusedLocationProviderClient

    private lateinit var database:
        OdometerDatabaseHelper

    private var currentTripId:
        Long? = null

    private var lastLocation:
        Location? = null

    private var tracking =
        false

    private var speedTotal =
        0.0

    private var speedCount =
        0

    private var maxSpeed =
        0.0

    private val providerReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                if (
                    intent?.action ==
                    LocationManager
                        .PROVIDERS_CHANGED_ACTION
                ) {

                    if (isGpsEnabled()) {

                        if (!tracking) {
                            startTracking()
                        }

                    } else {

                        finalizeCurrentTrip()
                    }
                }
            }
        }

    private val locationCallback =
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

        startForegroundNotification()

        ContextCompat.registerReceiver(
            this,
            providerReceiver,
            IntentFilter(
                LocationManager.PROVIDERS_CHANGED_ACTION
            ),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
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

            finalizeCurrentTrip()

            stopSelf()

            return START_NOT_STICKY
        }

        if (isGpsEnabled()) {

            startTracking()

        } else {

            updateNotification(
                "GPS is OFF"
            )
        }

        return START_STICKY
    }

    // =====================================================
    // FOREGROUND NOTIFICATION
    // =====================================================

    private fun startForegroundNotification() {

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

    // =====================================================
    // GPS
    // =====================================================

    private fun isGpsEnabled():
        Boolean {

        val manager =
            getSystemService(
                Context.LOCATION_SERVICE
            ) as LocationManager

        return try {

            manager.isProviderEnabled(
                LocationManager.GPS_PROVIDER
            )

        } catch (_: Exception) {

            false
        }
    }

    // =====================================================
    // START LOCATION TRACKING
    // =====================================================

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

            updateNotification(
                "Location permission required"
            )

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
                locationCallback,
                mainLooper
            )

            tracking = true

            lastLocation = null

            speedTotal = 0.0
            speedCount = 0
            maxSpeed = 0.0

            /*
             * Continue an unfinished trip if one exists.
             * Otherwise create a new trip.
             */
            currentTripId =
                database.getActiveTrip()

            if (currentTripId == null) {

                currentTripId =
                    database.createTrip(
                        System.currentTimeMillis()
                    )
            }

            updateNotification(
                "GPS tracking active"
            )

        } catch (_: Exception) {

            tracking = false

            updateNotification(
                "Unable to start GPS"
            )
        }
    }

    // =====================================================
    // LOCATION PROCESSING
    // =====================================================

    private fun processLocation(
        location: Location
    ) {

        if (!tracking) {
            return
        }

        if (!isGpsEnabled()) {

            finalizeCurrentTrip()

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

            val distanceMeters =
                previous.distanceTo(
                    location
                )

            val seconds =
                (
                    location.time -
                        previous.time
                    ) / 1000.0

            if (seconds <= 0.0) {
                return
            }

            val calculatedSpeed =
                distanceMeters /
                    seconds *
                    3.6

            /*
             * If Android does not provide a reliable speed,
             * calculate it from the GPS points.
             */
            if (
                !location.hasSpeed()
            ) {

                speedKmh =
                    calculatedSpeed
            }

            /*
             * Reject impossible GPS jumps.
             */
            if (
                distanceMeters >
                MAX_JUMP_METERS
            ) {

                return
            }

            if (
                calculatedSpeed >
                MAX_REASONABLE_SPEED
            ) {

                return
            }

            /*
             * Only movement at or above the configured
             * threshold contributes to the odometer.
             *
             * The GPS point itself is still saved regardless
             * of speed.
             */
            val threshold =
                database.getSpeedThreshold()

            if (
                speedKmh >= threshold
            ) {

                database.addToOdometer(
                    distanceMeters / 1000.0
                )
            }
        }

        val tripId =
            currentTripId ?: return

        /*
         * Save EVERY valid GPS point.
         */
        database.addTrackPoint(
            tripId = tripId,
            latitude = location.latitude,
            longitude = location.longitude,
            time = location.time,
            speed = speedKmh,
            accuracy =
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
            "Speed %.1f km/h • GPS active"
                .format(speedKmh)
        )
    }

    // =====================================================
    // FINALIZE TRIP
    // =====================================================

    private fun finalizeCurrentTrip() {

        try {

            fusedClient
                .removeLocationUpdates(
                    locationCallback
                )

        } catch (_: Exception) {
        }

        if (!tracking) {
            return
        }

        tracking = false

        val tripId =
            currentTripId

        currentTripId = null

        lastLocation = null

        if (tripId == null) {

            updateNotification(
                "GPS tracking paused"
            )

            return
        }

        Thread {

            finalizeTrip(
                tripId
            )

        }.start()
    }

    private fun finalizeTrip(
        tripId: Long
    ) {

        try {

            val points =
                database.getTrackPoints(
                    tripId
                )

            if (points.isEmpty()) {

                database.completeTrip(
                    tripId = tripId,
                    distanceKm = 0.0,
                    averageSpeed = 0.0,
                    maxSpeed = 0.0,
                    endTime =
                        System.currentTimeMillis()
                )

                return
            }

            val threshold =
                database.getSpeedThreshold()

            val tripDistance =
                calculateTripDistance(
                    points,
                    threshold
                )

            val averageSpeed =
                if (points.isNotEmpty()) {

                    points.sumOf {
                        it.speedKmh
                    } /
                        points.size

                } else {
                    0.0
                }

            val maximumSpeed =
                points.maxOfOrNull {
                    it.speedKmh
                } ?: 0.0

            val endTime =
                points.last().time

            database.completeTrip(
                tripId = tripId,
                distanceKm =
                    tripDistance,
                averageSpeed =
                    averageSpeed,
                maxSpeed =
                    maximumSpeed,
                endTime =
                    endTime
            )

            checkDistanceAlert()

            updateNotification(
                "Trip saved • %.2f km"
                    .format(tripDistance)
            )

        } catch (_: Exception) {

            updateNotification(
                "Trip finalization error"
            )
        }
    }

    // =====================================================
    // TRIP DISTANCE
    // =====================================================

    private fun calculateTripDistance(
        points: List<TrackPoint>,
        threshold: Double
    ): Double {

        if (points.size < 2) {
            return 0.0
        }

        var totalMeters =
            0.0

        for (
            index in 1 until points.size
        ) {

            val previous =
                points[index - 1]

            val current =
                points[index]

            /*
             * Distance is counted only when the CURRENT
             * point is at or above the odometer threshold.
             */
            if (
                current.speedKmh <
                threshold
            ) {
                continue
            }

            val distance =
                haversineMeters(
                    previous.latitude,
                    previous.longitude,
                    current.latitude,
                    current.longitude
                )

            if (
                distance > 0.0 &&
                distance <=
                MAX_JUMP_METERS
            ) {

                totalMeters +=
                    distance
            }
        }

        return totalMeters / 1000.0
    }

    // =====================================================
    // HAVERSINE
    // =====================================================

    private fun haversineMeters(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double
    ): Double {

        val earthRadius =
            6_371_000.0

        val lat1 =
            Math.toRadians(
                latitude1
            )

        val lat2 =
            Math.toRadians(
                latitude2
            )

        val deltaLat =
            Math.toRadians(
                latitude2 -
                    latitude1
            )

        val deltaLon =
            Math.toRadians(
                longitude2 -
                    longitude1
            )

        val a =
            kotlin.math.sin(
                deltaLat / 2
            ) *
                kotlin.math.sin(
                    deltaLat / 2
                ) +
                kotlin.math.cos(lat1) *
                kotlin.math.cos(lat2) *
                kotlin.math.sin(
                    deltaLon / 2
                ) *
                kotlin.math.sin(
                    deltaLon / 2
                )

        val c =
            2.0 *
                kotlin.math.atan2(
                    kotlin.math.sqrt(a),
                    kotlin.math.sqrt(
                        1.0 - a
                    )
                )

        return earthRadius * c
    }

    // =====================================================
    // DISTANCE ALERT
    // =====================================================

    private fun checkDistanceAlert() {

        if (
            !database
                .isDistanceAlertEnabled()
        ) {
            return
        }

        if (
            database
                .isDistanceAlertFired()
        ) {
            return
        }

        val target =
            database
                .getDistanceAlertTarget()

        if (target <= 0.0) {
            return
        }

        val total =
            database
                .getTotalOdometer()

        if (total >= target) {

            database
                .markDistanceAlertFired()

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.notify(
                2002,
                NotificationCompat
                    .Builder(
                        this,
                        CHANNEL_ID
                    )
                    .setContentTitle(
                        "Odometer target reached"
                    )
                    .setContentText(
                        "Odometer: %.2f km"
                            .format(total)
                    )
                    .setSmallIcon(
                        android.R.drawable
                            .ic_dialog_info
                    )
                    .setAutoCancel(true)
                    .build()
            )
        }
    }

    // =====================================================
    // SPEED BROADCAST
    // =====================================================

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

    // =====================================================
    // NOTIFICATION
    // =====================================================

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

    // =====================================================
    // DESTROY
    // =====================================================

    override fun onDestroy() {

        try {

            fusedClient
                .removeLocationUpdates(
                    locationCallback
                )

        } catch (_: Exception) {
        }

        try {

            unregisterReceiver(
                providerReceiver
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
