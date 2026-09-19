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
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var database: OdometerDatabaseHelper

    private lateinit var totalText: TextView
    private lateinit var speedText: TextView
    private lateinit var statusText: TextView
    private lateinit var trackingButton: Button

    private var trackingActive = false

    private val receiver = object : BroadcastReceiver() {

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

                refreshTotal()
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

        refreshTotal()
    }

    private fun buildInterface() {

        val scrollView =
            ScrollView(this)

        scrollView.setBackgroundColor(
            Color.BLACK
        )

        scrollView.isFillViewport = true

        val root =
            LinearLayout(this)

        root.orientation =
            LinearLayout.VERTICAL

        root.gravity =
            Gravity.CENTER_HORIZONTAL

        root.setPadding(
            24,
            30,
            24,
            40
        )

        // -------------------------------------------------
        // TITLE
        // -------------------------------------------------

        val title =
            TextView(this)

        title.text =
            "BACKGROUND ODOMETER"

        title.textSize = 24f

        title.setTextColor(
            Color.WHITE
        )

        title.typeface =
            Typeface.DEFAULT_BOLD

        title.gravity =
            Gravity.CENTER

        root.addView(
            title,
            wrapContentParams()
        )

        addSpace(root, 20)

        // -------------------------------------------------
        // TOTAL LABEL
        // -------------------------------------------------

        val totalLabel =
            TextView(this)

        totalLabel.text =
            "TOTAL ODOMETER"

        totalLabel.textSize = 15f

        totalLabel.setTextColor(
            Color.rgb(0, 188, 212)
        )

        totalLabel.typeface =
            Typeface.DEFAULT_BOLD

        totalLabel.gravity =
            Gravity.CENTER

        root.addView(
            totalLabel,
            wrapContentParams()
        )

        addSpace(root, 5)

        // -------------------------------------------------
        // TOTAL VALUE
        // -------------------------------------------------

        totalText =
            TextView(this)

        totalText.text =
            "0.00 km"

        totalText.textSize = 46f

        totalText.setTextColor(
            Color.WHITE
        )

        totalText.typeface =
            Typeface.DEFAULT_BOLD

        totalText.gravity =
            Gravity.CENTER

        root.addView(
            totalText,
            wrapContentParams()
        )

        addSpace(root, 30)

        // -------------------------------------------------
        // CURRENT SPEED LABEL
        // -------------------------------------------------

        val speedLabel =
            TextView(this)

        speedLabel.text =
            "CURRENT SPEED"

        speedLabel.textSize = 15f

        speedLabel.setTextColor(
            Color.GRAY
        )

        speedLabel.gravity =
            Gravity.CENTER

        root.addView(
            speedLabel,
            wrapContentParams()
        )

        addSpace(root, 5)

        // -------------------------------------------------
        // CURRENT SPEED
        // -------------------------------------------------

        speedText =
            TextView(this)

        speedText.text =
            "0.0 km/h"

        speedText.textSize = 34f

        speedText.setTextColor(
            Color.WHITE
        )

        speedText.typeface =
            Typeface.DEFAULT_BOLD

        speedText.gravity =
            Gravity.CENTER

        root.addView(
            speedText,
            wrapContentParams()
        )

        addSpace(root, 28)

        // -------------------------------------------------
        // GPS STATUS
        // -------------------------------------------------

        statusText =
            TextView(this)

        statusText.text =
            "GPS STATUS\nChecking..."

        statusText.textSize = 16f

        statusText.setTextColor(
            Color.LTGRAY
        )

        statusText.gravity =
            Gravity.CENTER

        statusText.setPadding(
            10,
            10,
            10,
            10
        )

        root.addView(
            statusText,
            wrapContentParams()
        )

        addSpace(root, 20)

        // -------------------------------------------------
        // START / STOP
        // -------------------------------------------------

        trackingButton =
            Button(this)

        trackingButton.text =
            "START TRACKING"

        trackingButton.textSize = 16f

        trackingButton.setOnClickListener {

            if (trackingActive) {

                stopTracking()

            } else {

                startTracking()
            }
        }

        root.addView(
            trackingButton,
            fullWidthParams(58)
        )

        addSpace(root, 12)

        // -------------------------------------------------
        // REFRESH
        // -------------------------------------------------

        val refreshButton =
            Button(this)

        refreshButton.text =
            "REFRESH"

        refreshButton.textSize = 16f

        refreshButton.setOnClickListener {

            refreshTotal()
        }

        root.addView(
            refreshButton,
            fullWidthParams(54)
        )

        addSpace(root, 25)

        // -------------------------------------------------
        // THRESHOLD LABEL
        // -------------------------------------------------

        val thresholdLabel =
            TextView(this)

        thresholdLabel.text =
            "ODOMETER SPEED THRESHOLD"

        thresholdLabel.textSize = 13f

        thresholdLabel.setTextColor(
            Color.GRAY
        )

        thresholdLabel.gravity =
            Gravity.CENTER

        root.addView(
            thresholdLabel,
            wrapContentParams()
        )

        addSpace(root, 5)

        // -------------------------------------------------
        // THRESHOLD VALUE
        // -------------------------------------------------

        val threshold =
            TextView(this)

        threshold.text =
            "%.1f km/h".format(
                database.getSpeedThreshold()
            )

        threshold.textSize = 19f

        threshold.setTextColor(
            Color.WHITE
        )

        threshold.typeface =
            Typeface.DEFAULT_BOLD

        threshold.gravity =
            Gravity.CENTER

        root.addView(
            threshold,
            wrapContentParams()
        )

        addSpace(root, 35)

        // -------------------------------------------------
        // FOOTER
        // -------------------------------------------------

        val footer =
            TextView(this)

        footer.text =
            "App by Potato's man"

        footer.textSize = 13f

        footer.setTextColor(
            Color.GRAY
        )

        footer.gravity =
            Gravity.CENTER

        root.addView(
            footer,
            wrapContentParams()
        )

        scrollView.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(scrollView)
    }

    // -----------------------------------------------------
    // START TRACKING
    // -----------------------------------------------------

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

            ContextCompat.startForegroundService(
                this,
                intent
            )

            trackingActive = true

            trackingButton.text =
                "STOP TRACKING"

            statusText.text =
                "GPS STATUS\nTRACKING ACTIVE"

        } catch (e: Exception) {

            statusText.text =
                "GPS STATUS\nUnable to start"
        }
    }

    // -----------------------------------------------------
    // STOP TRACKING
    // -----------------------------------------------------

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

        statusText.text =
            "GPS STATUS\nTRACKING STOPPED"

        speedText.text =
            "0.0 km/h"

        refreshTotal()
    }

    // -----------------------------------------------------
    // PERMISSIONS
    // -----------------------------------------------------

    private fun requestLocationPermission() {

        if (!hasLocationPermission()) {

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

    private fun hasLocationPermission(): Boolean {

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // -----------------------------------------------------
    // REFRESH
    // -----------------------------------------------------

    private fun refreshTotal() {

        totalText.text =
            "%.2f km".format(
                database.getTotalOdometer()
            )
    }

    // -----------------------------------------------------
    // LAYOUT HELPERS
    // -----------------------------------------------------

    private fun wrapContentParams():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun fullWidthParams(
        height: Int
    ):
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height
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

    // -----------------------------------------------------
    // LIFECYCLE
    // -----------------------------------------------------

    override fun onResume() {

        super.onResume()

        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(
                LocationTrackingService.ACTION_SPEED_UPDATE
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
