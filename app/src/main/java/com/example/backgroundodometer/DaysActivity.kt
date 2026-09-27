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
import android.widget.CalendarView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DaysActivity : Activity() {
    private lateinit var database: OdometerDatabaseHelper
    private lateinit var selectedText: TextView
    private lateinit var summaryText: TextView
    private var selectedDate = ""

    companion object {
        private const val PREFS = "background_odometer"
        private const val SELECTED_DATE = "selected_day"
        private const val GREEN = "#39D98A"
        private const val CARD = "#151816"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = OdometerDatabaseHelper(this)
        selectedDate = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(SELECTED_DATE, today()) ?: today()
        buildInterface()
        refreshSelectedDay()
    }

    private fun buildInterface() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.BLACK); isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }
        root.addView(title("DAY RECORDS"), full())
        root.addView(subtitle("Choose a date to view that day's trips"), full())
        space(root, 18)

        val calendarCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = cardBackground()
        }
        val calendar = CalendarView(this).apply {
            date = parseDate(selectedDate)
            setOnDateChangeListener { _, year, month, day ->
                selectedDate = "%04d-%02d-%02d".format(year, month + 1, day)
                saveSelectedDate()
                refreshSelectedDay()
            }
        }
        calendarCard.addView(calendar, full())
        root.addView(calendarCard, full())

        space(root, 18)
        selectedText = title(selectedDate).apply { textSize = 21f; setTextColor(Color.parseColor(GREEN)) }
        root.addView(selectedText, full())
        space(root, 10)
        summaryText = subtitle("")
        root.addView(summaryText, full())
        space(root, 18)

        root.addView(action("VIEW TRIPS FOR THIS DAY") { openTrips(false) }, buttonParams())
        root.addView(action("ADD MANUAL TRIP") { openTrips(true) }, buttonParams())
        root.addView(action("REFRESH") { refreshSelectedDay() }, buttonParams())
        root.addView(action("BACK") { finish() }, buttonParams())
        space(root, 24)
        root.addView(subtitle("Trip records are no longer listed on this screen. Select a date, then open its Trips view."), full())

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun refreshSelectedDay() {
        if (!::selectedText.isInitialized) return
        val trips = database.getTripsForDay(selectedDate)
        selectedText.text = formatDate(selectedDate)
        summaryText.text = "%.2f km  •  %d trip(s)  •  %.2f L fuel added".format(
            Locale.US, database.getDayDistance(selectedDate), trips.size, database.getDayFuel(selectedDate)
        )
    }

    private fun openTrips(addManual: Boolean) {
        saveSelectedDate()
        startActivity(Intent(this, TripsActivity::class.java).apply {
            putExtra("selected_day", selectedDate)
            putExtra("add_manual", addManual)
        })
    }

    private fun saveSelectedDate() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(SELECTED_DATE, selectedDate).apply()
    }
    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun parseDate(v: String) = try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(v)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
    private fun formatDate(v: String) = try { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(v)!!) } catch (_: Exception) { v }
    private fun title(t: String) = TextView(this).apply { text=t; textSize=28f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER }
    private fun subtitle(t: String) = TextView(this).apply { text=t; textSize=14f; setTextColor(Color.LTGRAY); gravity=Gravity.CENTER }
    private fun action(t: String, click: () -> Unit) = TextView(this).apply { text=t; textSize=15f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER; background=buttonBackground(); minHeight=dp(58); setPadding(dp(12),dp(12),dp(12),dp(12)); setOnClickListener{click()} }
    private fun cardBackground() = GradientDrawable().apply { cornerRadius=dp(22).toFloat(); setColor(Color.parseColor(CARD)); setStroke(dp(1),Color.rgb(42,48,44)) }
    private fun buttonBackground() = GradientDrawable().apply { cornerRadius=dp(18).toFloat(); setColor(Color.parseColor("#0D0F0E")); setStroke(dp(1),Color.parseColor(GREEN)) }
    private fun buttonParams() = full().apply { topMargin=dp(5); bottomMargin=dp(5) }
    private fun full() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun space(p: LinearLayout,h:Int){p.addView(View(this),LinearLayout.LayoutParams(1,dp(h)))}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    override fun onResume(){super.onResume();if(::database.isInitialized)refreshSelectedDay()}
    override fun onDestroy(){database.close();super.onDestroy()}
}