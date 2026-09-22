package com.example.backgroundodometer

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
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
import java.util.Date
import java.util.Locale

class TripsActivity : Activity() {

    private lateinit var database: OdometerDatabaseHelper
    private lateinit var list: LinearLayout
    private val selectedIds = mutableSetOf<Long>()

    companion object {
        private const val TIFFANY = "#00BCD4"
        private const val CARD = "#151515"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = OdometerDatabaseHelper(this)
        buildInterface()
        loadTrips()
    }

    private fun buildInterface() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.BLACK) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 30, 20, 30)
        }

        root.addView(title("TRIPS"), wrapParams())
        root.addView(
            info("Select GPS or manual trips to edit, assign/move, remove from a day, or delete."),
            wrapParams()
        )
        space(root, 14)

        root.addView(action("ADD MANUAL TRIP") { showManualTripDialog() }, buttonParams())
        root.addView(action("ASSIGN / MOVE SELECTED") {
            if (selectedIds.isEmpty()) toast("Select at least one trip.") else showAssignDialog()
        }, buttonParams())
        root.addView(action("EDIT SELECTED") {
            if (selectedIds.size != 1) toast("Select exactly one trip to edit.")
            else showEditTripDialog(selectedIds.first())
        }, buttonParams())
        root.addView(action("REMOVE SELECTED FROM DAY") {
            if (selectedIds.isEmpty()) toast("Select at least one trip.")
            else removeSelectedFromDay()
        }, buttonParams())
        root.addView(action("DELETE SELECTED") {
            if (selectedIds.isEmpty()) toast("Select at least one trip.")
            else confirmDeleteSelectedTrips()
        }, buttonParams())
        root.addView(action("CLEAR SELECTION") {
            selectedIds.clear()
            loadTrips()
        }, buttonParams())
        root.addView(action("REFRESH") { loadTrips() }, buttonParams())

        space(root, 14)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list, wrapParams())
        space(root, 18)
        root.addView(action("BACK") { finish() }, buttonParams())

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun loadTrips() {
        if (!::list.isInitialized) return
        list.removeAllViews()

        val trips = database.getAllTrips()
        if (trips.isEmpty()) {
            list.addView(info("No trips yet.\n\nAdd a manual trip or start GPS tracking."), wrapParams())
            return
        }

        trips.forEach { addTrip(it) }
    }

    private fun addTrip(trip: TripSummary) {
        val selected = selectedIds.contains(trip.id)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 14)
            background = GradientDrawable().apply {
                cornerRadius = 18f
                setColor(if (selected) Color.parseColor("#102F33") else Color.parseColor(CARD))
                setStroke(2, if (selected) Color.parseColor(TIFFANY) else Color.DKGRAY)
            }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val selector = TextView(this).apply {
            text = if (selected) "✓" else "□"
            textSize = 38f
            setTextColor(Color.parseColor(TIFFANY))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = selectionBackground(selected)
            minWidth = dp(72)
            minHeight = dp(72)
            setOnClickListener {
                if (selectedIds.contains(trip.id)) selectedIds.remove(trip.id)
                else selectedIds.add(trip.id)
                loadTrips()
            }
        }

        row.addView(selector, LinearLayout.LayoutParams(dp(72), dp(72)))
        row.addView(
            info("SELECT THIS TRIP").apply {
                setTextSize(16f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                setPadding(14, 0, 0, 0)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        card.addView(row, wrapParams())

        val manual = trip.distanceSource.equals("MANUAL", true)
        card.addView(label(if (manual) "MANUAL TRIP" else "GPS TRIP"), wrapParams())
        card.addView(big("%.2f km".format(Locale.US, trip.distanceKm)), wrapParams())

        if (manual) {
            card.addView(info("Manual distance"), wrapParams())
            card.addView(info("Date: ${formatDate(trip.startTime)}"), wrapParams())
        } else {
            card.addView(
                info(
                    "Average %.1f km/h • Max %.1f km/h".format(
                        Locale.US, trip.averageSpeed, trip.maxSpeed
                    )
                ),
                wrapParams()
            )
            card.addView(
                info(
                    "${formatDateTime(trip.startTime)} → " +
                        if (trip.endTime > 0) formatTime(trip.endTime) else "Active"
                ),
                wrapParams()
            )
        }

        card.addView(
            info(
                if (trip.assignedDate.isBlank()) {
                    "NOT ASSIGNED TO A DAY"
                } else {
                    "ASSIGNED: ${trip.assignedDate}" +
                        if (trip.assignedPlace.isNotBlank()) " • ${trip.assignedPlace}" else ""
                }
            ),
            wrapParams()
        )

        space(card, 6)

        card.addView(action("EDIT THIS TRIP") {
            showEditTripDialog(trip.id)
        }, buttonParams())

        card.addView(action(
            if (trip.assignedDate.isBlank()) "ASSIGN THIS TRIP" else "MOVE THIS TRIP"
        ) {
            selectedIds.clear()
            selectedIds.add(trip.id)
            showAssignDialog()
        }, buttonParams())

        card.addView(action("VIEW DETAILS") {
            startActivity(Intent(this, TripDetailActivity::class.java).apply {
                putExtra("trip_id", trip.id)
            })
        }, buttonParams())

        val p = wrapParams()
        p.bottomMargin = 10
        list.addView(card, p)
    }

    private fun showEditTripDialog(tripId: Long) {
        val trip = database.getTrip(tripId) ?: return

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(25, 5, 25, 5)
        }

        val date = TextView(this).apply {
            text = if (trip.assignedDate.isNotBlank()) {
                trip.assignedDate
            } else {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(trip.startTime))
            }
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(10, 16, 10, 16)
        }

        val cal = Calendar.getInstance()
        date.setOnClickListener {
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date.text.toString())?.let { cal.time = it }
            } catch (_: Exception) {}
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

        layout.addView(info("DATE / ASSIGNED DAY"), wrapParams())
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
            start = EditText(this).apply { hint = "Start time (optional, e.g. 08:30 AM)" }
            end = EditText(this).apply { hint = "End time (optional, e.g. 09:15 AM)" }

            layout.addView(distance, wrapParams())
            layout.addView(start, wrapParams())
            layout.addView(end, wrapParams())
        } else {
            layout.addView(
                info("GPS route, GPS distance and recorded times are kept unchanged. Only its assigned day/place can be changed here."),
                wrapParams()
            )
        }

        AlertDialog.Builder(this)
            .setTitle("EDIT TRIP")
            .setView(layout)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                val dateValue = date.text.toString().trim()
                if (dateValue.isBlank()) {
                    toast("Select a date.")
                    return@setPositiveButton
                }

                if (trip.distanceSource.equals("MANUAL", true)) {
                    val km = distance?.text?.toString()?.toDoubleOrNull()
                    if (km == null || km < 0.0) {
                        toast("Enter a valid distance.")
                        return@setPositiveButton
                    }

                    database.updateManualTrip(
                        tripId,
                        dateValue,
                        place.text.toString().trim(),
                        km,
                        parseOptionalTime(dateValue, start?.text?.toString().orEmpty()),
                        parseOptionalTime(dateValue, end?.text?.toString().orEmpty())
                    )
                } else {
                    database.updateTripAssignment(
                        tripId,
                        dateValue,
                        place.text.toString().trim()
                    )
                }

                selectedIds.remove(tripId)
                loadTrips()
                toast("Trip updated")
            }
            .show()
    }

    private fun showManualTripDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(25, 5, 25, 5)
        }

        val date = TextView(this).apply {
            text = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(10, 16, 10, 16)
        }

        val cal = Calendar.getInstance()
        date.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d -> date.text = "%04d-%02d-%02d".format(y, m + 1, d) },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val place = EditText(this).apply { hint = "Place (optional)" }
        val distance = EditText(this).apply {
            hint = "Distance (km)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val start = EditText(this).apply { hint = "Start time (optional, e.g. 08:30 AM)" }
        val end = EditText(this).apply { hint = "End time (optional, e.g. 09:15 AM)" }

        layout.addView(info("DATE"), wrapParams())
        layout.addView(date, wrapParams())
        layout.addView(place, wrapParams())
        layout.addView(distance, wrapParams())
        layout.addView(start, wrapParams())
        layout.addView(end, wrapParams())

        AlertDialog.Builder(this)
            .setTitle("ADD MANUAL TRIP")
            .setMessage("Manual trips add their distance directly to the odometer and do not require GPS route points.")
            .setView(layout)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                val km = distance.text.toString().toDoubleOrNull()
                if (km == null || km < 0.0) {
                    toast("Enter a valid distance.")
                    return@setPositiveButton
                }

                val dateValue = date.text.toString()
                database.createManualTrip(
                    dateValue,
                    place.text.toString().trim(),
                    km,
                    parseOptionalTime(dateValue, start.text.toString()),
                    parseOptionalTime(dateValue, end.text.toString())
                )

                loadTrips()
                toast("Manual trip added")
            }
            .show()
    }

    private fun showAssignDialog() {
        val cal = Calendar.getInstance()

        val dateText = TextView(this).apply {
            text = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
        }

        dateText.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d -> dateText.text = "%04d-%02d-%02d".format(y, m + 1, d) },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val place = EditText(this).apply { hint = "Place (optional)" }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(25, 5, 25, 5)
        }

        box.addView(dateText, wrapParams())
        box.addView(place, wrapParams())

        AlertDialog.Builder(this)
            .setTitle("ASSIGN / MOVE TRIPS")
            .setMessage("The selected trips will be assigned to this day and place. If they already belong to another day, they will be moved.")
            .setView(box)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                database.assignTripsToDay(
                    selectedIds.toList(),
                    dateText.text.toString(),
                    place.text.toString().trim()
                )
                selectedIds.clear()
                loadTrips()
                toast("Trips assigned / moved")
            }
            .show()
    }

    private fun removeSelectedFromDay() {
        val ids = selectedIds.toList()

        AlertDialog.Builder(this)
            .setTitle("REMOVE FROM DAY?")
            .setMessage("This removes only the day assignment. The trips themselves are not deleted.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("REMOVE") { _, _ ->
                var count = 0
                ids.forEach { if (database.updateTripAssignment(it, "", "")) count++ }
                selectedIds.clear()
                loadTrips()
                toast("$count trip(s) removed from day")
            }
            .show()
    }

    private fun confirmDeleteSelectedTrips() {
        val ids = selectedIds.toList()

        AlertDialog.Builder(this)
            .setTitle("DELETE SELECTED TRIPS?")
            .setMessage("This permanently deletes the selected trip records and stored GPS route points. Deleted distances will no longer count toward the odometer.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                var count = 0
                ids.forEach { if (database.deleteTrip(it)) count++ }
                selectedIds.clear()
                loadTrips()
                toast("$count trip(s) deleted")
            }
            .show()
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

    private fun selectionBackground(selected: Boolean) = GradientDrawable().apply {
        cornerRadius = 12f
        setColor(if (selected) Color.parseColor("#073C43") else Color.parseColor("#101010"))
        setStroke(dp(2), Color.parseColor(TIFFANY))
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
            setColor(Color.rgb(21, 21, 21))
            setStroke(1, Color.parseColor(TIFFANY))
        }
        setPadding(10, 14, 10, 14)
        minHeight = dp(58)
        setOnClickListener { click() }
    }

    private fun wrapParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun buttonParams() = wrapParams().apply {
        topMargin = 4
        bottomMargin = 4
    }

    private fun space(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()

    private fun formatDate(time: Long) =
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(time))

    private fun formatDateTime(time: Long) =
        SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()).format(Date(time))

    private fun formatTime(time: Long) =
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(time))

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        if (::database.isInitialized) loadTrips()
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }
}
