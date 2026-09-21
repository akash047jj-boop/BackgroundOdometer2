package com.example.backgroundodometer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

class ExportActivity : Activity() {

    private lateinit var database: OdometerDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = OdometerDatabaseHelper(this)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(24, 50, 24, 40)
        root.setBackgroundColor(Color.BLACK)

        val title = TextView(this)
        title.text = "EXPORT"
        title.textSize = 28f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER
        root.addView(title, params())

        addSpace(root, 25)

        val info = TextView(this)
        info.text = "Exports assigned day records as an Excel-compatible CSV file with Date, Place, Distance and Fuel."
        info.textSize = 16f
        info.setTextColor(Color.LTGRAY)
        info.gravity = Gravity.CENTER
        root.addView(info, params())

        addSpace(root, 25)

        root.addView(button("EXPORT DAY RECORDS") { exportDays() }, full(60))
        addSpace(root, 10)
        root.addView(button("BACK") { finish() }, full(60))

        setContentView(root)
    }

    private fun exportDays() {
        val dates = database.getAssignedDates()
        if (dates.isEmpty()) {
            Toast.makeText(this, "No assigned day records to export.", Toast.LENGTH_LONG).show()
            return
        }

        val csv = StringBuilder()
        csv.append("Date,Place,Distance,Fuel\n")

        for (date in dates) {
            val trips = database.getTripsForDay(date)
            val place = trips.firstOrNull()?.assignedPlace.orEmpty().replace("\"", "\"\"")
            val distance = database.getDayDistance(date)
            val fuel = database.getDayFuel(date)
            csv.append(
                "\"$date\",\"$place\",\"${String.format(Locale.US, "%.2f", distance)}\",\"${String.format(Locale.US, "%.2f", fuel)}\"\n"
            )
        }

        try {
            val dir = File(cacheDir, "exports")
            dir.mkdirs()
            val file = File(dir, "background_odometer_day_records.csv")
            file.writeText(csv.toString())

            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )

            val share = Intent(Intent.ACTION_SEND)
            share.type = "text/csv"
            share.putExtra(Intent.EXTRA_STREAM, uri)
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(share, "Export day records"))
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun button(text: String, action: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setBackgroundColor(Color.rgb(21, 21, 21))
        setOnClickListener { action() }
    }

    private fun params() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun full(height: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, height
    )

    private fun addSpace(parent: LinearLayout, dp: Int) {
        val density = resources.displayMetrics.density
        parent.addView(TextView(this), LinearLayout.LayoutParams(1, (dp * density).toInt()))
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }
}
