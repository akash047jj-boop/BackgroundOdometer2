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

       
