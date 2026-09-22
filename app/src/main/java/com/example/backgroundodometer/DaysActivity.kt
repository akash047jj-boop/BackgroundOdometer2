package com.example.backgroundodometer

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DaysActivity : Activity() {
    private lateinit var database: OdometerDatabaseHelper
    private lateinit var list: LinearLayout
    private var selectedDate: String? = null

    companion object {
        private const val TIFFANY = "#00BCD4"
        private const val CARD = "#151515"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = OdometerDatabaseHelper(this)
        buildInterface()
        loadDays()
    }

    private fun buildInterface() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.BLACK) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 40, 20, 35) }
        val title = TextView(this).apply { text = "DAY RECORDS"; textSize = 27f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        root.addView(title, wrapParams())
        addSpace(root, 20)
        root.addView(createAction("EDIT SELECTED") { selectedDate?.let { showEditDayDialog(it) } ?: Toast.makeText(this, "Select one day record.", Toast.LENGTH_SHORT).show() }, fullParams(58))
        addSpace(root, 8)
        root.addView(createAction("DELETE SELECTED") { selectedDate?.let { confirmDeleteDay(it) } ?: Toast.makeText(this, "Select one day record.", Toast.LENGTH_SHORT).show() }, fullParams(58))
        addSpace(root, 8)
        root.addView(createAction("REFRESH") { loadDays() }, fullParams(58))
        addSpace(root, 20)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list, wrapParams())
        addSpace(root, 25)
        root.addView(createAction("TRIP HISTORY") { startActivity(Intent(this, TripsActivity::class.java)) }, fullParams(58))
        addSpace(root, 10)
        root.addView(createAction("BACK") { finish() }, fullParams(58))
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun loadDays() {
        list.removeAllViews()
        selectedDate = null
        val dates = database.getAssignedDates()
        if (dates.isEmpty()) {
            val empty = TextView(this).apply { text = "No day records yet.\n\nGo to TRIPS → select trips → ASSIGN SELECTED."; textSize = 17f; setTextColor(Color.GRAY); gravity = Gravity.CENTER; setPadding(10, 40, 10, 40) }
            list.addView(empty, wrapParams())
            return
        }
        for (date in dates) addDay(date)
    }

    private fun addDay(date: String) {
        val trips = database.getTripsForDay(date)
        val distance = database.getDayDistance(date)
        val fuel = database.getDayFuel(date)
        val place = trips.firstOrNull()?.assignedPlace ?: ""

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 14)
            background = dayBackground(false)
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val selector = TextView(this).apply {
            text = "□"
            textSize = 38f
            setTextColor(Color.parseColor(TIFFANY))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = selectionBackground(false)
            minWidth = dp(72)
            minHeight = dp(72)
            contentDescription = "Select day record"
            setOnClickListener {
                val currentlySelected = selectedDate == date
                selectedDate = if (currentlySelected) null else date
                updateDaySelection(date)
                loadSelectionVisuals()
            }
        }
        row.addView(selector, LinearLayout.LayoutParams(dp(72), dp(72)))
        val label = TextView(this).apply { text = "SELECT THIS DAY"; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER_VERTICAL; setPadding(14, 0, 0, 0) }
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row, wrapParams())

        val day = TextView(this).apply { text = formatDate(date); textSize = 20f; setTextColor(Color.parseColor(TIFFANY)); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        card.addView(day, wrapParams())
        if (place.isNotBlank()) card.addView(TextView(this).apply { text = place; textSize = 15f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER }, wrapParams())
        val details = TextView(this).apply { text = "Distance: %.2f km\nFuel: %.2f L\nTrips: ${trips.size}".format(Locale.US, distance, fuel); textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        card.addView(details, wrapParams())

        card.setOnClickListener {
            startActivity(Intent(this, DayDetailActivity::class.java).apply { putExtra("day_date", date) })
        }

        card.tag = "day:$date"
        val params = wrapParams().apply { bottomMargin = 12 }
        list.addView(card, params)
    }

    private fun updateDaySelection(date: String) {
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i)
            if (child !is LinearLayout || child.tag != "day:$date") continue
            child.background = dayBackground(selectedDate == date)
            val row = (child as? ViewGroup)?.getChildAt(0) as? LinearLayout ?: continue
            val selector = row.getChildAt(0) as? TextView ?: continue
            selector.text = if (selectedDate == date) "✓" else "□"
            selector.background = selectionBackground(selectedDate == date)
        }
    }

    private fun loadSelectionVisuals() {
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i)
            val tag = child.tag?.toString() ?: continue
            if (!tag.startsWith("day:")) continue
            val date = tag.removePrefix("day:")
            val selected = selectedDate == date
            child.background = dayBackground(selected)
            val row = (child as? ViewGroup)?.getChildAt(0) as? LinearLayout ?: continue
            val selector = row.getChildAt(0) as? TextView ?: continue
            selector.text = if (selected) "✓" else "□"
            selector.background = selectionBackground(selected)
        }
    }

    private fun showEditDayDialog(oldDate: String) {
        val trips = database.getTripsForDay(oldDate)
        if (trips.isEmpty()) return
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(25, 5, 25, 5) }
        val date = TextView(this).apply { text = oldDate; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(20, 20, 20, 20) }
        val cal = Calendar.getInstance()
        try { cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(oldDate)!! } catch (_: Exception) {}
        date.setOnClickListener { DatePickerDialog(this, { _, y, m, d -> date.text = "%04d-%02d-%02d".format(y, m + 1, d) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show() }
        val place = EditText(this).apply { hint = "Place (optional)"; setText(trips.firstOrNull()?.assignedPlace ?: "") }
        layout.addView(TextView(this).apply { text = "DAY DATE"; setTextColor(Color.LTGRAY) }, wrapParams())
        layout.addView(date, wrapParams())
        layout.addView(place, wrapParams())
        layout.addView(TextView(this).apply { text = "This changes the day assignment of all ${trips.size} trip(s) in this day record. The trips themselves are not deleted."; setTextColor(Color.GRAY); setPadding(0, 15, 0, 0) }, wrapParams())

        AlertDialog.Builder(this).setTitle("EDIT DAY RECORD").setView(layout).setNegativeButton("CANCEL", null).setPositiveButton("SAVE") { _, _ ->
            database.updateDayRecord(oldDate, date.text.toString(), place.text.toString().trim())
            selectedDate = null
            loadDays()
            Toast.makeText(this, "Day record updated", Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun confirmDeleteDay(date: String) {
        val count = database.getTripsForDay(date).size
        AlertDialog.Builder(this)
            .setTitle("DELETE DAY RECORD?")
            .setMessage("This removes the day assignment for $count trip(s), but keeps the trips and their GPS routes in Trip History. The distance will no longer appear under this day.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                database.deleteDayRecord(date)
                selectedDate = null
                loadDays()
                Toast.makeText(this, "Day record deleted; trips preserved", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun dayBackground(selected: Boolean) = GradientDrawable().apply {
        cornerRadius = 20f
        setColor(if (selected) Color.parseColor("#102F33") else Color.parseColor(CARD))
        setStroke(2, if (selected) Color.parseColor(TIFFANY) else Color.DKGRAY)
    }

    private fun selectionBackground(selected: Boolean) = GradientDrawable().apply {
        cornerRadius = 12f
        setColor(if (selected) Color.parseColor("#073C43") else Color.parseColor("#101010"))
        setStroke(dp(2), Color.parseColor(TIFFANY))
    }

    private fun createAction(text: String, click: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        background = GradientDrawable().apply { cornerRadius = 18f; setColor(Color.parseColor(CARD)); setStroke(2, Color.parseColor(TIFFANY)) }
        setOnClickListener { click() }
    }

    private fun formatDate(value: String): String = try { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)!!) } catch (_: Exception) { value }
    private fun wrapParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun fullParams(height: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height))
    private fun addSpace(parent: LinearLayout, height: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onResume() { super.onResume(); if (::database.isInitialized) loadDays() }
    override fun onDestroy() { database.close(); super.onDestroy() }
}
