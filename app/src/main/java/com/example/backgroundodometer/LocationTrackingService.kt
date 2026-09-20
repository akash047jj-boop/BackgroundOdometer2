package com.example.backgroundodometer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

        private const val INTERVAL =
            2000L

        private const val FASTEST =
            1500L

        private const val MIN_DISTANCE =
            3f

        private const val MAX_ACCURACY =
            30f

        private const val MAX_JUMP =
            300f

        private const val MAX_SPEED =
            160.0
    }

    private lateinit var fused:
        FusedLocationProviderClient

    private lateinit var database:
        OdometerDatabaseHelper

    private var tripId:
        Long? = null

    private var lastLocation:
        Location? = null

    private var tracking =
        false

    private val speeds =
        mutableListOf<Double>()

    private var maximumSpeed =
        0.0

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

        fused =
            LocationServices
                .getFusedLocationProviderClient(
                    applicationContext
                )

        createNotificationChannel()

        startAsForeground()

        restoreTrip()

        startTracking()
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

            stopTrackingAndFinalize()

            stopSelf()

            return START_NOT_STICKY
        }

        if (!tracking) {
            startTracking()
        }

        return START_STICKY
    }

    // =====================================================
    // FOREGROUND
    // =====================================================

    private fun startAsForeground() {

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

        return NotificationCompat.Builder(
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

        } catch (
            _: Exception
        ) {

            false
        }
    }

    private fun startTracking() {

        if (tracking) {
            return
        }

        if (!isGpsEnabled()) {

            updateNotification(
                "GPS is off"
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
                INTERVAL
            )
                .setMinUpdateIntervalMillis(
                    FASTEST
                )
                .setMinUpdateDistanceMeters(
                    MIN_DISTANCE
                )
                .setWaitForAccurateLocation(
                    false
                )
                .build()

        try {

            fused.requestLocationUpdates(
                request,
                callback,
                mainLooper
            )

            tracking = true

            updateNotification(
                "GPS tracking active"
            )

        } catch (
            _: Exception
        ) {

            tracking = false
        }
    }

    private fun stopTrackingAndFinalize() {

        try {

            fused.removeLocationUpdates(
                callback
            )

        } catch (
            _: Exception
        ) {
        }

        tracking = false

        lastLocation = null

        val id =
            tripId

        tripId = null

        if (id != null) {

            Thread {

                finalizeTrip(
                    id
                )

            }.start()
        }

        updateNotification(
            "Matching route..."
        )
    }

    // =====================================================
    // TRIP RESTORE
    // =====================================================

    private fun restoreTrip() {

        val active =
            database.getActiveTrip()

        if (active == null) {

            tripId = null

            return
        }

        tripId =
            active.id

        speeds.clear()

        val points =
            database.getTrackPoints(
                active.id
            )

        for (
            point in points
        ) {

            speeds.add(
                point.speedKmh
            )
        }

        maximumSpeed =
            points.maxOfOrNull {
                it.speedKmh
            } ?: 0.0

        if (
            points.isNotEmpty()
        ) {

            val point =
                points.last()

            val location =
                Location(
                    "database"
                )

            location.latitude =
                point.latitude

            location.longitude =
                point.longitude

            location.time =
                point.time

            location.accuracy =
                point.accuracy.toFloat()

            location.speed =
                (
                    point.speedKmh /
                        3.6
                    ).toFloat()

            lastLocation =
                location
        }
    }

    // =====================================================
    // LOCATION
    // =====================================================

    private fun processLocation(
        location: Location
    ) {

        if (!isGpsEnabled()) {

            stopTrackingAndFinalize()

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

        if (
            previous != null
        ) {

            val distance =
                previous.distanceTo(
                    location
                )

            val seconds =
                (
                    location.time -
                        previous.time
                    ) / 1000.0

            if (
                seconds > 0
            ) {

                val implied =
                    (
                        distance /
                            seconds
                        ) * 3.6

                if (
                    distance >
                    MAX_JUMP ||
                    implied >
                    MAX_SPEED
                ) {

                    return
                }
            }
        }

        val speed =
            if (
                location.hasSpeed()
            ) {

                maxOf(
                    0.0,
                    location.speed *
                        3.6
                )

            } else {

                0.0
            }

        if (
            tripId == null
        ) {

            tripId =
                database.createTrip(
                    location.time
                )

            speeds.clear()

            maximumSpeed =
                0.0
        }

        val id =
            tripId
                ?: return

        database.addTrackPoint(
            tripId =
                id,

            latitude =
                location.latitude,

            longitude =
                location.longitude,

            time =
                location.time,

            speedKmh =
                speed,

            accuracy =
                location.accuracy
                    .toDouble()
        )

        speeds.add(
            speed
        )

        if (
            speed >
            maximumSpeed
        ) {

            maximumSpeed =
                speed
        }

        val average =
            if (
                speeds.isNotEmpty()
            ) {

                speeds.average()

            } else {

                0.0
            }

        database.updateTripSpeed(
            id,
            average,
            maximumSpeed,
            location.time
        )

        lastLocation =
            Location(
                location
            )

        sendSpeedUpdate(
            speed
        )

        updateNotification(
            "Speed %.1f km/h"
                .format(
                    speed
                )
        )
    }

    // =====================================================
    // FINALIZE V7
    // =====================================================

    private fun finalizeTrip(
        id: Long
    ) {

        try {

            val points =
                database.getTrackPoints(
                    id
                )

            if (
                points.isEmpty()
            ) {

                database.completeTripWithDistances(
                    tripId =
                        id,

                    gpsDistanceKm =
                        0.0,

                    roadDistanceKm =
                        0.0,

                    finalDistanceKm =
                        0.0,

                    distanceSource =
                        "GPS",

                    confidence =
                        0.0,

                    endTime =
                        System.currentTimeMillis()
                )

                return
            }

            val threshold =
                database.getSpeedThreshold()

            /*
             * First calculate the old-style GPS
             * distance. This is our guaranteed
             * fallback.
             */
            val gpsDistance =
                calculateDistance(
                    points,
                    threshold
                )

            updateNotification(
                "Matching GPS route..."
            )

            /*
             * Now perform V7 road matching.
             */
            val matched =
                MapMatchingHelper.matchTrip(
                    points,
                    threshold
                )

            if (
                matched != null &&
                matched.distanceMeters > 0.0
            ) {

                val roadDistance =
                    matched.distanceMeters /
                        1000.0

                /*
                 * Cache the matched route so
                 * opening the trip later does
                 * not call OSRM again.
                 */
                MapMatchingHelper
                    .saveCachedResult(
                        applicationContext,
                        id,
                        matched
                    )

                database.completeTripWithDistances(
                    tripId =
                        id,

                    gpsDistanceKm =
                        gpsDistance,

                    roadDistanceKm =
                        roadDistance,

                    finalDistanceKm =
                        roadDistance,

                    distanceSource =
                        "ROAD",

                    confidence =
                        matched.confidence,

                    endTime =
                        points.last().time
                )

                updateNotification(
                    "Trip saved • Road %.2f km"
                        .format(
                            roadDistance
                        )
                )

            } else {

                /*
                 * OSRM failed.
                 *
                 * GPS distance remains
                 * authoritative for this trip.
                 */
                database.completeTripWithDistances(
                    tripId =
                        id,

                    gpsDistanceKm =
                        gpsDistance,

                    roadDistanceKm =
                        0.0,

                    finalDistanceKm =
                        gpsDistance,

                    distanceSource =
                        "GPS",

                    confidence =
                        0.0,

                    endTime =
                        points.last().time
                )

                updateNotification(
                    "Trip saved • GPS fallback"
                )
            }

        } catch (
            _: Exception
        ) {

            /*
             * Never lose a trip because
             * map matching failed.
             */
            try {

                val points =
                    database.getTrackPoints(
                        id
                    )

                val threshold =
                    database.getSpeedThreshold()

                val gpsDistance =
                    calculateDistance(
                        points,
                        threshold
                    )

                database.completeTripWithDistances(
                    tripId =
                        id,

                    gpsDistanceKm =
                        gpsDistance,

                    roadDistanceKm =
                        0.0,

                    finalDistanceKm =
                        gpsDistance,

                    distanceSource =
                        "GPS",

                    confidence =
                        0.0,

                    endTime =
                        points.lastOrNull()
                            ?.time
                            ?: System
                                .currentTimeMillis()
                )

            } catch (
                _: Exception
            ) {
                // Final safety fallback.
            }
        }
    }

    private fun calculateDistance(
        points: List<TrackPoint>,
        threshold: Double
    ): Double {

        if (
            points.size < 2
        ) {

            return 0.0
        }

        var total =
            0.0

        for (
            i in 1 until points.size
        ) {

            val previous =
                points[i - 1]

            val current =
                points[i]

            if (
                current.speedKmh <
                threshold
            ) {

                continue
            }

            val distance =
                haversine(
                    previous.latitude,
                    previous.longitude,
                    current.latitude,
                    current.longitude
                )

            if (
                distance > 0 &&
                distance <= MAX_JUMP
            ) {

                total +=
                    distance
            }
        }

        return total /
            1000.0
    }

    private fun haversine(
        lat1Value: Double,
        lon1Value: Double,
        lat2Value: Double,
        lon2Value: Double
    ): Double {

        val earth =
            6_371_000.0

        val lat1 =
            Math.toRadians(
                lat1Value
            )

        val lat2 =
            Math.toRadians(
                lat2Value
            )

        val dLat =
            Math.toRadians(
                lat2Value -
                    lat1Value
            )

        val dLon =
            Math.toRadians(
                lon2Value -
                    lon1Value
            )

        val a =
            sin(dLat / 2) *
                sin(dLat / 2) +
                cos(lat1) *
                cos(lat2) *
                sin(dLon / 2) *
                sin(dLon / 2)

        val c =
            2 *
                atan2(
                    sqrt(a),
                    sqrt(1 - a)
                )

        return earth * c
    }

    // =====================================================
    // BROADCAST
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

    private fun updateNotification(
        text: String
    ) {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            createNotification(
                text
            )
        )
    }

    override fun onDestroy() {

        try {

            fused.removeLocationUpdates(
                callback
            )

        } catch (
            _: Exception
        ) {
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
