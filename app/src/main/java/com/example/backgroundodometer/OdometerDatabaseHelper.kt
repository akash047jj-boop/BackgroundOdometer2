package com.example.backgroundodometer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class OdometerDatabaseHelper(
    context: Context
) : SQLiteOpenHelper(
    context,
    "background_odometer.db",
    null,
    1
) {

    override fun onCreate(db: SQLiteDatabase) {

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

        val values = ContentValues()

        values.put("id", 1)
        values.put("total_odometer", 0.0)
        values.put("speed_threshold", 6.0)
        values.put("distance_alert_enabled", 0)
        values.put("distance_alert_target", 0.0)
        values.put("distance_alert_fired", 0)

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
    }

    fun getTotalOdometer(): Double {

        val cursor = readableDatabase.rawQuery(
            "SELECT total_odometer FROM settings WHERE id=1",
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

    fun addToOdometer(
        distanceKm: Double
    ) {

        if (distanceKm <= 0) return

        val newValue =
            getTotalOdometer() + distanceKm

        val values = ContentValues()

        values.put(
            "total_odometer",
            newValue
        )

        writableDatabase.update(
            "settings",
            values,
            "id=1",
            null
        )
    }

    fun clearTotalOdometer() {

        val values = ContentValues()

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

    fun getSpeedThreshold(): Double {

        val cursor = readableDatabase.rawQuery(
            "SELECT speed_threshold FROM settings WHERE id=1",
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

        val values = ContentValues()

        values.put(
            "speed_threshold",
            maxOf(0.0, threshold)
        )

        writableDatabase.update(
            "settings",
            values,
            "id=1",
            null
        )
    }

    fun createTrip(
        startTime: Long
    ): Long {

        val values = ContentValues()

        values.put(
            "start_time",
            startTime
        )

        return writableDatabase.insert(
            "trips",
            null,
            values
        )
    }

    fun addTrackPoint(
        tripId: Long,
        latitude: Double,
        longitude: Double,
        time: Long,
        speed: Double,
        accuracy: Double
    ) {

        val values = ContentValues()

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

    fun completeTrip(
        tripId: Long,
        distance: Double,
        averageSpeed: Double,
        maxSpeed: Double,
        endTime: Long
    ) {

        val values = ContentValues()

        values.put(
            "distance",
            distance
        )

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

    fun getActiveTrip(): Long? {

        val cursor = readableDatabase.rawQuery(
            """
            SELECT id
            FROM trips
            WHERE completed=0
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

    fun setDistanceAlert(
        enabled: Boolean,
        target: Double
    ) {

        val values = ContentValues()

        values.put(
            "distance_alert_enabled",
            if (enabled) 1 else 0
        )

        values.put(
            "distance_alert_target",
            target
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

    fun isDistanceAlertEnabled(): Boolean {

        return getIntSetting(
            "distance_alert_enabled"
        ) == 1
    }

    fun getDistanceAlertTarget(): Double {

        return getDoubleSetting(
            "distance_alert_target"
        )
    }

    fun isDistanceAlertFired(): Boolean {

        return getIntSetting(
            "distance_alert_fired"
        ) == 1
    }

    fun markDistanceAlertFired() {

        val values = ContentValues()

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

    private fun getIntSetting(
        column: String
    ): Int {

        val cursor = readableDatabase.rawQuery(
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

        val cursor = readableDatabase.rawQuery(
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
}
