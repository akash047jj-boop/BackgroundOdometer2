package com.example.backgroundodometer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class SettingsActivity : Activity() {

    private lateinit var database: OdometerDatabaseHelper

    private lateinit var thresholdText: TextView
    private lateinit var autoTrackingText: TextView
    private lateinit var backgroundLocationText: TextView
    private lateinit var batteryText: TextView
    private lateinit var alertText: TextView

    private val preferences by lazy {
        getSharedPreferences(
            "background_odometer",
            MODE_PRIVATE
        )
    }

    companion object {
        private const val TIFFANY = "#00BCD4"
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        database =
            OdometerDatabaseHelper(this)

        buildInterface()
        refreshInterface()
    }

    override fun onResume() {
        super.onResume()

        if (::database.isInitialized) {
            refreshInterface()
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

        root.setPadding(
            20,
            35,
            20,
            35
        )

        root.gravity =
            Gravity.CENTER_HORIZONTAL

        root.addView(
            createTitle("SETTINGS"),
            wrapParams()
        )

        addSpace(root, 25)

        root.addView(
            createSection("TRACKING"),
            wrapParams()
        )

        addSpace(root, 10)

        root.addView(
            createLabel(
                "Odometer speed threshold",
                "#AAAAAA"
            ),
            wrapParams()
        )

        thresholdText =
            createValue("6.0 km/h")

        root.addView(
            thresholdText,
            wrapParams()
        )

        addSpace(root, 8)

        val thresholdButton =
            createAction(
                "CHANGE SPEED THRESHOLD"
            )

        thresholdButton.setOnClickListener {
            showThresholdDialog()
        }

        root.addView(
            thresholdButton,
            fullParams(58)
        )

        addSpace(root, 12)

        root.addView(
            createLabel(
                "Automatic background tracking",
                "#AAAAAA"
            ),
            wrapParams()
        )

        autoTrackingText =
            createValue("OFF")

        root.addView(
            autoTrackingText,
            wrapParams()
        )

        addSpace(root, 8)

        val autoButton =
            createAction(
                "TOGGLE AUTOMATIC TRACKING"
            )

        autoButton.setOnClickListener {
            toggleAutoTracking()
        }

        root.addView(
            autoButton,
            fullParams(58)
        )

        addSpace(root, 25)

        root.addView(
            createSection("PERMISSIONS & BACKGROUND"),
            wrapParams()
        )

        addSpace(root, 10)

        backgroundLocationText =
            createValue("Checking...")

        root.addView(
            backgroundLocationText,
            wrapParams()
        )

        addSpace(root, 8)

        val backgroundButton =
            createAction(
                "OPEN LOCATION PERMISSIONS"
            )

        backgroundButton.setOnClickListener {
            openAppSettings()
        }

        root.addView(
            backgroundButton,
            fullParams(58)
        )

        addSpace(root, 12)

        batteryText =
            createValue("Checking...")

        root.addView(
            batteryText,
            wrapParams()
        )

        addSpace(root, 8)

        val batteryButton =
            createAction(
                "BATTERY OPTIMIZATION SETTINGS"
            )

        batteryButton.setOnClickListener {
            requestBatteryOptimization()
        }

        root.addView(
            batteryButton,
            fullParams(58)
        )

        addSpace(root, 25)

        root.addView(
            createSection("DISTANCE ALERT"),
            wrapParams()
        )

        addSpace(root, 10)

        alertText =
            createValue("OFF")

        root.addView(
            alertText,
            wrapParams()
        )

        addSpace(root, 8)

        val alertButton =
            createAction(
                "DISTANCE ALERT SETTINGS"
            )

        alertButton.setOnClickListener {
            showDistanceAlertDialog()
        }

        root.addView(
            alertButton,
            fullParams(58)
        )

        addSpace(root, 25)

        root.addView(
            createSection("ODOMETER DATA"),
            wrapParams()
        )

        addSpace(root, 10)

        val clearButton =
            createAction(
                "CLEAR TOTAL ODOMETER"
            )

        clearButton.setOnClickListener {
            confirmClearOdometer()
        }

        root.addView(
            clearButton,
            fullParams(58)
        )

        addSpace(root, 10)

        val deleteButton =
            createDangerAction(
                "DELETE ALL DATA"
            )

        deleteButton.setOnClickListener {
            confirmDeleteAllData()
        }

        root.addView(
            deleteButton,
            fullParams(58)
        )

        addSpace(root, 30)

        root.addView(
            createLabel(
                "Background Odometer V13",
                "#666666"
            ),
            wrapParams()
        )

        scroll.addView(
            root,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(scroll)
    }

    private fun refreshInterface() {

        val threshold =
            database.getSpeedThreshold()

        thresholdText.text =
            "%.1f km/h".format(threshold)

        val autoTracking =
            preferences.getBoolean(
                "auto_tracking_enabled",
                false
            )

        autoTrackingText.text =
            if (autoTracking) {
                "ON"
            } else {
                "OFF"
            }

        autoTrackingText.setTextColor(
            if (autoTracking) {
                Color.parseColor(TIFFANY)
            } else {
                Color.LTGRAY
            }
        )

        val backgroundGranted =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        backgroundLocationText.text =
            if (backgroundGranted) {
                "Background location: ALLOWED"
            } else {
                "Background location: NOT ALLOWED"
            }

        backgroundLocationText.setTextColor(
            if (backgroundGranted) {
                Color.parseColor(TIFFANY)
            } else {
                Color.LTGRAY
            }
        )

        val powerManager =
            getSystemService(
                Context.POWER_SERVICE
            ) as PowerManager

        val batteryIgnored =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.M
            ) {
                powerManager.isIgnoringBatteryOptimizations(
                    packageName
                )
            } else {
                true
            }

        batteryText.text =
            if (batteryIgnored) {
                "Battery optimization: DISABLED"
            } else {
                "Battery optimization: ACTIVE"
            }

        batteryText.setTextColor(
            if (batteryIgnored) {
                Color.parseColor(TIFFANY)
            } else {
                Color.LTGRAY
            }
        )

        val alertEnabled =
            database.isDistanceAlertEnabled()

        val target =
            database.getDistanceAlertTarget()

        alertText.text =
            if (alertEnabled && target > 0.0) {
                "ON — %.2f km".format(target)
            } else {
                "OFF"
            }

        alertText.setTextColor(
            if (alertEnabled && target > 0.0) {
                Color.parseColor(TIFFANY)
            } else {
                Color.LTGRAY
            }
        )
    }

    private fun toggleAutoTracking() {

        val current =
            preferences.getBoolean(
                "auto_tracking_enabled",
                false
            )

        val newValue =
            !current

        if (newValue) {

            val fine =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) ==
                    PackageManager.PERMISSION_GRANTED

            val coarse =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) ==
                    PackageManager.PERMISSION_GRANTED

            if (!fine && !coarse) {

                Toast.makeText(
                    this,
                    "Location permission is required first.",
                    Toast.LENGTH_LONG
                ).show()

                openAppSettings()

                return
            }
        }

        preferences.edit()
            .putBoolean(
                "auto_tracking_enabled",
                newValue
            )
            .apply()

        if (newValue) {

            try {

                ContextCompat.startForegroundService(
                    this,
                    Intent(
                        this,
                        LocationTrackingService::class.java
                    )
                )

            } catch (_: Exception) {
            }

            Toast.makeText(
                this,
                "Automatic tracking enabled.",
                Toast.LENGTH_SHORT
            ).show()

        } else {

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

            Toast.makeText(
                this,
                "Automatic tracking disabled.",
                Toast.LENGTH_SHORT
            ).show()
        }

        refreshInterface()
    }

    private fun showThresholdDialog() {

        val input =
            EditText(this)

        input.hint =
            "Speed in km/h"

        input.inputType =
            2 or 8192

        input.setText(
            database.getSpeedThreshold()
                .toString()
        )

        val container =
            LinearLayout(this)

        container.orientation =
            LinearLayout.VERTICAL

        container.setPadding(
            35,
            10,
            35,
            10
        )

        container.addView(
            input,
            wrapParams()
        )

        AlertDialog.Builder(this)
            .setTitle(
                "ODOMETER SPEED THRESHOLD"
            )
            .setMessage(
                "Distance is added to the odometer only when movement is at or above this speed."
            )
            .setView(container)
            .setNegativeButton(
                "CANCEL",
                null
            )
            .setPositiveButton(
                "SAVE"
            ) { _, _ ->

                val value =
                    input.text
                        .toString()
                        .toDoubleOrNull()

                if (
                    value == null ||
                    value < 0.0 ||
                    value > 200.0
                ) {

                    Toast.makeText(
                        this,
                        "Enter a value between 0 and 200 km/h.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@setPositiveButton
                }

                database.setSpeedThreshold(
                    value
                )

                refreshInterface()

                Toast.makeText(
                    this,
                    "Speed threshold saved.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun showDistanceAlertDialog() {

        val container =
            LinearLayout(this)

        container.orientation =
            LinearLayout.VERTICAL

        container.setPadding(
            35,
            10,
            35,
            10
        )

        val target =
            EditText(this)

        target.hint =
            "Target odometer (km)"

        target.inputType =
            2 or 8192

        val currentTarget =
            database.getDistanceAlertTarget()

        if (currentTarget > 0.0) {
            target.setText(
                currentTarget.toString()
            )
        }

        container.addView(
            target,
            wrapParams()
        )

        val enabled =
            database.isDistanceAlertEnabled()

        AlertDialog.Builder(this)
            .setTitle(
                "DISTANCE ALERT"
            )
            .setMessage(
                "You will receive a notification when the odometer reaches the target."
            )
            .setView(container)
            .setNegativeButton(
                "DISABLE"
            ) { _, _ ->

                database.setDistanceAlert(
                    0.0,
                    false
                )

                refreshInterface()
            }
            .setPositiveButton(
                if (enabled) {
                    "UPDATE"
                } else {
                    "ENABLE"
                }
            ) { _, _ ->

                val value =
                    target.text
                        .toString()
                        .toDoubleOrNull()

                if (
                    value == null ||
                    value <= 0.0
                ) {

                    Toast.makeText(
                        this,
                        "Enter a valid target distance.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@setPositiveButton
                }

                database.setDistanceAlert(
                    value,
                    true
                )

                refreshInterface()

                Toast.makeText(
                    this,
                    "Distance alert enabled at %.2f km."
                        .format(value),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun confirmClearOdometer() {

        AlertDialog.Builder(this)
            .setTitle(
                "CLEAR TOTAL ODOMETER?"
            )
            .setMessage(
                "The displayed total odometer will return to 0.00 km. Your trips, routes, fuel records and history will NOT be deleted."
            )
            .setNegativeButton(
                "CANCEL",
                null
            )
            .setPositiveButton(
                "CLEAR"
            ) { _, _ ->

                database.clearDisplayedOdometer()

                Toast.makeText(
                    this,
                    "Total odometer cleared.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun confirmDeleteAllData() {

        AlertDialog.Builder(this)
            .setTitle(
                "DELETE ALL DATA?"
            )
            .setMessage(
                "This permanently deletes trips, routes, fuel records, daily assignments, odometer data and app settings. This cannot be undone."
            )
            .setNegativeButton(
                "CANCEL",
                null
            )
            .setPositiveButton(
                "DELETE ALL"
            ) { _, _ ->

                preferences.edit()
                    .putBoolean(
                        "auto_tracking_enabled",
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

                database.deleteAllData()

                Toast.makeText(
                    this,
                    "All data deleted.",
                    Toast.LENGTH_LONG
                ).show()

                refreshInterface()
            }
            .show()
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

    private fun requestBatteryOptimization() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.M
        ) {
            return
        }

        try {

            val powerManager =
                getSystemService(
                    Context.POWER_SERVICE
                ) as PowerManager

            if (
                powerManager.isIgnoringBatteryOptimizations(
                    packageName
                )
            ) {

                Toast.makeText(
                    this,
                    "Battery optimization is already disabled for this app.",
                    Toast.LENGTH_LONG
                ).show()

                return
            }

            val intent =
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                )

            intent.data =
                Uri.parse(
                    "package:$packageName"
                )

            startActivity(intent)

        } catch (_: Exception) {

            try {

                startActivity(
                    Intent(
                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                    )
                )

            } catch (_: Exception) {
            }
        }
    }

    private fun createTitle(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text =
                text

            textSize =
                27f

            setTextColor(
                Color.WHITE
            )

            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )

            gravity =
                Gravity.CENTER
        }
    }

    private fun createSection(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text =
                text

            textSize =
                17f

            setTextColor(
                Color.parseColor(TIFFANY)
            )

            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )

            gravity =
                Gravity.CENTER
        }
    }

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
                Color.parseColor(color)
            )

            gravity =
                Gravity.CENTER
        }
    }

    private fun createValue(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text =
                text

            textSize =
                20f

            setTextColor(
                Color.WHITE
            )

            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )

            gravity =
                Gravity.CENTER
        }
    }

    private fun createAction(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text =
                text

            textSize =
                16f

            setTextColor(
                Color.WHITE
            )

            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )

            gravity =
                Gravity.CENTER

            setPadding(
                10,
                10,
                10,
                10
            )

            background =
                android.graphics.drawable.GradientDrawable().apply {

                    setColor(
                        Color.rgb(
                            25,
                            25,
                            25
                        )
                    )

                    setStroke(
                        2,
                        Color.parseColor(TIFFANY)
                    )

                    cornerRadius =
                        14f
                }
        }
    }

    private fun createDangerAction(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text =
                text

            textSize =
                16f

            setTextColor(
                Color.WHITE
            )

            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )

            gravity =
                Gravity.CENTER

            setPadding(
                10,
                10,
                10,
                10
            )

            background =
                android.graphics.drawable.GradientDrawable().apply {

                    setColor(
                        Color.rgb(
                            45,
                            15,
                            15
                        )
                    )

                    setStroke(
                        2,
                        Color.rgb(
                            180,
                            50,
                            50
                        )
                    )

                    cornerRadius =
                        14f
                }
        }
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

    private fun wrapParams():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun fullParams(
        height: Int
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            height
        )
    }
}
