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
    val matchingConfidence: Double = 0.0,
    val assignedDate: String = "",
    val assignedPlace: String = ""
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
    val note: String,
    val reserveCrossed: Boolean,
    val fuelStatus: String = "FUEL",
    val tankLevel: String = "PARTIAL"
)

class OdometerDatabaseHelper(
    context: Context
) : SQLiteOpenHelper(
    context,
    "background_odometer.db",
    null,
    8
) {

    companion object {

        private const val TABLE_TRIPS = "trips"
        private const val TABLE_POINTS = "track_points"
        private const val TABLE_SETTINGS = "settings"
        private const val TABLE_FUEL = "fuel_records"

        private const val MAX_JUMP = 300.0
        private const val MAX_TIME_GAP = 30.0
        private const val MAX_SPEED = 180.0

        private const val DEFAULT_TANK = 15.0
        private const val DEFAULT_RESERVE = 3.2
    }

    override fun onCreate(db: SQLiteDatabase) {

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
                matching_confidence REAL DEFAULT 0,
                assigned_date TEXT DEFAULT '',
                assigned_place TEXT DEFAULT ''
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
                note TEXT DEFAULT '',
                reserve_crossed INTEGER DEFAULT 0,
                fuel_status TEXT DEFAULT 'FUEL',
                tank_level TEXT DEFAULT 'PARTIAL'
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
            DEFAULT_TANK.toString()
        )

        setSetting(
            db,
            "reserve_fuel",
            DEFAULT_RESERVE.toString()
        )

        setSetting(db, "odometer_display_offset", "0.0")
        setSetting(db, "distance_alert_enabled", "0")
        setSetting(db, "distance_alert_target", "0.0")
        setSetting(db, "distance_alert_triggered", "0")
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {

        if (oldVersion < 2) {

            if (!columnExists(db, TABLE_TRIPS, "gps_distance_km")) {
                db.execSQL(
                    "ALTER TABLE trips ADD COLUMN gps_distance_km REAL DEFAULT 0"
                )
            }

            if (!columnExists(db, TABLE_TRIPS, "road_distance_km")) {
                db.execSQL(
                    "ALTER TABLE trips ADD COLUMN road_distance_km REAL DEFAULT 0"
                )
            }

            if (!columnExists(db, TABLE_TRIPS, "distance_source")) {
                db.execSQL(
                    "ALTER TABLE trips ADD COLUMN distance_source TEXT DEFAULT 'GPS'"
                )
            }

            if (!columnExists(db, TABLE_TRIPS, "matching_confidence")) {
                db.execSQL(
                    "ALTER TABLE trips ADD COLUMN matching_confidence REAL DEFAULT 0"
                )
            }

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

        if (!columnExists(db, TABLE_TRIPS, "assigned_date")) {

            db.execSQL(
                """
                ALTER TABLE trips
                ADD COLUMN assigned_date TEXT DEFAULT ''
                """.trimIndent()
            )
        }

        if (!columnExists(db, TABLE_TRIPS, "assigned_place")) {

            db.execSQL(
                """
                ALTER TABLE trips
                ADD COLUMN assigned_place TEXT DEFAULT ''
                """.trimIndent()
            )
        }

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS fuel_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                time INTEGER NOT NULL,
                odometer_km REAL DEFAULT 0,
                litres_added REAL DEFAULT 0,
                fuel_after_litres REAL DEFAULT 0,
                note TEXT DEFAULT '',
                reserve_crossed INTEGER DEFAULT 0,
                fuel_status TEXT DEFAULT 'FUEL'
            )
            """.trimIndent()
        )

        if (
            !columnExists(
                db,
                TABLE_FUEL,
                "reserve_crossed"
            )
        ) {

            db.execSQL(
                """
                ALTER TABLE fuel_records
                ADD COLUMN reserve_crossed INTEGER DEFAULT 0
                """.trimIndent()
            )
        }

        if (!columnExists(db, TABLE_FUEL, "fuel_status")) {
            db.execSQL(
                "ALTER TABLE fuel_records ADD COLUMN fuel_status TEXT DEFAULT 'FUEL'"
            )
            db.execSQL(
                "UPDATE fuel_records SET fuel_status = 'FUEL'"
            )
        }

        // V17: normalize legacy FUEL values. Existing fuel records are treated
        // as ABOVE RESERVE unless they were already explicitly marked BELOW.
        db.execSQL(
            "UPDATE fuel_records SET fuel_status = 'ABOVE' WHERE fuel_status IS NULL OR fuel_status = '' OR fuel_status = 'FUEL'"
        )

        // V18: record whether a refuelling event brought the tank to FULL.
        if (!columnExists(db, TABLE_FUEL, "tank_level")) {
            db.execSQL(
                "ALTER TABLE fuel_records ADD COLUMN tank_level TEXT DEFAULT 'PARTIAL'"
            )
        }
        db.execSQL(
            "UPDATE fuel_records SET tank_level = 'PARTIAL' WHERE tank_level IS NULL OR tank_level = ''"
        )

        setSettingIfMissing(
            db,
            "speed_threshold",
            "6.0"
        )

        setSettingIfMissing(
            db,
            "tank_capacity",
            DEFAULT_TANK.toString()
        )

        setSettingIfMissing(
            db,
            "reserve_fuel",
            DEFAULT_RESERVE.toString()
        )
        setSettingIfMissing(db, "odometer_display_offset", "0.0")
        setSettingIfMissing(db, "distance_alert_enabled", "0")
        setSettingIfMissing(db, "distance_alert_target", "0.0")
        setSettingIfMissing(db, "distance_alert_triggered", "0")
    }

   private fun columnExists(
        db: SQLiteDatabase,
        table: String,
        column: String
    ): Boolean {

        val cursor =
            db.rawQuery(
                "PRAGMA table_info($table)",
                null
            )

        cursor.use {

            while (it.moveToNext()) {

                if (
                    it.getString(1)
                        .equals(
                            column,
                            ignoreCase = true
                        )
                ) {
                    return true
                }
            }
        }

        return false
    }

    private fun setSettingIfMissing(
        db: SQLiteDatabase,
        key: String,
        value: String
    ) {

        val cursor =
            db.rawQuery(
                """
                SELECT value
                FROM settings
                WHERE key = ?
                """,
                arrayOf(key)
            )

        cursor.use {

            if (it.moveToFirst()) {
                return
            }
        }

        setSetting(
            db,
            key,
            value
        )
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
                    SELECT id, distance_km
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
                            arrayOf(id.toString())
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
                arrayOf("speed_threshold")
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

        values.put("key", key)
        values.put("value", value)

        db.insertWithOnConflict(
            TABLE_SETTINGS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getSpeedThreshold(): Double {

        return getSetting(
            "speed_threshold",
            6.0
        )
    }

    fun getTankCapacity(): Double {

        return getSetting(
            "tank_capacity",
            DEFAULT_TANK
        )
    }

    fun getReserveFuel(): Double {

        return getSetting(
            "reserve_fuel",
            DEFAULT_RESERVE
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

    fun setFuelSettings(
        tankCapacity: Double,
        reserveFuel: Double
    ) {

        setSetting(
            writableDatabase,
            "tank_capacity",
            tankCapacity.toString()
        )

        setSetting(
            writableDatabase,
            "reserve_fuel",
            reserveFuel.toString()
        )
    }

    fun createTrip(
        startTime: Long
    ): Long {

        val values =
            ContentValues()

        values.put("start_time", startTime)
        values.put("end_time", 0)
        values.put("completed", 0)
        values.put("distance_km", 0.0)
        values.put("gps_distance_km", 0.0)
        values.put("road_distance_km", 0.0)
        values.put("distance_source", "GPS")
        values.put("matching_confidence", 0.0)
        values.put("assigned_date", "")
        values.put("assigned_place", "")

        return writableDatabase.insert(
            TABLE_TRIPS,
            null,
            values
        )
    }

    fun createManualTrip(
        date: String,
        place: String,
        distanceKm: Double,
        startTime: Long = 0L,
        endTime: Long = 0L
    ): Long {
        val parsedDate = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }

        val effectiveStart = if (startTime > 0L) startTime else parsedDate
        val effectiveEnd = if (endTime > 0L) endTime else 0L

        val values = ContentValues()
        values.put("start_time", effectiveStart)
        values.put("end_time", effectiveEnd)
        values.put("distance_km", distanceKm.coerceAtLeast(0.0))
        values.put("average_speed", 0.0)
        values.put("max_speed", 0.0)
        values.put("completed", 1)
        values.put("gps_distance_km", 0.0)
        values.put("road_distance_km", 0.0)
        values.put("distance_source", "MANUAL")
        values.put("matching_confidence", 1.0)
        values.put("assigned_date", date)
        values.put("assigned_place", place)

        return writableDatabase.insert(TABLE_TRIPS, null, values)
    }

    fun getActiveTrip(): TripSummary? {

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

        values.put("trip_id", tripId)
        values.put("latitude", latitude)
        values.put("longitude", longitude)
        values.put("time", time)
        values.put("speed_kmh", speedKmh)
        values.put("accuracy", accuracy)

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
                arrayOf(tripId.toString())
            )

        cursor.use {

            while (it.moveToNext()) {

                result.add(
                    TrackPoint(
                        it.getLong(0),
                        it.getLong(1),
                        it.getDouble(2),
                        it.getDouble(3),
                        it.getLong(4),
                        it.getDouble(5),
                        it.getDouble(6)
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

        values.put("average_speed", averageSpeed)
        values.put("max_speed", maxSpeed)
        values.put("end_time", lastTime)

        writableDatabase.update(
            TABLE_TRIPS,
            values,
            "id = ?",
            arrayOf(tripId.toString())
        )
    }

    fun completeTrip(
        tripId: Long,
        distanceKm: Double,
        routeJson: String?,
        endTime: Long
    ) {

        completeTripWithDistances(
            tripId,
            distanceKm,
            0.0,
            distanceKm,
            "GPS",
            0.0,
            endTime
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
            arrayOf(tripId.toString())
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
                arrayOf(tripId.toString())
            )

        cursor.use {

            while (it.moveToNext()) {

                points.add(
                    TrackPoint(
                        it.getLong(0),
                        it.getLong(1),
                        it.getDouble(2),
                        it.getDouble(3),
                        it.getLong(4),
                        it.getDouble(5),
                        it.getDouble(6)
                    )
                )
            }
        }

        if (points.size < 2) {
            return 0.0
        }

        var coordinateMeters = 0.0
        var speedMeters = 0.0

        for (i in 1 until points.size) {

            val previous = points[i - 1]
            val current = points[i]

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

            if (coordinateDistance <= MAX_JUMP) {

                val impliedSpeed =
                    coordinateDistance /
                        seconds *
                        3.6

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
                    averageSpeed /
                        3.6 *
                        seconds
            }
        }

        val coordinateKm =
            coordinateMeters / 1000.0

        val speedKm =
            speedMeters / 1000.0

        return if (
            speedKm >= 1.0 &&
            coordinateKm < speedKm * 0.25
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

        values.put("completed", 1)
        values.put("distance_source", "GPS")

        writableDatabase.update(
            TABLE_TRIPS,
            values,
            "id = ?",
            arrayOf(tripId.toString())
        )
    }

    fun getAllTrips(): List<TripSummary> {

        val result =
            mutableListOf<TripSummary>()

        val cursor =
            readableDatabase.rawQuery(
                tripSelectSql(
                    "completed = 1"
                ) +
                    " ORDER BY start_time DESC",
                null
            )

        cursor.use {

            while (it.moveToNext()) {
                result.add(cursorToTrip(it))
            }
        }

        return result
    }

    fun getTrip(
        tripId: Long
    ): TripSummary? {

        val cursor =
            readableDatabase.rawQuery(
                tripSelectSql("id = ?"),
                arrayOf(tripId.toString())
            )

        cursor.use {

            if (it.moveToFirst()) {
                return cursorToTrip(it)
            }
        }

        return null
    }

    fun getLastCompletedTrip(): TripSummary? {

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

    fun getTotalOdometer(): Double {

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
                return maxOf(0.0, it.getDouble(0) + getDisplayedOdometerOffset())
            }
        }

        return maxOf(0.0, getDisplayedOdometerOffset())
    }

    fun getTodayDistance(): Double {

        val today =
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
            ).format(Date())

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
                arrayOf(today)
            )

        cursor.use {

            if (it.moveToFirst()) {
                return it.getDouble(0)
            }
        }

        return 0.0
    }

    fun assignTripsToDay(
        tripIds: List<Long>,
        date: String,
        place: String
    ) {

        val db =
            writableDatabase

        db.beginTransaction()

        try {

            val values =
                ContentValues()

            values.put(
                "assigned_date",
                date
            )

            values.put(
                "assigned_place",
                place
            )

            for (id in tripIds) {

                db.update(
                    TABLE_TRIPS,
                    values,
                    "id = ?",
                    arrayOf(id.toString())
                )
            }

            db.setTransactionSuccessful()

        } finally {

            db.endTransaction()
        }
    }

    fun getAssignedDates(): List<String> {

        val result =
            mutableListOf<String>()

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT DISTINCT assigned_date
                FROM trips
                WHERE completed = 1
                AND assigned_date != ''
                ORDER BY assigned_date DESC
                """,
                null
            )

        cursor.use {

            while (it.moveToNext()) {
                result.add(it.getString(0))
            }
        }

        return result
    }

    fun getTripsForDay(
        date: String
    ): List<TripSummary> {

        val result =
            mutableListOf<TripSummary>()

        val cursor =
            readableDatabase.rawQuery(
                tripSelectSql(
                    "completed = 1 AND assigned_date = ?"
                ) +
                    " ORDER BY start_time ASC",
                arrayOf(date)
            )

        cursor.use {

            while (it.moveToNext()) {
                result.add(cursorToTrip(it))
            }
        }

        return result
    }

    fun getDayDistance(
        date: String
    ): Double {

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT COALESCE(
                    SUM(distance_km),
                    0
                )
                FROM trips
                WHERE completed = 1
                AND assigned_date = ?
                """,
                arrayOf(date)
            )

        cursor.use {

            if (it.moveToFirst()) {
                return it.getDouble(0)
            }
        }

        return 0.0
    }

    fun getDayFuel(
        date: String
    ): Double {

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT COALESCE(
                    SUM(litres_added),
                    0
                )
                FROM fuel_records
                WHERE date(
                    time / 1000,
                    'unixepoch',
                    'localtime'
                ) = ?
                """,
                arrayOf(date)
            )

        cursor.use {

            if (it.moveToFirst()) {
                return it.getDouble(0)
            }
        }

        return 0.0
    }

    fun addFuel(
        litres: Double,
        note: String,
        fuelStatus: String = "ABOVE",
        tankLevel: String = "PARTIAL"
    ): Boolean {

        if (litres <= 0.0) return false

        val before = getCurrentFuel()
        val capacity = getTankCapacity()
        val reserve = getReserveFuel()

        val normalizedStatus =
            if (fuelStatus.equals("BELOW", true)) "BELOW" else "ABOVE"
        val normalizedTank =
            if (tankLevel.equals("FULL", true)) "FULL" else "PARTIAL"

        // A fuel level is only stored when it is actually knowable.
        // We deliberately do NOT estimate fuel remaining from the estimated
        // mileage. Until a confirmed mileage reference exists, the value is unknown.
        val after: Double = when {
            normalizedTank == "FULL" -> capacity
            normalizedStatus == "BELOW" -> -1.0
            before != null -> (before + litres).coerceIn(0.0, capacity)
            else -> -1.0
        }

        val values = ContentValues()
        values.put("time", System.currentTimeMillis())
        values.put("odometer_km", getTotalOdometer())
        values.put("litres_added", litres)
        values.put("fuel_after_litres", after)
        values.put("note", note)
        values.put("reserve_crossed", 0)
        values.put("fuel_status", normalizedStatus)
        values.put("tank_level", normalizedTank)

        writableDatabase.insert(TABLE_FUEL, null, values)
        return true
    }

    fun getFuelRecords(): List<FuelRecord> {

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
                    note,
                    reserve_crossed,
                    fuel_status,
                    tank_level
                FROM fuel_records
                ORDER BY time DESC
                """,
                null
            )

        cursor.use {

            while (it.moveToNext()) {

                result.add(
                    FuelRecord(
                        it.getLong(0),
                        it.getLong(1),
                        it.getDouble(2),
                        it.getDouble(3),
                        it.getDouble(4),
                        it.getString(5) ?: "",
                        it.getInt(6) != 0,
                        when (it.getString(7)?.uppercase(Locale.getDefault())) {
                            "BELOW" -> "BELOW"
                            else -> "ABOVE"
                        },
                        when (it.getString(8)?.uppercase(Locale.getDefault())) {
                            "FULL" -> "FULL"
                            else -> "PARTIAL"
                        }
                    )
                )
            }
        }

        return result
    }

    fun updateFuel(
        id: Long,
        litres: Double,
        note: String,
        fuelStatus: String = "ABOVE",
        tankLevel: String = "PARTIAL"
    ): Boolean {

        if (litres <= 0.0) {
            return false
        }

        val markerCursor = readableDatabase.rawQuery(
            "SELECT litres_added, fuel_status FROM fuel_records WHERE id = ?",
            arrayOf(id.toString())
        )
        var isMarker = false
        markerCursor.use {
            if (it.moveToFirst()) {
                isMarker = it.getDouble(0) <= 0.0 &&
                    (it.getString(1) ?: "").equals("BELOW", true)
            }
        }
        if (isMarker) return false

        val values = ContentValues()
        values.put("litres_added", litres)
        values.put("note", note)
        values.put(
            "fuel_status",
            if (fuelStatus.equals("BELOW", true)) "BELOW" else "ABOVE"
        )
        values.put(
            "tank_level",
            if (tankLevel.equals("FULL", true)) "FULL" else "PARTIAL"
        )

        val changed = writableDatabase.update(
            TABLE_FUEL,
            values,
            "id = ?",
            arrayOf(id.toString())
        )

        if (changed > 0) {
            rebuildFuelHistory()
            recalculateLatestReserveCrossed()
        }

        return changed > 0
    }

    fun deleteFuel(
        id: Long
    ) {

        writableDatabase.delete(
            TABLE_FUEL,
            "id = ?",
            arrayOf(id.toString())
        )

        rebuildFuelHistory()
        recalculateLatestReserveCrossed()
    }

    private fun adjustLaterFuelAmounts(
        id: Long,
        delta: Double
    ) {

        val target =
            readableDatabase.rawQuery(
                """
                SELECT time
                FROM fuel_records
                WHERE id = ?
                """,
                arrayOf(id.toString())
            )

        var time =
            Long.MAX_VALUE

        target.use {

            if (it.moveToFirst()) {
                time = it.getLong(0)
            }
        }

        if (time == Long.MAX_VALUE) {
            return
        }

        writableDatabase.execSQL(
            """
            UPDATE fuel_records
            SET fuel_after_litres =
                fuel_after_litres + ?
            WHERE time > ?
            """,
            arrayOf(delta, time)
        )
    }

    private fun clearOlderReserveCrossed() {

        val latest =
            readableDatabase.rawQuery(
                """
                SELECT id
                FROM fuel_records
                ORDER BY time DESC
                LIMIT 1
                """,
                null
            )

        var id =
            -1L

        latest.use {

            if (it.moveToFirst()) {
                id = it.getLong(0)
            }
        }

        if (id <= 0) {
            return
        }

        writableDatabase.execSQL(
            """
            UPDATE fuel_records
            SET reserve_crossed =
                CASE
                    WHEN id = ? THEN reserve_crossed
                    ELSE 0
                END
            """,
            arrayOf(id)
        )
    }

    private fun recalculateLatestReserveCrossed() {
        // Reserve-crossing is now represented by the explicit rider marker or
        // by the current confirmed fuel calculation. No estimated-mileage
        // inference is used here.
        writableDatabase.execSQL("UPDATE fuel_records SET reserve_crossed = 0")
    }

    /**
     * Returns the current fuel only when it can be derived from a confirmed
     * mileage method. Estimated mileage is NEVER used for fuel remaining.
     *
     * A known reference is either:
     * - a FULL TANK fuel entry (fuel = tank capacity), or
     * - a rider-confirmed BELOW-RESERVE marker (fuel = reserve).
     *
     * Fuel events after the reference are replayed. A PARTIAL + BELOW entry
     * makes the exact fuel level unknown, so null is returned until another
     * exact reference (FULL or confirmed marker) is created.
     */
    fun getCurrentFuel(): Double? {
        if (getConfirmedMileage() <= 0.0) return null

        val records = getFuelRecords().sortedBy { it.time }
        if (records.isEmpty()) return null

        val capacity = getTankCapacity()
        val reserve = getReserveFuel()

        var referenceIndex = -1
        var fuel = 0.0

        for (i in records.indices) {
            val record = records[i]
            val isMarker = record.litresAdded <= 0.0 &&
                record.fuelStatus.equals("BELOW", true)

            when {
                isMarker -> {
                    referenceIndex = i
                    fuel = reserve
                }
                record.tankLevel.equals("FULL", true) -> {
                    referenceIndex = i
                    fuel = capacity
                }
            }
        }

        if (referenceIndex < 0) return null

        for (i in (referenceIndex + 1) until records.size) {
            val record = records[i]
            val isMarker = record.litresAdded <= 0.0 &&
                record.fuelStatus.equals("BELOW", true)

            when {
                isMarker -> {
                    fuel = reserve
                }
                record.tankLevel.equals("FULL", true) -> {
                    fuel = capacity
                }
                record.fuelStatus.equals("BELOW", true) -> {
                    // We know only that it is below reserve, not the exact litres.
                    return null
                }
                else -> {
                    fuel += record.litresAdded
                    fuel = fuel.coerceIn(0.0, capacity)
                }
            }
        }

        val latestReference = records.lastOrNull() ?: return null
        val distanceSinceLatestEvent = maxOf(
            0.0,
            getTotalOdometer() - latestReference.odometerKm
        )

        // Consume fuel from the reconstructed level using CONFIRMED mileage.
        val consumed = distanceSinceLatestEvent / getConfirmedMileage()
        return (fuel - consumed).coerceIn(0.0, capacity)
    }

    /**
     * Confirmed mileage uses two reliable reference methods:
     * 1) FULL-TANK -> FULL-TANK cycles.
     * 2) Rider-confirmed BELOW-RESERVE -> BELOW-RESERVE cycles.
     *
     * The two methods are combined using total distance / total fuel.
     */
    fun getConfirmedMileage(): Double {
        val records = getFuelRecords().sortedBy { it.time }

        fun mileageBetweenReferences(references: List<FuelRecord>): Double {
            var totalDistance = 0.0
            var totalFuel = 0.0

            for (i in 1 until references.size) {
                val previous = references[i - 1]
                val current = references[i]
                val distance = current.odometerKm - previous.odometerKm
                if (distance <= 0.0) continue

                val fuelAdded = records
                    .filter {
                        it.time > previous.time &&
                            it.time <= current.time &&
                            it.litresAdded > 0.0
                    }
                    .sumOf { it.litresAdded }

                if (fuelAdded > 0.0) {
                    totalDistance += distance
                    totalFuel += fuelAdded
                }
            }

            return if (totalFuel > 0.0) totalDistance / totalFuel else 0.0
        }

        // Prefer full-tank-to-full-tank measurements. They are the clearest
        // confirmed fuel-consumption method and do not require reaching reserve.
        val fullReferences = records.filter {
            it.litresAdded > 0.0 && it.tankLevel.equals("FULL", true)
        }
        val fullMileage = mileageBetweenReferences(fullReferences)
        if (fullMileage > 0.0) return fullMileage

        // If no full-tank cycle exists, use rider-confirmed reserve-to-reserve
        // cycles. These are independent of the estimated mileage calculation.
        val reserveReferences = records.filter {
            it.litresAdded <= 0.0 && it.fuelStatus.equals("BELOW", true)
        }
        return mileageBetweenReferences(reserveReferences)
    }

    /**
     * Estimated mileage is available before a confirmed cycle exists.
     * It uses consecutive refuelling events as a running estimate:
     * distance travelled between refuels / litres added at the later refuel.
     * It is intentionally labelled ESTIMATED in the UI.
     */
    /**
     * Estimated mileage is the simple running estimate requested by the rider:
     * total displayed odometer distance divided by total fuel entered.
     * Below-reserve marker records have zero litres and therefore do not affect it.
     */
    fun getEstimatedMileage(): Double {
        val totalFuelEntered =
            getFuelRecords()
                .filter { it.litresAdded > 0.0 }
                .sumOf { it.litresAdded }

        val totalDistance = getTotalOdometer()

        return if (totalFuelEntered > 0.0 && totalDistance > 0.0) {
            totalDistance / totalFuelEntered
        } else {
            0.0
        }
    }

    fun getAverageMileage(): Double = getConfirmedMileage()

    fun getBestMileage(): Double {
        return getConfirmedMileage()
    }

    fun getOverallRange(): Double {
        val mileage = getConfirmedMileage()
        val fuel = getCurrentFuel() ?: return 0.0
        return if (mileage > 0.0) fuel * mileage else 0.0
    }

    fun getRangeToReserve(): Double {
        val mileage = getConfirmedMileage()
        val fuel = getCurrentFuel() ?: return 0.0
        val available = maxOf(0.0, fuel - getReserveFuel())
        return if (mileage > 0.0) available * mileage else 0.0
    }

    fun hasCurrentBelowReserveMarker(): Boolean {
        val latest = getFuelRecords().firstOrNull() ?: return false
        return latest.litresAdded <= 0.0 &&
            latest.fuelStatus.equals("BELOW", true)
    }

    /** Removes only the latest rider-created below-reserve marker. */
    fun removeCurrentBelowReserveMarker(): Boolean {
        val latest = getFuelRecords().firstOrNull() ?: return false
        if (latest.litresAdded > 0.0 || !latest.fuelStatus.equals("BELOW", true)) {
            return false
        }
        val deleted = writableDatabase.delete(
            TABLE_FUEL,
            "id = ?",
            arrayOf(latest.id.toString())
        ) > 0
        if (deleted) {
            rebuildFuelHistory()
            recalculateLatestReserveCrossed()
        }
        return deleted
    }

    /**
     * Saves a below-reserve point when the rider confirms it. This is deliberately
     * allowed even when the calculated fuel estimate is still above reserve,
     * because the rider's manual observation takes priority.
     */
    fun markCurrentFuelBelowReserve(): Boolean {
        if (hasCurrentBelowReserveMarker()) return false

        val now = System.currentTimeMillis()
        val currentFuel = getCurrentFuel()
        val markerFuel = currentFuel?.let { minOf(it, getReserveFuel()) } ?: getReserveFuel()
        val values = ContentValues()
        values.put("time", now)
        values.put("odometer_km", getTotalOdometer())
        values.put("litres_added", 0.0)
        values.put("fuel_after_litres", markerFuel)
        values.put("note", "Below-reserve mileage marker (rider confirmed)")
        values.put("reserve_crossed", 0)
        values.put("fuel_status", "BELOW")
        return writableDatabase.insert(TABLE_FUEL, null, values) != -1L
    }

    /** True when the calculated fuel estimate has reached the configured reserve. */
    fun isReserveReachedByCalculation(): Boolean {
        val fuel = getCurrentFuel() ?: return false
        return fuel <= getReserveFuel()
    }

    /**
     * User-selected status is primary, but automatic detection takes over once
     * the calculated fuel level actually reaches reserve.
     */
    fun getCurrentFuelStatus(): String {
        val latest = getFuelRecords().firstOrNull()
        if (latest?.fuelStatus?.equals("BELOW", true) == true) return "BELOW"
        return if (isReserveReachedByCalculation()) "BELOW" else "ABOVE"
    }

    fun isReserveReached(): Boolean = getCurrentFuelStatus() == "BELOW"

    fun isReserveCrossed(): Boolean {
        return getCurrentFuelStatus() == "BELOW"
    }

    fun setSpeedThreshold(value: Double) {

        if (value <= 0.0) return

        setSetting(
            writableDatabase,
            "speed_threshold",
            value.toString()
        )
    }

    fun getCurrentSpeed(): Double {
        val trip = getActiveTrip() ?: return 0.0
        val points = getTrackPoints(trip.id)
        return points.lastOrNull()?.speedKmh ?: 0.0
    }

    fun getAverageSpeed(): Double {
        val trip = getActiveTrip()
        if (trip != null) {
            val points = getTrackPoints(trip.id)
            if (points.isNotEmpty()) return points.map { it.speedKmh }.average()
        }
        return getAllTrips().map { it.averageSpeed }
            .filter { it > 0.0 }
            .let { if (it.isEmpty()) 0.0 else it.average() }
    }

    fun getMaximumSpeed(): Double {
        val active = getActiveTrip()
        val activeMax = active?.let { getTrackPoints(it.id).maxOfOrNull { p -> p.speedKmh } ?: 0.0 } ?: 0.0
        val completedMax = getAllTrips().maxOfOrNull { it.maxSpeed } ?: 0.0
        return maxOf(activeMax, completedMax)
    }

    fun getMileage(): Double = getAverageMileage()

    fun getEstimatedRange(): Double = getOverallRange()

    fun getRangeUntilReserve(): Double = getRangeToReserve()

    fun hasReserveBeenCrossed(): Boolean = isReserveCrossed()

    fun getDisplayedOdometerOffset(): Double =
        getSetting("odometer_display_offset", 0.0)

    fun clearDisplayedOdometer() {
        val rawTotal = getRawTripTotal()
        setSetting(
            writableDatabase,
            "odometer_display_offset",
            (-rawTotal).toString()
        )
    }

    private fun getRawTripTotal(): Double {
        val cursor = readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(distance_km), 0) FROM trips WHERE completed = 1",
            null
        )
        cursor.use {
            if (it.moveToFirst()) return it.getDouble(0)
        }
        return 0.0
    }

    fun updateTripAssignment(
        tripId: Long,
        date: String,
        place: String
    ): Boolean {
        val values = ContentValues().apply {
            put("assigned_date", date)
            put("assigned_place", place)
        }
        return writableDatabase.update(
            TABLE_TRIPS,
            values,
            "id = ? AND completed = 1",
            arrayOf(tripId.toString())
        ) > 0
    }

    fun updateManualTrip(
        tripId: Long,
        date: String,
        place: String,
        distanceKm: Double,
        startTime: Long,
        endTime: Long
    ): Boolean {
        val existing = getTrip(tripId) ?: return false
        if (existing.distanceSource != "MANUAL") return false

        val parsedDate = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)?.time ?: existing.startTime
        } catch (_: Exception) {
            existing.startTime
        }

        val finalStart = if (startTime > 0L) startTime else parsedDate
        val finalEnd = if (endTime > 0L) endTime else 0L

        val values = ContentValues().apply {
            put("start_time", finalStart)
            put("end_time", finalEnd)
            put("distance_km", distanceKm.coerceAtLeast(0.0))
            put("assigned_date", date)
            put("assigned_place", place)
            put("gps_distance_km", 0.0)
            put("road_distance_km", 0.0)
            put("distance_source", "MANUAL")
            put("matching_confidence", 1.0)
        }

        return writableDatabase.update(
            TABLE_TRIPS,
            values,
            "id = ? AND completed = 1",
            arrayOf(tripId.toString())
        ) > 0
    }

    fun updateDayRecord(
        oldDate: String,
        newDate: String,
        place: String
    ): Int {
        val values = ContentValues().apply {
            put("assigned_date", newDate)
            put("assigned_place", place)
        }
        return writableDatabase.update(
            TABLE_TRIPS,
            values,
            "completed = 1 AND assigned_date = ?",
            arrayOf(oldDate)
        )
    }

    fun deleteDayRecord(date: String): Int {
        val values = ContentValues().apply {
            put("assigned_date", "")
            put("assigned_place", "")
        }
        return writableDatabase.update(
            TABLE_TRIPS,
            values,
            "completed = 1 AND assigned_date = ?",
            arrayOf(date)
        )
    }

    fun deleteTrip(tripId: Long): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            db.delete(TABLE_POINTS, "trip_id = ?", arrayOf(tripId.toString()))
            val deleted = db.delete(TABLE_TRIPS, "id = ?", arrayOf(tripId.toString())) > 0
            db.setTransactionSuccessful()
            deleted
        } finally {
            db.endTransaction()
        }
    }

    fun isDistanceAlertEnabled(): Boolean =
        getSetting("distance_alert_enabled", 0.0) > 0.5

    fun getDistanceAlertTarget(): Double =
        getSetting("distance_alert_target", 0.0)

    fun setDistanceAlert(enabled: Boolean, target: Double) {
        setSetting(
            writableDatabase,
            "distance_alert_enabled",
            if (enabled) "1" else "0"
        )
        setSetting(
            writableDatabase,
            "distance_alert_target",
            target.coerceAtLeast(0.0).toString()
        )
    }

    fun isDistanceAlertTriggered(): Boolean =
        getSetting("distance_alert_triggered", 0.0) > 0.5

    fun resetDistanceAlertTrigger() {
        setSetting(writableDatabase, "distance_alert_triggered", "0")
    }

    fun checkDistanceAlert(): Boolean {
        if (!isDistanceAlertEnabled()) return false
        val target = getDistanceAlertTarget()
        if (target <= 0.0) return false
        if (getTotalOdometer() >= target && !isDistanceAlertTriggered()) {
            setSetting(writableDatabase, "distance_alert_triggered", "1")
            return true
        }
        return false
    }

    fun deleteAllData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_POINTS, null, null)
            db.delete(TABLE_TRIPS, null, null)
            db.delete(TABLE_FUEL, null, null)
            db.delete(TABLE_SETTINGS, null, null)
            setSetting(db, "speed_threshold", "6.0")
            setSetting(db, "tank_capacity", DEFAULT_TANK.toString())
            setSetting(db, "reserve_fuel", DEFAULT_RESERVE.toString())
            setSetting(db, "odometer_display_offset", "0.0")
            setSetting(db, "distance_alert_enabled", "0")
            setSetting(db, "distance_alert_target", "0.0")
            setSetting(db, "distance_alert_triggered", "0")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun rebuildFuelHistory() {
        // Do not invent fuel remaining values. Only exact references are stored:
        // FULL TANK and rider-confirmed BELOW-RESERVE markers. Other entries are
        // marked unknown (-1) until a confirmed reference can be used.
        val db = writableDatabase
        val capacity = getTankCapacity()
        val reserve = getReserveFuel()
        val cursor = db.rawQuery(
            "SELECT id, litres_added, fuel_status, tank_level FROM fuel_records ORDER BY time ASC, id ASC",
            null
        )
        val updates = mutableListOf<Pair<Long, Double>>()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val litres = it.getDouble(1)
                val status = it.getString(2) ?: "ABOVE"
                val tankLevel = it.getString(3) ?: "PARTIAL"
                val value = when {
                    litres <= 0.0 && status.equals("BELOW", true) -> reserve
                    tankLevel.equals("FULL", true) -> capacity
                    else -> -1.0
                }
                updates.add(id to value)
            }
        }
        db.beginTransaction()
        try {
            for ((id, value) in updates) {
                val values = ContentValues()
                values.put("fuel_after_litres", value)
                db.update(TABLE_FUEL, values, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
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
                matching_confidence,
                assigned_date,
                assigned_place
            FROM trips
            WHERE $where
        """.trimIndent()
    }

    private fun cursorToTrip(
        cursor: Cursor
    ): TripSummary {

        return TripSummary(
            id = cursor.getLong(0),
            startTime = cursor.getLong(1),
            endTime = cursor.getLong(2),
            distanceKm = cursor.getDouble(3),
            averageSpeed = cursor.getDouble(4),
            maxSpeed = cursor.getDouble(5),
            gpsDistanceKm = cursor.getDouble(6),
            roadDistanceKm = cursor.getDouble(7),
            distanceSource =
                cursor.getString(8) ?: "GPS",
            matchingConfidence =
                cursor.getDouble(9),
            assignedDate =
                cursor.getString(10) ?: "",
            assignedPlace =
                cursor.getString(11) ?: ""
        )
    }

    private fun haversine(
        lat1Value: Double,
        lon1Value: Double,
        lat2Value: Double,
        lon2Value: Double
    ): Double {

        val earth = 6_371_000.0

        val lat1 =
            Math.toRadians(lat1Value)

        val lat2 =
            Math.toRadians(lat2Value)

        val dLat =
            Math.toRadians(
                lat2Value - lat1Value
            )

        val dLon =
            Math.toRadians(
                lon2Value - lon1Value
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

    override fun onOpen(
        db: SQLiteDatabase
    ) {

        super.onOpen(db)

        try {
            db.execSQL(
                "PRAGMA foreign_keys=ON"
            )
        } catch (_: Exception) {
        }
    }
}
