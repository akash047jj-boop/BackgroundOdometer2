package com.example.backgroundodometer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TripSummary(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val distanceKm: Double,
    val averageSpeed: Double,
    val maxSpeed: Double,
    val completed: Boolean
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

class OdometerDatabaseHelper(
    context: Context
) : SQLiteOpenHelper(
    context,
    "background_odometer.db",
    null,
    2
) {

    override fun onCreate(
        db: SQLiteDatabase
    ) {

        db.execSQL(
            """
            CREATE TABLE settings (
                id INTEGER PRIMARY KEY,
                total_odometer REAL NOT NULL DEFAULT 0,
                speed_threshold REAL NOT NULL DEFAULT 6,
                distance_alert_enabled INTEGER NOT NULL DEFAULT 0,
                distance_alert_target REAL NOT NULL DEFAULT 0,
                distance_alert_fired INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE trips (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                distance REAL NOT NULL DEFAULT 0,
                average_speed REAL NOT NULL DEFAULT 0,
                max_speed REAL NOT NULL DEFAULT 0,
                route_json TEXT,
                manual INTEGER NOT NULL DEFAULT 0,
                completed INTEGER NOT NULL DEFAULT 0,
                assigned_date TEXT,
                assigned_place TEXT
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
                speed REAL NOT NULL DEFAULT 0,
                accuracy REAL NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        val values =
            ContentValues()

        values.put(
            "id",
            1
        )

        values.put(
            "total_odometer",
            0.0
        )

        values.put(
            "speed_threshold",
            6.0
        )

        values.put(
            "distance_alert_enabled",
            0
        )

        values.put(
            "distance_alert_target",
            0.0
        )

        values.put(
            "distance_alert_fired",
            0
        )

        db.insert(
            "settings",
            null,
            values
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {

        /*
         * V2 already contained the required tables.
         * This is intentionally left safe for existing
         * V2 installations.
         */
    }

    // =====================================================
    // ODOMETER
    // =====================================================

    fun getTotalOdometer(): Double {

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT total_odometer
                FROM settings
                WHERE id=1
                """.trimIndent(),
                null
            )

        return cursor.use {

            if (it.moveToFirst()) {
                it.getDouble(0)
            } else {
                0.0
            }
        }
    }

    @Synchronized
    fun addToOdometer(
        distanceKm: Double
    ) {

        if (distanceKm <= 0.0) {
            return
        }

        val current =
            getTotalOdometer()

        val values =
            ContentValues()

        values.put(
            "total_odometer",
            current + distanceKm
        )

        writableDatabase.update(
            "settings",
            values,
            "id=1",
            null
        )
    }

    fun clearTotalOdometer() {

        val values =
            ContentValues()

        values.put(
            "total_odometer",
            0.0
        )

        writableDatabase.update(
            "settings",
            values,
            "id=1",
            null
        )
    }

    // =====================================================
    // SPEED THRESHOLD
    // =====================================================

    fun getSpeedThreshold(): Double {

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT speed_threshold
                FROM settings
                WHERE id=1
                """.trimIndent(),
                null
            )

        return cursor.use {

            if (it.moveToFirst()) {
                it.getDouble(0)
            } else {
                6.0
            }
        }
    }

    fun setSpeedThreshold(
        threshold: Double
    ) {

        val values =
            ContentValues()

        values.put(
            "speed_threshold",
            maxOf(
                0.0,
                threshold
            )
        )

        writableDatabase.update(
            "settings",
            values,
            "id=1",
            null
        )
    }

    // =====================================================
    // TRIPS
    // =====================================================

    @Synchronized
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
            "completed",
            0
        )

        values.put(
            "manual",
            0
        )

        return writableDatabase.insert(
            "trips",
            null,
            values
        )
    }

    fun getActiveTrip(): Long? {

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT id
                FROM trips
                WHERE completed=0
                AND manual=0
                ORDER BY id DESC
                LIMIT 1
                """.trimIndent(),
                null
            )

        return cursor.use {

            if (it.moveToFirst()) {
                it.getLong(0)
            } else {
                null
            }
        }
    }

    fun completeTrip(
        tripId: Long,
        distanceKm: Double,
        averageSpeed: Double,
        maxSpeed: Double,
        endTime: Long
    ) {

        val values =
            ContentValues()

        values.put(
            "distance",
            maxOf(
                0.0,
                distanceKm
            )
        )

        values.put(
            "average_speed",
            maxOf(
                0.0,
                averageSpeed
            )
        )

        values.put(
            "max_speed",
            maxOf(
                0.0,
                maxSpeed
            )
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
            "trips",
            values,
            "id=?",
            arrayOf(
                tripId.toString()
            )
        )
    }

    fun getTrip(
        tripId: Long
    ): TripSummary? {

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT
                    id,
                    start_time,
                    end_time,
                    distance,
                    average_speed,
                    max_speed,
                    completed
                FROM trips
                WHERE id=?
                LIMIT 1
                """.trimIndent(),
                arrayOf(
                    tripId.toString()
                )
            )

        return cursor.use {

            if (!it.moveToFirst()) {
                return null
            }

            TripSummary(
                id = it.getLong(0),
                startTime = it.getLong(1),
                endTime =
                    if (it.isNull(2)) {
                        null
                    } else {
                        it.getLong(2)
                    },
                distanceKm =
                    it.getDouble(3),
                averageSpeed =
                    it.getDouble(4),
                maxSpeed =
                    it.getDouble(5),
                completed =
                    it.getInt(6) == 1
            )
        }
    }

    fun getAllTrips(): List<TripSummary> {

        val result =
            mutableListOf<TripSummary>()

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT
                    id,
                    start_time,
                    end_time,
                    distance,
                    average_speed,
                    max_speed,
                    completed
                FROM trips
                WHERE manual=0
                ORDER BY start_time DESC
                """.trimIndent(),
                null
            )

        cursor.use {

            while (it.moveToNext()) {

                result.add(
                    TripSummary(
                        id = it.getLong(0),
                        startTime = it.getLong(1),
                        endTime =
                            if (it.isNull(2)) {
                                null
                            } else {
                                it.getLong(2)
                            },
                        distanceKm =
                            it.getDouble(3),
                        averageSpeed =
                            it.getDouble(4),
                        maxSpeed =
                            it.getDouble(5),
                        completed =
                            it.getInt(6) == 1
                    )
                )
            }
        }

        return result
    }

    fun getTodayDistance(): Double {

        val start =
            getStartOfToday()

        val end =
            start + 24L * 60L * 60L * 1000L

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT COALESCE(
                    SUM(distance),
                    0
                )
                FROM trips
                WHERE completed=1
                AND start_time>=?
                AND start_time<?
                """.trimIndent(),
                arrayOf(
                    start.toString(),
                    end.toString()
                )
            )

        return cursor.use {

            if (it.moveToFirst()) {
                it.getDouble(0)
            } else {
                0.0
            }
        }
    }

    fun getLastCompletedTrip():
        TripSummary? {

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT
                    id,
                    start_time,
                    end_time,
                    distance,
                    average_speed,
                    max_speed,
                    completed
                FROM trips
                WHERE completed=1
                AND manual=0
                ORDER BY end_time DESC
                LIMIT 1
                """.trimIndent(),
                null
            )

        return cursor.use {

            if (!it.moveToFirst()) {
                return null
            }

            TripSummary(
                id = it.getLong(0),
                startTime = it.getLong(1),
                endTime =
                    if (it.isNull(2)) {
                        null
                    } else {
                        it.getLong(2)
                    },
                distanceKm =
                    it.getDouble(3),
                averageSpeed =
                    it.getDouble(4),
                maxSpeed =
                    it.getDouble(5),
                completed =
                    it.getInt(6) == 1
            )
        }
    }

    // =====================================================
    // TRACK POINTS
    // =====================================================

    fun addTrackPoint(
        tripId: Long,
        latitude: Double,
        longitude: Double,
        time: Long,
        speed: Double,
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
            "speed",
            speed
        )

        values.put(
            "accuracy",
            accuracy
        )

        writableDatabase.insert(
            "track_points",
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
                    speed,
                    accuracy
                FROM track_points
                WHERE trip_id=?
                ORDER BY time ASC
                """.trimIndent(),
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

    // =====================================================
    // DISTANCE ALERT
    // =====================================================

    fun setDistanceAlert(
        enabled: Boolean,
        target: Double
    ) {

        val values =
            ContentValues()

        values.put(
            "distance_alert_enabled",
            if (enabled) 1 else 0
        )

        values.put(
            "distance_alert_target",
            maxOf(0.0, target)
        )

        values.put(
            "distance_alert_fired",
            0
        )

        writableDatabase.update(
            "settings",
            values,
            "id=1",
            null
        )
    }

    fun isDistanceAlertEnabled():
        Boolean {

        return getIntSetting(
            "distance_alert_enabled"
        ) == 1
    }

    fun getDistanceAlertTarget():
        Double {

        return getDoubleSetting(
            "distance_alert_target"
        )
    }

    fun isDistanceAlertFired():
        Boolean {

        return getIntSetting(
            "distance_alert_fired"
        ) == 1
    }

    fun markDistanceAlertFired() {

        val values =
            ContentValues()

        values.put(
            "distance_alert_fired",
            1
        )

        writableDatabase.update(
            "settings",
            values,
            "id=1",
            null
        )
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private fun getIntSetting(
        column: String
    ): Int {

        val cursor =
            readableDatabase.rawQuery(
                "SELECT $column FROM settings WHERE id=1",
                null
            )

        return cursor.use {

            if (it.moveToFirst()) {
                it.getInt(0)
            } else {
                0
            }
        }
    }

    private fun getDoubleSetting(
        column: String
    ): Double {

        val cursor =
            readableDatabase.rawQuery(
                "SELECT $column FROM settings WHERE id=1",
                null
            )

        return cursor.use {

            if (it.moveToFirst()) {
                it.getDouble(0)
            } else {
                0.0
            }
        }
    }

    private fun getStartOfToday(): Long {

        val format =
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
            )

        val today =
            format.format(
                Date()
            )

        return format.parse(
            today
        )?.time ?: System.currentTimeMillis()
    }
}
