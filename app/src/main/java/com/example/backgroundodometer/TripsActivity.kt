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
    private lateinit var dayTitle: TextView
    private var selectedDate = ""

    companion object {
        private const val PREFS = "background_odometer"
        private const val SELECTED_DATE = "selected_day"
        private const val GREEN = "#39D98A"
        private const val CARD = "#151816"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database=OdometerDatabaseHelper(this)
        selectedDate=intent.getStringExtra("selected_day")
            ?: getSharedPreferences(PREFS,MODE_PRIVATE).getString(SELECTED_DATE,today())!!
        saveSelectedDate()
        buildInterface()
        loadTrips()
        if(intent.getBooleanExtra("add_manual",false)) showManualTripDialog()
    }

    private fun buildInterface() {
        val scroll=ScrollView(this).apply{setBackgroundColor(Color.BLACK);isFillViewport=true}
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(28),dp(20),dp(32))}
        dayTitle=title(formatDate(selectedDate))
        root.addView(dayTitle,full())
        root.addView(info("Only trips assigned to this day are shown."),full())
        space(root,16)
        root.addView(action("CHANGE DAY"){chooseDay()},buttonParams())
        root.addView(action("ADD MANUAL TRIP"){showManualTripDialog()},buttonParams())
        root.addView(action("REFRESH"){loadTrips()},buttonParams())
        space(root,16)
        list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(list,full())
        space(root,18)
        root.addView(action("DAY CALENDAR"){startActivity(Intent(this,DaysActivity::class.java))},buttonParams())
        root.addView(action("BACK"){finish()},buttonParams())
        scroll.addView(root);setContentView(scroll)
    }

    private fun loadTrips(){
        if(!::list.isInitialized)return
        list.removeAllViews();dayTitle.text=formatDate(selectedDate)
        val trips=database.getTripsForDay(selectedDate)
        if(trips.isEmpty()){list.addView(emptyCard("No trips recorded for this day.\n\nAdd a manual trip or choose another date."),full());return}
        list.addView(info("%.2f km total  •  %d trip(s)".format(Locale.US,database.getDayDistance(selectedDate),trips.size)),full())
        space(list,10)
        trips.forEach{addTrip(it)}
    }

    private fun addTrip(trip:TripSummary){
        val manual=trip.distanceSource.equals("MANUAL",true) || trip.distanceSource.equals("MANUAL_ROUTE",true)
        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(16));background=cardBackground()}
        card.addView(label(if(manual)"MANUAL TRIP" else "GPS TRIP"),full())
        card.addView(big("%.2f km".format(Locale.US,trip.distanceKm)),full())
        card.addView(info(if(trip.distanceSource.equals("MANUAL_ROUTE",true)) "Manual road route" else if(manual) "Manual distance" else "Average %.1f km/h  •  Max %.1f km/h".format(Locale.US,trip.averageSpeed,trip.maxSpeed)),full())
        val time=if(trip.endTime>0)formatTime(trip.startTime)+" → "+formatTime(trip.endTime) else formatTime(trip.startTime)
        card.addView(info(time),full())
        if(trip.assignedPlace.isNotBlank())card.addView(info(trip.assignedPlace),full())
        space(card,8)
        card.addView(action(if(trip.distanceSource.equals("MANUAL_ROUTE",true)) "EDIT ROUTE" else "EDIT THIS TRIP"){ if(trip.distanceSource.equals("MANUAL_ROUTE",true)) startActivity(Intent(this,ManualRouteActivity::class.java).apply{putExtra("trip_id",trip.id)}) else showEditTripDialog(trip.id)},buttonParams())
        card.addView(action("VIEW DETAILS"){startActivity(Intent(this,TripDetailActivity::class.java).apply{putExtra("trip_id",trip.id)})},buttonParams())
        card.addView(action("DELETE THIS TRIP"){confirmDelete(trip.id)},buttonParams())
        val p=full();p.bottomMargin=dp(10);list.addView(card,p)
    }

    private fun chooseDay(){
        val cal=Calendar.getInstance()
        try{SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(selectedDate)?.let{cal.time=it}}catch(_:Exception){}
        DatePickerDialog(this,{_,y,m,d->selectedDate="%04d-%02d-%02d".format(y,m+1,d);saveSelectedDate();loadTrips()},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showEditTripDialog(id:Long){
        val trip=database.getTrip(id)?:return
        val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(25),dp(5),dp(25),dp(5))}
        val date=TextView(this).apply{text=selectedDate;textSize=17f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(dp(10),dp(16),dp(10),dp(16))}
        val cal=Calendar.getInstance()
        date.setOnClickListener{
            try{SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(date.text.toString())?.let{cal.time=it}}catch(_:Exception){}
            DatePickerDialog(this,{_,y,m,d->date.text="%04d-%02d-%02d".format(y,m+1,d)},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        val place=EditText(this).apply{hint="Place (optional)";setText(trip.assignedPlace)}
        layout.addView(info("ASSIGNED DAY"),full());layout.addView(date,full());layout.addView(place,full())
        var distance:EditText?=null;var start:EditText?=null;var end:EditText?=null
        if(trip.distanceSource.equals("MANUAL",true)){
            distance=EditText(this).apply{hint="Distance (km)";inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;setText("%.2f".format(Locale.US,trip.distanceKm))}
            start=EditText(this).apply{hint="Start time (optional)"}
            end=EditText(this).apply{hint="End time (optional)"}
            layout.addView(distance,full());layout.addView(start,full());layout.addView(end,full())
        }else layout.addView(info("GPS route and recorded GPS data remain unchanged. Only day and place can be changed."),full())
        AlertDialog.Builder(this).setTitle("EDIT TRIP").setView(layout).setNegativeButton("CANCEL",null).setPositiveButton("SAVE"){_,_->
            val d=date.text.toString().trim()
            if(trip.distanceSource.equals("MANUAL",true)){
                val km=distance?.text?.toString()?.toDoubleOrNull()
                if(km==null||km<0){toast("Enter a valid distance.");return@setPositiveButton}
                database.updateManualTrip(id,d,place.text.toString().trim(),km,parseOptionalTime(d,start?.text?.toString().orEmpty()),parseOptionalTime(d,end?.text?.toString().orEmpty()))
            }else database.updateTripAssignment(id,d,place.text.toString().trim())
            selectedDate=d;saveSelectedDate();loadTrips();toast("Trip updated")
        }.show()
    }

    private fun showManualTripDialog(){
        val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(25),dp(5),dp(25),dp(5))}
        val date=TextView(this).apply{text=selectedDate;textSize=17f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(dp(10),dp(16),dp(10),dp(16))}
        val cal=Calendar.getInstance()
        date.setOnClickListener{DatePickerDialog(this,{_,y,m,d->date.text="%04d-%02d-%02d".format(y,m+1,d)},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show()}
        val place=EditText(this).apply{hint="Place (optional)"}
        val distance=EditText(this).apply{hint="Distance (km)";inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL}
        val start=EditText(this).apply{hint="Start time (optional)"}
        val end=EditText(this).apply{hint="End time (optional)"}
        layout.addView(info("DAY"),full());layout.addView(date,full());layout.addView(place,full());layout.addView(distance,full());layout.addView(start,full());layout.addView(end,full())
        AlertDialog.Builder(this)
            .setTitle("ADD MANUAL TRIP")
            .setView(layout)
            .setNegativeButton("CANCEL",null)
            .setNeutralButton("DRAW / MARK ROUTE"){_,_->
                startActivity(Intent(this,ManualRouteActivity::class.java).apply{
                    putExtra("date",date.text.toString())
                    putExtra("place",place.text.toString().trim())
                })
            }
            .setPositiveButton("SAVE DISTANCE"){_,_->
                val km=distance.text.toString().toDoubleOrNull()
                if(km==null||km<0){toast("Enter a valid distance.");return@setPositiveButton}
                val d=date.text.toString()
                database.createManualTrip(d,place.text.toString().trim(),km,parseOptionalTime(d,start.text.toString()),parseOptionalTime(d,end.text.toString()))
                selectedDate=d;saveSelectedDate();loadTrips();toast("Manual trip added")
            }.show()
    }

    private fun confirmDelete(id:Long){AlertDialog.Builder(this).setTitle("DELETE TRIP?").setMessage("This permanently deletes the trip and its saved route points.").setNegativeButton("CANCEL",null).setPositiveButton("DELETE"){_,_->database.deleteTrip(id);loadTrips();toast("Trip deleted")}.show()}
    private fun parseOptionalTime(date:String,text:String):Long{if(text.isBlank())return 0L;for(f in arrayOf("yyyy-MM-dd hh:mm a","yyyy-MM-dd HH:mm","yyyy-MM-dd h:mm a"))try{return SimpleDateFormat(f,Locale.US).parse(if(f.contains("a"))date+" "+text.uppercase(Locale.US) else date+" "+text)?.time?:0L}catch(_:Exception){};return 0L}
    private fun saveSelectedDate(){getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(SELECTED_DATE,selectedDate).apply()}
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    private fun today()=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
    private fun formatDate(v:String)=try{SimpleDateFormat("dd MMMM yyyy",Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(v)!!)}catch(_:Exception){v}
    private fun formatTime(t:Long)=SimpleDateFormat("hh:mm a",Locale.getDefault()).format(Date(t))
    private fun title(t:String)=TextView(this).apply{text=t;textSize=27f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER}
    private fun big(t:String)=TextView(this).apply{text=t;textSize=30f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER}
    private fun info(t:String)=TextView(this).apply{text=t;textSize=14f;setTextColor(Color.LTGRAY);gravity=Gravity.CENTER}
    private fun label(t:String)=TextView(this).apply{text=t;textSize=13f;setTextColor(Color.parseColor(GREEN));gravity=Gravity.CENTER;typeface=Typeface.DEFAULT_BOLD}
    private fun action(t:String,click:()->Unit)=TextView(this).apply{text=t;textSize=15f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;background=buttonBackground();minHeight=dp(56);setPadding(dp(10),dp(12),dp(10),dp(12));setOnClickListener{click()}}
    private fun cardBackground()=GradientDrawable().apply{cornerRadius=dp(20).toFloat();setColor(Color.parseColor(CARD));setStroke(dp(1),Color.rgb(42,48,44))}
    private fun buttonBackground()=GradientDrawable().apply{cornerRadius=dp(18).toFloat();setColor(Color.parseColor("#0D0F0E"));setStroke(dp(1),Color.parseColor(GREEN))}
    private fun buttonParams()=full().apply{topMargin=dp(5);bottomMargin=dp(5)}
    private fun emptyCard(t:String)=TextView(this).apply{text=t;textSize=15f;setTextColor(Color.LTGRAY);gravity=Gravity.CENTER;setPadding(dp(24),dp(40),dp(24),dp(40));background=cardBackground()}
    private fun full()=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun space(p:LinearLayout,h:Int){p.addView(View(this),LinearLayout.LayoutParams(1,dp(h)))}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    override fun onResume(){super.onResume();if(::database.isInitialized)loadTrips()}
    override fun onDestroy(){database.close();super.onDestroy()}
}