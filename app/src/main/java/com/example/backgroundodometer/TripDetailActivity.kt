package com.example.backgroundodometer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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

class TripDetailActivity : Activity() {

    private lateinit var database:
        OdometerDatabaseHelper

    private var tripId:
        Long = -1

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        database =
            OdometerDatabaseHelper(this)

        tripId =
            intent.getLongExtra(
                "trip_id",
                -1
            )

        if (tripId <= 0) {

            finish()

            return
        }

        buildInterface()
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
            24,
            45,
            24,
            40
        )

        addTitle(
            root,
            "TRIP DETAILS"
        )

        addSpace(
            root,
            25
        )

        val trip =
            database.getTrip(
                tripId
            )

        if (trip == null) {

            addText(
                root,
                "Trip not found.",
                18f,
                Color.RED
            )

        } else {

            val points =
                database.getTrackPoints(
                    tripId
                )

            addSection(
                root,
                "DATE",
                formatDate(
                    trip.startTime
                )
            )

            addSection(
                root,
                "START TIME",
                formatDateTime(
                    trip.startTime
                )
            )

            addSection(
                root,
                "END TIME",
                if (trip.endTime > 0) {
                    formatDateTime(
                        trip.endTime
                    )
                } else {
                    "Active"
                }
            )

            addSection(
                root,
                "ODOMETER DISTANCE",
                "%.2f km".format(
                    trip.distanceKm
                ),
                true
            )

            addSection(
                root,
                "AVERAGE SPEED",
                "%.1f km/h".format(
                    trip.averageSpeed
                ),
                true
            )

            addSection(
                root,
                "MAXIMUM SPEED",
                "%.1f km/h".format(
                    trip.maxSpeed
                ),
                true
            )

            addSection(
                root,
                "GPS POINTS RECORDED",
                points.size.toString(),
                true
            )

            addSpace(
                root,
                10
            )

            val routeButton =
                TextView(this)

            routeButton.text =
                "VIEW ROUTE MAP"

            routeButton.textSize =
                17f

            routeButton.setTextColor(
                Color.WHITE
            )

            routeButton.typeface =
                Typeface.DEFAULT_BOLD

            routeButton.gravity =
                Gravity.CENTER

            routeButton.setPadding(
                10,
                20,
                10,
                20
            )

            routeButton.setBackgroundColor(
                Color.rgb(
                    0,
                    90,
                    100
                )
            )

            routeButton.setOnClickListener {

                val intent =
                    Intent(
                        this,
                        RouteMapActivity::class.java
                    )

                intent.putExtra(
                    "trip_id",
                    tripId
                )

                startActivity(intent)
            }

            root.addView(
                routeButton,
                wrapParams()
            )

            addSpace(
                root,
                18
            )

            addText(
                root,
                "GPS points are retained even when " +
                    "speed is below the odometer threshold.",
                13f,
                Color.GRAY
            )
        }

        addSpace(
            root,
            30
        )

        val back =
            TextView(this)

        back.text =
            "BACK TO TRIPS"

        back.textSize =
            16f

        back.setTextColor(
            Color.WHITE
        )

        back.typeface =
            Typeface.DEFAULT_BOLD

        back.gravity =
            Gravity.CENTER

        back.setPadding(
            10,
            18,
            10,
            18
        )

        back.setBackgroundColor(
            Color.rgb(
                21,
                21,
                21
            )
        )

        back.setOnClickListener {
            finish()
        }

        root.addView(
            back,
            wrapParams()
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

    private fun addTitle(
        root: LinearLayout,
        text: String
    ) {

        addText(
            root,
            text,
            27f,
            Color.WHITE,
            true
        )
    }

    private fun addSection(
        root: LinearLayout,
        label: String,
        value: String,
        big: Boolean = false
    ) {

        addText(
            root,
            label,
            14f,
            Color.GRAY
        )

        addSpace(
            root,
            4
        )

        addText(
            root,
            value,
            if (big) 27f else 18f,
            Color.WHITE,
            big
        )

        addSpace(
            root,
            20
        )
    }

    private fun addText(
        root: LinearLayout,
        text: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) {

        val view =
            TextView(this)

        view.text =
            text

        view.textSize =
            size

        view.setTextColor(
            color
        )

        view.gravity =
            Gravity.CENTER

        if (bold) {

            view.typeface =
                Typeface.DEFAULT_BOLD
        }

        root.addView(
            view,
            wrapParams()
        )
    }

    private fun formatDate(
        time: Long
    ): String {

        return SimpleDateFormat(
            "dd MMMM yyyy",
            Locale.getDefault()
        ).format(
            Date(time)
        )
    }

    private fun formatDateTime(
        time: Long
    ): String {

        return SimpleDateFormat(
            "dd MMM yyyy • hh:mm:ss a",
            Locale.getDefault()
        ).format(
            Date(time)
        )
    }

    private fun wrapParams():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
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

    override fun onDestroy() {

        database.close()

        super.onDestroy()
    }
}
