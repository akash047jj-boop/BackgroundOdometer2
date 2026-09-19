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

    // =====================================================
    // MAIN INTERFACE
    // =====================================================

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

        /*
         * Extra top padding keeps the UI below the
         * Android status bar on newer Android versions.
         */
        root.setPadding(
            24,
            65,
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
            wrapContentParams()
        )

        addSpace(root, 24)

        // =================================================
        // TOTAL ODOMETER
        // =================================================

        val totalLabel =
            TextView(this)

        totalLabel.text =
            "TOTAL ODOMETER"

        totalLabel.textSize =
            15f

        totalLabel.setTextColor(
            Color.parseColor(TIFFANY)
        )

        totalLabel.typeface =
            Typeface.DEFAULT_BOLD

        totalLabel.gravity =
            Gravity.CENTER

        root.addView(
            totalLabel,
            wrapContentParams()
        )

        addSpace(root, 4)

        totalText =
            TextView(this)

        totalText.text =
            "0.00 km"

        totalText.textSize =
            46f

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

        // =================================================
        // CURRENT SPEED
        // =================================================

        val speedLabel =
            TextView(this)

        speedLabel.text =
            "CURRENT SPEED"

        speedLabel.textSize =
            15f

        speedLabel.setTextColor(
            Color.GRAY
        )

        speedLabel.gravity =
            Gravity.CENTER

        root.addView(
            speedLabel,
            wrapContentParams()
        )

        addSpace(root, 4)

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
            wrapContentParams()
        )

        addSpace(root, 28)

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

        statusText.setPadding(
            10,
            8,
            10,
            8
        )

        root.addView(
            statusText,
            wrapContentParams()
        )

        addSpace(root, 22)

        // =================================================
        // START / STOP BUTTON
        // =================================================

        trackingButton =
            createActionButton(
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
            fullWidthParams(58)
        )

        addSpace(root, 12)

        // =================================================
        // REFRESH BUTTON
        // =================================================

        val refreshButton =
            createActionButton(
                "REFRESH"
            )

        refreshButton.setOnClickListener {

            refreshTotal()
        }

        root.addView(
            refreshButton,
            fullWidthParams(58)
        )

        addSpace(root, 28)

        // =================================================
        // SPEED THRESHOLD
        // =================================================

        val thresholdLabel =
            TextView(this)

        thresholdLabel.text =
            "ODOMETER SPEED THRESHOLD"

        thresholdLabel.textSize =
            13f

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

        val threshold =
            TextView(this)

        threshold.text =
            "%.1f km/h".format(
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
            wrapContentParams()
        )

        addSpace(root, 35)

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

    // =====================================================
    // BUTTON CREATION
    // =====================================================

    private fun createActionButton(
        text: String
    ): Button {

        return Button(this).apply {

            this.text = text

            textSize = 16f

            setTextColor(
                Color.WHITE
            )

            typeface =
                Typeface.DEFAULT_BOLD

            gravity =
                Gravity.CENTER

            isAllCaps = false

            setPadding(
                10,
                0,
                10,
                0
            )

            background =
                GradientDrawable().apply {

                    cornerRadius =
                        18f

                    setColor(
                        Color.parseColor(
                            BUTTON_DARK
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

            trackingButton.background =
                GradientDrawable().apply {

                    cornerRadius =
                        18f

                    setColor(
                        Color.parseColor(
                            DARK_TIFFANY
                        )
                    )

                    setStroke(
                        2,
                        Color.parseColor(
                            TIFFANY
                        )
                    )
                }

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
            LocationTrackingService.ACTION_STOP

        try {

            startService(intent)

        } catch (_: Exception) {
        }

        trackingActive =
            false

        trackingButton.text =
            "START TRACKING"

        trackingButton.background =
            GradientDrawable().apply {

                cornerRadius =
                    18f

                setColor(
                    Color.parseColor(
                        BUTTON_DARK
                    )
                )

                setStroke(
                    2,
                    Color.parseColor(
                        TIFFANY
                    )
                )
            }

        statusText.text =
            "GPS STATUS\nTRACKING STOPPED"

        statusText.setTextColor(
            Color.LTGRAY
        )

        speedText.text =
            "0.0 km/h"

        refreshTotal()
    }

    // =====================================================
    // PERMISSIONS
    // =====================================================

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

    private fun hasLocationPermission():
        Boolean {

        return ContextCompat
            .checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
            PackageManager.PERMISSION_GRANTED
    }

    // =====================================================
    // REFRESH
    // =====================================================

    private fun refreshTotal() {

        totalText.text =
            "%.2f km".format(
                database.getTotalOdometer()
            )
    }

    // =====================================================
    // LAYOUT HELPERS
    // =====================================================

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
