package com.example.backgroundodometer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.drawerlayout.widget.DrawerLayout

class MainActivity : Activity() {

    private lateinit var database:
        OdometerDatabaseHelper

    private lateinit var totalText:
        TextView

    private lateinit var speedText:
        TextView

    private lateinit var mileageText:
        TextView

    private lateinit var rangeText:
        TextView

    private lateinit var gpsText:
        TextView

    companion object {

        private const val LOCATION_REQUEST =
            500
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        database =
            OdometerDatabaseHelper(
                this
            )

        buildScreen()

        requestPermissionsIfNeeded()

        refresh()
    }

    private fun buildScreen() {

        val drawer =
            DrawerLayout(this)

        val content =
            LinearLayout(this)

        content.orientation =
            LinearLayout.VERTICAL

        content.setBackgroundColor(
            Color.BLACK
        )

        val toolbar =
            LinearLayout(this)

        toolbar.gravity =
            Gravity.CENTER_VERTICAL

        val menu =
            Button(this)

        menu.text = "☰"

        menu.setOnClickListener {

            drawer.openDrawer(
                Gravity.START
            )
        }

        toolbar.addView(
            menu,
            LinearLayout.LayoutParams(
                70,
                70
            )
        )

        val title =
            TextView(this)

        title.text =
            "BACKGROUND ODOMETER"

        title.textSize =
            19f

        title.setTextColor(
            Color.WHITE
        )

        title.gravity =
            Gravity.CENTER_VERTICAL

        toolbar.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                70,
                1f
            )
        )

        content.addView(
            toolbar
        )

        content.addView(
            label(
                "TOTAL ODOMETER"
            )
        )

        totalText =
            valueText(
                "0.00 km",
                48f
            )

        content.addView(
            totalText
        )

        speedText =
            valueText(
                "Current speed: 0.0 km/h",
                18f
            )

        content.addView(
            speedText
        )

        mileageText =
            valueText(
                "Mileage: -- km/L",
                18f
            )

        content.addView(
            mileageText
        )

        rangeText =
            valueText(
                "Reserve range: -- km",
                18f
            )

        content.addView(
            rangeText
        )

        gpsText =
            valueText(
                "GPS: checking...",
                17f
            )

        content.addView(
            gpsText
        )

        val start =
            Button(this)

        start.text =
            "START TRACKING"

        start.setOnClickListener {

            requestPermissionsIfNeeded()

            if (
                hasLocationPermission()
            ) {

                val intent =
                    Intent(
                        this,
                        LocationTrackingService::class.java
                    )

                startForegroundService(
                    intent
                )
            }
        }

        content.addView(
            start
        )

        val trips =
            Button(this)

        trips.text =
            "TRIPS"

        trips.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    TripsActivity::class.java
                )
            )
        }

        content.addView(
            trips
        )

        drawer.addView(
            content
        )

        val drawerMenu =
            LinearLayout(this)

        drawerMenu.orientation =
            LinearLayout.VERTICAL

        drawerMenu.setBackgroundColor(
            Color.rgb(
                20,
                20,
                20
            )
        )

        drawerMenu.setPadding(
            20,
            80,
            20,
            20
        )

        addMenu(
            drawerMenu,
            "HOME"
        ) {}

        addMenu(
            drawerMenu,
            "TRIPS"
        ) {

            startActivity(
                Intent(
                    this,
                    TripsActivity::class.java
                )
            )
        }

        addMenu(
            drawerMenu,
            "DAYS"
        ) {

            startActivity(
                Intent(
                    this,
                    DaysActivity::class.java
                )
            )
        }

        addMenu(
            drawerMenu,
            "FUEL"
        ) {

            startActivity(
                Intent(
                    this,
                    FuelActivity::class.java
                )
            )
        }

        addMenu(
            drawerMenu,
            "SETTINGS"
        ) {

            startActivity(
                Intent(
                    this,
                    SettingsActivity::class.java
                )
            )
        }

        drawer.addView(
            drawerMenu,
            DrawerLayout.LayoutParams(
                290,
                -1
            ).apply {
                gravity =
                    Gravity.START
            }
        )

        setContentView(
            drawer
        )
    }

    private fun addMenu(
        menu: LinearLayout,
        text: String,
        action: () -> Unit
    ) {

        val button =
            Button(this)

        button.text =
            text

        button.setTextColor(
            Color.WHITE
        )

        button.setOnClickListener {
            action()
        }

        menu.addView(
            button
        )
    }

    private fun label(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text =
                text

            textSize =
                14f

            setTextColor(
                Color.GRAY
            )

            gravity =
                Gravity.CENTER

            setPadding(
                10,
                20,
                10,
                10
            )
        }
    }

    private fun valueText(
        text: String,
        size: Float
    ): TextView {

        return TextView(this).apply {

            this.text =
                text

            textSize =
                size

            setTextColor(
                Color.WHITE
            )

            gravity =
                Gravity.CENTER

            setPadding(
                10,
                12,
                10,
                12
            )
        }
    }

    private fun hasLocationPermission():
        Boolean {

        return ActivityCompat
            .checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionsIfNeeded() {

        if (
            !hasLocationPermission()
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_REQUEST
            )
        }
    }

    private fun refresh() {

        totalText.text =
            "%.2f km".format(
                database.getTotalOdometer()
            )

        val mileage =
            database.getFuelBasedMileage()

        mileageText.text =
            if (mileage > 0) {
                "Mileage: %.2f km/L"
                    .format(mileage)
            } else {
                "Mileage: -- km/L"
            }

        val reserveRange =
            database.getReserveRange()

        rangeText.text =
            if (reserveRange > 0) {
                "Reserve range: %.1f km"
                    .format(
                        reserveRange
                    )
            } else {
                "Reserve range: -- km"
            }

        gpsText.text =
            "GPS tracking available"
    }

    override fun onResume() {

        super.onResume()

        if (
            ::database.isInitialized
        ) {
            refresh()
        }
    }
}
