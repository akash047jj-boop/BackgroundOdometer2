package com.example.backgroundodometer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Locale

class DayDetailActivity : Activity() {

    private lateinit var database:
        OdometerDatabaseHelper

    private var dayDate =
        ""

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        database =
            OdometerDatabaseHelper(this)

        dayDate =
            intent.getStringExtra(
                "day_date"
            ) ?: ""

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
            20,
            40,
            20,
            40
        )

        addText(
            root,
            "DAY DETAILS",
            27f,
            Color.WHITE,
            true
        )

        addSpace(root, 20)

        addText(
            root,
            formatDate(dayDate),
            21f,
            Color.parseColor("#00BCD4"),
            true
        )

        addSpace(root, 20)

        val trips =
            database.getTripsForDay(
                dayDate
            )

        addSection(
            root,
            "TOTAL DISTANCE",
            "%.2f km".format(
                database.getDayDistance(
                    dayDate
                )
            )
        )

        addSection(
            root,
            "FUEL ADDED",
            "%.2f L".format(
                database.getDayFuel(
                    dayDate
                )
            )
        )

        addSection(
            root,
            "TRIPS",
            trips.size.toString()
        )

        val place =
            trips.firstOrNull()
                ?.assignedPlace
                ?: ""

        addSection(
            root,
            "PLACE",
            if (place.isBlank()) {
                "Not specified"
            } else {
                place
            }
        )

        addSpace(root, 10)

        addText(
            root,
            "TRIPS ON THIS DAY",
            18f,
            Color.WHITE,
            true
        )

        addSpace(root, 10)

        for (trip in trips) {

            val card =
                LinearLayout(this)

            card.orientation =
                LinearLayout.VERTICAL

            card.setPadding(
                16,
                16,
                16,
                16
            )

            card.background =
                GradientDrawable().apply {

                    cornerRadius = 18f

                    setColor(
                        Color.rgb(
                            21,
                            21,
                            21
                        )
                    )

                    setStroke(
                        1,
                        Color.DKGRAY
                    )
                }

            val title =
                TextView(this)

            title.text =
                "%.2f km".format(
                    trip.distanceKm
                )

            title.textSize = 24f
            title.setTextColor(Color.WHITE)
            title.typeface =
                Typeface.DEFAULT_BOLD

            card.addView(
                title,
                wrapParams()
            )

            val time =
                TextView(this)

            time.text =
                SimpleDateFormat(
                    "hh:mm a",
                    Locale.getDefault()
                ).format(
                    java.util.Date(
                        trip.startTime
                    )
                )

            time.textSize = 14f
            time.setTextColor(Color.GRAY)

            card.addView(
                time,
                wrapParams()
            )

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

            params.bottomMargin = 10

            root.addView(
                card,
                params
            )
        }

        addSpace(root, 20)

        val back =
            createButton("BACK")

        back.setOnClickListener {
            finish()
        }

        root.addView(
            back,
            wrapParams()
        )

        scroll.addView(root)

        setContentView(scroll)
    }

    private fun addSection(
        root: LinearLayout,
        label: String,
        value: String
    ) {

        addText(
            root,
            label,
            14f,
            Color.GRAY
        )

        addSpace(root, 3)

        addText(
            root,
            value,
            22f,
            Color.WHITE,
            true
        )

        addSpace(root, 16)
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

        view.text = text
        view.textSize = size
        view.setTextColor(color)
        view.gravity = Gravity.CENTER

        if (bold) {
            view.typeface =
                Typeface.DEFAULT_BOLD
        }

        root.addView(
            view,
            wrapParams()
        )
    }

    private fun createButton(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text = text
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(
                10,
                18,
                10,
                18
            )

            setBackgroundColor(
                Color.rgb(
                    21,
                    21,
                    21
                )
            )
        }
    }

    private fun formatDate(
        value: String
    ): String {

        return try {

            SimpleDateFormat(
                "dd MMMM yyyy",
                Locale.getDefault()
            ).format(
                SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
                ).parse(value)!!
            )

        } catch (_: Exception) {
            value
        }
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
            android.view.View(this),
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
