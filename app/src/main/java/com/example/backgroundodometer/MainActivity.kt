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
        private const val DARK = "#151515"

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

        refreshData()
    }

    private fun buildInterface() {

        val scroll =
            ScrollView(this)

        scroll.setBackgroundColor(Color.BLACK)

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
            createTitle("BACKGROUND ODOMETER"),
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
            createLargeValue("0.00 km")

        root.addView(
            totalText,
            wrapParams()
        )

        addSpace(root, 20)

        root.addView(
            createLabel("TODAY", "#888888"),
            wrapParams()
        )

        todayText =
            createMediumValue("0.00 km")

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
            createMediumValue("0.0 km/h")

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

        statusText.textSize = 16f
        statusText.setTextColor(Color.LTGRAY)
        statusText.gravity = Gravity.CENTER

        root.addView(
            statusText,
            wrapParams()
        )

        addSpace(root, 15)

        trackingButton =
            createAction("START TRACKING")

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
            createAction("REFRESH")

        refresh.setOnClickListener {
            refreshData()
        }

        root.addView(
            refresh,
            fullParams(58)
        )

        addSpace(root, 10)

        val trips =
            createAction("TRIPS")

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
            createAction("DAYS")

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
            createMediumValue("0.00 L")

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
            createAction("FUEL")

        fuel.setOnClickListener {
            showFuelMenu()
        }

        root.addView(
            fuel,
            fullParams(58)
        )

        addSpace(root, 10)

        val history =
            createAction("FUEL HISTORY")

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
            createMediumValue("No trips yet")

        lastTripText.textSize = 20f

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

                    0 -> showAddFuelDialog()

                    1 -> showFuelSettingsDialog()

                    2 -> showFuelHistory()
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
            .setTitle("ADD FUEL")
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
            .setTitle("FUEL SETTINGS")
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
                .setTitle("FUEL HISTORY")
                .setMessage("No fuel records yet.")
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

            text.textSize = 15f
            text.setTextColor(Color.WHITE)

            row.addView(
                text,
                wrapParams()
            )

            val buttons =
                LinearLayout(this)

            buttons.orientation =
                LinearLayout.HORIZONTAL

            val edit =
                createAction("EDIT")

            edit.setOnClickListener {

                showEditFuelDialog(
                    record
                )
            }

            val delete =
                createAction("DELETE")

            delete.setOnClickListener {

                AlertDialog.Builder(this)
                    .setTitle("DELETE FUEL?")
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
                halfParams()
            )

            buttons.addView(
                delete,
                halfParams()
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
            layout
        )

        AlertDialog.Builder(this)
            .setTitle("FUEL HISTORY")
            .setView(scroll)
            .setPositiveButton(
                "CLOSE",
                null
            )
            .show()
    }

    private fun showEditFuelDialog(
        record: FuelRecord
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

        note.setText(
            record.note
        )

        layout.addView(
            note,
            wrapParams()
        )

        AlertDialog.Builder(this)
            .setTitle("EDIT FUEL")
            .setView(layout)
            .setNegativeButton(
                "CANCEL",
                null
            )
            .setPositiveButton(
                "SAVE"
            ) { _, _ ->

                val value =
                    litres.text
                        .toString()
                        .toDoubleOrNull()

                if (
                    value == null ||
                    value <= 0
                ) {
                    return@setPositiveButton
                }

                database.updateFuel(
                    record.id,
                    value,
                    note.text.toString()
                )

                refreshData()
            }
            .show()
    }

    private fun startTracking() {

        if (!hasLocationPermission()) {

            requestLocationPermission()
            return
        }

        if (!notificationsAllowed()) {

            requestNotificationPermission()
            return
        }

        preferences.edit()
            .putBoolean(
                PREF_AUTO_TRACKING,
                true
            )
            .apply()

        try {

            ContextCompat.startForegroundService(
                this,
                Intent(
                    this,
                    LocationTrackingService::class.java
                )
            )

            trackingActive = true

            trackingButton.text =
                "STOP TRACKING"

            trackingButton.background =
                buttonBackground(true)

            updateStatusFromCurrentGps()

            showBackgroundLocationHintOnce()

        } catch (_: Exception) {

            preferences.edit()
                .putBoolean(
                    PREF_AUTO_TRACKING,
                    false
                )
                .apply()

            trackingActive = false

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
            startService(intent)
        } catch (_: Exception) {
        }

        trackingActive = false

        trackingButton.text =
            "START TRACKING"

        trackingButton.background =
            buttonBackground(false)

        statusText.text =
            "GPS STATUS\nTRACKING STOPPED"

        speedText.text =
            "0.0 km/h"

        refreshData()
    }

    private fun updateStatusFromCurrentGps() {

        if (!isLocationEnabled()) {

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

        statusText.setTextColor(
            if (
                lower.contains("tracking") ||
                lower.contains("gps on")
            ) {
                Color.parseColor(TIFFANY)
            } else {
                Color.LTGRAY
            }
        )
    }

    private fun isLocationEnabled(): Boolean {

        val manager =
            getSystemService(
                Context.LOCATION_SERVICE
            ) as android.location.LocationManager

        return try {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.P
            ) {
                manager.isLocationEnabled
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

        if (trip == null) {

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

        val fuel =
            database.getCurrentFuel()

        fuelText.text =
            "%.2f L / %.2f L".format(
                fuel,
                database.getTankCapacity()
            )

        val mileage =
            database.getAverageMileage()

        mileageText.text =
            if (mileage > 0) {
                "Mileage: %.2f km/L".format(
                    mileage
                )
            } else {
                "Mileage: --"
            }

        val overallRange =
            database.getOverallRange()

        rangeText.text =
            if (mileage > 0) {
                "Overall range: %.1f km".format(
                    overallRange
                )
            } else {
                "Overall range: --"
            }

        val reserveRange =
            database.getRangeToReserve()

        reserveRangeText.text =
            if (mileage > 0) {
                "Range until reserve: %.1f km".format(
                    reserveRange
                )
            } else {
                "Range until reserve: --"
            }

        val reached =
            database.isReserveReached()

        reserveReachedButton.background =
            statusButtonBackground(
                reached
            )

        reserveReachedButton.setTextColor(
            if (reached) {
                Color.WHITE
            } else {
                Color.GRAY
            }
        )

        val crossed =
            database.isReserveCrossed()

        reserveCrossedButton.background =
            statusButtonBackground(
                crossed
            )

        reserveCrossedButton.setTextColor(
            if (crossed) {
                Color.WHITE
            } else {
                Color.GRAY
            )
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

                    startActivity(intent)

                } catch (_: Exception) {
                }
            }
            .show()
    }

    private fun notificationsAllowed(): Boolean {

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

            startActivity(intent)

        } catch (_: Exception) {
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

    private fun hasLocationPermission(): Boolean {

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

            Toast.makeText(
                this,
                if (notificationsAllowed()) {
                    "Notifications enabled."
                } else {
                    "Please allow notifications."
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun createTitle(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text = text
            textSize = 25f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
    }

    private fun createLabel(
        text: String,
        color: String
    ): TextView {

        return TextView(this).apply {

            this.text = text
            textSize = 15f
            setTextColor(
                Color.parseColor(color)
            )
            gravity = Gravity.CENTER
        }
    }

    private fun createLargeValue(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text = text
            textSize = 46f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
    }

    private fun createMediumValue(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text = text
            textSize = 25f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
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

        val valueView =
            createMediumValue(value)

        valueView.textSize = 20f

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

            this.text = text
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background =
                buttonBackground(false)
        }
    }

    private fun createStatusButton(
        text: String,
        active: Boolean
    ): TextView {

        return TextView(this).apply {

            this.text = text
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER

            background =
                statusButtonBackground(active)
        }
    }

    private fun buttonBackground(
        active: Boolean
    ): GradientDrawable {

        return GradientDrawable().apply {

            cornerRadius = 18f

            setColor(
                Color.parseColor(
                    if (active) {
                        "#07383E"
                    } else {
                        DARK
                    }
                )
            )

            setStroke(
                2,
                Color.parseColor(TIFFANY)
            )
        }
    }

    private fun statusButtonBackground(
        active: Boolean
    ): GradientDrawable {

        return GradientDrawable().apply {

            cornerRadius = 16f

            setColor(
                Color.parseColor(
                    if (active) {
                        "#07383E"
                    } else {
                        "#101010"
                    }
                )
            )

            setStroke(
                2,
                Color.parseColor(
                    if (active) {
                        TIFFANY
                    } else {
                        "#444444"
                    }
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

        trackingButton.text =
            if (trackingActive) {
                "STOP TRACKING"
            } else {
                "START TRACKING"
            }

        trackingButton.background =
            buttonBackground(trackingActive)

        if (trackingActive) {
            updateStatusFromCurrentGps()
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
