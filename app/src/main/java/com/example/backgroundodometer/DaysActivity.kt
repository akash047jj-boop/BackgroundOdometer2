package com.example.backgroundodometer

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 40, 20, 35)
        }

        root.addView(title("DAY RECORDS"), wrapParams())
        space(root, 20)

        root.addView(action("EDIT SELECTED DAY") {
            selectedDate?.let { showEditDayDialog(it) }
                ?: toast("Select one day record.")
        }, fullParams(58))

        root.addView(action("ADD TRIPS TO SELECTED DAY") {
            selectedDate?.let { showAddTripsDialog(it) }
                ?: toast("Select one day record.")
        }, fullParams(58))

        root.addView(action("EDIT / REMOVE TRIPS IN SELECTED DAY") {
            selectedDate?.let { showTripsInDayDialog(it) }
                ?: toast("Select one day record.")
        }, fullParams(58))

        root.addView(action("DELETE SELECTED DAY") {
            selectedDate?.let { confirmDeleteDay(it) }
                ?: toast("Select one day record.")
        }, fullParams(58))

        root.addView(action("REFRESH") { loadDays() }, fullParams(58))

        space(root, 18)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list, wrapParams())

        space(root, 25)
        root.addView(action("TRIP HISTORY") {
            startActivity(android.content.Intent(this, TripsActivity::class.java))
        }, fullParams(58))

        root.addView(action("BACK") { finish() }, fullParams(58))

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun loadDays() {
        if (!::list.isInitialized) return
        list.removeAllViews()

        val dates = database.getAssignedDates()
        if (dates.isEmpty()) {
            list.addView(
                info(
                    "No day records yet.\n\n" +
                        "Go to TRIP HISTORY → select trips → ASSIGN / MOVE SELECTED."
                ),
                wrapParams()
            )
            selectedDate = null
            return
        }

        if (selectedDate !in dates) selectedDate = null
        dates.forEach { addDay(it) }
    }

    private fun addDay(date: String) {
        val trips = database.getTripsForDay(date)
        val distance = database.getDayDistance(date)
        val fuel = database.getDayFuel(date)
        val place = trips.firstOrNull()?.assignedPlace ?: ""
        val selected = selectedDate == date

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 14)
            background = dayBackground(selected)
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val selector = TextView(this).apply {
            text = if (selected) "✓" else "□"
            textSize = 34f
            setTextColor(Color.parseColor(TIFFANY))
            gravity = Gravity.CENTER
            background = selectionBackground(selected)
            minWidth = dp(65)
            minHeight = dp(65)
            setOnClickListener {
                selectedDate = if (selectedDate == date) null else date
                loadDays()
            }
        }

        row.addView(selector, LinearLayout.LayoutParams(dp(65), dp(65)))
        row.addView(
            TextView(this).apply {
                text = formatDate(date)
                textSize = 21f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                setPadding(15, 0, 0, 0)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        card.addView(row, wrapParams())
        space(card, 8)
        card.addView(label(if (place.isBlank()) "DAY RECORD" else place), wrapParams())
        card.addView(big("%.2f km".format(Locale.US, distance)), wrapParams())
        card.addView(info("${trips.size} trip(s)"), wrapParams())

        if (fuel > 0.0) {
            card.addView(info("Fuel added: %.2f L".format(Locale.US, fuel)), wrapParams())
        }

        space(card, 8)

        card.addView(action("ADD TRIPS TO THIS DAY") {
            selectedDate = date
            showAddTripsDialog(date)
        }, buttonParams())

        card.addView(action("EDIT / REMOVE TRIPS") {
            selectedDate = date
            showTripsInDayDialog(date)
        }, buttonParams())

        card.addView(action("EDIT DAY") {
            selectedDate = date
            showEditDayDialog(date)
        }, buttonParams())

        val p = wrapParams()
        p.bottomMargin = 12
        list.addView(card, p)
    }

    private fun showAddTripsDialog(date: String) {
        val allTrips = database.getAllTrips()
        val available = allTrips.filter { it.assignedDate != date }

        if (available.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("ADD TRIPS")
                .setMessage("There are no other trips available to add to this day.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val selected = mutableSetOf<Long>()
        val inside = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(15, 5, 15, 5)
        }

        available.forEach { trip ->
            val check = CheckBox(this).apply {
                text = buildTripText(trip)
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(5, 12, 5, 12)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(trip.id) else selected.remove(trip.id)
                }
            }
            inside.addView(check, wrapParams())
        }

        val scroll = ScrollView(this).apply { addView(inside) }

        AlertDialog.Builder(this)
            .setTitle("ADD TRIPS TO ${formatDate(date)}")
            .setMessage("Select trips to add. A trip assigned to another day will be moved here.")
            .setView(scroll)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("ADD SELECTED") { _, _ ->
                if (selected.isEmpty()) {
                    toast("Select at least one trip.")
                    return@setPositiveButton
                }

                val place = database.getTripsForDay(date)
                    .firstOrNull()?.assignedPlace ?: ""

                database.assignTripsToDay(selected.toList(), date, place)
                loadDays()
                toast("${selected.size} trip(s) added to day")
            }
            .show()
    }

    private fun showTripsInDayDialog(date: String) {
        val trips = database.getTripsForDay(date)

        if (trips.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("TRIPS IN DAY")
                .setMessage("This day has no trips.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val inside = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(15, 5, 15, 5)
        }

        trips.forEach { trip ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
                background = GradientDrawable().apply {
                    cornerRadius = 16f
                    setColor(Color.parseColor(CARD))
                    setStroke(1, Color.DKGRAY)
                }
            }

            card.addView(
                label(if (trip.distanceSource.equals("MANUAL", true)) "MANUAL TRIP" else "GPS TRIP"),
                wrapParams()
            )
            card.addView(big("%.2f km".format(Locale.US, trip.distanceKm)), wrapParams())
            card.addView(info(buildTripText(trip)), wrapParams())

            if (trip.assignedPlace.isNotBlank()) {
                card.addView(info("Place: ${trip.assignedPlace}"), wrapParams())
            }

            space(card, 6)

            card.addView(action("EDIT THIS TRIP") {
                showTripEditDialog(trip.id, date)
            }, buttonParams())

            card.addView(action("REMOVE FROM THIS DAY") {
                confirmRemoveTripFromDay(trip.id)
            }, buttonParams())

            val p = wrapParams()
            p.bottomMargin = 10
            inside.addView(card, p)
        }

        val scroll = ScrollView(this).apply { addView(inside) }

        AlertDialog.Builder(this)
            .setTitle("TRIPS IN ${formatDate(date)}")
            .setView(scroll)
            .setPositiveButton("DONE") { _, _ -> loadDays() }
            .show()
    }

    private fun showTripEditDialog(tripId: Long, oldDay: String) {
        val trip = database.getTrip(tripId) ?: return

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(25, 5, 25, 5)
        }

        val date = TextView(this).apply {
            text = oldDay
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(10, 16, 10, 16)
        }

        val cal = Calendar.getInstance()
        try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(oldDay)?.let { cal.time = it }
        } catch (_: Exception) {}

        date.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d -> date.text = "%04d-%02d-%02d".format(y, m + 1, d) },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val place = EditText(this).apply {
            hint = "Place (optional)"
            setText(trip.assignedPlace)
        }

        layout.addView(info("ASSIGNED DAY"), wrapParams())
        layout.addView(date, wrapParams())
        layout.addView(place, wrapParams())

        var distance: EditText? = null
        var start: EditText? = null
        var end: EditText? = null

        if (trip.distanceSource.equals("MANUAL", true)) {
            distance = EditText(this).apply {
                hint = "Distance (km)"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText("%.2f".format(Locale.US, trip.distanceKm))
            }
            start = EditText(this).apply { hint = "Start time (optional)" }
            end = EditText(this).apply { hint = "End time (optional)" }

            layout.addView(distance, wrapParams())
            layout.addView(start, wrapParams())
            layout.addView(end, wrapParams())
        } else {
            layout.addView(
                info("GPS route and recorded GPS data remain unchanged."),
                wrapParams()
            )
        }

        AlertDialog.Builder(this)
            .setTitle("EDIT TRIP")
            .setView(layout)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                val newDate = date.text.toString().trim()
                val newPlace = place.text.toString().trim()

                if (trip.distanceSource.equals("MANUAL", true)) {
                    val km = distance?.text?.toString()?.toDoubleOrNull()
                    if (km == null || km < 0.0) {
                        toast("Enter a valid distance.")
                        return@setPositiveButton
                    }

                    database.updateManualTrip(
                        tripId,
                        newDate,
                        newPlace,
                        km,
                        parseOptionalTime(newDate, start?.text?.toString().orEmpty()),
                        parseOptionalTime(newDate, end?.text?.toString().orEmpty())
                    )
                } else {
                    database.updateTripAssignment(tripId, newDate, newPlace)
                }

                loadDays()
                toast("Trip updated")
            }
            .show()
    }

    private fun confirmRemoveTripFromDay(tripId: Long) {
        AlertDialog.Builder(this)
            .setTitle("REMOVE TRIP?")
            .setMessage("The trip will remain in Trip History. Only its day assignment will be removed.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("REMOVE") { _, _ ->
                database.updateTripAssignment(tripId, "", "")
                loadDays()
                toast("Trip removed from day")
            }
            .show()
    }

    private fun showEditDayDialog(oldDate: String) {
        val trips = database.getTripsForDay(oldDate)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(25, 5, 25, 5)
        }

        val date = TextView(this).apply {
            text = oldDate
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(10, 16, 10, 16)
        }

        val cal = Calendar.getInstance()
        try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(oldDate)?.let { cal.time = it }
        } catch (_: Exception) {}

        date.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d -> date.text = "%04d-%02d-%02d".format(y, m + 1, d) },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val place = EditText(this).apply {
            hint = "Place (optional)"
            setText(trips.firstOrNull()?.assignedPlace ?: "")
        }

        layout.addView(info("DAY DATE"), wrapParams())
        layout.addView(date, wrapParams())
        layout.addView(place, wrapParams())
        layout.addView(
            info(
                "This changes the day assignment of all ${trips.size} trip(s) in this day record. " +
                    "The trips themselves are not deleted."
            ),
            wrapParams()
        )

        AlertDialog.Builder(this)
            .setTitle("EDIT DAY RECORD")
            .setView(layout)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                database.updateDayRecord(
                    oldDate,
                    date.text.toString(),
                    place.text.toString().trim()
                )
                selectedDate = null
                loadDays()
                toast("Day record updated")
            }
            .show()
    }

    private fun confirmDeleteDay(date: String) {
        val count = database.getTripsForDay(date).size

        AlertDialog.Builder(this)
            .setTitle("DELETE DAY RECORD?")
            .setMessage(
                "This removes the day assignment for $count trip(s), " +
                    "but keeps the trips and their GPS routes in Trip History."
            )
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                database.deleteDayRecord(date)
                selectedDate = null
                loadDays()
                toast("Day record deleted; trips preserved")
            }
            .show()
    }

    private fun buildTripText(trip: TripSummary): String {
        val type = if (trip.distanceSource.equals("MANUAL", true)) "MANUAL" else "GPS"
        val date = formatDate(trip.startTime)
        return "$type • %.2f km • $date".format(Locale.US, trip.distanceKm)
    }

    private fun parseOptionalTime(date: String, text: String): Long {
        if (text.isBlank()) return 0L

        val formats = arrayOf(
            "yyyy-MM-dd hh:mm a",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd h:mm a"
        )

        for (format in formats) {
            try {
                val value = if (format.contains("a")) {
                    "$date ${text.uppercase(Locale.US)}"
                } else {
                    "$date $text"
                }
                return SimpleDateFormat(format, Locale.US).parse(value)?.time ?: 0L
            } catch (_: Exception) {}
        }

        return 0L
    }

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 27f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
    }

    private fun info(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.LTGRAY)
        gravity = Gravity.CENTER
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor(TIFFANY))
        gravity = Gravity.CENTER
    }

    private fun big(text: String) = TextView(this).apply {
        this.text = text
        textSize = 27f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
    }

    private fun action(text: String, click: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = 18f
            setColor(Color.parseColor(CARD))
            setStroke(2, Color.parseColor(TIFFANY))
        }
        setPadding(10, 14, 10, 14)
        minHeight = dp(58)
        setOnClickListener { click() }
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

    private fun wrapParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun fullParams(height: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(height)
    ).apply {
        topMargin = 4
        bottomMargin = 4
    }

    private fun buttonParams() = wrapParams().apply {
        topMargin = 4
        bottomMargin = 4
    }

    private fun space(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()

    private fun formatDate(value: String): String = try {
        SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)!!
        )
    } catch (_: Exception) {
        value
    }

    private fun formatDate(time: Long) =
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(time))

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        if (::database.isInitialized) loadDays()
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }
}
