package com.example.backgroundodometer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout

class MainActivity : Activity() {

    private lateinit var database: OdometerDatabaseHelper
    private lateinit var totalText: TextView
    private lateinit var todayText: TextView
    private lateinit var speedText: TextView
    private lateinit var averageText: TextView
    private lateinit var maxText: TextView
    private lateinit var statusText: TextView
    private lateinit var fuelText: TextView
    private lateinit var rangeText: TextView
    private lateinit var reserveStatusText: TextView
    private lateinit var belowReserveButton: TextView
    private lateinit var trackingButton: TextView
    private lateinit var drawerLayout: DrawerLayout

    private var trackingActive = false

    private val preferences by lazy {
        getSharedPreferences("background_odometer", MODE_PRIVATE)
    }

    companion object {
        private const val TIFFANY = "#00BCD4"
        private const val DARK = "#151515"
        private const val REQUEST_LOCATION = 100
        private const val REQUEST_NOTIFICATIONS = 101
        private const val PREF_AUTO_TRACKING = "auto_tracking_enabled"
        private const val PREF_BACKGROUND_HINT = "background_location_hint_shown"
    }

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                LocationTrackingService.ACTION_SPEED_UPDATE,
                LocationTrackingService.ACTION_STATUS_UPDATE -> {
                    val speed = intent.getDoubleExtra(LocationTrackingService.EXTRA_SPEED, 0.0)
                    speedText.text = "%.1f km/h".format(speed)
                    intent.getStringExtra(LocationTrackingService.EXTRA_STATUS)?.let { updateStatus(it) }
                    refreshData()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = OdometerDatabaseHelper(this)
        buildInterface()
        requestLocationPermission()
        refreshData()
    }

    private fun buildInterface() {
        drawerLayout = DrawerLayout(this)
        drawerLayout.setBackgroundColor(Color.BLACK)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.BLACK)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(20, 20, 20, 30)

        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.VERTICAL
        topBar.gravity = Gravity.CENTER_HORIZONTAL
        topBar.setPadding(0, dp(12), 0, 0)

        val title = createTitle("BACKGROUND ODOMETER")
        topBar.addView(title, wrapParams())

        val menu = createMenuButton("☰  MENU")
        menu.textSize = 18f
        topBar.addView(menu, buttonParams())

        val topBarParams = wrapParams().apply {
            topMargin = dp(10)
        }
        root.addView(topBar, topBarParams)
        addSpace(root, 18)

        root.addView(createLabel("TOTAL ODOMETER", TIFFANY), wrapParams())
        totalText = createLargeValue("0.00 km")
        root.addView(totalText, wrapParams())

        addSpace(root, 14)
        root.addView(createLabel("TODAY", "#888888"), wrapParams())
        todayText = createMediumValue("0.00 km")
        root.addView(todayText, wrapParams())

        addSpace(root, 14)
        root.addView(createLabel("CURRENT SPEED", "#888888"), wrapParams())
        speedText = createMediumValue("0.0 km/h")
        root.addView(speedText, wrapParams())

        addSpace(root, 8)
        val speedRow = LinearLayout(this)
        speedRow.orientation = LinearLayout.HORIZONTAL
        speedRow.gravity = Gravity.CENTER
        val avg = createStatColumn("AVERAGE", "0.0 km/h")
        averageText = avg.second
        val max = createStatColumn("MAXIMUM", "0.0 km/h")
        maxText = max.second
        speedRow.addView(avg.first, halfParams())
        speedRow.addView(max.first, halfParams())
        root.addView(speedRow, wrapParams())

        addSpace(root, 16)
        statusText = TextView(this).apply {
            text = "GPS STATUS\nREADY"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(statusText, wrapParams())

        addSpace(root, 12)
        trackingButton = createAction("START TRACKING")
        trackingButton.setOnClickListener {
            if (trackingActive) stopTracking() else startTracking()
        }
        root.addView(trackingButton, buttonParams())

        addSpace(root, 10)
        val fuelCard = LinearLayout(this)
        fuelCard.orientation = LinearLayout.VERTICAL
        fuelCard.setPadding(16, 14, 16, 14)
        fuelCard.background = cardBackground()
        fuelCard.addView(createLabel("FUEL", TIFFANY), wrapParams())
        val fuelStatusRow = LinearLayout(this)
        fuelStatusRow.orientation = LinearLayout.HORIZONTAL
        fuelStatusRow.gravity = Gravity.CENTER_VERTICAL
        fuelText = createMediumValue("0.00 L")
        fuelText.textSize = 22f
        fuelStatusRow.addView(fuelText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        reserveStatusText = createStatusText("ABOVE RESERVE", false)
        reserveStatusText.textSize = 12f
        fuelStatusRow.addView(reserveStatusText, LinearLayout.LayoutParams(dp(145), ViewGroup.LayoutParams.WRAP_CONTENT))
        fuelCard.addView(fuelStatusRow, wrapParams())
        rangeText = createLabel("Range: --", "#AAAAAA")
        fuelCard.addView(rangeText, wrapParams())

        belowReserveButton = createAction("BELOW RESERVE — TAP TO MARK")
        belowReserveButton.textSize = 14f
        fuelCard.addView(belowReserveButton, wrapParams())
        belowReserveButton.setOnClickListener {
            if (database.hasCurrentBelowReserveMarker()) {
                Toast.makeText(this, "Below-reserve point is already saved.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val autoDetected = database.isReserveReachedByCalculation()
            if (database.markCurrentFuelBelowReserve()) {
                val message = if (autoDetected) {
                    "Auto-detected below reserve confirmed and saved."
                } else {
                    "Manual below-reserve point saved for mileage calculation."
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                refreshData()
            } else {
                Toast.makeText(this, "Unable to save below-reserve point.", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(fuelCard, wrapParams())

        addSpace(root, 12)
        val refresh = createAction("REFRESH")
        refresh.setOnClickListener { refreshData() }
        root.addView(refresh, buttonParams())

        addSpace(root, 24)
        root.addView(createLabel("Use ☰ for Trips, Days, Fuel, Settings and Export", "#777777"), wrapParams())
        addSpace(root, 10)
        root.addView(createLabel("App by Potato's man", "#666666"), wrapParams())

        scroll.addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        drawerLayout.addView(scroll, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val drawer = buildNavigationDrawer()
        val drawerParams = DrawerLayout.LayoutParams(dp(310), ViewGroup.LayoutParams.MATCH_PARENT)
        drawerParams.gravity = Gravity.START
        drawerLayout.addView(drawer, drawerParams)

        menu.setOnClickListener { drawerLayout.openDrawer(Gravity.START) }
        setContentView(drawerLayout)
    }

    private fun buildNavigationDrawer(): LinearLayout {
        val drawer = LinearLayout(this)
        drawer.orientation = LinearLayout.VERTICAL
        drawer.setPadding(22, 35, 22, 24)
        drawer.setBackgroundColor(Color.rgb(10, 10, 10))

        drawer.addView(createTitle("MENU"), wrapParams())
        addSpace(drawer, 20)
        addDrawerItem(drawer, "HOME") { drawerLayout.closeDrawer(Gravity.START) }
        addDrawerItem(drawer, "TRIPS") { openActivity(TripsActivity::class.java) }
        addDrawerItem(drawer, "DAYS") { openActivity(DaysActivity::class.java) }
        addDrawerItem(drawer, "FUEL") { showFuelMenu() }
        addDrawerItem(drawer, "SETTINGS") { openActivity(SettingsActivity::class.java) }
        addDrawerItem(drawer, "EXPORT") { openActivity(ExportActivity::class.java) }
        addSpace(drawer, 25)
        drawer.addView(createLabel("Background Odometer V16", "#777777"), wrapParams())
        return drawer
    }

    private fun addDrawerItem(drawer: LinearLayout, text: String, action: () -> Unit) {
        val item = createAction(text)
        item.setOnClickListener {
            drawerLayout.closeDrawer(Gravity.START)
            action()
        }
        drawer.addView(item, buttonParams())
        addSpace(drawer, 8)
    }

    private fun openActivity(clazz: Class<*>) {
        startActivity(Intent(this, clazz))
    }

    private fun showFuelMenu() {
        AlertDialog.Builder(this)
            .setTitle("FUEL")
            .setItems(arrayOf("ADD FUEL", "FUEL SETTINGS", "FUEL HISTORY")) { _, which ->
                when (which) {
                    0 -> showAddFuelDialog()
                    1 -> showFuelSettingsDialog()
                    2 -> showFuelHistory()
                }
            }
            .show()
    }

    private fun showAddFuelDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(30, 10, 30, 10)
        val litres = EditText(this)
        litres.hint = "Litres added"
        litres.inputType = 2 or 8192
        layout.addView(litres, wrapParams())

        val statusLabel = createLabel("FUEL STATUS AFTER REFUELLING", "#AAAAAA")
        statusLabel.textSize = 13f
        layout.addView(statusLabel, wrapParams())

        val statusGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        val aboveRadio = RadioButton(this).apply {
            text = "ABOVE RESERVE"
            textSize = 13f
            setTextColor(Color.WHITE)
            id = View.generateViewId()
        }
        val belowRadio = RadioButton(this).apply {
            text = "BELOW RESERVE"
            textSize = 13f
            setTextColor(Color.WHITE)
            id = View.generateViewId()
        }
        statusGroup.addView(aboveRadio, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        statusGroup.addView(belowRadio, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val currentBelow = database.isReserveReached()
        statusGroup.check(if (currentBelow) belowRadio.id else aboveRadio.id)
        layout.addView(statusGroup, wrapParams())

        val note = EditText(this)
        note.hint = "Note (optional)"
        layout.addView(note, wrapParams())

        AlertDialog.Builder(this)
            .setTitle("ADD FUEL")
            .setView(layout)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("ADD") { _, _ ->
                val amount = litres.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(this, "Enter valid litres", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val status = if (statusGroup.checkedRadioButtonId == belowRadio.id) "BELOW" else "ABOVE"
                database.addFuel(amount, note.text.toString(), status)
                refreshData()
            }
            .show()
    }

    private fun showFuelSettingsDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(30, 10, 30, 10)
        val tank = EditText(this)
        tank.hint = "Tank capacity (L)"
        tank.inputType = 2 or 8192
        tank.setText(database.getTankCapacity().toString())
        layout.addView(tank, wrapParams())
        val reserve = EditText(this)
        reserve.hint = "Reserve fuel (L)"
        reserve.inputType = 2 or 8192
        reserve.setText(database.getReserveFuel().toString())
        layout.addView(reserve, wrapParams())
        AlertDialog.Builder(this)
            .setTitle("FUEL SETTINGS")
            .setView(layout)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                val tankValue = tank.text.toString().toDoubleOrNull()
                val reserveValue = reserve.text.toString().toDoubleOrNull()
                if (tankValue == null || reserveValue == null || tankValue <= 0 || reserveValue < 0 || reserveValue >= tankValue) {
                    Toast.makeText(this, "Invalid fuel settings", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                database.setFuelSettings(tankValue, reserveValue)
                refreshData()
            }
            .show()
    }

    private fun showFuelHistory() {
        val records = database.getFuelRecords()
        if (records.isEmpty()) {
            AlertDialog.Builder(this).setTitle("FUEL HISTORY").setMessage("No fuel records yet.").setPositiveButton("OK", null).show()
            return
        }
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(this)
        for (record in records) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(12, 12, 12, 12)
            val date = java.text.SimpleDateFormat("dd MMM yyyy hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(record.time))
            val text = TextView(this)
            val isMarker = record.litresAdded <= 0.0 && record.fuelStatus == "BELOW"
            val statusLine = if (record.fuelStatus == "BELOW") "BELOW RESERVE" else "ABOVE RESERVE"
            text.text = if (isMarker) {
                "$date\nBELOW RESERVE MARKER • Odometer %.2f km\n%s".format(record.odometerKm, record.note)
            } else {
                "$date\n+%.2f L • Fuel after %.2f L • %s\n%s".format(record.litresAdded, record.fuelAfterLitres, statusLine, record.note)
            }
            text.textSize = 15f
            text.setTextColor(Color.WHITE)
            row.addView(text, wrapParams())
            val buttons = LinearLayout(this)
            buttons.orientation = LinearLayout.HORIZONTAL
            val edit = createAction("EDIT")
            edit.setOnClickListener { showEditFuelDialog(record) }
            val delete = createAction("DELETE")
            delete.setOnClickListener {
                AlertDialog.Builder(this).setTitle("DELETE FUEL?").setMessage("Delete this fuel record?")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("DELETE") { _, _ -> database.deleteFuel(record.id); refreshData(); showFuelHistory() }.show()
            }
            buttons.addView(edit, halfParams())
            buttons.addView(delete, halfParams())
            row.addView(buttons, wrapParams())
            layout.addView(row, wrapParams())
        }
        scroll.addView(layout)
        AlertDialog.Builder(this).setTitle("FUEL HISTORY").setView(scroll).setPositiveButton("CLOSE", null).show()
    }

    private fun showEditFuelDialog(record: FuelRecord) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(30, 10, 30, 10)
        val litres = EditText(this)
        litres.setText(record.litresAdded.toString())
        litres.inputType = 2 or 8192
        layout.addView(litres, wrapParams())

        val statusLabel = createLabel("FUEL STATUS AFTER REFUELLING", "#AAAAAA")
        statusLabel.textSize = 13f
        layout.addView(statusLabel, wrapParams())
        val statusGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val aboveRadio = RadioButton(this).apply {
            text = "ABOVE RESERVE"; textSize = 13f; setTextColor(Color.WHITE); id = View.generateViewId()
        }
        val belowRadio = RadioButton(this).apply {
            text = "BELOW RESERVE"; textSize = 13f; setTextColor(Color.WHITE); id = View.generateViewId()
        }
        statusGroup.addView(aboveRadio, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        statusGroup.addView(belowRadio, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        statusGroup.check(if (record.fuelStatus == "BELOW") belowRadio.id else aboveRadio.id)
        layout.addView(statusGroup, wrapParams())

        val note = EditText(this)
        note.setText(record.note)
        layout.addView(note, wrapParams())
        AlertDialog.Builder(this).setTitle("EDIT FUEL").setView(layout)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                val value = litres.text.toString().toDoubleOrNull()
                if (value == null || value <= 0) return@setPositiveButton
                val status = if (statusGroup.checkedRadioButtonId == belowRadio.id) "BELOW" else "ABOVE"
                database.updateFuel(record.id, value, note.text.toString(), status)
                refreshData()
            }.show()
    }

    private fun startTracking() {
        if (!hasLocationPermission()) { requestLocationPermission(); return }
        if (!notificationsAllowed()) { requestNotificationPermission(); return }
        preferences.edit().putBoolean(PREF_AUTO_TRACKING, true).apply()
        try {
            ContextCompat.startForegroundService(this, Intent(this, LocationTrackingService::class.java))
            trackingActive = true
            trackingButton.text = "STOP TRACKING"
            trackingButton.background = buttonBackground(true)
            updateStatusFromCurrentGps()
            showBackgroundLocationHintOnce()
        } catch (_: Exception) {
            preferences.edit().putBoolean(PREF_AUTO_TRACKING, false).apply()
            trackingActive = false
            Toast.makeText(this, "Unable to start tracking", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopTracking() {
        preferences.edit().putBoolean(PREF_AUTO_TRACKING, false).apply()
        val intent = Intent(this, LocationTrackingService::class.java)
        intent.action = LocationTrackingService.ACTION_STOP
        try { startService(intent) } catch (_: Exception) { }
        trackingActive = false
        trackingButton.text = "START TRACKING"
        trackingButton.background = buttonBackground(false)
        statusText.text = "GPS STATUS\nTRACKING STOPPED"
        speedText.text = "0.0 km/h"
        refreshData()
    }

    private fun updateStatusFromCurrentGps() {
        updateStatus(if (isLocationEnabled()) "GPS ON\nTRACKING ACTIVE" else "GPS OFF\nWAITING")
    }

    private fun updateStatus(status: String) {
        statusText.text = "GPS STATUS\n$status"
        val lower = status.lowercase()
        statusText.setTextColor(if (lower.contains("tracking") || lower.contains("gps on")) Color.parseColor(TIFFANY) else Color.LTGRAY)
    }

    private fun isLocationEnabled(): Boolean {
        val manager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.isLocationEnabled
            else manager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { false }
    }

    private fun refreshData() {
        if (!::totalText.isInitialized) return
        totalText.text = "%.2f km".format(database.getTotalOdometer())
        todayText.text = "%.2f km".format(database.getTodayDistance())
        averageText.text = "%.1f km/h".format(database.getAverageSpeed())
        maxText.text = "%.1f km/h".format(database.getMaximumSpeed())
        speedText.text = "%.1f km/h".format(database.getCurrentSpeed())

        val fuel = database.getCurrentFuel()
        val tank = database.getTankCapacity()
        fuelText.text = "%.2f L / %.2f L".format(fuel, tank)
        val mileage = database.getAverageMileage()
        rangeText.text = if (mileage > 0) "Range: %.1f km • Mileage: %.2f km/L".format(database.getOverallRange(), mileage) else "Range: -- • Mileage: --"

        val autoBelow = database.isReserveReachedByCalculation()
        val userStatus = database.getCurrentFuelStatus()
        val below = userStatus == "BELOW" || autoBelow
        val currentStatus = when {
            userStatus == "BELOW" -> "BELOW RESERVE"
            autoBelow -> "BELOW RESERVE • AUTO DETECTED"
            else -> "ABOVE RESERVE"
        }
        reserveStatusText.text = currentStatus
        reserveStatusText.background = statusBackground(below)
        reserveStatusText.setTextColor(if (below) Color.WHITE else Color.parseColor(TIFFANY))

        belowReserveButton.visibility = View.VISIBLE
        when {
            database.hasCurrentBelowReserveMarker() -> {
                belowReserveButton.text = "BELOW RESERVE ✓ • MILEAGE POINT SAVED"
                belowReserveButton.isEnabled = false
            }
            autoBelow -> {
                belowReserveButton.text = "BELOW RESERVE — AUTO-DETECTED • TAP TO CONFIRM"
                belowReserveButton.isEnabled = true
            }
            else -> {
                belowReserveButton.text = "MARK BELOW RESERVE MANUALLY"
                belowReserveButton.isEnabled = true
            }
        }
        belowReserveButton.alpha = if (belowReserveButton.isEnabled) 1f else 0.7f
    }

    private fun showBackgroundLocationHintOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) return
        if (preferences.getBoolean(PREF_BACKGROUND_HINT, false)) return
        preferences.edit().putBoolean(PREF_BACKGROUND_HINT, true).apply()
        AlertDialog.Builder(this)
            .setTitle("Background location")
            .setMessage("For the strongest automatic tracking support, including restarting after a phone reboot, set Background Odometer location permission to \"Allow all the time\" in Android settings.")
            .setNegativeButton("LATER", null)
            .setPositiveButton("OPEN SETTINGS") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (_: Exception) { }
            }.show()
    }

    private fun notificationsAllowed(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) NotificationManagerCompat.from(this).areNotificationsEnabled() else true

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        } else openNotificationSettings()
    }

    private fun openNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        } catch (_: Exception) { }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
        }
    }

    private fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) Toast.makeText(this, if (notificationsAllowed()) "Notifications enabled." else "Please allow notifications.", Toast.LENGTH_LONG).show()
    }

    private fun createTitle(text: String) = TextView(this).apply {
        this.text = text; textSize = 23f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
    }

    private fun createLabel(text: String, color: String) = TextView(this).apply {
        this.text = text; textSize = 14f; setTextColor(Color.parseColor(color)); gravity = Gravity.CENTER
    }

    private fun createLargeValue(text: String) = TextView(this).apply {
        this.text = text; textSize = 43f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
    }

    private fun createMediumValue(text: String) = TextView(this).apply {
        this.text = text; textSize = 23f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
    }

    private fun createStatColumn(label: String, value: String): Pair<LinearLayout, TextView> {
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL; column.gravity = Gravity.CENTER
        column.addView(createLabel(label, "#777777"), wrapParams())
        val v = createMediumValue(value); v.textSize = 18f
        column.addView(v, wrapParams())
        return Pair(column, v)
    }

    private fun createAction(text: String) = TextView(this).apply {
        this.text = text; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; background = buttonBackground(false); minHeight = dp(58); setPadding(8, 12, 8, 12)
    }

    private fun createMenuButton(text: String) = TextView(this).apply {
        this.text = text; textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = buttonBackground(false); minWidth = dp(120); minHeight = dp(64); setPadding(6, 0, 6, 0)
    }

    private fun createStatusText(text: String, active: Boolean) = TextView(this).apply {
        this.text = text; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(8, 12, 8, 12); background = statusBackground(active)
    }

    private fun cardBackground() = GradientDrawable().apply {
        cornerRadius = 18f; setColor(Color.rgb(16,16,16)); setStroke(1, Color.rgb(50,50,50))
    }

    private fun buttonBackground(active: Boolean) = GradientDrawable().apply {
        cornerRadius = 18f; setColor(Color.parseColor(if (active) "#07383E" else DARK)); setStroke(2, Color.parseColor(TIFFANY))
    }

    private fun statusBackground(active: Boolean) = GradientDrawable().apply {
        cornerRadius = 14f; setColor(Color.parseColor(if (active) "#07383E" else "#101010")); setStroke(2, Color.parseColor(if (active) TIFFANY else "#444444"))
    }

    private fun wrapParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun buttonParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 2; bottomMargin = 2 }
    private fun halfParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    private fun addSpace(parent: LinearLayout, height: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(LocationTrackingService.ACTION_SPEED_UPDATE)
            addAction(LocationTrackingService.ACTION_STATUS_UPDATE)
        }
        try { ContextCompat.registerReceiver(this, serviceReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) } catch (_: Exception) { }
        trackingActive = preferences.getBoolean(PREF_AUTO_TRACKING, false)
        if (::trackingButton.isInitialized) {
            trackingButton.text = if (trackingActive) "STOP TRACKING" else "START TRACKING"
            trackingButton.background = buttonBackground(trackingActive)
            if (trackingActive) updateStatusFromCurrentGps()
            refreshData()
        }
    }

    override fun onPause() {
        try { unregisterReceiver(serviceReceiver) } catch (_: Exception) { }
        super.onPause()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::drawerLayout.isInitialized && drawerLayout.isDrawerOpen(Gravity.START)) { drawerLayout.closeDrawer(Gravity.START); return }
        super.onBackPressed()
    }

    override fun onDestroy() { database.close(); super.onDestroy() }
}
