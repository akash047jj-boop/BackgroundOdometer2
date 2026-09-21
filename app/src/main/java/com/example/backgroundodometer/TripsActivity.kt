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

class TripsActivity : Activity() {

    private lateinit var database:
        OdometerDatabaseHelper

    private lateinit var list:
        LinearLayout

    private val selectedIds =
        mutableSetOf<Long>()

    private val checkboxes =
        mutableMapOf<Long, CheckBox>()

    companion object {

        private const val TIFFANY = "#00BCD4"
        private const val CARD = "#151515"
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        database =
            OdometerDatabaseHelper(this)

        buildInterface()
        loadTrips()
    }

    private fun buildInterface() {

        val scroll =
            ScrollView(this)

        scroll.setBackgroundColor(Color.BLACK)

        val root =
            LinearLayout(this)

        root.orientation =
            LinearLayout.VERTICAL

        root.setPadding(
            20,
            40,
            20,
            35
        )

        val title =
            TextView(this)

        title.text =
            "TRIP HISTORY"

        title.textSize = 27f
        title.setTextColor(Color.WHITE)
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER

        root.addView(
            title,
            wrapParams()
        )

        val subtitle =
            TextView(this)

        subtitle.text =
            "Select trips from any date and assign them to a day/place."

        subtitle.textSize = 14f
        subtitle.setTextColor(Color.GRAY)
        subtitle.gravity = Gravity.CENTER

        root.addView(
            subtitle,
            wrapParams()
        )

        addSpace(root, 20)

        val assign =
            createAction("ASSIGN SELECTED")

        assign.setOnClickListener {

            if (selectedIds.isEmpty()) {

                Toast.makeText(
                    this,
                    "Select at least one trip.",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                showAssignDialog()
            }
        }

        root.addView(
            assign,
            fullParams(58)
        )

        addSpace(root, 10)

        val refresh =
            createAction("REFRESH")

        refresh.setOnClickListener {
            loadTrips()
        }

        root.addView(
            refresh,
            fullParams(58)
        )

        addSpace(root, 20)

        list =
            LinearLayout(this)

        list.orientation =
            LinearLayout.VERTICAL

        root.addView(
            list,
            wrapParams()
        )

        addSpace(root, 25)

        val back =
            createAction("BACK")

        back.setOnClickListener {
            finish()
        }

        root.addView(
            back,
            fullParams(58)
        )

        scroll.addView(root)

        setContentView(scroll)
    }

    private fun loadTrips() {

        list.removeAllViews()
        checkboxes.clear()

        val trips =
            database.getAllTrips()

        if (trips.isEmpty()) {

            val empty =
                TextView(this)

            empty.text =
                "No completed trips yet."

            empty.textSize = 18f
            empty.setTextColor(Color.GRAY)
            empty.gravity = Gravity.CENTER

            list.addView(
                empty,
                wrapParams()
            )

            return
        }

        for (trip in trips) {
            addTrip(trip)
        }
    }

    private fun addTrip(
        trip: TripSummary
    ) {

        val card =
            LinearLayout(this)

        card.orientation =
            LinearLayout.VERTICAL

        card.setPadding(
            15,
            15,
            15,
            15
        )

        card.background =
            GradientDrawable().apply {

                cornerRadius = 20f

                setColor(
                    Color.parseColor(CARD)
                )

                setStroke(
                    1,
                    Color.DKGRAY
                )
            }

        val checkRow =
            LinearLayout(this)

        checkRow.orientation =
            LinearLayout.HORIZONTAL

        checkRow.gravity =
            Gravity.CENTER_VERTICAL

        val checkbox =
            CheckBox(this)

        checkbox.buttonTintList =
            android.content.res.ColorStateList.valueOf(
                Color.parseColor(TIFFANY)
            )

        checkbox.setOnCheckedChangeListener {
                _, checked ->

            if (checked) {
                selectedIds.add(trip.id)
            } else {
                selectedIds.remove(trip.id)
            }
        }

        checkboxes[trip.id] =
            checkbox

        checkRow.addView(
            checkbox,
            LinearLayout.LayoutParams(
                55,
                55
            )
        )

        val selectLabel =
            TextView(this)

        selectLabel.text =
            "SELECT THIS TRIP"

        selectLabel.textSize = 14f
        selectLabel.setTextColor(Color.LTGRAY)

        checkRow.addView(
            selectLabel,
            wrapParams()
        )

        card.addView(
            checkRow,
            wrapParams()
        )

        val date =
            TextView(this)

        date.text =
            formatDate(trip.startTime)

        date.textSize = 17f
        date.setTextColor(
            Color.parseColor(TIFFANY)
        )
        date.typeface = Typeface.DEFAULT_BOLD

        card.addView(
            date,
            wrapParams()
        )

        val distance =
            TextView(this)

        distance.text =
            "%.2f km".format(
                trip.distanceKm
            )

        distance.textSize = 29f
        distance.setTextColor(Color.WHITE)
        distance.typeface = Typeface.DEFAULT_BOLD

        card.addView(
            distance,
            wrapParams()
        )

        val speed =
            TextView(this)

        speed.text =
            "Average %.1f km/h • Max %.1f km/h"
                .format(
                    trip.averageSpeed,
                    trip.maxSpeed
                )

        speed.textSize = 14f
        speed.setTextColor(Color.LTGRAY)

        card.addView(
            speed,
            wrapParams()
        )

        val time =
            TextView(this)

        time.text =
            if (trip.endTime > 0) {
                "${formatTime(trip.startTime)} → " +
                    formatTime(trip.endTime)
            } else {
                "${formatTime(trip.startTime)} → Active"
            }

        time.textSize = 13f
        time.setTextColor(Color.GRAY)

        card.addView(
            time,
            wrapParams()
        )

        if (trip.assignedDate.isNotBlank()) {

            val assigned =
                TextView(this)

            assigned.text =
                "ASSIGNED: ${trip.assignedDate}" +
                    if (
                        trip.assignedPlace.isNotBlank()
                    ) {
                        " • ${trip.assignedPlace}"
                    } else {
                        ""
                    }

            assigned.textSize = 13f
            assigned.setTextColor(
                Color.parseColor(TIFFANY)
            )

            card.addView(
                assigned,
                wrapParams()
            )
        }

        card.setOnClickListener {

            val intent =
                Intent(
                    this,
                    TripDetailActivity::class.java
                )

            intent.putExtra(
                "trip_id",
                trip.id
            )

            startActivity(intent)
        }

        val params =
            wrapParams()

        params.bottomMargin = 12

        list.addView(
            card,
            params
        )
    }

    private fun showAssignDialog() {

        val calendar =
            Calendar.getInstance()

        val dateText =
            TextView(this)

        dateText.text =
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
            ).format(calendar.time)

        dateText.textSize = 18f
        dateText.setTextColor(Color.WHITE)
        dateText.gravity = Gravity.CENTER
        dateText.setPadding(20, 20, 20, 20)

        dateText.setOnClickListener {

            DatePickerDialog(
                this,
                { _, year, month, day ->

                    dateText.text =
                        "%04d-%02d-%02d".format(
                            year,
                            month + 1,
                            day
                        )
                },
                calendar.get(
                    Calendar.YEAR
                ),
                calendar.get(
                    Calendar.MONTH
                ),
                calendar.get(
                    Calendar.DAY_OF_MONTH
                )
            ).show()
        }

        val place =
            EditText(this)

        place.hint =
            "Place"

        place.setTextColor(Color.WHITE)
        place.setHintTextColor(Color.GRAY)

        val layout =
            LinearLayout(this)

        layout.orientation =
            LinearLayout.VERTICAL

        layout.setPadding(
            30,
            10,
            30,
            10
        )

        val dateLabel =
            TextView(this)

        dateLabel.text =
            "DATE"

        dateLabel.setTextColor(
            Color.parseColor(TIFFANY)
        )

        layout.addView(
            dateLabel,
            wrapParams()
        )

        layout.addView(
            dateText,
            wrapParams()
        )

        val placeLabel =
            TextView(this)

        placeLabel.text =
            "PLACE"

        placeLabel.setTextColor(
            Color.parseColor(TIFFANY)
        )

        layout.addView(
            placeLabel,
            wrapParams()
        )

        layout.addView(
            place,
            wrapParams()
        )

        AlertDialog.Builder(this)
            .setTitle(
                "ASSIGN SELECTED TRIPS"
            )
            .setMessage(
                "${selectedIds.size} trip(s) selected."
            )
            .setView(layout)
            .setNegativeButton(
                "CANCEL",
                null
            )
            .setPositiveButton(
                "ASSIGN"
            ) { _, _ ->

                database.assignTripsToDay(
                    selectedIds.toList(),
                    dateText.text.toString(),
                    place.text.toString().trim()
                )

                selectedIds.clear()

                loadTrips()

                Toast.makeText(
                    this,
                    "Trips assigned.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun formatDate(
        time: Long
    ): String {

        return SimpleDateFormat(
            "dd MMM yyyy",
            Locale.getDefault()
        ).format(Date(time))
    }

    private fun formatTime(
        time: Long
    ): String {

        return SimpleDateFormat(
            "hh:mm a",
            Locale.getDefault()
        ).format(Date(time))
    }

    private fun createAction(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text = text
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER

            background =
                GradientDrawable().apply {

                    cornerRadius = 18f

                    setColor(
                        Color.parseColor(CARD)
                    )

                    setStroke(
                        2,
                        Color.parseColor(TIFFANY)
                    )
                }
        }
    }

    private fun wrapParams():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun fullParams(
        height: Int
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height
        )
    }

    private fun addSpace(
        parent: LinearLayout,
        height: Int
    ) {

        parent.addView(
            View(this),
            LinearLayout.LayoutParams(
                1,
                height
            )
        )
    }

    override fun onResume() {

        super.onResume()

        if (::database.isInitialized) {
            loadTrips()
        }
    }

    override fun onDestroy() {

        database.close()

        super.onDestroy()
    }
}
