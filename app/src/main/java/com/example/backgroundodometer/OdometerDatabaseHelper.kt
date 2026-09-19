package com.example.backgroundodometer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class OdometerDatabaseHelper(
    context: Context
) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {

        private const val DATABASE_NAME =
            "background_odometer.db"

        private const val DATABASE_VERSION = 1

        private const val TABLE_SETTINGS =
            "settings"

        private const val TABLE_TRIPS =
            "trips"

        private const val TABLE_POINTS =
            "track_points"

        private const val TABLE_DAYS =
            "days"

        private const val TABLE_FUEL =
            "fuel"

        private const val SETTINGS_ID = "id"

        private const val TOTAL_ODOMETER =
            "total_odometer"

        private const val SPEED_THRESHOLD =
            "speed_threshold"

        private const val TANK_CAPACITY =
            "tank_capacity"

        private const val RESERVE_FUEL =
            "reserve_fuel"

        private const val ALERT_ENABLED =
            "alert_enabled"

        private const val ALERT_TARGET =
            "alert_target"

        private const val ALERT_FIRED =
            "alert_fired"
    }

    override fun onCreate(
        db: SQLiteDatabase
    ) {

        db.execSQL(
            """
            CREATE TABLE settings (
                id INTEGER PRIMARY KEY,
                total_odometer REAL NOT NULL DEFAULT 0,
                speed_threshold REAL NOT NULL DEFAULT 6,
                tank_capacity REAL NOT NULL DEFAULT 0,
                reserve_fuel REAL NOT NULL DEFAULT 0,
                alert_enabled INTEGER NOT NULL DEFAULT 0,
                alert_target REAL NOT NULL DEFAULT 0,
                alert_fired INTEGER NOT NULL DEFAULT 0
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
                accuracy REAL NOT NULL DEFAULT 0,
                FOREIGN KEY(trip_id)
                    REFERENCES trips(id)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE days (
                date TEXT PRIMARY KEY,
                place TEXT,
                distance REAL NOT NULL DEFAULT 0,
                fuel REAL NOT NULL DEFAULT 0,
                trip_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE fuel (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date INTEGER NOT NULL,
                litres REAL NOT NULL,
                odometer REAL NOT NULL,
                price REAL NOT NULL DEFAULT 0,
                note TEXT
            )
            """.trimIndent()
        )

        val values = ContentValues()

        values.put(
            SETTINGS_ID,
            1
        )

        values.put(
            TOTAL_ODOMETER,
            0.0
        )

        values.put(
            SPEED_THRESHOLD,
            6.0
        )

        values.put(
            TANK_CAPACITY,
            0.0
        )

        values.put(
            RESERVE_FUEL,
            0.0
        )

        values.put(
            ALERT_ENABLED,
            0
        )

        values.put(
            ALERT_TARGET,
            0.0
        )

        values.put(
            ALERT_FIRED,
            0
        )

        db.insert(
            TABLE_SETTINGS,
            null,
            values
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        // Future migrations go here.
    }

    fun getTotalOdometer(): Double {

        val cursor = readableDatabase.query(
            TABLE_SETTINGS,
            arrayOf(TOTAL_ODOMETER),
            "id = 1",
            null,
            null,
            null,
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

    fun setTotalOdometer(
        value: Double
    ) {

        val values = ContentValues()

        values.put(
            TOTAL_ODOMETER,
            maxOf(0.0, value)
        )

        writableDatabase.update(
            TABLE_SETTINGS,
            values,
            "id = 1",
            null
        )
    }

    fun addToOdometer(
        value: Double
    ) {

        if (value <= 0) return

        setTotalOdometer(
            getTotalOdometer() + value
        )
    }

    fun getSpeedThreshold(): Double {

        val cursor = readableDatabase.query(
            TABLE_SETTINGS,
            arrayOf(SPEED_THRESHOLD),
            "id = 1",
            null,
            null,
            null,
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
        value: Double
    ) {

        val values = ContentValues()

        values.put(
            SPEED_THRESHOLD,
            maxOf(0.0, value)
        )

        writableDatabase.update(
            TABLE_SETTINGS,
            values,
            "id = 1",
            null
        )
    }

    fun getTankCapacity(): Double {

        return getSettingDouble(
            TANK_CAPACITY
        )
    }

    fun setTankCapacity(
        value: Double
    ) {

        setSettingDouble(
            TANK_CAPACITY,
            value
        )
    }

    fun getReserveFuel(): Double {

        return getSettingDouble(
            RESERVE_FUEL
        )
    }

    fun setReserveFuel(
        value: Double
    ) {

        setSettingDouble(
            RESERVE_FUEL,
            value
        )
    }

    private fun getSettingDouble(
        column: String
    ): Double {

        val cursor =
            readableDatabase.query(
                TABLE_SETTINGS,
                arrayOf(column),
                "id = 1",
                null,
                null,
                null,
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

    private fun setSettingDouble(
        column: String,
        value: Double
    ) {

        val values = ContentValues()

        values.put(
            column,
            maxOf(0.0, value)
        )

        writableDatabase.update(
            TABLE_SETTINGS,
            values,
            "id = 1",
            null
        )
    }

    fun createTrip(
        startTime: Long,
        manual: Boolean = false
    ): Long {

        val values = ContentValues()

        values.put(
            "start_time",
            startTime
        )

        values.put(
            "manual",
            if (manual) 1 else 0
        )

        return writableDatabase.insert(
            TABLE_TRIPS,
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
            TABLE_POINTS,
            null,
            values
        )
    }

    fun completeTrip(
        tripId: Long,
        distance: Double,
        averageSpeed: Double,
        maxSpeed: Double,
        endTime: Long,
        routeJson: String?
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
            "route_json",
            routeJson
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

    fun getActiveTripId(): Long? {

        val cursor =
            readableDatabase.query(
                TABLE_TRIPS,
                arrayOf("id"),
                "completed = 0",
                null,
                null,
                null,
                "id DESC",
                "1"
            )

        return cursor.use {

            if (it.moveToFirst()) {
                it.getLong(0)
            } else {
                null
            }
        }
    }

    fun deleteTrip(
        tripId: Long
    ) {

        writableDatabase.delete(
            TABLE_POINTS,
            "trip_id = ?",
            arrayOf(
                tripId.toString()
            )
        )

        writableDatabase.delete(
            TABLE_TRIPS,
            "id = ?",
            arrayOf(
                tripId.toString()
            )
        )
    }

    fun addFuel(
        date: Long,
        litres: Double,
        odometer: Double,
        price: Double,
        note: String
    ): Long {

        val values = ContentValues()

        values.put(
            "date",
            date
        )

        values.put(
            "litres",
            litres
        )

        values.put(
            "odometer",
            odometer
        )

        values.put(
            "price",
            price
        )

        values.put(
            "note",
            note
        )

        return writableDatabase.insert(
            TABLE_FUEL,
            null,
            values
        )
    }

    fun getFuelBasedMileage(): Double {

        val cursor =
            readableDatabase.rawQuery(
                """
                SELECT
                    odometer,
                    litres
                FROM fuel
                ORDER BY odometer ASC
                """.trimIndent(),
                null
            )

        cursor.use {

            if (it.count < 2) {
                return 0.0
            }

            var previousOdometer =
                0.0

            var previousFuel =
                0.0

            var totalDistance =
                0.0

            var totalFuel =
                0.0

            var first = true

            while (it.moveToNext()) {

                val odometer =
                    it.getDouble(0)

                val litres =
                    it.getDouble(1)

                if (first) {

                    previousOdometer =
                        odometer

                    previousFuel =
                        litres

                    first = false

                    continue
                }

                val distance =
                    odometer -
                        previousOdometer

                if (distance > 0 &&
                    litres > 0
                ) {

                    totalDistance +=
                        distance

                    totalFuel +=
                        litres
                }

                previousOdometer =
                    odometer

                previousFuel =
                    litres
            }

            return if (
                totalFuel > 0
            ) {
                totalDistance /
                    totalFuel
            } else {
                0.0
            }
        }
    }

    fun getReserveRange(): Double {

        val mileage =
            getFuelBasedMileage()

        val reserve =
            getReserveFuel()

        if (
            mileage <= 0 ||
            reserve <= 0
        ) {
            return 0.0
        }

        return mileage *
            reserve
    }

    fun getFullTankRange(): Double {

        val mileage =
            getFuelBasedMileage()

        val tank =
            getTankCapacity()

        if (
            mileage <= 0 ||
            tank <= 0
        ) {
            return 0.0
        }

        return mileage *
            tank
    }

    fun clearTotalOnly() {

        setTotalOdometer(0.0)
    }

    fun deleteAllData() {

        writableDatabase.delete(
            TABLE_POINTS,
            null,
            null
        )

        writableDatabase.delete(
            TABLE_TRIPS,
            null,
            null
        )

        writableDatabase.delete(
            TABLE_DAYS,
            null,
            null
        )

        writableDatabase.delete(
            TABLE_FUEL,
            null,
            null
        )

        clearTotalOnly()
    }
}
