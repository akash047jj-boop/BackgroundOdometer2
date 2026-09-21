package com.example.backgroundodometer

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class TripSummary(
    val id: Long,
    val startTime: Long,
    val endTime: Long,
    val distanceKm: Double,
    val averageSpeed: Double,
    val maxSpeed: Double,
    val gpsDistanceKm: Double = 0.0,
    val roadDistanceKm: Double = 0.0,
    val distanceSource: String = "GPS",
    val matchingConfidence: Double = 0.0
)

data class TrackPoint(
    val id: Long,
    val tripId: Long,
    val latitude: Double,
    val longitude: Double,
    val time: Long,
    val speedKmh: Double,
    val accuracy: Double
)

data class FuelRecord(
    val id: Long,
    val time: Long,
    val odometerKm: Double,
    val litresAdded: Double,
    val fuelAfterLitres: Double,
    val note: String
)

class OdometerDatabaseHelper(
    context: Context
) : SQLiteOpenHelper(
    context,
    "background_odometer.db",
    null,
    4
) {

    companion object {

        private const val TABLE_TRIPS =
            "trips"

        private const val TABLE_POINTS =
            "track_points"

        private const val TABLE_SETTINGS =
            "settings"

        private const val TABLE_FUEL =
            "fuel_records"

        private const val MAX_JUMP =
            300.0

        private const val MAX_TIME_GAP =
            30.0

        private const val MAX_SPEED =
            180.0

        private const val DEFAULT_TANK_CAPACITY =
            10.0

        private const val DEFAULT_RESERVE =
            1.0
    }

    override fun onCreate(
        db: SQLiteDatabase
    ) {

        db.execSQL(
            """
            CREATE TABLE trips (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                start_time INTEGER NOT NULL,
                end_time INTEGER DEFAULT 0,
                distance_km REAL DEFAULT 0,
                average_speed REAL DEFAULT 0,
                max_speed REAL DEFAULT 0,
                completed INTEGER DEFAULT 0,
                gps_distance_km REAL DEFAULT 0,
                road_distance_km REAL DEFAULT 0,
                distance_source TEXT DEFAULT 'GPS',
                matching_confidence REAL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE track_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                trip_id INTEGER NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                time INTEGER NOT NULL,
                speed_kmh REAL DEFAULT 0,
                accuracy REAL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE settings (
                key TEXT PRIMARY KEY,
                value TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE fuel_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                time INTEGER NOT NULL,
                odometer_km REAL DEFAULT 0,
                litres_added REAL DEFAULT 0,
                fuel_after_litres REAL DEFAULT 0,
                note TEXT DEFAULT ''
            )
            """.trimIndent()
        )

        setSetting(
            db,
            "speed_threshold",
            "6.0"
        )

        setSetting(
            db,
            "tank_capacity",
            DEFAULT_TANK_CAPACITY.toString()
        )

        setSetting(
            db,
            "reserve_fuel",
            DEFAULT_RESERVE.toString()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {

        if (oldVersion < 2) {

            db.execSQL(
                """
                ALTER TABLE trips
                ADD COLUMN gps_distance_km REAL DEFAULT 0
                """.trimIndent()
            )

            db.execSQL(
                """
                ALTER TABLE trips
                ADD COLUMN road_distance_km REAL DEFAULT 0
                """.trimIndent()
            )

            db.execSQL(
                """
                ALTER TABLE trips
                ADD COLUMN distance_source TEXT DEFAULT 'GPS'
                """.trimIndent()
            )

            db.execSQL(
                """
                ALTER TABLE trips
                ADD COLUMN matching_confidence REAL DEFAULT 0
                """.trimIndent()
            )

            db.execSQL(
                """
                UPDATE trips
                SET gps_distance_km = distance_km
                WHERE gps_distance_km = 0
                """.trimIndent()
            )
        }

        if (oldVersion < 3) {

            repairSuspiciousTrips(db)
        }

        if (oldVersion < 4) {

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS fuel_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    time INTEGER NOT NULL,
                    odometer_km REAL DEFAULT 0,
                    litres_added REAL DEFAULT 0,
                    fuel_after_litres REAL DEFAULT 0,
                    note TEXT DEFAULT ''
                )
                """.trimIndent()
            )

            setSetting(
                db,
                "tank_capacity",
                DEFAULT_TANK_CAPACITY.toString()
            )

            setSetting(
                db,
                "reserve_fuel",
                DEFAULT_RESERVE.toString()
            )
        }
    }

    private fun repairSuspiciousTrips(
        db: SQLiteDatabase
    ) {

        try {

            val threshold =
                getSpeedThresholdFromDb(db)

            val cursor =
                db.rawQuery(
                    """
                    SELECT
                        id,
                        distance_km
                    FROM trips
                    WHERE completed = 1
                    """,
                    null
                )

            cursor.use {

                while (it.moveToNext()) {

                    val id =
                        it.getLong(0)

                    val oldDistance =
                        it.getDouble(1)

                    val calculated =
                        calculateGpsDistance(
                            db,
                            id,
                            threshold
                        )

                    val suspicious =
                        calculated > 0.5 &&
                            (
                                oldDistance <= 0.1 ||
                                    calculated >
                                    oldDistance * 1.5
                                )

                    if (suspicious) {

                        val values =
                            ContentValues()

                        values.put(
                            "distance_km",
                            calculated
                        )

                        values.put(
                            "gps_distance_km",
                            calculated
                        )

                        values.put(
                            "road_distance_km",
                            0.0
                        )

                        values.put(
                            "distance_source",
                            "GPS"
                        )

                        values.put(
                            "matching_confidence",
                            0.0
                        )

                        db.update(
                            TABLE_TRIPS,
                            values,
                            "id = ?",
                            arrayOf(
                                id.toString()
                            )
                        )
                    }
                }
            }

        } catch (_: Exception) {
        }
    }

    private fun getSpeedThresholdFromDb(
        db: SQLiteDatabase
    ): Double {

        val cursor =
            db.rawQuery(
                """
                SELECT value
                FROM settings
                WHERE key = ?
                """,
                arrayOf(
                    "speed_threshold"
                )
            )

        cursor.use {

            if (it.moveToFirst()) {

                return it.getString(0)
                    .toDoubleOrNull()
                    ?: 6.0
            }
        }

        return 6.0
    }

    private fun setSetting(
        db: SQLiteDatabase,
        key: String,
        value: String
    ) {

        val values =
            ContentValues()

        values.put(
            "key",
            key
        )

        values.put(
            "value",
            value
        )

        db.insertWithOnConflict(
            TABLE_SETTINGS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun getSetting(
        key: String,
        defaultValue: Double
    ): Double {

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT value
                FROM settings
                WHERE key = ?
                """,
                arrayOf(key)
            )

        cursor.use {

            if (it.moveToFirst()) {

                return it.getString(0)
                    .toDoubleOrNull()
                    ?: defaultValue
            }
        }

        return defaultValue
    }

    private fun saveSetting(
        key: String,
        value: Double
    ) {

        setSetting(
            writableDatabase,
            key,
            value.toString()
        )
    }

    fun getSpeedThreshold():
        Double {

        return getSetting(
            "speed_threshold",
            6.0
        )
    }

    fun getTankCapacity():
        Double {

        return getSetting(
            "tank_capacity",
            DEFAULT_TANK_CAPACITY
        )
    }

    fun setTankCapacity(
        litres: Double
    ) {

        saveSetting(
            "tank_capacity",
            litres.coerceAtLeast(0.1)
        )
    }

    fun getReserveFuel():
        Double {

        return getSetting(
            "reserve_fuel",
            DEFAULT_RESERVE
        )
    }

    fun setReserveFuel(
        litres: Double
    ) {

        saveSetting(
            "reserve_fuel",
            litres.coerceAtLeast(0.0)
        )
    }

    fun createTrip(
        startTime: Long
    ): Long {

        val values =
            ContentValues()

        values.put(
            "start_time",
            startTime
        )

        values.put(
            "end_time",
            0
        )

        values.put(
            "completed",
            0
        )

        values.put(
            "distance_km",
            0.0
        )

        values.put(
            "gps_distance_km",
            0.0
        )

        values.put(
            "road_distance_km",
            0.0
        )

        values.put(
            "distance_source",
            "GPS"
        )

        values.put(
            "matching_confidence",
            0.0
        )

        return writableDatabase.insert(
            TABLE_TRIPS,
            null,
            values
        )
    }

    fun getActiveTrip():
        TripSummary? {

        val cursor =
            readableDatabase.rawQuery(
                tripSelectSql(
                    "completed = 0"
                ) +
                    """
                    ORDER BY id DESC
                    LIMIT 1
                    """,
                null
            )

        cursor.use {

            if (it.moveToFirst()) {

                return cursorToTrip(it)
            }
        }

        return null
    }

    fun addTrackPoint(
        tripId: Long,
        latitude: Double,
        longitude: Double,
        time: Long,
        speedKmh: Double,
        accuracy: Double
    ) {

        val values =
            ContentValues()

        values.put(
            "trip_id",
            tripId
        )

        values.put(
            "latitude",
            latitude
        )

        values.put(
            "longitude",
            longitude
        )

        values.put(
            "time",
            time
        )

        values.put(
            "speed_kmh",
            speedKmh
        )

        values.put(
            "accuracy",
            accuracy
        )

        writableDatabase.insert(
            TABLE_POINTS,
            null,
            values
        )
    }

    fun getTrackPoints(
        tripId: Long
    ): List<TrackPoint> {

        val result =
            mutableListOf<TrackPoint>()

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT
                    id,
                    trip_id,
                    latitude,
                    longitude,
                    time,
                    speed_kmh,
                    accuracy
                FROM track_points
                WHERE trip_id = ?
                ORDER BY time ASC
                """,
                arrayOf(
                    tripId.toString()
                )
            )

        cursor.use {

            while (it.moveToNext()) {

                result.add(
                    TrackPoint(
                        id = it.getLong(0),
                        tripId = it.getLong(1),
                        latitude = it.getDouble(2),
                        longitude = it.getDouble(3),
                        time = it.getLong(4),
                        speedKmh = it.getDouble(5),
                        accuracy = it.getDouble(6)
                    )
                )
            }
        }

        return result
    }

    fun updateTripSpeed(
        tripId: Long,
        averageSpeed: Double,
        maxSpeed: Double,
        lastTime: Long
    ) {

        val values =
            ContentValues()

        values.put(
            "average_speed",
            averageSpeed
        )

        values.put(
            "max_speed",
            maxSpeed
        )

        values.put(
            "end_time",
            lastTime
        )

        writableDatabase.update(
            TABLE_TRIPS,
            values,
            "id = ?",
            arrayOf(
                tripId.toString()
            )
        )
    }

    fun completeTrip(
        tripId: Long,
        distanceKm: Double,
        routeJson: String?,
        endTime: Long
    ) {

        completeTripWithDistances(
            tripId = tripId,
            gpsDistanceKm = distanceKm,
            roadDistanceKm = 0.0,
            finalDistanceKm = distanceKm,
            distanceSource = "GPS",
            confidence = 0.0,
            endTime = endTime
        )
    }

    fun completeTripWithDistances(
        tripId: Long,
        gpsDistanceKm: Double,
        roadDistanceKm: Double,
        finalDistanceKm: Double,
        distanceSource: String,
        confidence: Double,
        endTime: Long
    ) {

        val values =
            ContentValues()

        values.put(
            "distance_km",
            finalDistanceKm
        )

        values.put(
            "gps_distance_km",
            gpsDistanceKm
        )

        values.put(
            "road_distance_km",
            roadDistanceKm
        )

        values.put(
            "distance_source",
            distanceSource
        )

        values.put(
            "matching_confidence",
            confidence
        )

        values.put(
            "end_time",
            endTime
        )

        values.put(
            "completed",
            1
        )

        writableDatabase.update(
            TABLE_TRIPS,
            values,
            "id = ?",
            arrayOf(
                tripId.toString()
            )
        )
    }

    fun calculateGpsDistance(
        tripId: Long,
        threshold: Double
    ): Double {

        return calculateGpsDistance(
            readableDatabase,
            tripId,
            threshold
        )
    }

    private fun calculateGpsDistance(
        db: SQLiteDatabase,
        tripId: Long,
        threshold: Double
    ): Double {

        val points =
            ArrayList<TrackPoint>()

        val cursor =
            db.rawQuery(
                """
                SELECT
                    id,
                    trip_id,
                    latitude,
                    longitude,
                    time,
                    speed_kmh,
                    accuracy
                FROM track_points
                WHERE trip_id = ?
                ORDER BY time ASC
                """,
                arrayOf(
                    tripId.toString()
                )
            )

        cursor.use {

            while (it.moveToNext()) {

                points.add(
                    TrackPoint(
                        id = it.getLong(0),
                        tripId = it.getLong(1),
                        latitude = it.getDouble(2),
                        longitude = it.getDouble(3),
                        time = it.getLong(4),
                        speedKmh = it.getDouble(5),
                        accuracy = it.getDouble(6)
                    )
                )
            }
        }

        if (
            points.size < 2
        ) {

            return 0.0
        }

        var coordinateMeters =
            0.0

        var speedMeters =
            0.0

        for (
            i in 1 until points.size
        ) {

            val previous =
                points[i - 1]

            val current =
                points[i]

            val seconds =
                (
                    current.time -
                        previous.time
                    ) / 1000.0

            if (
                seconds <= 0.0 ||
                seconds > MAX_TIME_GAP
            ) {

                continue
            }

            val coordinateDistance =
                haversine(
                    previous.latitude,
                    previous.longitude,
                    current.latitude,
                    current.longitude
                )

            if (
                coordinateDistance <=
                MAX_JUMP
            ) {

                val impliedSpeed =
                    (
                        coordinateDistance /
                            seconds
                        ) * 3.6

                val moving =
                    current.speedKmh >= threshold ||
                        previous.speedKmh >= threshold ||
                        impliedSpeed >= threshold

                if (moving) {

                    val minimumDistance =
                        maxOf(
                            5.0,
                            previous.accuracy * 1.5,
                            current.accuracy * 1.5
                        )

                    if (
                        coordinateDistance >=
                        minimumDistance ||
                        current.speedKmh >= threshold ||
                        previous.speedKmh >= threshold
                    ) {

                        coordinateMeters +=
                            coordinateDistance
                    }
                }
            }

            val averageSpeed =
                (
                    previous.speedKmh +
                        current.speedKmh
                    ) / 2.0

            if (
                averageSpeed >= threshold &&
                averageSpeed <= MAX_SPEED
            ) {

                speedMeters +=
                    (
                        averageSpeed /
                            3.6
                        ) * seconds
            }
        }

        val coordinateKm =
            coordinateMeters / 1000.0

        val speedKm =
            speedMeters / 1000.0

        return if (
            speedKm >= 1.0 &&
            coordinateKm <
            speedKm * 0.25
        ) {

            speedKm

        } else {

            coordinateKm
        }
    }

    fun markTripProcessing(
        tripId: Long
    ) {
    }

    fun markTripFailed(
        tripId: Long
    ) {

        val values =
            ContentValues()

        values.put(
            "completed",
            1
        )

        values.put(
            "distance_source",
            "GPS"
        )

        writableDatabase.update(
            TABLE_TRIPS,
            values,
            "id = ?",
            arrayOf(
                tripId.toString()
            )
        )
    }

    fun getAllTrips():
        List<TripSummary> {

        val result =
            mutableListOf<TripSummary>()

        val cursor =
            readableDatabase.rawQuery(
                tripSelectSql(
                    "completed = 1"
                ) +
                    """
                    ORDER BY start_time DESC
                    """,
                null
            )

        cursor.use {

            while (it.moveToNext()) {

                result.add(
                    cursorToTrip(it)
                )
            }
        }

        return result
    }

    fun getTrip(
        tripId: Long
    ): TripSummary? {

        val cursor =
            readableDatabase.rawQuery(
                tripSelectSql(
                    "id = ?"
                ),
                arrayOf(
                    tripId.toString()
                )
            )

        cursor.use {

            if (it.moveToFirst()) {

                return cursorToTrip(it)
            }
        }

        return null
    }

    fun getLastCompletedTrip():
        TripSummary? {

        val cursor =
            readableDatabase.rawQuery(
                tripSelectSql(
                    "completed = 1"
                ) +
                    """
                    ORDER BY end_time DESC
                    LIMIT 1
                    """,
                null
            )

        cursor.use {

            if (it.moveToFirst()) {

                return cursorToTrip(it)
            }
        }

        return null
    }

    fun getTotalOdometer():
        Double {

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT COALESCE(
                    SUM(distance_km),
                    0
                )
                FROM trips
                WHERE completed = 1
                """,
                null
            )

        cursor.use {

            if (it.moveToFirst()) {

                return it.getDouble(0)
            }
        }

        return 0.0
    }

    fun getTodayDistance():
        Double {

        val format =
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
            )

        val today =
            format.format(
                Date()
            )

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT COALESCE(
                    SUM(distance_km),
                    0
                )
                FROM trips
                WHERE completed = 1
                AND date(
                    start_time / 1000,
                    'unixepoch',
                    'localtime'
                ) = ?
                """,
                arrayOf(
                    today
                )
            )

        cursor.use {

            if (it.moveToFirst()) {

                return it.getDouble(0)
            }
        }

        return 0.0
    }

    /*
     * --------------------------------------------------
     * FUEL FUNCTIONS
     * --------------------------------------------------
     */

    fun addFuelRecord(
        litresAdded: Double,
        note: String = ""
    ): FuelRecord? {

        if (
            litresAdded <= 0.0
        ) {

            return null
        }

        val now =
            System.currentTimeMillis()

        val odometer =
            getTotalOdometer()

        val previousFuel =
            getCurrentFuel()

        val tankCapacity =
            getTankCapacity()

        val fuelAfter =
            (
                previousFuel +
                    litresAdded
                ).coerceAtMost(
                    tankCapacity
                )

        val values =
            ContentValues()

        values.put(
            "time",
            now
        )

        values.put(
            "odometer_km",
            odometer
        )

        values.put(
            "litres_added",
            litresAdded
        )

        values.put(
            "fuel_after_litres",
            fuelAfter
        )

        values.put(
            "note",
            note
        )

        val id =
            writableDatabase.insert(
                TABLE_FUEL,
                null,
                values
            )

        if (
            id <= 0L
        ) {

            return null
        }

        return FuelRecord(
            id = id,
            time = now,
            odometerKm = odometer,
            litresAdded = litresAdded,
            fuelAfterLitres = fuelAfter,
            note = note
        )
    }

    fun updateFuelRecord(
        id: Long,
        litresAdded: Double,
        note: String
    ): Boolean {

        if (
            litresAdded <= 0.0
        ) {

            return false
        }

        val record =
            getFuelRecord(id)
                ?: return false

        val all =
            getFuelRecords()

        val previous =
            all.filter {
                it.id != id &&
                    it.time < record.time
            }.maxByOrNull {
                it.time
            }

        val previousFuel =
            if (previous == null) {

                0.0

            } else {

                previous.fuelAfterLitres
            }

        val tankCapacity =
            getTankCapacity()

        val newFuelAfter =
            (
                previousFuel +
                    litresAdded
                ).coerceAtMost(
                    tankCapacity
                )

        val values =
            ContentValues()

        values.put(
            "litres_added",
            litresAdded
        )

        values.put(
            "fuel_after_litres",
            newFuelAfter
        )

        values.put(
            "note",
            note
        )

        val rows =
            writableDatabase.update(
                TABLE_FUEL,
                values,
                "id = ?",
                arrayOf(
                    id.toString()
                )
            )

        return rows > 0
    }

    fun deleteFuelRecord(
        id: Long
    ): Boolean {

        val rows =
            writableDatabase.delete(
                TABLE_FUEL,
                "id = ?",
                arrayOf(
                    id.toString()
                )
            )

        return rows > 0
    }

    fun getFuelRecords():
        List<FuelRecord> {

        val result =
            mutableListOf<FuelRecord>()

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT
                    id,
                    time,
                    odometer_km,
                    litres_added,
                    fuel_after_litres,
                    note
                FROM fuel_records
                ORDER BY time DESC
                """,
                null
            )

        cursor.use {

            while (it.moveToNext()) {

                result.add(
                    FuelRecord(
                        id = it.getLong(0),
                        time = it.getLong(1),
                        odometerKm = it.getDouble(2),
                        litresAdded = it.getDouble(3),
                        fuelAfterLitres = it.getDouble(4),
                        note = it.getString(5) ?: ""
                    )
                )
            }
        }

        return result
    }

    fun getFuelRecord(
        id: Long
    ): FuelRecord? {

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT
                    id,
                    time,
                    odometer_km,
                    litres_added,
                    fuel_after_litres,
                    note
                FROM fuel_records
                WHERE id = ?
                """,
                arrayOf(
                    id.toString()
                )
            )

        cursor.use {

            if (it.moveToFirst()) {

                return FuelRecord(
                    id = it.getLong(0),
                    time = it.getLong(1),
                    odometerKm = it.getDouble(2),
                    litresAdded = it.getDouble(3),
                    fuelAfterLitres = it.getDouble(4),
                    note = it.getString(5) ?: ""
                )
            }
        }

        return null
    }

    fun getLastFuelRecord():
        FuelRecord? {

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT
                    id,
                    time,
                    odometer_km,
                    litres_added,
                    fuel_after_litres,
                    note
                FROM fuel_records
                ORDER BY time DESC
                LIMIT 1
                """,
                null
            )

        cursor.use {

            if (it.moveToFirst()) {

                return FuelRecord(
                    id = it.getLong(0),
                    time = it.getLong(1),
                    odometerKm = it.getDouble(2),
                    litresAdded = it.getDouble(3),
                    fuelAfterLitres = it.getDouble(4),
                    note = it.getString(5) ?: ""
                )
            }
        }

        return null
    }

    /*
     * Estimated current fuel is calculated from
     * the most recent fuel entry and the distance
     * travelled since that entry.
     */
    fun getCurrentFuel():
        Double {

        val last =
            getLastFuelRecord()
                ?: return 0.0

        val mileage =
            getAverageMileage()

        if (
            mileage <= 0.0
        ) {

            return last.fuelAfterLitres
        }

        val currentOdometer =
            getTotalOdometer()

        val distance =
            (
                currentOdometer -
                    last.odometerKm
                ).coerceAtLeast(0.0)

        val consumed =
            distance / mileage

        return (
            last.fuelAfterLitres -
                consumed
            ).coerceAtLeast(0.0)
    }

    /*
     * Mileage is calculated from the distance between
     * fuel events and the estimated fuel consumed
     * before each subsequent refuelling.
     */
    fun getAverageMileage():
        Double {

        val records =
            getFuelRecords()
                .sortedBy {
                    it.time
                }

        if (
            records.size < 2
        ) {

            return 0.0
        }

        var totalDistance =
            0.0

        var totalFuelConsumed =
            0.0

        for (
            i in 1 until records.size
        ) {

            val previous =
                records[i - 1]

            val current =
                records[i]

            val distance =
                (
                    current.odometerKm -
                        previous.odometerKm
                    ).coerceAtLeast(0.0)

            val fuelBeforeCurrent =
                if (
                    i == 0
                ) {

                    0.0

                } else {

                    current.fuelAfterLitres -
                        current.litresAdded
                }

            val consumed =
                (
                    previous.fuelAfterLitres -
                        fuelBeforeCurrent
                    ).coerceAtLeast(0.0)

            if (
                distance > 0.0 &&
                consumed > 0.05
            ) {

                totalDistance +=
                    distance

                totalFuelConsumed +=
                    consumed
            }
        }

        if (
            totalFuelConsumed <= 0.0
        ) {

            return 0.0
        }

        return totalDistance /
            totalFuelConsumed
    }

    fun getOverallRangeKm():
        Double {

        val fuel =
            getCurrentFuel()

        val mileage =
            getAverageMileage()

        if (
            fuel <= 0.0 ||
            mileage <= 0.0
        ) {

            return 0.0
        }

        /*
         * Reserve is INCLUDED here.
         */
        return fuel * mileage
    }

    fun getRangeUntilReserveKm():
        Double {

        val fuel =
            getCurrentFuel()

        val reserve =
            getReserveFuel()

        val mileage =
            getAverageMileage()

        val usableBeforeReserve =
            (
                fuel -
                    reserve
                ).coerceAtLeast(0.0)

        if (
            mileage <= 0.0
        ) {

            return 0.0
        }

        return usableBeforeReserve *
            mileage
    }

    fun isReserveReached():
        Boolean {

        val fuel =
            getCurrentFuel()

        return fuel <=
            getReserveFuel()
    }

    private fun tripSelectSql(
        where: String
    ): String {

        return """
            SELECT
                id,
                start_time,
                end_time,
                distance_km,
                average_speed,
                max_speed,
                gps_distance_km,
                road_distance_km,
                distance_source,
                matching_confidence
            FROM trips
            WHERE $where
        """.trimIndent()
    }

    private fun cursorToTrip(
        cursor: Cursor
    ): TripSummary {

        return TripSummary(

            id =
                cursor.getLong(0),

            startTime =
                cursor.getLong(1),

            endTime =
                cursor.getLong(2),

            distanceKm =
                cursor.getDouble(3),

            averageSpeed =
                cursor.getDouble(4),

            maxSpeed =
                cursor.getDouble(5),

            gpsDistanceKm =
                cursor.getDouble(6),

            roadDistanceKm =
                cursor.getDouble(7),

            distanceSource =
                cursor.getString(8)
                    ?: "GPS",

            matchingConfidence =
                cursor.getDouble(9)
        )
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
}
