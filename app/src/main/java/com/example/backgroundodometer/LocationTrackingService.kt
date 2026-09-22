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
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationTrackingService : Service() {

    companion object {

        const val ACTION_SPEED_UPDATE =
            "com.example.backgroundodometer.SPEED_UPDATE"

        const val ACTION_STATUS_UPDATE =
            "com.example.backgroundodometer.STATUS_UPDATE"

        const val ACTION_STOP =
            "com.example.backgroundodometer.STOP"

        const val EXTRA_SPEED =
            "speed"

        const val EXTRA_STATUS =
            "status"

        private const val CHANNEL_ID =
            "odometer_tracking"

        private const val NOTIFICATION_ID =
            1001

        private const val INTERVAL =
            2000L

        private const val FASTEST =
            1000L

        private const val MIN_DISTANCE =
            2f

        private const val MAX_ACCURACY =
            40f

        private const val MAX_JUMP =
            300f

        private const val MAX_SPEED =
            180.0

        /*
         * V25 movement gate.
         * GPS ON alone is never enough to create a trip.
         * A session must show sustained movement and
         * meaningful displacement before a database trip exists.
         */
        private const val MOVEMENT_SPEED =
            3.0

        private const val MOVEMENT_STREAK_REQUIRED =
            5

        private const val MIN_MOVEMENT_DISTANCE_METERS =
            20.0

        private const val PRE_TRIP_BUFFER_SIZE =
            12

        private const val PREFS =
            "background_odometer"

        private const val PREF_AUTO_TRACKING =
            "auto_tracking_enabled"
    }

    private lateinit var fused:
        FusedLocationProviderClient

    private lateinit var database:
        OdometerDatabaseHelper

    private val preferences by lazy {

        getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )
    }

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    private var tripId:
        Long? = null

    private var lastLocation:
        Location? = null

    /*
     * true only while actual location updates
     * are being requested.
     */
    private var tracking =
        false

    /*
     * true while a completed trip is being
     * calculated/saved.
     */
    private var finalizingTrip =
        false

    private val speeds =
        mutableListOf<Double>()

    private var maximumSpeed =
        0.0

    /*
     * V25: points received before genuine movement is
     * confirmed. These are kept only in memory and are
     * never saved as a trip unless the movement gate opens.
     */
    private val preTripBuffer =
        ArrayDeque<Location>()

    private var movementStreak =
        0

    private var preTripAnchor: Location? =
        null

    private var movementConfirmed =
        false

    private var locationReceiverRegistered =
        false

    /*
     * V9:
     *
     * Listen directly for Android location-state
     * changes.
     *
     * This is what fixes the GPS OFF problem.
     */
    private val locationStateReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                if (
                    intent == null
                ) {
                    return
                }

                if (
                    intent.action ==
                    LocationManager.MODE_CHANGED_ACTION ||
                    intent.action ==
                    LocationManager.PROVIDERS_CHANGED_ACTION
                ) {

                    handleLocationStateChanged()
                }
            }
        }

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

        registerLocationStateReceiver()

        restoreTrip()

        if (
            isAutoTrackingEnabled()
        ) {

            startTracking()

        } else {

            updateNotification(
                "Tracking stopped"
            )
        }
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

            preferences.edit()
                .putBoolean(
                    PREF_AUTO_TRACKING,
                    false
                )
                .apply()

            stopTrackingAndFinalize(
                stopServiceAfter = true
            )

            return START_NOT_STICKY
        }

        if (
            !isAutoTrackingEnabled()
        ) {

            stopSelf()

            return START_NOT_STICKY
        }

        /*
         * Sticky service means that if Android
         * recreates the service, it can resume
         * the armed tracking state.
         */
        if (!tracking) {

            handleLocationStateChanged()
        }

        return START_STICKY
    }

    private fun isAutoTrackingEnabled():
        Boolean {

        return preferences.getBoolean(
            PREF_AUTO_TRACKING,
            false
        )
    }

    private fun registerLocationStateReceiver() {

        if (
            locationReceiverRegistered
        ) {
            return
        }

        val filter =
            IntentFilter().apply {

                addAction(
                    LocationManager.MODE_CHANGED_ACTION
                )

                addAction(
                    LocationManager.PROVIDERS_CHANGED_ACTION
                )
            }

        try {

            /*
             * System location broadcasts are used here,
             * so EXPORTED is required on some Android
             * versions/configurations.
             */
            ContextCompat.registerReceiver(
                this,
                locationStateReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )

            locationReceiverRegistered =
                true

        } catch (_: Exception) {
        }
    }

    private fun handleLocationStateChanged() {

        if (
            !isAutoTrackingEnabled()
        ) {
            return
        }

        if (
            isLocationEnabled()
        ) {

            if (!tracking) {

                startTracking()
            }

        } else {

            if (tracking) {

                stopLocationUpdatesOnly()
            }

            finalizeCurrentTripForGpsOff()
        }
    }

    private fun isLocationEnabled():
        Boolean {

        val manager =
            getSystemService(
                Context.LOCATION_SERVICE
            ) as LocationManager

        return try {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.P
            ) {

                manager.isLocationEnabled()

            } else {

                manager.isProviderEnabled(
                    LocationManager.GPS_PROVIDER
                ) ||
                    manager.isProviderEnabled(
                        LocationManager.NETWORK_PROVIDER
                    )
            }

        } catch (_: Exception) {

            false
        }
    }

    private fun startAsForeground() {

        val notification =
            createNotification(
                "Waiting for GPS..."
            )

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
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
                android.R.drawable.ic_menu_mylocation
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .setForegroundServiceBehavior(
                NotificationCompat
                    .FOREGROUND_SERVICE_IMMEDIATE
            )
            .build()
    }

    private fun createNotificationChannel() {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        val existing =
            manager.getNotificationChannel(
                CHANNEL_ID
            )

        if (
            existing == null
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Background Odometer Tracking",
                    NotificationManager
                        .IMPORTANCE_LOW
                )

            channel.description =
                "Shows GPS tracking status and speed."

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun startTracking() {

        if (
            tracking
        ) {
            return
        }

        if (
            !isAutoTrackingEnabled()
        ) {
            return
        }

        if (
            !isLocationEnabled()
        ) {

            updateNotification(
                "GPS OFF • waiting"
            )

            sendStatus(
                "GPS OFF • WAITING"
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

            sendStatus(
                "LOCATION PERMISSION REQUIRED"
            )

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

            tracking =
                true

            resetMovementGate()

            updateNotification(
                "GPS ON • waiting for movement"
            )

            sendStatus(
                "GPS ON • WAITING FOR LOCATION"
            )

        } catch (_: Exception) {

            tracking =
                false

            updateNotification(
                "Unable to start GPS"
            )

            sendStatus(
                "UNABLE TO START GPS"
            )
        }
    }

    private fun stopLocationUpdatesOnly() {

        try {

            fused.removeLocationUpdates(
                callback
            )

        } catch (_: Exception) {
        }

        tracking =
            false

        lastLocation =
            null

        resetMovementGate()

        speeds.clear()

        maximumSpeed =
            0.0

        updateNotification(
            "GPS OFF • trip ending"
        )

        sendStatus(
            "GPS OFF • FINALIZING"
        )
    }

    private fun finalizeCurrentTripForGpsOff() {

        val id =
            tripId

        if (
            id == null
        ) {

            updateNotification(
                "GPS OFF • waiting"
            )

            sendStatus(
                "GPS OFF • WAITING"
            )

            return
        }

        if (
            finalizingTrip
        ) {
            return
        }

        tripId =
            null

        finalizingTrip =
            true

        Thread {

            finalizeTrip(
                id
            )

            finalizingTrip =
                false

            mainHandler.post {

                if (
                    !isAutoTrackingEnabled()
                ) {
                    return@post
                }

                if (
                    isLocationEnabled()
                ) {

                    startTracking()

                } else {

                    updateNotification(
                        "GPS OFF • waiting"
                    )

                    sendStatus(
                        "GPS OFF • WAITING"
                    )
                }
            }

        }.start()
    }

    private fun stopTrackingAndFinalize(
        stopServiceAfter: Boolean
    ) {

        try {

            fused.removeLocationUpdates(
                callback
            )

        } catch (_: Exception) {
        }

        tracking =
            false

        lastLocation =
            null

        speeds.clear()

        maximumSpeed =
            0.0

        val id =
            tripId

        tripId =
            null

        if (
            id == null
        ) {

            if (
                stopServiceAfter
            ) {

                updateNotification(
                    "Tracking stopped"
                )

                stopSelf()

            } else {

                updateNotification(
                    "GPS OFF • waiting"
                )
            }

            return
        }

        if (
            finalizingTrip
        ) {

            if (
                stopServiceAfter
            ) {

                stopSelf()
            }

            return
        }

        finalizingTrip =
            true

        updateNotification(
            "Saving trip..."
        )

        sendStatus(
            "SAVING TRIP"
        )

        Thread {

            finalizeTrip(
                id
            )

            finalizingTrip =
                false

            mainHandler.post {

                if (
                    stopServiceAfter
                ) {

                    updateNotification(
                        "Tracking stopped"
                    )

                    sendStatus(
                        "TRACKING STOPPED"
                    )

                    stopSelf()

                } else {

                    updateNotification(
                        "Trip saved"
                    )
                }
            }

        }.start()
    }

    private fun restoreTrip() {

        val active =
            database.getActiveTrip()

        if (
            active == null
        ) {

            tripId =
                null

            return
        }

        tripId =
            active.id

        movementConfirmed = true

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

    private fun processLocation(
        location: Location
    ) {

        /*
         * GPS OFF is handled immediately.
         */
        if (
            !isLocationEnabled()
        ) {

            stopLocationUpdatesOnly()

            finalizeCurrentTripForGpsOff()

            return
        }

        if (!location.hasAccuracy()) {
            return
        }

        if (location.accuracy > MAX_ACCURACY) {
            return
        }

        val previous = lastLocation

        if (previous != null) {

            val distance =
                previous.distanceTo(location)

            val seconds =
                (location.time - previous.time) / 1000.0

            if (seconds > 0.0) {

                val implied =
                    distance / seconds * 3.6

                if (
                    distance > MAX_JUMP ||
                    implied > MAX_SPEED
                ) {
                    return
                }
            }
        }

        val speed =
            if (location.hasSpeed()) {
                maxOf(0.0, location.speed * 3.6)
            } else {
                0.0
            }

        /*
         * V25: DO NOT create a database trip just because
         * GPS produced a valid location.
         *
         * While the vehicle is stationary we keep only a
         * tiny in-memory buffer. GPS drift in this phase can
         * therefore never become a 0.02/0.20 km trip.
         */
        if (!movementConfirmed && tripId == null) {

            updateMovementGate(
                location,
                speed
            )

            lastLocation = Location(location)

            sendSpeedUpdate(speed)

            updateNotification(
                if (movementConfirmed) {
                    "GPS ON • movement detected"
                } else {
                    "GPS ON • stationary / waiting"
                }
            )

            sendStatus(
                if (movementConfirmed) {
                    "GPS ON • MOVEMENT DETECTED"
                } else {
                    "GPS ON • WAITING FOR MOVEMENT"
                }
            )

            if (!movementConfirmed) {
                return
            }

            /*
             * Movement has now been confirmed. Create the
             * real trip and save the buffered route points.
             */
            tripId = database.createTrip(
                preTripBuffer.firstOrNull()?.time
                    ?: location.time
            )

            speeds.clear()
            maximumSpeed = 0.0

            val id = tripId ?: return

            for (buffered in preTripBuffer) {
                database.addTrackPoint(
                    tripId = id,
                    latitude = buffered.latitude,
                    longitude = buffered.longitude,
                    time = buffered.time,
                    speedKmh = if (buffered.hasSpeed()) {
                        maxOf(0.0, buffered.speed * 3.6)
                    } else {
                        0.0
                    },
                    accuracy = buffered.accuracy.toDouble()
                )
            }

            preTripBuffer.clear()
        }

        val id = tripId ?: return

        database.addTrackPoint(
            tripId = id,
            latitude = location.latitude,
            longitude = location.longitude,
            time = location.time,
            speedKmh = speed,
            accuracy = location.accuracy.toDouble()
        )

        speeds.add(speed)

        if (speed > maximumSpeed) {
            maximumSpeed = speed
        }

        val average =
            if (speeds.isNotEmpty()) speeds.average() else 0.0

        database.updateTripSpeed(
            id,
            average,
            maximumSpeed,
            location.time
        )

        lastLocation = Location(location)

        sendSpeedUpdate(speed)

        updateNotification(
            "GPS ON • %.1f km/h".format(speed)
        )

        sendStatus(
            "GPS ON • TRACKING"
        )
    }

    private fun updateMovementGate(
        location: Location,
        speed: Double
    ) {

        if (preTripAnchor == null) {
            preTripAnchor = Location(location)
        }

        preTripBuffer.addLast(Location(location))

        while (preTripBuffer.size > PRE_TRIP_BUFFER_SIZE) {
            preTripBuffer.removeFirst()
        }

        if (speed >= MOVEMENT_SPEED) {
            movementStreak++
        } else {
            movementStreak = 0
        }

        val anchor = preTripAnchor
        val displacement =
            if (anchor != null) {
                anchor.distanceTo(location).toDouble()
            } else {
                0.0
            }

        /*
         * Genuine movement needs both sustained speed and
         * meaningful displacement. This prevents GPS speed
         * spikes while parked from opening a trip.
         */
        if (
            movementStreak >= MOVEMENT_STREAK_REQUIRED &&
            displacement >= MIN_MOVEMENT_DISTANCE_METERS
        ) {
            movementConfirmed = true
        }
    }

    private fun resetMovementGate() {

        preTripBuffer.clear()
        movementStreak = 0
        preTripAnchor = null
        movementConfirmed = false
    }

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

            val gpsDistance =
                database.calculateGpsDistance(
                    id,
                    threshold
                )

            /*
             * V25 safety net: a real trip must not finish as
             * a tiny GPS-noise trip. Manual trips are unaffected
             * because this code only handles automatic GPS trips.
             */
            if (gpsDistance < 0.03) {
                database.discardTrip(id)
                updateNotification(
                    "No meaningful movement • trip discarded"
                )
                return
            }

            updateNotification(
                "Matching road route..."
            )

            val matched =
                try {

                    MapMatchingHelper.matchTrip(
                        points,
                        threshold
                    )

                } catch (_: Exception) {

                    null
                }

            if (
                matched != null &&
                matched.distanceMeters > 0.0
            ) {

                val roadDistance =
                    matched.distanceMeters /
                        1000.0

                val reasonable =
                    roadDistance >=
                        gpsDistance * 0.45 &&
                    roadDistance <=
                        gpsDistance * 2.5

                if (
                    reasonable
                ) {

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
                        "Trip saved • %.2f km"
                            .format(
                                roadDistance
                            )
                    )

                    return
                }
            }

            /*
             * GPS fallback.
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
                "Trip saved • GPS %.2f km"
                    .format(
                        gpsDistance
                    )
            )

        } catch (_: Exception) {

            try {

                val threshold =
                    database.getSpeedThreshold()

                val gpsDistance =
                    database.calculateGpsDistance(
                        id,
                        threshold
                    )

                val points =
                    database.getTrackPoints(
                        id
                    )

                if (gpsDistance < 0.03) {
                    database.discardTrip(id)
                    return
                }

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
                        points.lastOrNull()?.time
                            ?: System.currentTimeMillis()
                )

            } catch (_: Exception) {
            }
        }
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

    private fun sendStatus(
        status: String
    ) {

        sendBroadcast(
            Intent(
                ACTION_STATUS_UPDATE
            )
                .putExtra(
                    EXTRA_STATUS,
                    status
                )
                .putExtra(
                    EXTRA_SPEED,
                    lastLocation?.speed
                        ?.toDouble()
                        ?.times(3.6)
                        ?: 0.0
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

        } catch (_: Exception) {
        }

        if (
            locationReceiverRegistered
        ) {

            try {

                unregisterReceiver(
                    locationStateReceiver
                )

            } catch (_: Exception) {
            }

            locationReceiverRegistered =
                false
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
