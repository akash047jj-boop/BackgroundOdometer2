package com.example.backgroundodometer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var database: OdometerDatabaseHelper
    private val prefs by lazy { getSharedPreferences("background_odometer", MODE_PRIVATE) }
    private val cyan = Color.rgb(0,188,212)

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); database = OdometerDatabaseHelper(this); buildUi() }
    override fun onResume() { super.onResume(); if (::database.isInitialized) buildUi() }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.BLACK); isFillViewport = true }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22,28,22,32) }
        root.addView(title("SETTINGS"), params())
        root.addView(value("Scroll down for all settings"), params())
        space(root,20)

        section(root, "ODOMETER SPEED THRESHOLD", "%.1f km/h".format(database.getSpeedThreshold()))
        root.addView(button("CHANGE SPEED THRESHOLD") { showSpeedDialog() }, buttonParams())
        space(root,22)

        val enabled = database.isDistanceAlertEnabled(); val target = database.getDistanceAlertTarget()
        section(root, "DISTANCE ALERT", if (enabled && target > 0) "ON • %.2f km".format(target) else "OFF")
        root.addView(button("SET DISTANCE ALERT") { showDistanceAlertDialog() }, buttonParams())
        root.addView(button("DISABLE DISTANCE ALERT") { database.setDistanceAlert(false,0.0); database.resetDistanceAlertTrigger(); buildUi() }, buttonParams())
        space(root,22)

        section(root, "FUEL SETTINGS", "Tank %.2f L • Reserve %.2f L".format(database.getTankCapacity(), database.getReserveFuel()))
        root.addView(button("CHANGE FUEL SETTINGS") { showFuelSettingsDialog() }, buttonParams())
        space(root,22)

        val auto = prefs.getBoolean("auto_tracking_enabled", false)
        section(root, "BACKGROUND TRACKING", if (auto) "AUTO TRACKING: ON" else "AUTO TRACKING: OFF")
        root.addView(button(if (auto) "DISABLE AUTO TRACKING" else "ENABLE AUTO TRACKING") { prefs.edit().putBoolean("auto_tracking_enabled", !auto).apply(); buildUi() }, buttonParams())
        root.addView(button("OPEN APP PERMISSIONS") { openAppSettings() }, buttonParams())
        root.addView(button("BATTERY OPTIMIZATION SETTINGS") { openBatterySettings() }, buttonParams())
        space(root,22)

        section(root, "ODOMETER RESET", "Resets displayed total only. Trips and routes remain saved.")
        root.addView(button("CLEAR TOTAL ODOMETER") { confirmClearOdometer() }, buttonParams())
        space(root,22)

        section(root, "DATA", "Delete every trip, route point, fuel record and saved setting.")
        root.addView(button("DELETE ALL DATA") { confirmDeleteAll() }, buttonParams())
        space(root,22)
        root.addView(button("BACK TO HOME") { finish() }, buttonParams())
        space(root,30)
        root.addView(value("App by Potato's man"), params())
        scroll.addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(scroll)
    }

    private fun section(root: LinearLayout, heading: String, current: String) {
        root.addView(label(heading), params())
        root.addView(value(current), params())
        space(root,8)
    }

    private fun showFuelSettingsDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30,10,30,10) }
        val tank = EditText(this).apply { hint="Tank capacity (L)"; inputType=2 or 8192; setText(database.getTankCapacity().toString()) }
        val reserve = EditText(this).apply { hint="Reserve fuel (L)"; inputType=2 or 8192; setText(database.getReserveFuel().toString()) }
        layout.addView(tank,params()); layout.addView(reserve,params())
        AlertDialog.Builder(this).setTitle("FUEL SETTINGS").setView(layout).setNegativeButton("CANCEL",null).setPositiveButton("SAVE") { _, _ ->
            val t=tank.text.toString().toDoubleOrNull(); val r=reserve.text.toString().toDoubleOrNull()
            if(t==null||r==null||t<=0||r<0||r>=t){Toast.makeText(this,"Invalid fuel settings",Toast.LENGTH_SHORT).show();return@setPositiveButton}
            database.setFuelSettings(t,r); buildUi()
        }.show()
    }

    private fun showSpeedDialog() {
        val input=EditText(this).apply{hint="km/h";inputType=2 or 8192;setText(database.getSpeedThreshold().toString())}
        AlertDialog.Builder(this).setTitle("SPEED THRESHOLD").setMessage("Distance is accumulated when movement reaches this speed threshold.").setView(input).setNegativeButton("CANCEL",null).setPositiveButton("SAVE"){_,_->
            val v=input.text.toString().toDoubleOrNull();if(v==null||v<=0){Toast.makeText(this,"Enter a valid speed",Toast.LENGTH_SHORT).show();return@setPositiveButton};database.setSpeedThreshold(v);buildUi()
        }.show()
    }

    private fun showDistanceAlertDialog() {
        val input=EditText(this).apply{hint="Target odometer (km)";inputType=2 or 8192;database.getDistanceAlertTarget().takeIf{it>0}?.let{setText(it.toString())}}
        AlertDialog.Builder(this).setTitle("DISTANCE ALERT").setMessage("Alert when the displayed odometer reaches this value.").setView(input).setNegativeButton("CANCEL",null).setPositiveButton("SAVE"){_,_->
            val t=input.text.toString().toDoubleOrNull();if(t==null||t<=0){Toast.makeText(this,"Enter a valid target",Toast.LENGTH_SHORT).show();return@setPositiveButton};database.setDistanceAlert(true,t);database.resetDistanceAlertTrigger();buildUi()
        }.show()
    }

    private fun confirmClearOdometer(){AlertDialog.Builder(this).setTitle("CLEAR TOTAL ODOMETER?").setMessage("The displayed total will become 0 km. Existing trips and routes remain saved.").setNegativeButton("CANCEL",null).setPositiveButton("CLEAR"){_,_->database.clearDisplayedOdometer();buildUi()}.show()}
    private fun confirmDeleteAll(){AlertDialog.Builder(this).setTitle("DELETE ALL DATA?").setMessage("This permanently deletes all trips, route points, fuel records and saved settings. This cannot be undone.").setNegativeButton("CANCEL",null).setPositiveButton("DELETE EVERYTHING"){_,_->prefs.edit().clear().apply();database.deleteAllData();buildUi()}.show()}
    private fun openAppSettings(){try{startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply{data=Uri.parse("package:$packageName")})}catch(_:Exception){}}
    private fun openBatterySettings(){try{startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))}catch(_:Exception){openAppSettings()}}

    private fun title(text:String)=TextView(this).apply{this.text=text;textSize=27f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER}
    private fun label(text:String)=TextView(this).apply{this.text=text;textSize=14f;setTextColor(cyan);gravity=Gravity.CENTER}
    private fun value(text:String)=TextView(this).apply{this.text=text;textSize=17f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(8,8,8,8)}
    private fun button(text:String,action:()->Unit)=TextView(this).apply{this.text=text;textSize=15f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setBackgroundColor(Color.rgb(22,22,22));setPadding(12,14,12,14);minHeight=dp(60);setOnClickListener{action()}}
    private fun params()=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun buttonParams()=params().apply{topMargin=5;bottomMargin=5}
    private fun space(parent:LinearLayout,dp:Int){parent.addView(View(this),LinearLayout.LayoutParams(1,(dp*resources.displayMetrics.density).toInt()))}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    override fun onDestroy(){database.close();super.onDestroy()}
}
