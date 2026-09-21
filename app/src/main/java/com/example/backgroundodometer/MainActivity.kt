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

    private lateinit var database: OdometerDatabaseHelper

    private lateinit var totalText: TextView
    private lateinit var todayText: TextView
    private lateinit var speedText: TextView
    private lateinit var averageText: TextView
    private lateinit var maxText: TextView
    private lateinit var statusText: TextView
    private lateinit var lastTripText: TextView

    private lateinit var fuelText: TextView
    private lateinit var mileageText: TextView
    private lateinit var rangeText: TextView
    private lateinit var reserveRangeText: TextView
    private lateinit var reserveReachedButton: TextView
    private lateinit var reserveCrossedButton: TextView

    private lateinit var trackingButton: TextView

    private var trackingActive = false

    private val preferences by lazy {
        getSharedPreferences(
            "background_odometer",
            MODE_PRIVATE
        )
    }

    companion object {

        private const val TIFFANY = "#00BCD4"

        private const val REQUEST_LOCATION = 100
        private const val REQUEST_NOTIFICATIONS = 101

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
                        "%.1f km/h".format(speed)

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

                    updateStatus(status)

                    val speed =
                        intent.getDoubleExtra(
                            LocationTrackingService.EXTRA_SPEED,
                            0.0
                        )

                    speedText.text =
                        "%.1f km/h".format(speed)

                    refreshData()
                }
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        database =
            OdometerDatabaseHelper(this)

        buildInterface()

        requestLocationPermission()

        requestNotificationPermissionIfNeeded()

        refreshData()
    }

    override fun onResume() {

        super.onResume()

        if (::database.isInitialized) {
            refreshData()
        }

        registerServiceReceiver()
    }

    override fun onPause() {

        unregisterServiceReceiver()

        super.onPause()
    }

    private fun registerServiceReceiver() {

        try {

            val filter =
                IntentFilter()

            filter.addAction(
                LocationTrackingService.ACTION_SPEED_UPDATE
            )

            filter.addAction(
                LocationTrackingService.ACTION_STATUS_UPDATE
            )

            if (Build.VERSION.SDK_INT >= 33) {

                registerReceiver(
                    serviceReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )

            } else {

                @Suppress("DEPRECATION")
                registerReceiver(
                    serviceReceiver,
                    filter
                )
            }

        } catch (_: Exception) {
        }
    }

    private fun unregisterServiceReceiver() {

        try {
            unregisterReceiver(
                serviceReceiver
            )
        } catch (_: Exception) {
        }
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
            40,
            20,
            40
        )

        root.addView(
            createTitle(
                "BACKGROUND ODOMETER"
            ),
            wrapParams()
        )

        addSpace(root, 22)

        root.addView(
            createLabel(
                "TOTAL ODOMETER",
                TIFFANY
            ),
            wrapParams()
        )

        totalText =
            createLargeValue(
                "0.00 km"
            )

        root.addView(
            totalText,
            wrapParams()
        )

        addSpace(root, 20)

        root.addView(
            createLabel(
                "TODAY",
                "#888888"
            ),
            wrapParams()
        )

        todayText =
            createMediumValue(
                "0.00 km"
            )

        root.addView(
            todayText,
            wrapParams()
        )

        addSpace(root, 20)

        root.addView(
            createLabel(
                "CURRENT SPEED",
                "#888888"
            ),
            wrapParams()
        )

        speedText =
            createMediumValue(
                "0.0 km/h"
            )

        root.addView(
            speedText,
            wrapParams()
        )

        addSpace(root, 15)

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

        addSpace(root, 22)

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

        addSpace(root, 15)

        trackingButton =
            createAction(
                "START TRACKING"
            )

        trackingButton.setOnClickListener {

            if (trackingActive) {
                stopTracking()
            } else {
                startTracking()
            }
        }

        root.addView(
            trackingButton,
            fullParams(62)
        )

        addSpace(root, 10)

        val refresh =
            createAction(
                "REFRESH"
            )

        refresh.setOnClickListener {

            refreshData()
        }

        root.addView(
            refresh,
            fullParams(58)
        )

        addSpace(root, 10)

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
            fullParams(58)
        )

        addSpace(root, 10)

        val days =
            createAction(
                "DAYS"
            )

        days.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    DaysActivity::class.java
                )
            )
        }

        root.addView(
            days,
            fullParams(58)
        )

        addSpace(root, 10)

        val settings =
            createAction(
                "SETTINGS"
            )

        settings.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    SettingsActivity::class.java
                )
            )
        }

        root.addView(
            settings,
            fullParams(58)
        )

        addSpace(root, 25)

        root.addView(
            createLabel(
                "FUEL",
                TIFFANY
            ),
            wrapParams()
        )

        addSpace(root, 5)

        fuelText =
            createMediumValue(
                "0.00 L"
            )

        root.addView(
            fuelText,
            wrapParams()
        )

        addSpace(root, 5)

        mileageText =
            createLabel(
                "Mileage: --",
                "#AAAAAA"
            )

        root.addView(
            mileageText,
            wrapParams()
        )

        addSpace(root, 5)

        rangeText =
            createLabel(
                "Overall range: --",
                "#AAAAAA"
            )

        root.addView(
            rangeText,
            wrapParams()
        )

        addSpace(root, 3)

        reserveRangeText =
            createLabel(
                "Range until reserve: --",
                "#AAAAAA"
            )

        root.addView(
            reserveRangeText,
            wrapParams()
        )

        addSpace(root, 12)

        reserveReachedButton =
            createStatusButton(
                "RESERVE REACHED",
                false
            )

        root.addView(
            reserveReachedButton,
            fullParams(54)
        )

        addSpace(root, 8)

        reserveCrossedButton =
            createStatusButton(
                "RESERVE CROSSED",
                false
            )

        root.addView(
            reserveCrossedButton,
            fullParams(54)
        )

        addSpace(root, 10)

        val fuel =
            createAction(
                "FUEL"
            )

        fuel.setOnClickListener {
            showFuelMenu()
        }

        root.addView(
            fuel,
            fullParams(58)
        )

        addSpace(root, 10)

        val history =
            createAction(
                "FUEL HISTORY"
            )

        history.setOnClickListener {
            showFuelHistory()
        }

        root.addView(
            history,
            fullParams(58)
        )

        addSpace(root, 25)

        root.addView(
            createLabel(
                "LAST COMPLETED TRIP",
                "#888888"
            ),
            wrapParams()
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

        addSpace(root, 25)

        root.addView(
            createLabel(
                "ODOMETER SPEED THRESHOLD",
                "#888888"
            ),
            wrapParams()
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

        addSpace(root, 20)

        val distanceAlert =
            createAction(
                "DISTANCE ALERT"
            )

        distanceAlert.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    SettingsActivity::class.java
                )
            )
        }

        root.addView(
            distanceAlert,
            fullParams(58)
        )

        addSpace(root, 35)

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

        setContentView(scroll)
    }

    private fun showFuelMenu() {

        val options =
            arrayOf(
                "ADD FUEL",
                "FUEL SETTINGS",
                "FUEL HISTORY"
            )

        AlertDialog.Builder(this)
            .setTitle("FUEL")
            .setItems(options) { _, which ->

                when (which) {

                    0 ->
                        showAddFuelDialog()

                    1 ->
                        showFuelSettingsDialog()

                    2 ->
                        showFuelHistory()
                }
            }
            .show()
    }

    private fun showAddFuelDialog() {

        val layout =
            LinearLayout(this)

        layout.orientation =
            LinearLayout.VERTICAL

        layout.setPadding(
            35,
            10,
            35,
            10
        )

        val litres =
            EditText(this)

        litres.hint =
            "Litres added"

        litres.inputType =
            2 or 8192

        layout.addView(
            litres,
            wrapParams()
        )

        val note =
            EditText(this)

        note.hint =
            "Note (optional)"

        layout.addView(
            note,
            wrapParams()
        )

        AlertDialog.Builder(this)
            .setTitle(
                "ADD FUEL"
            )
            .setView(layout)
            .setNegativeButton(
                "CANCEL",
                null
            )
            .setPositiveButton(
                "ADD"
            ) { _, _ ->

                val amount =
                    litres.text
                        .toString()
                        .toDoubleOrNull()

                if (
                    amount == null ||
                    amount <= 0.0
                ) {

                    Toast.makeText(
                        this,
                        "Enter valid litres",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                database.addFuel(
                    amount,
                    note.text.toString()
                )

                refreshData()
            }
            .show()
    }

    private fun showFuelSettingsDialog() {

        val layout =
            LinearLayout(this)

        layout.orientation =
            LinearLayout.VERTICAL

        layout.setPadding(
            35,
            10,
            35,
            10
        )

        val tank =
            EditText(this)

        tank.hint =
            "Tank capacity (L)"

        tank.setText(
            database.getTankCapacity()
                .toString()
        )

        tank.inputType =
            2 or 8192

        layout.addView(
            tank,
            wrapParams()
        )

        val reserve =
            EditText(this)

        reserve.hint =
            "Reserve fuel (L)"

        reserve.setText(
            database.getReserveFuel()
                .toString()
        )

        reserve.inputType =
            2 or 8192

        layout.addView(
            reserve,
            wrapParams()
        )

        AlertDialog.Builder(this)
            .setTitle(
                "FUEL SETTINGS"
            )
            .setView(layout)
            .setNegativeButton(
                "CANCEL",
                null
            )
            .setPositiveButton(
                "SAVE"
            ) { _, _ ->

                val tankValue =
                    tank.text
                        .toString()
                        .toDoubleOrNull()

                val reserveValue =
                    reserve.text
                        .toString()
                        .toDoubleOrNull()

                if (
                    tankValue == null ||
                    reserveValue == null ||
                    tankValue <= 0 ||
                    reserveValue < 0 ||
                    reserveValue >= tankValue
                ) {

                    Toast.makeText(
                        this,
                        "Invalid fuel settings",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                database.setFuelSettings(
                    tankValue,
                    reserveValue
                )

                refreshData()
            }
            .show()
    }

    private fun showFuelHistory() {

        val records =
            database.getFuelRecords()

        if (records.isEmpty()) {

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

        val layout =
            LinearLayout(this)

        layout.orientation =
            LinearLayout.VERTICAL

        val scroll =
            ScrollView(this)

        for (record in records) {

            val row =
                LinearLayout(this)

            row.orientation =
                LinearLayout.VERTICAL

            row.setPadding(
                15,
                15,
                15,
                15
            )

            val date =
                SimpleDateFormat(
                    "dd MMM yyyy hh:mm a",
                    Locale.getDefault()
                ).format(
                    Date(record.time)
                )

            val text =
                TextView(this)

            text.text =
                "$date\n" +
                    "+%.2f L   •   Fuel after %.2f L\n".format(
                        record.litresAdded,
                        record.fuelAfterLitres
                    ) +
                    if (
                        record.note.isNotBlank()
                    ) {
                        record.note
                    } else {
                        ""
                    }

            text.textSize =
                15f

            text.setTextColor(
                Color.WHITE
            )

            row.addView(
                text,
                wrapParams()
            )

            val buttons =
                LinearLayout(this)

            buttons.orientation =
                LinearLayout.HORIZONTAL

            val edit =
                createAction(
                    "EDIT"
                )

            edit.setOnClickListener {

                showEditFuelDialog(
                    record
                )
            }

            val delete =
                createAction(
                    "DELETE"
                )

            delete.setOnClickListener {

                AlertDialog.Builder(this)
                    .setTitle(
                        "DELETE FUEL?"
                    )
                    .setMessage(
                        "Delete this fuel record?"
                    )
                    .setNegativeButton(
                        "CANCEL",
                        null
                    )
                    .setPositiveButton(
                        "DELETE"
                    ) { _, _ ->

                        database.deleteFuel(
                            record.id
                        )

                        refreshData()

                        showFuelHistory()
                    }
                    .show()
            }

            buttons.addView(
                edit,
                LinearLayout.LayoutParams(
                    0,
                    55,
                    1f
                )
            )

            buttons.addView(
                delete,
                LinearLayout.LayoutParams(
                    0,
                    55,
                    1f
                )
            )

            row.addView(
                buttons,
                wrapParams()
            )

            layout.addView(
                row,
                wrapParams()
            )
        }

        scroll.addView(
            layout,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        AlertDialog.Builder(this)
            .setTitle(
                "FUEL HISTORY"
            )
            .setView(scroll)
            .setPositiveButton(
                "CLOSE",
                null
            )
            .show()
    }

    private fun showEditFuelDialog(
        record:
            OdometerDatabaseHelper.FuelRecord
    ) {

        val layout =
            LinearLayout(this)

        layout.orientation =
            LinearLayout.VERTICAL

        layout.setPadding(
            35,
            10,
            35,
            10
        )

        val litres =
            EditText(this)

        litres.hint =
            "Litres"

        litres.setText(
            record.litresAdded.toString()
        )

        litres.inputType =
            2 or 8192

        layout.addView(
            litres,
            wrapParams()
        )

        val note =
            EditText(this)

        note.hint =
            "Note"

        note.setText(
            record.note
        )

        layout.addView(
            note,
            wrapParams()
        )

        AlertDialog.Builder(this)
            .setTitle(
                "EDIT FUEL"
            )
            .setView(layout)
            .setNegativeButton(
                "CANCEL",
                null
            )
            .setPositiveButton(
                "SAVE"
            ) { _, _ ->

                val amount =
                    litres.text
                        .toString()
                        .toDoubleOrNull()

                if (
                    amount == null ||
                    amount <= 0.0
                ) {

                    Toast.makeText(
                        this,
                        "Enter valid litres",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                database.updateFuel(
                    record.id,
                    amount,
                    note.text.toString()
                )

                refreshData()

                showFuelHistory()
            }
            .show()
    }
        private fun startTracking() {

        if (!hasLocationPermission()) {

            requestLocationPermission()

            Toast.makeText(
                this,
                "Location permission is required",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission()
        ) {

            requestNotificationPermissionIfNeeded()

            Toast.makeText(
                this,
                "Allow notifications and try again",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        if (!isLocationEnabled()) {

            AlertDialog.Builder(this)
                .setTitle("GPS IS OFF")
                .setMessage(
                    "Turn on Location/GPS to start background tracking."
                )
                .setNegativeButton(
                    "CANCEL",
                    null
                )
                .setPositiveButton(
                    "OPEN SETTINGS"
                ) { _, _ ->

                    try {

                        startActivity(
                            Intent(
                                Settings.ACTION_LOCATION_SOURCE_SETTINGS
                            )
                        )

                    } catch (_: Exception) {
                    }
                }
                .show()

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

        intent.action =
            LocationTrackingService.ACTION_START

        try {

            ContextCompat.startForegroundService(
                this,
                intent
            )

            trackingActive = true

            updateTrackingButton()

            updateStatus(
                "TRACKING\nGPS ACTIVE"
            )

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Unable to start tracking: ${e.message}",
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

            startService(intent)

        } catch (_: Exception) {
        }

        trackingActive = false

        updateTrackingButton()

        updateStatus(
            "TRACKING STOPPED"
        )

        refreshData()
    }

    private fun refreshData() {

        try {

            val total =
                database.getTotalOdometer()

            totalText.text =
                "%.2f km".format(total)

            val today =
                database.getTodayDistance()

            todayText.text =
                "%.2f km".format(today)

            val speed =
                database.getCurrentSpeed()

            speedText.text =
                "%.1f km/h".format(speed)

            val average =
                database.getAverageSpeed()

            averageText.text =
                "%.1f km/h".format(average)

            val maximum =
                database.getMaximumSpeed()

            maxText.text =
                "%.1f km/h".format(maximum)

            trackingActive =
                preferences.getBoolean(
                    PREF_AUTO_TRACKING,
                    false
                )

            updateTrackingButton()

            val fuel =
                database.getCurrentFuel()

            fuelText.text =
                "%.2f L".format(fuel)

            val mileage =
                database.getMileage()

            if (mileage > 0.0) {

                mileageText.text =
                    "Mileage: %.2f km/L".format(
                        mileage
                    )

            } else {

                mileageText.text =
                    "Mileage: --"
            }

            val range =
                database.getEstimatedRange()

            if (range >= 0.0) {

                rangeText.text =
                    "Overall range: %.1f km".format(
                        range
                    )

            } else {

                rangeText.text =
                    "Overall range: --"
            }

            val reserveRange =
                database.getRangeUntilReserve()

            if (reserveRange >= 0.0) {

                reserveRangeText.text =
                    "Range until reserve: %.1f km".format(
                        reserveRange
                    )

            } else {

                reserveRangeText.text =
                    "Range until reserve: --"
            }

            updateReserveButtons()

            val lastTrip =
                database.getLastCompletedTrip()

            if (lastTrip != null) {

                lastTripText.text =
                    "%.2f km  •  %.1f km/h".format(
                        lastTrip.distanceKm,
                        lastTrip.averageSpeedKmh
                    )

            } else {

                lastTripText.text =
                    "No trips yet"
            }

            updateGpsStatus()

        } catch (e: Exception) {

            // Keep the screen usable even if a refresh
            // occurs while the database/service is changing.
        }
    }

    private fun updateTrackingButton() {

        if (!::trackingButton.isInitialized) {
            return
        }

        if (trackingActive) {

            trackingButton.text =
                "STOP TRACKING"

            trackingButton.setTextColor(
                Color.WHITE
            )

        } else {

            trackingButton.text =
                "START TRACKING"

            trackingButton.setTextColor(
                Color.WHITE
            )
        }
    }

    private fun updateStatus(
        status: String
    ) {

        if (!::statusText.isInitialized) {
            return
        }

        statusText.text =
            "GPS STATUS\n$status"
    }

    private fun updateGpsStatus() {

        if (!::statusText.isInitialized) {
            return
        }

        val enabled =
            isLocationEnabled()

        if (!enabled) {

            statusText.text =
                "GPS STATUS\nGPS OFF"

            return
        }

        if (trackingActive) {

            statusText.text =
                "GPS STATUS\nTRACKING ACTIVE"

        } else {

            statusText.text =
                "GPS STATUS\nGPS READY"
        }
    }

    private fun updateReserveButtons() {

        try {

            val fuel =
                database.getCurrentFuel()

            val reserve =
                database.getReserveFuel()

            if (fuel <= reserve) {

                reserveReachedButton.text =
                    "RESERVE REACHED"

                reserveReachedButton.setTextColor(
                    Color.WHITE
                )

            } else {

                reserveReachedButton.text =
                    "RESERVE NOT REACHED"

                reserveReachedButton.setTextColor(
                    Color.LTGRAY
                )
            }

            val crossed =
                database.hasReserveBeenCrossed()

            if (crossed) {

                reserveCrossedButton.text =
                    "RESERVE CROSSED"

                reserveCrossedButton.setTextColor(
                    Color.WHITE
                )

            } else {

                reserveCrossedButton.text =
                    "RESERVE NOT CROSSED"

                reserveCrossedButton.setTextColor(
                    Color.LTGRAY
                )
            }

        } catch (_: Exception) {
        }
    }

    private fun hasLocationPermission(): Boolean {

        val fine =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarse =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    private fun hasBackgroundLocationPermission(): Boolean {

        if (Build.VERSION.SDK_INT <
            Build.VERSION_CODES.Q
        ) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {

        if (hasLocationPermission()) {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q &&
                !hasBackgroundLocationPermission()
            ) {

                showBackgroundLocationPermissionHint()
            }

            return
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQUEST_LOCATION
        )
    }

    private fun requestNotificationPermissionIfNeeded() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission()
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                REQUEST_NOTIFICATIONS
            )
        }
    }

    private fun showBackgroundLocationPermissionHint() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.Q
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
                "BACKGROUND LOCATION"
            )
            .setMessage(
                "For reliable background tracking while the app is not open, Android may require Background Location permission. You can enable it from App permissions."
            )
            .setNegativeButton(
                "LATER",
                null
            )
            .setPositiveButton(
                "OPEN APP SETTINGS"
            ) { _, _ ->

                openAppSettings()
            }
            .show()
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

        when (requestCode) {

            REQUEST_LOCATION -> {

                if (hasLocationPermission()) {

                    Toast.makeText(
                        this,
                        "Location permission granted",
                        Toast.LENGTH_SHORT
                    ).show()

                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q &&
                        !hasBackgroundLocationPermission()
                    ) {

                        showBackgroundLocationPermissionHint()
                    }

                } else {

                    Toast.makeText(
                        this,
                        "Location permission is required",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            REQUEST_NOTIFICATIONS -> {

                if (!hasNotificationPermission()) {

                    Toast.makeText(
                        this,
                        "Notifications are disabled. Background tracking may not work correctly.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun openAppSettings() {

        try {

            val intent =
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                )

            intent.data =
                Uri.parse(
                    "package:$packageName"
                )

            startActivity(intent)

        } catch (_: Exception) {
        }
    }

    private fun isLocationEnabled(): Boolean {

        return try {

            val manager =
                getSystemService(
                    Context.LOCATION_SERVICE
                ) as android.location.LocationManager

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.P
            ) {

                manager.isLocationEnabled

            } else {

                @Suppress("DEPRECATION")
                manager.isProviderEnabled(
                    android.location.LocationManager.GPS_PROVIDER
                ) ||
                    @Suppress("DEPRECATION")
                    manager.isProviderEnabled(
                        android.location.LocationManager.NETWORK_PROVIDER
                    )
            }

        } catch (_: Exception) {

            false
        }
    }

    private fun createTitle(
        text: String
    ): TextView {

        val view =
            TextView(this)

        view.text =
            text

        view.textSize =
            26f

        view.typeface =
            Typeface.DEFAULT_BOLD

        view.setTextColor(
            Color.WHITE
        )

        view.gravity =
            Gravity.CENTER

        return view
    }

    private fun createLabel(
        text: String,
        color: String
    ): TextView {

        val view =
            TextView(this)

        view.text =
            text

        view.textSize =
            14f

        view.setTextColor(
            Color.parseColor(color)
        )

        view.gravity =
            Gravity.CENTER

        return view
    }

    private fun createLargeValue(
        text: String
    ): TextView {

        val view =
            TextView(this)

        view.text =
            text

        view.textSize =
            42f

        view.typeface =
            Typeface.DEFAULT_BOLD

        view.setTextColor(
            Color.WHITE
        )

        view.gravity =
            Gravity.CENTER

        return view
    }

    private fun createMediumValue(
        text: String
    ): TextView {

        val view =
            TextView(this)

        view.text =
            text

        view.textSize =
            25f

        view.typeface =
            Typeface.DEFAULT_BOLD

        view.setTextColor(
            Color.WHITE
        )

        view.gravity =
            Gravity.CENTER

        return view
    }

    private fun createStatColumn(
        title: String,
        value: String
    ): Pair<View, TextView> {

        val column =
            LinearLayout(this)

        column.orientation =
            LinearLayout.VERTICAL

        column.gravity =
            Gravity.CENTER

        val label =
            createLabel(
                title,
                "#888888"
            )

        val number =
            createMediumValue(
                value
            )

        number.textSize =
            20f

        column.addView(
            label,
            wrapParams()
        )

        column.addView(
            number,
            wrapParams()
        )

        return Pair(
            column,
            number
        )
    }

    private fun createAction(
        text: String
    ): TextView {

        val view =
            TextView(this)

        view.text =
            text

        view.textSize =
            15f

        view.typeface =
            Typeface.DEFAULT_BOLD

        view.gravity =
            Gravity.CENTER

        view.setTextColor(
            Color.WHITE
        )

        view.background =
            roundedBackground(
                Color.rgb(
                    18,
                    18,
                    18
                ),
                Color.parseColor(
                    TIFFANY
                )
            )

        view.setPadding(
            12,
            8,
            12,
            8
        )

        return view
    }

    private fun createStatusButton(
        text: String,
        active: Boolean
    ): TextView {

        val view =
            createAction(text)

        if (!active) {

            view.alpha =
                0.75f
        }

        return view
    }

    private fun roundedBackground(
        fill: Int,
        stroke: Int
    ): GradientDrawable {

        val drawable =
            GradientDrawable()

        drawable.setColor(
            fill
        )

        drawable.cornerRadius =
            18f

        drawable.setStroke(
            2,
            stroke
        )

        return drawable
    }

    private fun wrapParams():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
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

    private fun fullParams(
        height: Int
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height
        )
    }

    private fun addSpace(
        parent: LinearLayout,
        dp: Int
    ) {

        val space =
            View(this)

        val density =
            resources.displayMetrics.density

        val pixels =
            (dp * density).toInt()

        parent.addView(
            space,
            LinearLayout.LayoutParams(
                1,
                pixels
            )
        )
    }
}
