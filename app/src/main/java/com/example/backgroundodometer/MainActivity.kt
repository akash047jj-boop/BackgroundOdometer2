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
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

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

    private lateinit var refreshButton:
        TextView

    private var trackingActive =
        false

    companion object {

        private const val TIFFANY =
            "#00BCD4"

        private const val DARK_TIFFANY =
            "#07383E"

        private const val BUTTON_DARK =
            "#151515"
    }

    private val receiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                if (
                    intent?.action ==
                    LocationTrackingService
                        .ACTION_SPEED_UPDATE
                ) {

                    val speed =
                        intent.getDoubleExtra(
                            LocationTrackingService
                                .EXTRA_SPEED,
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

        super.onCreate(
            savedInstanceState
        )

        database =
            OdometerDatabaseHelper(this)

        buildInterface()

        requestLocationPermission()

        refreshData()
    }

    // =====================================================
    // MAIN INTERFACE
    // =====================================================

    private fun buildInterface() {

        val scroll =
            ScrollView(this)

        scroll.setBackgroundColor(
            Color.BLACK
        )

        scroll.isFillViewport =
            true

        val root =
            LinearLayout(this)

        root.orientation =
            LinearLayout.VERTICAL

        root.gravity =
            Gravity.CENTER_HORIZONTAL

        root.setPadding(
            24,
            55,
            24,
            40
        )

        // =================================================
        // TITLE
        // =================================================

        val title =
            TextView(this)

        title.text =
            "BACKGROUND ODOMETER"

        title.textSize =
            24f

        title.setTextColor(
            Color.WHITE
        )

        title.typeface =
            Typeface.DEFAULT_BOLD

        title.gravity =
            Gravity.CENTER

        root.addView(
            title,
            wrapParams()
        )

        addSpace(
            root,
            24
        )

        // =================================================
        // TOTAL
        // =================================================

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
            25
        )

        // =================================================
        // TODAY
        // =================================================

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
            28
        )

        // =================================================
        // CURRENT SPEED
        // =================================================

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

        // =================================================
        // AVERAGE / MAXIMUM
        // =================================================

        val speedRow =
            LinearLayout(this)

        speedRow.orientation =
            LinearLayout.HORIZONTAL

        speedRow.gravity =
            Gravity.CENTER

        val averageColumn =
            createStatColumn(
                "AVERAGE",
                "0.0 km/h"
            )

        averageText =
            averageColumn.second

        val maxColumn =
            createStatColumn(
                "MAXIMUM",
                "0.0 km/h"
            )

        maxText =
            maxColumn.second

        speedRow.addView(
            averageColumn.first,
            halfWidthParams()
        )

        speedRow.addView(
            maxColumn.first,
            halfWidthParams()
        )

        root.addView(
            speedRow,
            wrapParams()
        )

        addSpace(
            root,
            28
        )

        // =================================================
        // GPS STATUS
        // =================================================

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
            20
        )

        // =================================================
        // START TRACKING
        // =================================================

        trackingButton =
            createActionText(
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
            fullWidthParams(64)
        )

        addSpace(
            root,
            12
        )

        // =================================================
        // REFRESH
        // =================================================

        refreshButton =
            createActionText(
                "REFRESH"
            )

        refreshButton.setOnClickListener {

            refreshData()
        }

        root.addView(
            refreshButton,
            fullWidthParams(64)
        )

        addSpace(
            root,
            28
        )

        // =================================================
        // LAST TRIP
        // =================================================

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
            18f

        root.addView(
            lastTripText,
            wrapParams()
        )

        addSpace(
            root,
            25
        )

        // =================================================
        // THRESHOLD
        // =================================================

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

        // =================================================
        // FOOTER
        // =================================================

        val footer =
            TextView(this)

        footer.text =
            "App by Potato's man"

        footer.textSize =
            13f

        footer.setTextColor(
            Color.GRAY
        )

        footer.gravity =
            Gravity.CENTER

        root.addView(
            footer,
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

    // =====================================================
    // START TRACKING
    // =====================================================

    private fun startTracking() {

        if (!hasLocationPermission()) {

            requestLocationPermission()

            return
        }

        val intent =
            Intent(
                this,
                LocationTrackingService::class.java
            )

        try {

            ContextCompat
                .startForegroundService(
                    this,
                    intent
                )

            trackingActive =
                true

            trackingButton.text =
                "STOP TRACKING"

            setTrackingButtonActive()

            statusText.text =
                "GPS STATUS\nTRACKING ACTIVE"

            statusText.setTextColor(
                Color.parseColor(
                    TIFFANY
                )
            )

        } catch (_: Exception) {

            statusText.text =
                "GPS STATUS\nUNABLE TO START"

            statusText.setTextColor(
                Color.RED
            )
        }
    }

    // =====================================================
    // STOP TRACKING
    // =====================================================

    private fun stopTracking() {

        val intent =
            Intent(
                this,
                LocationTrackingService::class.java
            )

        intent.action =
            LocationTrackingService
                .ACTION_STOP

        try {

            startService(intent)

        } catch (_: Exception) {
        }

        trackingActive =
            false

        trackingButton.text =
            "START TRACKING"

        setTrackingButtonInactive()

        statusText.text =
            "GPS STATUS\nTRACKING STOPPED"

        statusText.setTextColor(
            Color.LTGRAY
        )

        speedText.text =
            "0.0 km/h"

        refreshData()
    }

    // =====================================================
    // BUTTON STYLE
    // =====================================================

    private fun createActionText(
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

            isFocusable =
                true

            setPadding(
                8,
                0,
                8,
                0
            )

            background =
                createButtonBackground(
                    false
                )
        }
    }

    private fun setTrackingButtonActive() {

        trackingButton.background =
            createButtonBackground(
                true
            )
    }

    private fun setTrackingButtonInactive() {

        trackingButton.background =
            createButtonBackground(
                false
            )
    }

    private fun createButtonBackground(
        active: Boolean
    ): GradientDrawable {

        return GradientDrawable().apply {

            cornerRadius =
                18f

            setColor(
                Color.parseColor(
                    if (active) {
                        DARK_TIFFANY
                    } else {
                        BUTTON_DARK
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

    // =====================================================
    // DATA
    // =====================================================

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

    // =====================================================
    // PERMISSION
    // =====================================================

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
                100
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

    // =====================================================
    // TEXT HELPERS
    // =====================================================

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

        val labelView =
            createLabel(
                label,
                "#777777"
            )

        val valueView =
            createMediumValue(
                value
            )

        valueView.textSize =
            20f

        column.addView(
            labelView,
            wrapParams()
        )

        addSpace(
            column,
            3
        )

        column.addView(
            valueView,
            wrapParams()
        )

        return Pair(
            column,
            valueView
        )
    }

    // =====================================================
    // LAYOUT HELPERS
    // =====================================================

    private fun wrapParams():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun fullWidthParams(
        height: Int
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height
        )
    }

    private fun halfWidthParams():
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

        val space =
            View(this)

        parent.addView(
            space,
            LinearLayout.LayoutParams(
                1,
                height
            )
        )
    }

    // =====================================================
    // LIFECYCLE
    // =====================================================

    override fun onResume() {

        super.onResume()

        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(
                LocationTrackingService
                    .ACTION_SPEED_UPDATE
            ),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        refreshData()
    }

    override fun onPause() {

        try {

            unregisterReceiver(
                receiver
            )

        } catch (_: Exception) {
        }

        super.onPause()
    }

    override fun onDestroy() {

        try {

            database.close()

        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}
