package com.example.backgroundodometer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TripsActivity : Activity() {

    private lateinit var database: OdometerDatabaseHelper
    private lateinit var listContainer: LinearLayout

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

    // =====================================================
    // INTERFACE
    // =====================================================

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
            55,
            20,
            40
        )

        // =================================================
        // TITLE
        // =================================================

        val title =
            TextView(this)

        title.text =
            "TRIP HISTORY"

        title.textSize =
            26f

        title.setTextColor(
            Color.WHITE
        )

        title.typeface =
            Typeface.DEFAULT_BOLD

        title.gravity =
            Gravity.CENTER

        root.addView(
            title,
            wrapParams()
        )

        addSpace(
            root,
            10
        )

        val subtitle =
            TextView(this)

        subtitle.text =
            "Recorded GPS trips"

        subtitle.textSize =
            14f

        subtitle.setTextColor(
            Color.GRAY
        )

        subtitle.gravity =
            Gravity.CENTER

        root.addView(
            subtitle,
            wrapParams()
        )

        addSpace(
            root,
            25
        )

        // =================================================
        // REFRESH
        // =================================================

        val refresh =
            createAction(
                "REFRESH"
            )

        refresh.setOnClickListener {
            loadTrips()
        }

        root.addView(
            refresh,
            fullWidthParams(58)
        )

        addSpace(
            root,
            20
        )

        // =================================================
        // LIST
        // =================================================

        listContainer =
            LinearLayout(this)

        listContainer.orientation =
            LinearLayout.VERTICAL

        root.addView(
            listContainer,
            wrapParams()
        )

        // =================================================
        // BACK
        // =================================================

        addSpace(
            root,
            25
        )

        val back =
            createAction(
                "BACK"
            )

        back.setOnClickListener {
            finish()
        }

        root.addView(
            back,
            fullWidthParams(58)
        )

        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(scroll)
    }

    // =====================================================
    // LOAD TRIPS
    // =====================================================

    private fun loadTrips() {

        listContainer.removeAllViews()

        val trips =
            database.getAllTrips()

        if (trips.isEmpty()) {

            val empty =
                TextView(this)

            empty.text =
                "No completed trips yet."

            empty.textSize =
                18f

            empty.setTextColor(
                Color.GRAY
            )

            empty.gravity =
                Gravity.CENTER

            empty.setPadding(
                10,
                30,
                10,
                30
            )

            listContainer.addView(
                empty,
                wrapParams()
            )

            return
        }

        for (trip in trips) {

            addTripCard(trip)
        }
    }

    // =====================================================
    // TRIP CARD
    // =====================================================

    private fun addTripCard(
        trip: TripSummary
    ) {

        val card =
            LinearLayout(this)

        card.orientation =
            LinearLayout.VERTICAL

        card.setPadding(
            18,
            18,
            18,
            18
        )

        card.background =
            GradientDrawable().apply {

                cornerRadius =
                    20f

                setColor(
                    Color.parseColor(
                        CARD
                    )
                )

                setStroke(
                    1,
                    Color.parseColor(
                        "#333333"
                    )
                )
            }

        card.isClickable =
            true

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

        // -------------------------------------------------
        // DATE
        // -------------------------------------------------

        val date =
            TextView(this)

        date.text =
            formatDate(
                trip.startTime
            )

        date.textSize =
            17f

        date.setTextColor(
            Color.parseColor(
                TIFFANY
            )
        )

        date.typeface =
            Typeface.DEFAULT_BOLD

        card.addView(
            date,
            wrapParams()
        )

        addSpace(
            card,
            8
        )

        // -------------------------------------------------
        // DISTANCE
        // -------------------------------------------------

        val distance =
            TextView(this)

        distance.text =
            "%.2f km".format(
                trip.distanceKm
            )

        distance.textSize =
            29f

        distance.setTextColor(
            Color.WHITE
        )

        distance.typeface =
            Typeface.DEFAULT_BOLD

        card.addView(
            distance,
            wrapParams()
        )

        addSpace(
            card,
            8
        )

        // -------------------------------------------------
        // SPEED
        // -------------------------------------------------

        val speed =
            TextView(this)

        speed.text =
            "Average %.1f km/h   •   Max %.1f km/h"
                .format(
                    trip.averageSpeed,
                    trip.maxSpeed
                )

        speed.textSize =
            14f

        speed.setTextColor(
            Color.LTGRAY
        )

        card.addView(
            speed,
            wrapParams()
        )

        addSpace(
            card,
            5
        )

        // -------------------------------------------------
        // TIME
        // -------------------------------------------------

        val time =
            TextView(this)

        val end =
            trip.endTime

        time.text =
            if (end != null) {

                "${formatTime(trip.startTime)} → " +
                    formatTime(end)

            } else {

                "${formatTime(trip.startTime)} → Active"
            }

        time.textSize =
            13f

        time.setTextColor(
            Color.GRAY
        )

        card.addView(
            time,
            wrapParams()
        )

        listContainer.addView(
            card,
            fullWidthParams(0).apply {
                height =
                    ViewGroup.LayoutParams.WRAP_CONTENT

                bottomMargin = 12
            }
        )
    }

    // =====================================================
    // FORMATTING
    // =====================================================

    private fun formatDate(
        timestamp: Long
    ): String {

        return SimpleDateFormat(
            "dd MMM yyyy",
            Locale.getDefault()
        ).format(
            Date(timestamp)
        )
    }

    private fun formatTime(
        timestamp: Long
    ): String {

        return SimpleDateFormat(
            "hh:mm a",
            Locale.getDefault()
        ).format(
            Date(timestamp)
        )
    }

    // =====================================================
    // UI HELPERS
    // =====================================================

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

            typeface =
                Typeface.DEFAULT_BOLD

            gravity =
                Gravity.CENTER

            isClickable =
                true

            background =
                GradientDrawable().apply {

                    cornerRadius =
                        18f

                    setColor(
                        Color.parseColor(
                            CARD
                        )
                    )

                    setStroke(
                        2,
                        Color.parseColor(
                            TIFFANY
                        )
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

    private fun fullWidthParams(
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

    override fun onDestroy() {

        database.close()

        super.onDestroy()
    }
}
