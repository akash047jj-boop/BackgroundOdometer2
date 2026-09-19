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
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
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

    private lateinit var speedText:
        TextView

    private lateinit var statusText:
        TextView

    private lateinit var trackingButton:
        Button

    private var trackingActive =
        false

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
                        "%.1f km/h"
                            .format(speed)

                    refreshTotal()
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

        refreshTotal()
    }

    // =========================================================
    // MAIN UI
    // =========================================================

    private fun buildInterface() {

        val scrollView =
            ScrollView(this)

        scrollView.setBackgroundColor(
            Color.BLACK
        )

        scrollView.isFillViewport =
            true

        val root =
            LinearLayout(this)

        root.orientation =
            LinearLayout.VERTICAL

        root.gravity =
            Gravity.CENTER_HORIZONTAL

        root.setPadding(
            24,
            24,
            24,
            32
        )

        // =====================================================
        // TITLE
        // =====================================================

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
            matchWrap()
        )

        addSpace(
            root,
            18
        )

        // =====================================================
        // TOTAL ODOMETER LABEL
        // =====================================================

        val totalLabel =
            TextView(this)

        totalLabel.text =
            "TOTAL ODOMETER"

        totalLabel.textSize =
            15f

        totalLabel.setTextColor(
            Color.rgb(
                0,
                188,
                212
            )
        )

        totalLabel.typeface =
            Typeface.DEFAULT_BOLD

        totalLabel.gravity =
            Gravity.CENTER

        root.addView(
            totalLabel,
            matchWrap()
        )

        addSpace(
            root,
            4
        )

        // =====================================================
        // TOTAL ODOMETER VALUE
        // =====================================================

        totalText =
            TextView(this)

        totalText.text =
            "0.00 km"

        totalText.textSize =
            48f

        totalText.setTextColor(
            Color.WHITE
        )

        totalText.typeface =
            Typeface.DEFAULT_BOLD

        totalText.gravity =
            Gravity.CENTER

        totalText.includeFontPadding =
            true

        root.addView(
            totalText,
            LinearLayout.LayoutParams(
                -1,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(
            root,
            28
        )

        // =====================================================
        // SPEED SECTION
        // =====================================================

        val speedTitle =
            TextView(this)

        speedTitle.text =
            "CURRENT SPEED"

        speedTitle.textSize =
            15f

        speedTitle.setTextColor(
            Color.GRAY
        )

        speedTitle.gravity =
            Gravity.CENTER

        root.addView(
            speedTitle,
            matchWrap()
        )

        addSpace(
            root,
            4
        )

        speedText =
            TextView(this)

        speedText.text =
            "0.0 km/h"

        speedText.textSize =
            34f

        speedText.setTextColor(
            Color.WHITE
        )

        speedText.typeface =
            Typeface.DEFAULT_BOLD

        speedText.gravity =
            Gravity.CENTER

        root.addView(
            speedText,
            matchWrap()
        )

        addSpace(
            root,
            24
        )

        // =====================================================
        // GPS STATUS
        // =====================================================

        statusText =
            TextView(this)

        statusText.text =
            "GPS STATUS\nChecking..."

        statusText.textSize =
            16f

        statusText.setTextColor(
            Color.LTGRAY
        )

        statusText.gravity =
            Gravity.CENTER

        statusText.setPadding(
            20,
            16,
            20,
            16
        )

        root.addView(
            statusText,
            LinearLayout.LayoutParams(
                -1,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(
            root,
            20
        )

        // =====================================================
        // START / STOP BUTTON
        // =====================================================

        trackingButton =
            Button(this)

        trackingButton.text =
            "START TRACKING"

        trackingButton.textSize =
            17f

        trackingButton.setTextColor(
            Color.WHITE
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
            LinearLayout.LayoutParams(
                -1,
                58
            )
        )

        addSpace(
            root,
            12
        )

        // =====================================================
        // REFRESH BUTTON
        // =====================================================

        val refreshButton =
            Button(this)

        refreshButton.text =
            "REFRESH"

        refreshButton.textSize =
            16f

        refreshButton.setOnClickListener {

            refreshTotal()
        }

        root.addView(
            refreshButton,
            LinearLayout.LayoutParams(
                -1,
                54
            )
        )

        addSpace(
            root,
            22
        )

        // =====================================================
        // THRESHOLD
        // =====================================================

        val thresholdTitle =
            TextView(this)

        thresholdTitle.text =
            "ODOMETER SPEED THRESHOLD"

        thresholdTitle.textSize =
            13f

        thresholdTitle.setTextColor(
            Color.GRAY
        )

        thresholdTitle.gravity =
            Gravity.CENTER

        root.addView(
            thresholdTitle,
            matchWrap()
        )

        addSpace(
            root,
            3
        )

        val threshold =
            TextView(this)

        threshold.text =
            "%.1f km/h"
                .format(
                    database.getSpeedThreshold()
                )

        threshold.textSize =
            19f

        threshold.setTextColor(
            Color.WHITE
        )

        threshold.typeface =
            Typeface.DEFAULT_BOLD

        threshold.gravity =
            Gravity.CENTER

        root.addView(
            threshold,
            matchWrap()
        )

        addSpace(
            root,
            30
        )

        // =====================================================
        // FOOTER
        // =====================================================

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
            matchWrap()
        )

        scrollView.addView(
            root,
            ScrollView.LayoutParams(
                -1,
                -1
            )
        )

        setContentView(
            scrollView
        )
    }

    // =========================================================
    // TRACKING
    // =========================================================

    private fun startTracking() {

        if (
            !hasLocationPermission()
        ) {

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

            statusText.text =
                "GPS STATUS\nTRACKING ACTIVE"

        } catch (_: Exception) {

            statusText.text =
                "GPS STATUS\nUnable to start tracking"
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

        trackingActive =
            false

        trackingButton.text =
            "START TRACKING"

        statusText.text =
            "GPS STATUS\nTRACKING STOPPED"

        speedText.text =
            "0.0 km/h"

        refreshTotal()
    }

    // =========================================================
    // LOCATION PERMISSION
    // =========================================================

    private fun requestLocationPermission() {

        if (
            !hasLocationPermission()
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

        return ContextCompat
            .checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
            PackageManager.PERMISSION_GRANTED
    }

    // =========================================================
    // DISPLAY
    // =========================================================

    private fun refreshTotal() {

        totalText.text =
            "%.2f km"
                .format(
                    database.getTotalOdometer()
                )
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private fun matchWrap():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            -1,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun addSpace(
        parent: LinearLayout,
        height: Int
    ) {

        val spacer =
            View(this)

        parent.addView(
            spacer,
            LinearLayout.LayoutParams(
                1,
                height
            )
        )
    }

    // =========================================================
    // LIFECYCLE
    // =========================================================

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

        refreshTotal()
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
