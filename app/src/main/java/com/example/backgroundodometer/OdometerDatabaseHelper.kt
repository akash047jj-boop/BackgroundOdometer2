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
    val reserveCrossed: Boolean
)

class OdometerDatabaseHelper(
    context: Context
) : SQLiteOpenHelper(
    context,
    "background_odometer.db",
    null,
    6
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
                reserve_crossed INTEGER DEFAULT 0
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
                reserve_crossed INTEGER DEFAULT 0
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
        note: String
    ): Boolean {

        if (litres <= 0.0) {
            return false
        }

        val before =
            getCurrentFuel()

        val capacity =
            getTankCapacity()

        val after =
            minOf(
                capacity,
                before + litres
            )

        val reserve =
            getReserveFuel()

        val crossed =
            before <= reserve &&
                after > reserve

        val values =
            ContentValues()

        values.put(
            "time",
            System.currentTimeMillis()
        )

        values.put(
            "odometer_km",
            getTotalOdometer()
        )

        values.put(
            "litres_added",
            litres
        )

        values.put(
            "fuel_after_litres",
            after
        )

        values.put(
            "note",
            note
        )

        values.put(
            "reserve_crossed",
            if (crossed) 1 else 0
        )

        writableDatabase.insert(
            TABLE_FUEL,
            null,
            values
        )

        clearOlderReserveCrossed()

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
                    reserve_crossed
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
                        it.getInt(6) != 0
                    )
                )
            }
        }

        return result
    }

    fun updateFuel(
        id: Long,
        litres: Double,
        note: String
    ): Boolean {

        if (litres <= 0.0) {
            return false
        }

        val values = ContentValues()
        values.put("litres_added", litres)
        values.put("note", note)

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

        val records =
            getFuelRecords()

        if (records.isEmpty()) {
            return
        }

        writableDatabase.execSQL(
            "UPDATE fuel_records SET reserve_crossed = 0"
        )

        val latest =
            records.first()

        val previous =
            records.getOrNull(1)

        if (previous == null) {
            return
        }

        val mileage =
            getAverageMileage()

        val fuelBefore =
            if (mileage > 0.0) {

                maxOf(
                    0.0,
                    previous.fuelAfterLitres -
                        (
                            latest.odometerKm -
                                previous.odometerKm
                            ) / mileage
                )

            } else {

                previous.fuelAfterLitres
            }

        val crossed =
            fuelBefore <= getReserveFuel() &&
                latest.fuelAfterLitres >
                getReserveFuel()

        if (crossed) {

            val values =
                ContentValues()

            values.put(
                "reserve_crossed",
                1
            )

            writableDatabase.update(
                TABLE_FUEL,
                values,
                "id = ?",
                arrayOf(latest.id.toString())
            )
        }
    }

    fun getCurrentFuel(): Double {

        val records =
            getFuelRecords()

        if (records.isEmpty()) {
            return 0.0
        }

        val latest =
            records.first()

        val mileage =
            getAverageMileage()

        if (mileage <= 0.0) {

            return latest.fuelAfterLitres
                .coerceIn(
                    0.0,
                    getTankCapacity()
                )
        }

        val distanceSinceFuel =
            maxOf(
                0.0,
                getTotalOdometer() -
                    latest.odometerKm
            )

        val consumed =
            distanceSinceFuel / mileage

        return (
            latest.fuelAfterLitres -
                consumed
            ).coerceIn(
                0.0,
                getTankCapacity()
            )
    }

    fun getAverageMileage(): Double {

        val records =
            getFuelRecords()
                .sortedBy { it.time }

        if (records.size < 2) {
            return 0.0
        }

        var totalDistance =
            0.0

        var totalFuel =
            0.0

        for (i in 1 until records.size) {

            val previous =
                records[i - 1]

            val current =
                records[i]

            val distance =
                current.odometerKm -
                    previous.odometerKm

            if (
                distance > 0.0 &&
                current.litresAdded > 0.0
            ) {

                totalDistance +=
                    distance

                totalFuel +=
                    current.litresAdded
            }
        }

        return if (
            totalFuel > 0.0
        ) {
            totalDistance / totalFuel
        } else {
            0.0
        }
    }

    fun getOverallRange(): Double {

        val mileage =
            getAverageMileage()

        return if (mileage > 0.0) {
            getCurrentFuel() * mileage
        } else {
            0.0
        }
    }

    fun getRangeToReserve(): Double {

        val mileage =
            getAverageMileage()

        val available =
            maxOf(
                0.0,
                getCurrentFuel() -
                    getReserveFuel()
            )

        return if (mileage > 0.0) {
            available * mileage
        } else {
            0.0
        }
    }

    fun isReserveReached(): Boolean {

        return getCurrentFuel() <=
            getReserveFuel()
    }

    fun isReserveCrossed(): Boolean {
        return getCurrentFuel() <= getReserveFuel()
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
        val db = writableDatabase
        val records = mutableListOf<Pair<Long, Double>>()
        val cursor = db.rawQuery(
            "SELECT id, litres_added FROM fuel_records ORDER BY time ASC, id ASC",
            null
        )
        cursor.use {
            while (it.moveToNext()) records.add(it.getLong(0) to it.getDouble(1))
        }
        var fuel = 0.0
        val capacity = getTankCapacity()
        db.beginTransaction()
        try {
            for ((id, litres) in records) {
                fuel = (fuel + litres).coerceIn(0.0, capacity)
                val values = ContentValues()
                values.put("fuel_after_litres", fuel)
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
