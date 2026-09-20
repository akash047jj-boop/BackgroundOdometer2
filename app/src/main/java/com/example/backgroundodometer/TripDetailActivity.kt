package com.example.backgroundodometer

import android.app.Activity
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

    companion object {
        private const val TIFFANY =
            "#00BCD4"
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        database =
            OdometerDatabaseHelper(this)

        val tripId =
            intent.getLongExtra(
                "trip_id",
                -1
            )

        if (tripId <= 0) {

            finish()

            return
        }

        buildInterface(
            tripId
        )
    }

    // =====================================================
    // INTERFACE
    // =====================================================

    private fun buildInterface(
        tripId: Long
    ) {

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
            55,
            24,
            40
        )

        val title =
            TextView(this)

        title.text =
            "TRIP DETAILS"

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

            setContentView(scroll)

            scroll.addView(root)

            return
        }

        val points =
            database.getTrackPoints(
                tripId
            )

        // =================================================
        // DATE
        // =================================================

        addLabel(
            root,
            "DATE"
        )

        addValue(
            root,
            formatDate(
                trip.startTime
            )
        )

        addSpace(
            root,
            20
        )

        // =================================================
        // START
        // =================================================

        addLabel(
            root,
            "START TIME"
        )

        addValue(
            root,
            formatDateTime(
                trip.startTime
            )
        )

        addSpace(
            root,
            20
        )

        // =================================================
        // END
        // =================================================

        addLabel(
            root,
            "END TIME"
        )

        addValue(
            root,
            trip.endTime?.let {
                formatDateTime(it)
            } ?: "Active"
        )

        addSpace(
            root,
            20
        )

        // =================================================
        // DISTANCE
        // =================================================

        addLabel(
            root,
            "ODOMETER DISTANCE"
        )

        addBigValue(
            root,
            "%.2f km".format(
                trip.distanceKm
            )
        )

        addSpace(
            root,
            20
        )

        // =================================================
        // AVERAGE
        // =================================================

        addLabel(
            root,
            "AVERAGE SPEED"
        )

        addBigValue(
            root,
            "%.1f km/h".format(
                trip.averageSpeed
            )
        )

        addSpace(
            root,
            20
        )

        // =================================================
        // MAXIMUM
        // =================================================

        addLabel(
            root,
            "MAXIMUM SPEED"
        )

        addBigValue(
            root,
            "%.1f km/h".format(
                trip.maxSpeed
            )
        )

        addSpace(
            root,
            20
        )

        // =================================================
        // GPS POINTS
        // =================================================

        addLabel(
            root,
            "GPS POINTS RECORDED"
        )

        addBigValue(
            root,
            points.size.toString()
        )

        addSpace(
            root,
            15
        )

        val explanation =
            TextView(this)

        explanation.text =
            "GPS points are retained even when " +
                "speed is below the odometer threshold."

        explanation.textSize =
            13f

        explanation.setTextColor(
            Color.GRAY
        )

        explanation.gravity =
            Gravity.CENTER

        root.addView(
            explanation,
            wrapParams()
        )

        addSpace(
            root,
            30
        )

        // =================================================
        // BACK
        // =================================================

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
            Color.parseColor(
                "#151515"
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

    // =====================================================
    // TEXT
    // =====================================================

    private fun addLabel(
        root: LinearLayout,
        text: String
    ) {

        addText(
            root,
            text,
            14f,
            Color.GRAY
        )
    }

    private fun addValue(
        root: LinearLayout,
        text: String
    ) {

        addText(
            root,
            text,
            19f,
            Color.WHITE
        )
    }

    private fun addBigValue(
        root: LinearLayout,
        text: String
    ) {

        addText(
            root,
            text,
            28f,
            Color.WHITE
        )
    }

    private fun addText(
        root: LinearLayout,
        text: String,
        size: Float,
        color: Int
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

        if (size >= 25f) {
            view.typeface =
                Typeface.DEFAULT_BOLD
        }

        root.addView(
            view,
            wrapParams()
        )
    }

    // =====================================================
    // DATE
    // =====================================================

    private fun formatDate(
        timestamp: Long
    ): String {

        return SimpleDateFormat(
            "dd MMMM yyyy",
            Locale.getDefault()
        ).format(
            Date(timestamp)
        )
    }

    private fun formatDateTime(
        timestamp: Long
    ): String {

        return SimpleDateFormat(
            "dd MMM yyyy • hh:mm:ss a",
            Locale.getDefault()
        ).format(
            Date(timestamp)
        )
    }

    // =====================================================
    // HELPERS
    // =====================================================

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
