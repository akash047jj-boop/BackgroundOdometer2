package com.example.backgroundodometer

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
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
                        "CURRENT SPEED\n%.1f km/h"
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

    private fun buildInterface() {

        val root =
            LinearLayout(this)

        root.orientation =
            LinearLayout.VERTICAL

        root.gravity =
            Gravity.CENTER_HORIZONTAL

        root.setBackgroundColor(
            Color.BLACK
        )

        root.setPadding(
            30,
            40,
            30,
            30
        )

        val title =
            TextView(this)

        title.text =
            "BACKGROUND ODOMETER"

        title.textSize =
            22f

        title.setTextColor(
            Color.WHITE
        )

        title.gravity =
            Gravity.CENTER

        root.addView(
            title,
            LinearLayout.LayoutParams(
                -1,
                80
            )
        )

        val label =
            TextView(this)

        label.text =
            "TOTAL ODOMETER"

        label.textSize =
            15f

        label.setTextColor(
            Color.GRAY
        )

        label.gravity =
            Gravity.CENTER

        root.addView(label)

        totalText =
            TextView(this)

        totalText.textSize =
            55f

        totalText.setTextColor(
            Color.WHITE
        )

        totalText.gravity =
            Gravity.CENTER

        root.addView(
            totalText,
            LinearLayout.LayoutParams(
                -1,
                130
            )
        )

        speedText =
            TextView(this)

        speedText.text =
            "CURRENT SPEED\n0.0 km/h"

        speedText.textSize =
            20f

        speedText.setTextColor(
            Color.WHITE
        )

        speedText.gravity =
            Gravity.CENTER

        root.addView(
            speedText,
            LinearLayout.LayoutParams(
                -1,
                100
            )
        )

        statusText =
            TextView(this)

        statusText.text =
            "GPS: Checking..."

        statusText.textSize =
            17f

        statusText.setTextColor(
            Color.LTGRAY
        )

        statusText.gravity =
            Gravity.CENTER

        root.addView(
            statusText,
            LinearLayout.LayoutParams(
                -1,
                70
            )
        )

        trackingButton =
            Button(this)

        trackingButton.text =
            "START TRACKING"

        trackingButton.textSize =
            18f

        trackingButton.setOnClickListener {

            startTracking()
        }

        root.addView(
            trackingButton,
            LinearLayout.LayoutParams(
                -1,
                70
            )
        )

        val threshold =
            TextView(this)

        threshold.text =
            "Odometer threshold: %.1f km/h"
                .format(
                    database.getSpeedThreshold()
                )

        threshold.textSize =
            15f

        threshold.setTextColor(
            Color.GRAY
        )

        threshold.gravity =
            Gravity.CENTER

        root.addView(threshold)

        val refresh =
            Button(this)

        refresh.text =
            "REFRESH"

        refresh.setOnClickListener {
            refreshTotal()
        }

        root.addView(refresh)

        val footer =
            TextView(this)

        footer.text =
            "App by Potato's man"

        footer.setTextColor(
            Color.GRAY
        )

        footer.gravity =
            Gravity.CENTER

        root.addView(
            footer,
            LinearLayout.LayoutParams(
                -1,
                60
            )
        )

        setContentView(root)
    }

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

        ContextCompat.startForegroundService(
            this,
            intent
        )

        trackingButton.text =
            "TRACKING ACTIVE"

        statusText.text =
            "GPS TRACKING ACTIVE"
    }

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

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) ==
            PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) ==
                    PackageManager.PERMISSION_GRANTED
    }

    private fun refreshTotal() {

        totalText.text =
            "%.2f km"
                .format(
                    database.getTotalOdometer()
                )
    }

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

        database.close()

        super.onDestroy()
    }
}
