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
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); database=OdometerDatabaseHelper(this); buildUi() }
    private fun buildUi(){
        val scroll=android.widget.ScrollView(this).apply{setBackgroundColor(Color.BLACK)}
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,35,24,35)}
        root.addView(title("EXPORT"),params());space(root,18)
        root.addView(info("Excel-compatible CSV: Date, Place, Distance and Fuel."),params());space(root,18)
        root.addView(button("EXPORT DAY RECORDS"){exportDays()},buttonParams());root.addView(button("BACK"){finish()},buttonParams())
        scroll.addView(root);setContentView(scroll)
    }
    private fun exportDays(){
        val dates=database.getAssignedDates();if(dates.isEmpty()){Toast.makeText(this,"No assigned day records to export.",Toast.LENGTH_LONG).show();return}
        val csv=StringBuilder().append("Date,Place,Distance,Fuel\n")
        for(date in dates){
            val trips=database.getTripsForDay(date)
            val place=trips.firstOrNull()?.assignedPlace.orEmpty().replace("\"","\"\"")
            val distance=database.getDayDistance(date)
            val fuel=database.getDayFuel(date)
            csv.append("\"").append(date).append("\",\"").append(place).append("\",\"")
                .append(String.format(Locale.US,"%.2f",distance)).append("\",\"")
                .append(String.format(Locale.US,"%.2f",fuel)).append("\"\n")
        }
        try{
            val dir=File(cacheDir,"exports");dir.mkdirs();val file=File(dir,"background_odometer_day_records.csv");file.writeText(csv.toString())
            val uri=FileProvider.getUriForFile(this,"$packageName.fileprovider",file)
            val share=Intent(Intent.ACTION_SEND).apply{type="text/csv";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            startActivity(Intent.createChooser(share,"Export day records"))
        }catch(e:Exception){Toast.makeText(this,"Export failed: ${e.message}",Toast.LENGTH_LONG).show()}
    }
    private fun title(t:String)=TextView(this).apply{this.text=t;textSize=28f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER}
    private fun info(t:String)=TextView(this).apply{this.text=t;textSize=16f;setTextColor(Color.LTGRAY);gravity=Gravity.CENTER}
    private fun button(t:String,a:()->Unit)=TextView(this).apply{this.text=t;textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setBackgroundColor(Color.rgb(21,21,21));setPadding(10,14,10,14);minHeight=dp(60);setOnClickListener{a()}}
    private fun params()=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun buttonParams()=params().apply{topMargin=5;bottomMargin=5}
    private fun space(p:LinearLayout,h:Int){p.addView(TextView(this),LinearLayout.LayoutParams(1,dp(h)))}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    override fun onDestroy(){database.close();super.onDestroy()}
}
