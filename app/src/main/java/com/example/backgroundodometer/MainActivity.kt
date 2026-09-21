package com.example.backgroundodometer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var database:
        OdometerDatabaseHelper

    private lateinit var totalText:
        TextView

    private lateinit var todayText:
        TextView

    private lateinit var speedText:
        TextView

    private lateinit var averageText:
        TextView

    private lateinit var maxText:
        TextView

    private lateinit var statusText:
        TextView

    private lateinit var lastTripText:
        TextView

    private lateinit var trackingButton:
        TextView

    private lateinit var fuelText:
        TextView

    private lateinit var mileageText:
        TextView

    private lateinit var rangeText:
        TextView

    private lateinit var reserveText:
        TextView

    private lateinit var reserveButton:
        TextView

    private var trackingActive =
        false

    private val preferences by lazy {

        getSharedPreferences(
            "background_odometer",
            MODE_PRIVATE
        )
    }

    companion object {

        private const val TIFFANY =
            "#00BCD4"

        private const val DARK =
            "#151515"

        private const val RED =
            "#D32F2F"

        private const val GREEN =
            "#1B5E20"

        private const val REQUEST_LOCATION =
            100

        private const val REQUEST_NOTIFICATIONS =
            101

        private const val PREF_AUTO_TRACKING =
            "auto_tracking_enabled"

        private const val PREF_BACKGROUND_HINT =
            "background_location_hint_shown"
    }

    private val serviceReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                if (
                    intent?.action ==
                    LocationTrackingService.ACTION_SPEED_UPDATE
                ) {

                    val speed =
                        intent.getDoubleExtra(
                            LocationTrackingService.EXTRA_SPEED,
                            0.0
                        )

                    speedText.text =
                        "%.1f km/h".format(
                            speed
                        )

                    refreshData()

                    return
                }

                if (
                    intent?.action ==
                    LocationTrackingService.ACTION_STATUS_UPDATE
                ) {

                    val status =
                        intent.getStringExtra(
                            LocationTrackingService.EXTRA_STATUS
                        ) ?: return

                    updateStatus(
                        status
                    )

                    val speed =
                        intent.getDoubleExtra(
                            LocationTrackingService.EXTRA_SPEED,
                            0.0
                        )

                    if (
                        speed >= 0.0
                    ) {

                        speedText.text =
                            "%.1f km/h".format(
                                speed
                            )
                    }

                    refreshData()
                }
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        database =
            OdometerDatabaseHelper(this)

        buildInterface()

        requestLocationPermission()

        refreshData()
    }

    private fun buildInterface() {

        val scroll =
            ScrollView(this)

        scroll.setBackgroundColor(
            Color.BLACK
        )

        val root =
            LinearLayout(this)

        root.orientation =
            LinearLayout.VERTICAL

        root.gravity =
            Gravity.CENTER_HORIZONTAL

        root.setPadding(
            20,
            45,
            20,
            40
        )

        root.addView(
            createTitle(
                "BACKGROUND ODOMETER"
            ),
            wrapParams()
        )

        addSpace(
            root,
            22
        )

        root.addView(
            createLabel(
                "TOTAL ODOMETER",
                TIFFANY
            ),
            wrapParams()
        )

        addSpace(
            root,
            4
        )

        totalText =
            createLargeValue(
                "0.00 km"
            )

        root.addView(
            totalText,
            wrapParams()
        )

        addSpace(
            root,
            24
        )

        root.addView(
            createLabel(
                "TODAY",
                "#888888"
            ),
            wrapParams()
        )

        addSpace(
            root,
            3
        )

        todayText =
            createMediumValue(
                "0.00 km"
            )

        root.addView(
            todayText,
            wrapParams()
        )

        addSpace(
            root,
            24
        )

        root.addView(
            createLabel(
                "CURRENT SPEED",
                "#888888"
            ),
            wrapParams()
        )

        addSpace(
            root,
            3
        )

        speedText =
            createMediumValue(
                "0.0 km/h"
            )

        root.addView(
            speedText,
            wrapParams()
        )

        addSpace(
            root,
            18
        )

        val speedRow =
            LinearLayout(this)

        speedRow.orientation =
            LinearLayout.HORIZONTAL

        speedRow.gravity =
            Gravity.CENTER

        val average =
            createStatColumn(
                "AVERAGE",
                "0.0 km/h"
            )

        averageText =
            average.second

        val maximum =
            createStatColumn(
                "MAXIMUM",
                "0.0 km/h"
            )

        maxText =
            maximum.second

        speedRow.addView(
            average.first,
            halfParams()
        )

        speedRow.addView(
            maximum.first,
            halfParams()
        )

        root.addView(
            speedRow,
            wrapParams()
        )

        addSpace(
            root,
            25
        )

        statusText =
            TextView(this)

        statusText.text =
            "GPS STATUS\nREADY"

        statusText.textSize =
            16f

        statusText.setTextColor(
            Color.LTGRAY
        )

        statusText.gravity =
            Gravity.CENTER

        root.addView(
            statusText,
            wrapParams()
        )

        addSpace(
            root,
            18
        )

        trackingButton =
            createAction(
                "START TRACKING"
            )

        trackingButton.setOnClickListener {

            if (
                trackingActive
            ) {

                stopTracking()

            } else {

                startTracking()
            }
        }

        root.addView(
            trackingButton,
            fullParams(62)
        )

        addSpace(
            root,
            10
        )

        val refresh =
            createAction(
                "REFRESH"
            )

        refresh.setOnClickListener {

            refreshData()
        }

        root.addView(
            refresh,
            fullParams(62)
        )

        addSpace(
            root,
            10
        )

        val trips =
            createAction(
                "TRIPS"
            )

        trips.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    TripsActivity::class.java
                )
            )
        }

        root.addView(
            trips,
            fullParams(62)
        )

        addSpace(
            root,
            25
        )

        /*
         * FUEL SECTION
         */

        root.addView(
            createLabel(
                "FUEL",
                TIFFANY
            ),
            wrapParams()
        )

        addSpace(
            root,
            8
        )

        fuelText =
            createMediumValue(
                "0.0 L"
            )

        root.addView(
            fuelText,
            wrapParams()
        )

        addSpace(
            root,
            15
        )

        val fuelInfoRow =
            LinearLayout(this)

        fuelInfoRow.orientation =
            LinearLayout.HORIZONTAL

        fuelInfoRow.gravity =
            Gravity.CENTER

        val mileageColumn =
            createStatColumn(
                "MILEAGE",
                "0.0 km/L"
            )

        mileageText =
            mileageColumn.second

        val rangeColumn =
            createStatColumn(
                "OVERALL RANGE",
                "0 km"
            )

        rangeText =
            rangeColumn.second

        fuelInfoRow.addView(
            mileageColumn.first,
            halfParams()
        )

        fuelInfoRow.addView(
            rangeColumn.first,
            halfParams()
        )

        root.addView(
            fuelInfoRow,
            wrapParams()
        )

        addSpace(
            root,
            15
        )

        reserveText =
            createLabel(
                "RESERVE: 0.0 L",
                "#888888"
            )

        root.addView(
            reserveText,
            wrapParams()
        )

        addSpace(
            root,
            10
        )

        reserveButton =
            createAction(
                "RESERVE NOT REACHED"
            )

        root.addView(
            reserveButton,
            fullParams(58)
        )

        addSpace(
            root,
            10
        )

        val fuelButton =
            createAction(
                "FUEL"
            )

        fuelButton.setOnClickListener {

            showFuelMenu()
        }

        root.addView(
            fuelButton,
            fullParams(62)
        )

        addSpace(
            root,
            30
        )

        root.addView(
            createLabel(
                "LAST COMPLETED TRIP",
                "#888888"
            ),
            wrapParams()
        )

        addSpace(
            root,
            5
        )

        lastTripText =
            createMediumValue(
                "No trips yet"
            )

        lastTripText.textSize =
            20f

        root.addView(
            lastTripText,
            wrapParams()
        )

        addSpace(
            root,
            25
        )

        root.addView(
            createLabel(
                "ODOMETER SPEED THRESHOLD",
                "#888888"
            ),
            wrapParams()
        )

        addSpace(
            root,
            5
        )

        val threshold =
            createMediumValue(
                "%.1f km/h".format(
                    database.getSpeedThreshold()
                )
            )

        root.addView(
            threshold,
            wrapParams()
        )

        addSpace(
            root,
            35
        )

        root.addView(
            createLabel(
                "App by Potato's man",
                "#777777"
            ),
            wrapParams()
        )

        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(
            scroll
        )
    }

    /*
     * --------------------------------------------------
     * FUEL MENU
     * --------------------------------------------------
     */

    private fun showFuelMenu() {

        val options =
            arrayOf(
                "ADD FUEL",
                "FUEL HISTORY",
                "TANK & RESERVE SETTINGS"
            )

        AlertDialog.Builder(this)
            .setTitle(
                "FUEL"
            )
            .setItems(
                options
            ) { _, which ->

                when (which) {

                    0 ->
                        showAddFuelDialog()

                    1 ->
                        showFuelHistory()

                    2 ->
                        showFuelSettings()
                }
            }
            .show()
    }

    private fun showAddFuelDialog() {

        val container =
            LinearLayout(this)

        container.orientation =
            LinearLayout.VERTICAL

        container.setPadding(
            40,
            10,
            40,
            5
        )

        val litresInput =
            EditText(this)

        litresInput.hint =
            "Litres added"

        litresInput.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        container.addView(
            litresInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val noteInput =
            EditText(this)

        noteInput.hint =
            "Note (optional)"

        container.addView(
            noteInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val currentFuel =
            database.getCurrentFuel()

        val reserve =
            database.getReserveFuel()

        val oldInReserve =
            currentFuel <= reserve

        AlertDialog.Builder(this)
            .setTitle(
                "ADD FUEL"
            )
            .setMessage(
                "Current fuel: %.2f L\nReserve: %.2f L".format(
                    currentFuel,
                    reserve
                )
            )
            .setView(
                container
            )
            .setNegativeButton(
                "CANCEL",
                null
            )
            .setPositiveButton(
                "ADD"
            ) { _, _ ->

                val litres =
                    litresInput.text
                        .toString()
                        .toDoubleOrNull()

                if (
                    litres == null ||
                    litres <= 0.0
                ) {

                    Toast.makeText(
                        this,
                        "Enter a valid fuel amount",
                        Toast.LENGTH_LONG
                    ).show()

                    return@setPositiveButton
                }

                val record =
                    database.addFuelRecord(
                        litres,
                        noteInput.text
                            .toString()
                    )

                if (
                    record == null
                ) {

                    Toast.makeText(
                        this,
                        "Could not save fuel",
                        Toast.LENGTH_LONG
                    ).show()

                    return@setPositiveButton
                }

                val newFuel =
                    database.getCurrentFuel()

                if (
                    oldInReserve &&
                    newFuel > reserve
                ) {

                    showReserveCrossed()

                } else {

                    Toast.makeText(
                        this,
                        "Fuel added: %.2f L".format(
                            litres
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }

                refreshData()
            }
            .show()
    }

    private fun showReserveCrossed() {

        Toast.makeText(
            this,
            "RESERVE CROSSED",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showFuelSettings() {

        val container =
            LinearLayout(this)

        container.orientation =
            LinearLayout.VERTICAL

        container.setPadding(
            40,
            10,
            40,
            5
        )

        val capacityInput =
            EditText(this)

        capacityInput.hint =
            "Tank capacity (L)"

        capacityInput.setText(
            "%.2f".format(
                database.getTankCapacity()
            )
        )

        capacityInput.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        container.addView(
            capacityInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val reserveInput =
            EditText(this)

        reserveInput.hint =
            "Reserve fuel (L)"

        reserveInput.setText(
            "%.2f".format(
                database.getReserveFuel()
            )
        )

        reserveInput.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        container.addView(
            reserveInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        AlertDialog.Builder(this)
            .setTitle(
                "TANK & RESERVE"
            )
            .setView(
                container
            )
            .setNegativeButton(
                "CANCEL",
                null
            )
            .setPositiveButton(
                "SAVE"
            ) { _, _ ->

                val capacity =
                    capacityInput.text
                        .toString()
                        .toDoubleOrNull()

                val reserve =
                    reserveInput.text
                        .toString()
                        .toDoubleOrNull()

                if (
                    capacity == null ||
                    capacity <= 0.0
                ) {

                    Toast.makeText(
                        this,
                        "Invalid tank capacity",
                        Toast.LENGTH_LONG
                    ).show()

                    return@setPositiveButton
                }

                if (
                    reserve == null ||
                    reserve < 0.0 ||
                    reserve > capacity
                ) {

                    Toast.makeText(
                        this,
                        "Invalid reserve amount",
                        Toast.LENGTH_LONG
                    ).show()

                    return@setPositiveButton
                }

                database.setTankCapacity(
                    capacity
                )

                database.setReserveFuel(
                    reserve
                )

                refreshData()

                Toast.makeText(
                    this,
                    "Fuel settings saved",
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }

    private fun showFuelHistory() {

        val records =
            database.getFuelRecords()

        if (
            records.isEmpty()
        ) {

            AlertDialog.Builder(this)
                .setTitle(
                    "FUEL HISTORY"
                )
                .setMessage(
                    "No fuel records yet."
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .show()

            return
        }

        val items =
            records.map {

                val date =
                    SimpleDateFormat(
                        "dd/MM/yyyy HH:mm",
                        Locale.getDefault()
                    ).format(
                        Date(it.time)
                    )

                "$date\n" +
                    "+%.2f L  •  %.2f L remaining\n" +
                    "Odometer: %.2f km".format(
                        it.litresAdded,
                        it.fuelAfterLitres,
                        it.odometerKm
                    )
            }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(
                "FUEL HISTORY"
            )
            .setItems(
                items
            ) { _, which ->

                val selected =
                    records[which]

                showFuelRecordOptions(
                    selected
                )
            }
            .setNegativeButton(
                "CLOSE",
                null
            )
            .show()
    }

    private fun showFuelRecordOptions(
        record: FuelRecord
    ) {

        AlertDialog.Builder(this)
            .setTitle(
                "FUEL RECORD"
            )
            .setItems(
                arrayOf(
                    "EDIT",
                    "DELETE"
                )
            ) { _, which ->

                when (which) {

                    0 ->
                        showEditFuelDialog(
                            record
                        )

                    1 ->
                        confirmDeleteFuel(
                            record
                        )
                }
            }
            .show()
    }

    private fun showEditFuelDialog(
        record: FuelRecord
    ) {

        val container =
            LinearLayout(this)

        container.orientation =
            LinearLayout.VERTICAL

        container.setPadding(
            40,
            10,
            40,
            5
        )

        val litresInput =
            EditText(this)

        litresInput.setText(
            "%.2f".format(
                record.litresAdded
            )
        )

        litresInput.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        container.addView(
            litresInput
        )

        val noteInput =
            EditText(this)

        noteInput.hint =
            "Note"

        noteInput.setText(
            record.note
        )

        container.addView(
            noteInput
        )

        AlertDialog.Builder(this)
            .setTitle(
                "EDIT FUEL"
            )
            .setView(
                container
            )
            .setNegativeButton(
                "CANCEL",
                null
            )
            .setPositiveButton(
                "SAVE"
            ) { _, _ ->

                val litres =
                    litresInput.text
                        .toString()
                        .toDoubleOrNull()

                if (
                    litres == null ||
                    litres <= 0.0
                ) {

                    Toast.makeText(
                        this,
                        "Invalid fuel amount",
                        Toast.LENGTH_LONG
                    ).show()

                    return@setPositiveButton
                }

                database.updateFuelRecord(
                    record.id,
                    litres,
                    noteInput.text.toString()
                )

                refreshData()
            }
            .show()
    }

    private fun confirmDeleteFuel(
        record: FuelRecord
    ) {

        AlertDialog.Builder(this)
            .setTitle(
                "DELETE FUEL RECORD?"
            )
            .setMessage(
                "+%.2f L fuel record".format(
                    record.litresAdded
                )
            )
            .setNegativeButton(
                "CANCEL",
                null
            )
            .setPositiveButton(
                "DELETE"
            ) { _, _ ->

                database.deleteFuelRecord(
                    record.id
                )

                refreshData()

                Toast.makeText(
                    this,
                    "Fuel record deleted",
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }

    private fun startTracking() {

        if (
            !hasLocationPermission()
        ) {

            requestLocationPermission()

            return
        }

        if (
            !notificationsAllowed()
        ) {

            requestNotificationPermission()

            return
        }

        preferences.edit()
            .putBoolean(
                PREF_AUTO_TRACKING,
                true
            )
            .apply()

        val intent =
            Intent(
                this,
                LocationTrackingService::class.java
            )

        try {

            ContextCompat.startForegroundService(
                this,
                intent
            )

            trackingActive =
                true

            trackingButton.text =
                "STOP TRACKING"

            trackingButton.background =
                buttonBackground(
                    true
                )

            updateStatusFromCurrentGps()

            showBackgroundLocationHintOnce()

        } catch (_: Exception) {

            preferences.edit()
                .putBoolean(
                    PREF_AUTO_TRACKING,
                    false
                )
                .apply()

            trackingActive =
                false

            statusText.text =
                "GPS STATUS\nUNABLE TO START"

            statusText.setTextColor(
                Color.RED
            )

            Toast.makeText(
                this,
                "Unable to start tracking",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopTracking() {

        preferences.edit()
            .putBoolean(
                PREF_AUTO_TRACKING,
                false
            )
            .apply()

        val intent =
            Intent(
                this,
                LocationTrackingService::class.java
            )

        intent.action =
            LocationTrackingService.ACTION_STOP

        try {

            startService(
                intent
            )

        } catch (_: Exception) {
        }

        trackingActive =
            false

        trackingButton.text =
            "START TRACKING"

        trackingButton.background =
            buttonBackground(
                false
            )

        statusText.text =
            "GPS STATUS\nTRACKING STOPPED"

        statusText.setTextColor(
            Color.LTGRAY
        )

        speedText.text =
            "0.0 km/h"

        refreshData()
    }

    private fun updateStatusFromCurrentGps() {

        if (
            !isLocationEnabled()
        ) {

            updateStatus(
                "GPS OFF\nWAITING"
            )

        } else {

            updateStatus(
                "GPS ON\nTRACKING ACTIVE"
            )
        }
    }

    private fun updateStatus(
        status: String
    ) {

        statusText.text =
            "GPS STATUS\n$status"

        val lower =
            status.lowercase()

        if (
            lower.contains("tracking") ||
            lower.contains("gps on")
        ) {

            statusText.setTextColor(
                Color.parseColor(
                    TIFFANY
                )
            )

        } else {

            statusText.setTextColor(
                Color.LTGRAY
            )
        }
    }

    private fun isLocationEnabled():
        Boolean {

        val manager =
            getSystemService(
                Context.LOCATION_SERVICE
            ) as android.location.LocationManager

        return try {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.P
            ) {

                manager.isLocationEnabled()

            } else {

                manager.isProviderEnabled(
                    android.location.LocationManager.GPS_PROVIDER
                ) ||
                    manager.isProviderEnabled(
                        android.location.LocationManager.NETWORK_PROVIDER
                    )
            }

        } catch (_: Exception) {

            false
        }
    }

    private fun showBackgroundLocationHintOnce() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.Q
        ) {
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (
            preferences.getBoolean(
                PREF_BACKGROUND_HINT,
                false
            )
        ) {
            return
        }

        preferences.edit()
            .putBoolean(
                PREF_BACKGROUND_HINT,
                true
            )
            .apply()

        AlertDialog.Builder(this)
            .setTitle(
                "Background location"
            )
            .setMessage(
                "For the strongest automatic tracking support, " +
                    "including restarting after a phone reboot, " +
                    "set Background Odometer location permission to " +
                    "\"Allow all the time\" in Android settings."
            )
            .setNegativeButton(
                "LATER",
                null
            )
            .setPositiveButton(
                "OPEN SETTINGS"
            ) { _, _ ->

                try {

                    val intent =
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        )

                    intent.data =
                        Uri.parse(
                            "package:$packageName"
                        )

                    startActivity(
                        intent
                    )

                } catch (_: Exception) {
                }
            }
            .show()
    }

    private fun notificationsAllowed():
        Boolean {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            NotificationManagerCompat
                .from(this)
                .areNotificationsEnabled()

        } else {

            true
        }
    }

    private fun requestNotificationPermission() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    REQUEST_NOTIFICATIONS
                )

            } else {

                openNotificationSettings()
            }

        } else {

            openNotificationSettings()
        }
    }

    private fun openNotificationSettings() {

        try {

            val intent =
                Intent(
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS
                )

            intent.putExtra(
                Settings.EXTRA_APP_PACKAGE,
                packageName
            )

            startActivity(
                intent
            )

        } catch (_: Exception) {
        }
    }

    private fun refreshData() {

        totalText.text =
            "%.2f km".format(
                database.getTotalOdometer()
            )

        todayText.text =
            "%.2f km".format(
                database.getTodayDistance()
            )

        val trip =
            database.getLastCompletedTrip()

        if (
            trip == null
        ) {

            lastTripText.text =
                "No trips yet"

            averageText.text =
                "0.0 km/h"

            maxText.text =
                "0.0 km/h"

        } else {

            lastTripText.text =
                "%.2f km".format(
                    trip.distanceKm
                )

            averageText.text =
                "%.1f km/h".format(
                    trip.averageSpeed
                )

            maxText.text =
                "%.1f km/h".format(
                    trip.maxSpeed
                )
        }

        /*
         * FUEL INFORMATION
         */

        val currentFuel =
            database.getCurrentFuel()

        val mileage =
            database.getAverageMileage()

        val overallRange =
            database.getOverallRangeKm()

        val reserve =
            database.getReserveFuel()

        val tankCapacity =
            database.getTankCapacity()

        fuelText.text =
            "%.2f L / %.2f L".format(
                currentFuel,
                tankCapacity
            )

        mileageText.text =
            if (
                mileage > 0.0
            ) {

                "%.1f km/L".format(
                    mileage
                )

            } else {

                "--"
            }

        rangeText.text =
            if (
                overallRange > 0.0
            ) {

                "%.0f km".format(
                    overallRange
                )

            } else {

                "--"
            }

        reserveText.text =
            "RESERVE: %.2f L".format(
                reserve
            )

        if (
            currentFuel <= reserve
        ) {

            reserveButton.text =
                "RESERVE REACHED"

            reserveButton.background =
                buttonBackground(
                    true
                )

            reserveButton.setTextColor(
                Color.WHITE
            )

        } else {

            reserveButton.text =
                "RESERVE NOT REACHED"

            reserveButton.background =
                buttonBackground(
                    false
                )

            reserveButton.setTextColor(
                Color.WHITE
            )
        }
    }

    private fun requestLocationPermission() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_LOCATION
            )
        }
    }

    private fun hasLocationPermission():
        Boolean {

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode ==
            REQUEST_NOTIFICATIONS
        ) {

            if (
                notificationsAllowed()
            ) {

                Toast.makeText(
                    this,
                    "Notifications enabled. Tap START TRACKING again.",
                    Toast.LENGTH_LONG
                ).show()

            } else {

                Toast.makeText(
                    this,
                    "Please allow Background Odometer notifications.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun createTitle(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text =
                text

            textSize =
                25f

            setTextColor(
                Color.WHITE
            )

            typeface =
                Typeface.DEFAULT_BOLD

            gravity =
                Gravity.CENTER
        }
    }

    private fun createLabel(
        text: String,
        color: String
    ): TextView {

        return TextView(this).apply {

            this.text =
                text

            textSize =
                15f

            setTextColor(
                Color.parseColor(
                    color
                )
            )

            gravity =
                Gravity.CENTER
        }
    }

    private fun createLargeValue(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text =
                text

            textSize =
                46f

            setTextColor(
                Color.WHITE
            )

            typeface =
                Typeface.DEFAULT_BOLD

            gravity =
                Gravity.CENTER
        }
    }

    private fun createMediumValue(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text =
                text

            textSize =
                25f

            setTextColor(
                Color.WHITE
            )

            typeface =
                Typeface.DEFAULT_BOLD

            gravity =
                Gravity.CENTER
        }
    }

    private fun createStatColumn(
        label: String,
        value: String
    ): Pair<LinearLayout, TextView> {

        val column =
            LinearLayout(this)

        column.orientation =
            LinearLayout.VERTICAL

        column.gravity =
            Gravity.CENTER

        column.addView(
            createLabel(
                label,
                "#777777"
            ),
            wrapParams()
        )

        addSpace(
            column,
            3
        )

        val valueView =
            createMediumValue(
                value
            )

        valueView.textSize =
            20f

        column.addView(
            valueView,
            wrapParams()
        )

        return Pair(
            column,
            valueView
        )
    }

    private fun createAction(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text =
                text

            textSize =
                17f

            setTextColor(
                Color.WHITE
            )

            typeface =
                Typeface.DEFAULT_BOLD

            gravity =
                Gravity.CENTER

            isClickable =
                true

            background =
                buttonBackground(
                    false
                )
        }
    }

    private fun buttonBackground(
        active: Boolean
    ): GradientDrawable {

        return GradientDrawable().apply {

            cornerRadius =
                18f

            setColor(
                Color.parseColor(
                    if (
                        active
                    ) {

                        "#07383E"

                    } else {

                        DARK
                    }
                )
            )

            setStroke(
                2,
                Color.parseColor(
                    TIFFANY
                )
            )
        }
    }

    private fun wrapParams():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun fullParams(
        height: Int
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height
        )
    }

    private fun halfParams():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
    }

    private fun addSpace(
        parent: LinearLayout,
        height: Int
    ) {

        parent.addView(
            View(this),
            LinearLayout.LayoutParams(
                1,
                height
            )
        )
    }

    override fun onResume() {

        super.onResume()

        val filter =
            IntentFilter().apply {

                addAction(
                    LocationTrackingService.ACTION_SPEED_UPDATE
                )

                addAction(
                    LocationTrackingService.ACTION_STATUS_UPDATE
                )
            }

        ContextCompat.registerReceiver(
            this,
            serviceReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        trackingActive =
            preferences.getBoolean(
                PREF_AUTO_TRACKING,
                false
            )

        if (
            trackingActive
        ) {

            trackingButton.text =
                "STOP TRACKING"

            trackingButton.background =
                buttonBackground(
                    true
                )

            updateStatusFromCurrentGps()

        } else {

            trackingButton.text =
                "START TRACKING"

            trackingButton.background =
                buttonBackground(
                    false
                )
        }

        refreshData()
    }

    override fun onPause() {

        try {

            unregisterReceiver(
                serviceReceiver
            )

        } catch (_: Exception) {
        }

        super.onPause()
    }

    override fun onDestroy() {

        database.close()

        super.onDestroy()
    }
}
