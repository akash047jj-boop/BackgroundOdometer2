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
    private lateinit var database: OdometerDatabaseHelper
    private lateinit var list: LinearLayout
    private val selectedIds=mutableSetOf<Long>()
    private val checkboxes=mutableMapOf<Long,TextView>()
    companion object { private const val TIFFANY="#00BCD4"; private const val CARD="#151515" }

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);database=OdometerDatabaseHelper(this);buildInterface();loadTrips()}

    private fun buildInterface(){
        val scroll=ScrollView(this).apply{setBackgroundColor(Color.BLACK)}
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,30,20,30)}
        root.addView(title("TRIPS"),wrapParams())
        root.addView(info("Select automatic trips to assign them to a date/place, or add a manual trip."),wrapParams())
        space(root,16)
        root.addView(action("ADD MANUAL TRIP"){showManualTripDialog()},buttonParams())
        root.addView(action("ASSIGN SELECTED"){if(selectedIds.isEmpty())Toast.makeText(this,"Select at least one trip.",Toast.LENGTH_SHORT).show()else showAssignDialog()},buttonParams())
        root.addView(action("REFRESH"){loadTrips()},buttonParams())
        space(root,14)
        list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(list,wrapParams())
        space(root,18)
        root.addView(action("BACK"){finish()},buttonParams())
        scroll.addView(root);setContentView(scroll)
    }

    private fun loadTrips(){
        if(!::list.isInitialized)return
        list.removeAllViews();checkboxes.clear()
        val trips=database.getAllTrips()
        if(trips.isEmpty()){list.addView(info("No trips yet. Add a manual trip or start GPS tracking."),wrapParams());return}
        trips.forEach{addTrip(it)}
    }

    private fun addTrip(trip:TripSummary){
        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(14,14,14,14);background=GradientDrawable().apply{cornerRadius=18f;setColor(Color.parseColor(CARD));setStroke(1,Color.DKGRAY)}}
        if(trip.distanceSource!="MANUAL"){
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,0,0,8)}
            val selector=TextView(this).apply{
                text="□"
                textSize=38f
                setTextColor(Color.parseColor(TIFFANY))
                typeface=Typeface.DEFAULT_BOLD
                gravity=Gravity.CENTER
                minWidth=dp(68)
                minHeight=dp(68)
                setPadding(4,0,4,4)
                background=selectionBackground(false)
                contentDescription="Select trip"
                setOnClickListener{
                    val selected=if(selectedIds.contains(trip.id)){selectedIds.remove(trip.id);false}else{selectedIds.add(trip.id);true}
                    updateSelectionVisual(this,selected)
                }
            }
            checkboxes[trip.id]=selector
            row.addView(selector,LinearLayout.LayoutParams(dp(72),dp(72)))
            val selectLabel=info("SELECT THIS TRIP").apply{setTextSize(16f);setTypeface(Typeface.DEFAULT_BOLD);setPadding(14,0,0,0);gravity=Gravity.CENTER_VERTICAL}
            row.addView(selectLabel,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
            card.addView(row,wrapParams())
        }
        val type=if(trip.distanceSource=="MANUAL")"MANUAL TRIP" else "GPS TRIP"
        card.addView(label(type),wrapParams())
        card.addView(big("%.2f km".format(trip.distanceKm)),wrapParams())
        card.addView(info(if(trip.distanceSource=="MANUAL")"Manual distance" else "Average %.1f km/h • Max %.1f km/h".format(trip.averageSpeed,trip.maxSpeed)),wrapParams())
        if(trip.distanceSource=="MANUAL") card.addView(info("Date: ${formatDate(trip.startTime)}"),wrapParams()) else card.addView(info("${formatDateTime(trip.startTime)} → ${if(trip.endTime>0)formatTime(trip.endTime)else"Active"}"),wrapParams())
        if(trip.assignedDate.isNotBlank())card.addView(info("ASSIGNED: ${trip.assignedDate}${if(trip.assignedPlace.isNotBlank())" • ${trip.assignedPlace}" else ""}"),wrapParams())
        card.setOnClickListener{startActivity(Intent(this,TripDetailActivity::class.java).apply{putExtra("trip_id",trip.id)})}
        val p=wrapParams();p.bottomMargin=10;list.addView(card,p)
    }

    private fun showManualTripDialog(){
        val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(25,5,25,5)}
        val date=TextView(this).apply{text=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date());textSize=17f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(10,16,10,16)}
        val cal=Calendar.getInstance();date.setOnClickListener{DatePickerDialog(this,{_,y,m,d->date.text="%04d-%02d-%02d".format(y,m+1,d)},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show()}
        val place=EditText(this).apply{hint="Place (optional)"}
        val distance=EditText(this).apply{hint="Distance (km)";inputType=2 or 8192}
        val start=EditText(this).apply{hint="Start time (optional, e.g. 08:30 AM)"}
        val end=EditText(this).apply{hint="End time (optional, e.g. 09:15 AM)"}
        layout.addView(info("DATE"),wrapParams());layout.addView(date,wrapParams());layout.addView(place,wrapParams());layout.addView(distance,wrapParams());layout.addView(start,wrapParams());layout.addView(end,wrapParams())
        AlertDialog.Builder(this).setTitle("ADD MANUAL TRIP").setMessage("Manual trips add their distance directly to the odometer and do not require GPS route points.").setView(layout).setNegativeButton("CANCEL",null).setPositiveButton("SAVE"){_,_->
            val km=distance.text.toString().toDoubleOrNull();if(km==null||km<0){Toast.makeText(this,"Enter a valid distance",Toast.LENGTH_SHORT).show();return@setPositiveButton}
            val dateValue=date.text.toString();val startMs=parseOptionalTime(dateValue,start.text.toString());val endMs=parseOptionalTime(dateValue,end.text.toString());
            database.createManualTrip(dateValue,place.text.toString().trim(),km,startMs,endMs)
            loadTrips();Toast.makeText(this,"Manual trip added",Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun parseOptionalTime(date:String,text:String):Long{if(text.isBlank())return 0L;val formats=arrayOf("yyyy-MM-dd hh:mm a","yyyy-MM-dd HH:mm","yyyy-MM-dd h:mm a");for(f in formats){try{return SimpleDateFormat(f,Locale.US).parse(if(f.contains("hh")||f.contains("h:mm a"))"$date ${text.uppercase()}" else "$date $text")?.time?:0L}catch(_:Exception){}};return 0L}

    private fun showAssignDialog(){
        val cal=Calendar.getInstance();val dateText=TextView(this).apply{text=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(cal.time);textSize=18f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(20,20,20,20)}
        dateText.setOnClickListener{DatePickerDialog(this,{_,y,m,d->dateText.text="%04d-%02d-%02d".format(y,m+1,d)},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show()}
        val place=EditText(this).apply{hint="Place (optional)"}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(25,5,25,5)};box.addView(dateText,wrapParams());box.addView(place,wrapParams())
        AlertDialog.Builder(this).setTitle("ASSIGN TRIPS").setMessage("The original trip time remains unchanged. This only assigns the trip to a day/place record.").setView(box).setNegativeButton("CANCEL",null).setPositiveButton("ASSIGN"){_,_->database.assignTripsToDay(selectedIds.toList(),dateText.text.toString(),place.text.toString().trim());selectedIds.clear();loadTrips();Toast.makeText(this,"Trips assigned",Toast.LENGTH_SHORT).show()}.show()
    }


    private fun selectionBackground(selected:Boolean)=GradientDrawable().apply{
        cornerRadius=12f
        setColor(if(selected)Color.parseColor("#073C43") else Color.parseColor("#101010"))
        setStroke(dp(2),Color.parseColor(TIFFANY))
    }

    private fun updateSelectionVisual(view:TextView,selected:Boolean){
        view.text=if(selected)"✓" else "□"
        view.background=selectionBackground(selected)
    }

    private fun title(t:String)=TextView(this).apply{this.text=t;textSize=27f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER}
    private fun info(t:String)=TextView(this).apply{this.text=t;textSize=14f;setTextColor(Color.LTGRAY);gravity=Gravity.CENTER}
    private fun label(t:String)=TextView(this).apply{this.text=t;textSize=13f;setTextColor(Color.parseColor(TIFFANY));gravity=Gravity.CENTER}
    private fun big(t:String)=TextView(this).apply{this.text=t;textSize=27f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER}
    private fun action(t:String,a:()->Unit)=TextView(this).apply{this.text=t;textSize=15f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setBackgroundColor(Color.rgb(21,21,21));setPadding(10,14,10,14);minHeight=dp(58);setOnClickListener{a()}}
    private fun wrapParams()=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun buttonParams()=wrapParams().apply{topMargin=4;bottomMargin=4}
    private fun space(p:LinearLayout,h:Int){p.addView(View(this),LinearLayout.LayoutParams(1,dp(h)))}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun formatDate(t:Long)=SimpleDateFormat("dd MMM yyyy",Locale.getDefault()).format(Date(t))
    private fun formatDateTime(t:Long)=SimpleDateFormat("dd MMM yyyy • hh:mm a",Locale.getDefault()).format(Date(t))
    private fun formatTime(t:Long)=SimpleDateFormat("hh:mm a",Locale.getDefault()).format(Date(t))
    override fun onResume(){super.onResume();if(::database.isInitialized)loadTrips()}
    override fun onDestroy(){database.close();super.onDestroy()}
}
