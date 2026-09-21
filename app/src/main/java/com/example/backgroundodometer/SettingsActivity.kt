package com.example.backgroundodometer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
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

class SettingsActivity : Activity() {

    private lateinit var database: OdometerDatabaseHelper

    private val prefs by lazy {
        getSharedPreferences("background_odometer", MODE_PRIVATE)
    }

    private val cyan = Color.rgb(0, 188, 212)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = OdometerDatabaseHelper(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::database.isInitialized) buildUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.BLACK)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(24, 40, 24, 40)

        root.addView(title("SETTINGS"), params())
        space(root, 25)

        root.addView(label("ODOMETER SPEED THRESHOLD"), params())
        root.addView(value("%.1f km/h".format(database.getSpeedThreshold())), params())
        space(root, 10)

        root.addView(button("CHANGE SPEED THRESHOLD") {
            showSpeedDialog()
        }, full(58))

        space(root, 25)
        root.addView(label("DISTANCE ALERT"), params())
        val enabled = database.isDistanceAlertEnabled()
        val target = database.getDistanceAlertTarget()
        root.addView(
            value(if (enabled && target > 0) "ON • %.2f km".format(target) else "OFF"),
            params()
        )
        space(root, 10)
        root.addView(button("SET DISTANCE ALERT") {
            showDistanceAlertDialog()
        }, full(58))

        space(root, 10)
        root.addView(button("DISABLE DISTANCE ALERT") {
            database.setDistanceAlert(false, 0.0)
            database.resetDistanceAlertTrigger()
            Toast.makeText(this, "Distance alert disabled", Toast.LENGTH_SHORT).show()
            buildUi()
        }, full(58))

        space(root, 25)
        root.addView(label("ODOMETER RESET"), params())
        root.addView(
            value("Resets the displayed total only. Trips and routes remain saved."),
            params()
        )
        space(root, 10)
        root.addView(button("CLEAR TOTAL ODOMETER") {
            confirmClearOdometer()
        }, full(58))

        space(root, 25)
        root.addView(label("DATA"), params())
        root.addView(
            value("Delete every trip, route point, fuel record and setting."),
            params()
        )
        space(root, 10)
        root.addView(button("DELETE ALL DATA") {
            confirmDeleteAll()
        }, full(58))

        space(root, 25)
        root.addView(button("OPEN APP SETTINGS") {
            openAppSettings()
        }, full(58))

        space(root, 10)
        root.addView(button("BATTERY OPTIMIZATION SETTINGS") {
            openBatterySettings()
        }, full(58))

        space(root, 35)
        root.addView(value("App by Potato's man"), params())

        scroll.addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(scroll)
    }

    private fun showSpeedDialog() {
        val input = EditText(this)
        input.hint = "km/h"
        input.inputType = 2 or 8192
        input.setText(database.getSpeedThreshold().toString())

        AlertDialog.Builder(this)
            .setTitle("SPEED THRESHOLD")
            .setMessage("Distance is accumulated when movement reaches this speed threshold.")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                val v = input.text.toString().toDoubleOrNull()
                if (v == null || v <= 0) {
                    Toast.makeText(this, "Enter a valid speed", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                database.setSpeedThreshold(v)
                Toast.makeText(this, "Speed threshold saved", Toast.LENGTH_SHORT).show()
                buildUi()
            }
            .show()
    }

    private fun showDistanceAlertDialog() {
        val input = EditText(this)
        input.hint = "Target odometer (km)"
        input.inputType = 2 or 8192
        val old = database.getDistanceAlertTarget()
        if (old > 0) input.setText(old.toString())

        AlertDialog.Builder(this)
            .setTitle("DISTANCE ALERT")
            .setMessage("You will be alerted when the displayed odometer reaches this value.")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                val target = input.text.toString().toDoubleOrNull()
                if (target == null || target <= 0) {
                    Toast.makeText(this, "Enter a valid target", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                database.setDistanceAlert(true, target)
                database.resetDistanceAlertTrigger()
                Toast.makeText(this, "Distance alert enabled", Toast.LENGTH_SHORT).show()
                buildUi()
            }
            .show()
    }

    private fun confirmClearOdometer() {
        AlertDialog.Builder(this)
            .setTitle("CLEAR TOTAL ODOMETER?")
            .setMessage("The displayed total will become 0 km. Existing trips and routes will remain saved.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CLEAR") { _, _ ->
                database.clearDisplayedOdometer()
                Toast.makeText(this, "Displayed odometer cleared", Toast.LENGTH_SHORT).show()
                buildUi()
            }
            .show()
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle("DELETE ALL DATA?")
            .setMessage("This permanently deletes all trips, route points, fuel records and saved settings. This cannot be undone.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE EVERYTHING") { _, _ ->
                prefs.edit().clear().apply()
                database.deleteAllData()
                Toast.makeText(this, "All data deleted", Toast.LENGTH_LONG).show()
                buildUi()
            }
            .show()
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (_: Exception) { }
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    private fun title(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
    }

    private fun label(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(cyan)
            gravity = Gravity.CENTER
        }
    }

    private fun value(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 19f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
        }
    }

    private fun button(text: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(22, 22, 22))
            setPadding(10, 10, 10, 10)
            setOnClickListener { action() }
        }
    }

    private fun params() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun full(height: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        height
    )

    private fun space(parent: LinearLayout, dp: Int) {
        val density = resources.displayMetrics.density
        parent.addView(
            View(this),
            LinearLayout.LayoutParams(1, (dp * density).toInt())
        )
    }
}
