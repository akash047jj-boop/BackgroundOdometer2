package com.example.backgroundodometer

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var database: OdometerDatabaseHelper

    private lateinit var totalText: TextView
    private lateinit var todayText: TextView
    private lateinit var speedText: TextView
    private lateinit var averageText: TextView
    private lateinit var maxText: TextView
    private lateinit var statusText: TextView
    private lateinit var lastTripText: TextView
    private lateinit var trackingButton: TextView

    private var trackingActive = false

    companion object {
        private const val TIFFANY = "#00BCD4"
        private const val DARK = "#151515"

        private const val REQUEST_LOCATION = 100
        private const val REQUEST_NOTIFICATIONS = 101
    }

    private val speedReceiver =
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
            createTitle("BACKGROUND ODOMETER"),
            wrapParams()
        )

        addSpace(root, 22)

        root.addView(
            createLabel("TOTAL ODOMETER", TIFFANY),
            wrapParams()
        )

        addSpace(root, 4)

        totalText =
            createLargeValue("0.00 km")

        root.addView(
            totalText,
            wrapParams()
        )

        addSpace(root, 24)

        root.addView(
            createLabel("TODAY", "#888888"),
            wrapParams()
        )

        addSpace(root, 3)

        todayText =
            createMediumValue("0.00 km")

        root.addView(
            todayText,
            wrapParams()
        )

        addSpace(root, 24)

        root.addView(
            createLabel("CURRENT SPEED", "#888888"),
            wrapParams()
        )

        addSpace(root, 3)

        speedText =
            createMediumValue("0.0 km/h")

        root.addView(
            speedText,
            wrapParams()
        )

        addSpace(root, 18)

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

        addSpace(root, 25)

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

        addSpace(root, 18)

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
            fullParams(62)
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
            fullParams(62)
        )

        addSpace(root, 25)

        root.addView(
            createLabel(
                "LAST COMPLETED TRIP",
                "#888888"
            ),
            wrapParams()
        )

        addSpace(root, 5)

        lastTripText =
            createMediumValue("No trips yet")

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

        addSpace(root, 5)

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

    private fun startTracking() {

        if (!hasLocationPermission()) {

            requestLocationPermission()

            return
        }

        if (!notificationsAllowed()) {

            requestNotificationPermission()

            return
        }

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

            trackingActive = true

            trackingButton.text =
                "STOP TRACKING"

            trackingButton.background =
                buttonBackground(true)

            statusText.text =
                "GPS STATUS\nTRACKING ACTIVE"

            statusText.setTextColor(
                Color.parseColor(TIFFANY)
            )

        } catch (e: Exception) {

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

        statusText.setTextColor(
            Color.LTGRAY
        )

        speedText.text =
            "0.0 km/h"

        refreshData()
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

            this.text = text

            textSize = 25f

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

            this.text = text

            textSize = 15f

            setTextColor(
                Color.parseColor(color)
            )

            gravity =
                Gravity.CENTER
        }
    }

    private fun createLargeValue(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text = text

            textSize = 46f

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

            this.text = text

            textSize = 25f

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
            createMediumValue(value)

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

            this.text = text

            textSize = 17f

            setTextColor(
                Color.WHITE
            )

            typeface =
                Typeface.DEFAULT_BOLD

            gravity =
                Gravity.CENTER

            isClickable = true

            background =
                buttonBackground(false)
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

        ContextCompat.registerReceiver(
            this,
            speedReceiver,
            IntentFilter(
                LocationTrackingService.ACTION_SPEED_UPDATE
            ),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        refreshData()
    }

    override fun onPause() {

        try {
            unregisterReceiver(speedReceiver)
        } catch (_: Exception) {
        }

        super.onPause()
    }

    override fun onDestroy() {

        database.close()

        super.onDestroy()
    }
}
