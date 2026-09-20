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

class OdometerDatabaseHelper(
    context: Context
) : SQLiteOpenHelper(
    context,
    "background_odometer.db",
    null,
    2
) {

    companion object {

        private const val TABLE_TRIPS =
            "trips"

        private const val TABLE_POINTS =
            "track_points"

        private const val TABLE_SETTINGS =
            "settings"
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

        setSetting(
            db,
            "speed_threshold",
            "6.0"
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

            /*
             * Existing V6 trips already have distance_km.
             * Preserve that distance as the GPS distance.
             */
            db.execSQL(
                """
                UPDATE trips
                SET gps_distance_km = distance_km
                WHERE gps_distance_km = 0
                """.trimIndent()
            )
        }
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

    fun getSpeedThreshold():
        Double {

        val cursor =
            readableDatabase.rawQuery(
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
                """
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
                WHERE completed = 0
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

    /*
     * Existing API preserved.
     */
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

    /*
     * V7 authoritative completion.
     */
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

    fun markTripProcessing(
        tripId: Long
    ) {
        /*
         * Reserved for future UI status.
         */
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
                """
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
                WHERE completed = 1
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
                """
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
                WHERE id = ?
                """,
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
                """
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
                WHERE completed = 1
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

    private fun cursorToTrip(
        cursor: android.database.Cursor
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
}
