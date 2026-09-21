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
import java.util.Locale

class DaysActivity : Activity() {

    private lateinit var database:
        OdometerDatabaseHelper

    private lateinit var list:
        LinearLayout

    companion object {

        private const val TIFFANY =
            "#00BCD4"

        private const val CARD =
            "#151515"
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        database =
            OdometerDatabaseHelper(this)

        buildInterface()
        loadDays()
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
            35
        )

        val title =
            TextView(this)

        title.text =
            "DAY RECORDS"

        title.textSize = 27f
        title.setTextColor(Color.WHITE)
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER

        root.addView(
            title,
            wrapParams()
        )

        addSpace(root, 20)

        val refresh =
            createAction("REFRESH")

        refresh.setOnClickListener {
            loadDays()
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

        val trips =
            createAction("TRIP HISTORY")

        trips.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    TripsActivity::class.java
                )
            )
        }

        root.addView(
            trips,
            fullParams(58)
        )

        addSpace(root, 10)

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

    private fun loadDays() {

        list.removeAllViews()

        val dates =
            database.getAssignedDates()

        if (dates.isEmpty()) {

            val empty =
                TextView(this)

            empty.text =
                "No day records yet.\n\n" +
                    "Go to TRIPS → select trips → " +
                    "ASSIGN SELECTED."

            empty.textSize = 17f
            empty.setTextColor(Color.GRAY)
            empty.gravity = Gravity.CENTER
            empty.setPadding(
                10,
                40,
                10,
                40
            )

            list.addView(
                empty,
                wrapParams()
            )

            return
        }

        for (date in dates) {

            val trips =
                database.getTripsForDay(date)

            val distance =
                database.getDayDistance(date)

            val fuel =
                database.getDayFuel(date)

            val place =
                trips.firstOrNull()
                    ?.assignedPlace
                    ?: ""

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

                    cornerRadius = 20f

                    setColor(
                        Color.parseColor(CARD)
                    )

                    setStroke(
                        1,
                        Color.DKGRAY
                    )
                }

            val day =
                TextView(this)

            day.text =
                formatDate(date)

            day.textSize = 20f
            day.setTextColor(
                Color.parseColor(TIFFANY)
            )
            day.typeface =
                Typeface.DEFAULT_BOLD

            card.addView(
                day,
                wrapParams()
            )

            if (place.isNotBlank()) {

                val placeText =
                    TextView(this)

                placeText.text =
                    place

                placeText.textSize = 15f
                placeText.setTextColor(
                    Color.LTGRAY
                )

                card.addView(
                    placeText,
                    wrapParams()
                )
            }

            val details =
                TextView(this)

            details.text =
                "Distance: %.2f km\n".format(
                    distance
                ) +
                    "Fuel: %.2f L\n".format(
                        fuel
                    ) +
                    "Trips: ${trips.size}"

            details.textSize = 16f
            details.setTextColor(Color.WHITE)

            card.addView(
                details,
                wrapParams()
            )

            card.setOnClickListener {

                val intent =
                    Intent(
                        this,
                        DayDetailActivity::class.java
                    )

                intent.putExtra(
                    "day_date",
                    date
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
            loadDays()
        }
    }

    override fun onDestroy() {

        database.close()

        super.onDestroy()
    }
}
