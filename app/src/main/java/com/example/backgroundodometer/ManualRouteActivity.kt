package com.example.backgroundodometer

import android.app.Activity
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.hypot

class ManualRouteActivity : Activity() {
    private lateinit var map: MapView
    private lateinit var status: TextView
    private lateinit var distance: TextView
    private lateinit var dateText: TextView
    private lateinit var placeText: EditText
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val markedPoints = mutableListOf<GeoPoint>()
    private val routePoints = mutableListOf<GeoPoint>()
    private var tripId = -1L
    private var selectedDate = ""
    private var existingStart = 0L
    private var existingEnd = 0L
    private var calculatedDistanceKm = 0.0
    private var drawingMode = false
    private var drawing = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var currentStroke = mutableListOf<GeoPoint>()

    companion object {
        private const val GREEN = "#39D98A"
        private const val OSRM = "https://router.project-osrm.org/route/v1/driving/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tripId = intent.getLongExtra("trip_id", -1L)
        selectedDate = intent.getStringExtra("date")
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "BackgroundOdometer/27.0 (Android; com.example.backgroundodometer)"
        buildScreen()
        if (tripId > 0L) loadExistingTrip() else {
            placeText.setText(intent.getStringExtra("place") ?: "")
            centerDefault()
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.addView(TextView(this).apply {
            text = if (tripId > 0) "EDIT ROUTE" else "DRAW MANUAL ROUTE"
            textSize = 23f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(dp(16), dp(14), dp(16), dp(8))
        }, full())

        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(14), dp(6)) }
        dateText = TextView(this).apply {
            text = formatDate(selectedDate); textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { chooseDate() }
        }
        controls.addView(dateText, full())
        placeText = EditText(this).apply { hint = "Place (optional)"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); textSize = 15f }
        controls.addView(placeText, full())
        distance = TextView(this).apply {
            text = "ROAD DISTANCE: --"; textSize = 17f; setTextColor(Color.parseColor(GREEN))
            gravity = Gravity.CENTER; setPadding(0, dp(7), 0, dp(7))
        }
        controls.addView(distance, full())
        status = TextView(this).apply {
            text = "Tap POINTS to mark stops, or DRAW to trace the road."
            textSize = 13f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        controls.addView(status, full())

        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        modeRow.addView(button("POINTS") { drawingMode = false; status.text = "POINT MODE • tap the map to add route points" }, weight())
        modeRow.addView(button("DRAW") { drawingMode = true; status.text = "DRAW MODE • swipe along the road, then release" }, weight())
        modeRow.addView(button("UNDO") {
            if (drawingMode && currentStroke.isNotEmpty()) {
                currentStroke.clear(); drawing = false; redrawMarked()
            } else if (markedPoints.isNotEmpty()) {
                markedPoints.removeAt(markedPoints.lastIndex); redrawMarked()
            }
        }, weight())
        modeRow.addView(button("CLEAR") {
            markedPoints.clear(); routePoints.clear(); calculatedDistanceKm = 0.0
            redrawMarked(); distance.text = "ROAD DISTANCE: --"; status.text = "Route cleared."
        }, weight())
        controls.addView(modeRow, full())
        root.addView(controls, full())

        map = MapView(this)
        map.setTileSource(createCartoTileSource()); map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(true); map.setUseDataConnection(true)
        root.addView(map, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(TextView(this).apply {
            text = "© OpenStreetMap contributors © CARTO • Routes © OSRM"
            textSize = 10f; setTextColor(Color.GRAY); gravity = Gravity.CENTER
            setPadding(dp(5), dp(4), dp(5), dp(4))
        }, full())

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(10), dp(6), dp(10), dp(10)) }
        bottom.addView(button("CALCULATE ROAD") { calculateRoute() }, weight())
        bottom.addView(button("SAVE TRIP") { saveTrip() }, weight())
        bottom.addView(button("BACK") { finish() }, weight())
        root.addView(bottom, full())
        map.setOnTouchListener { _, event -> handleMapTouch(event) }
        setContentView(root)
    }

    private fun handleMapTouch(event: MotionEvent): Boolean {
        if (!drawingMode) {
            if (event.action == MotionEvent.ACTION_DOWN) { lastTouchX = event.x; lastTouchY = event.y }
            else if (event.action == MotionEvent.ACTION_UP) {
                val moved = hypot((event.x - lastTouchX).toDouble(), (event.y - lastTouchY).toDouble())
                if (moved < dp(24)) {
                    val p = map.projection.fromPixels(event.x.toInt(), event.y.toInt())
                    markedPoints.add(GeoPoint(p.latitude, p.longitude)); routePoints.clear(); redrawMarked()
                    status.text = "Point \${markedPoints.size} added • add more or CALCULATE ROAD"
                    return true
                }
            }
            return false
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { drawing = true; currentStroke = mutableListOf(); addStrokePoint(event.x, event.y); return true }
            MotionEvent.ACTION_MOVE -> { if (drawing) addStrokePoint(event.x, event.y); return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (drawing) {
                    addStrokePoint(event.x, event.y); drawing = false
                    if (currentStroke.size >= 2) {
                        markedPoints.clear(); markedPoints.addAll(downsample(currentStroke, 24)); routePoints.clear()
                        redrawMarked(); status.text = "Freehand route captured • CALCULATE ROAD to snap it to roads"
                    }
                    currentStroke.clear()
                }
                return true
            }
        }
        return true
    }

    private fun addStrokePoint(x: Float, y: Float) {
        val p = map.projection.fromPixels(x.toInt(), y.toInt())
        if (currentStroke.isEmpty()) { currentStroke.add(GeoPoint(p.latitude, p.longitude)); return }
        val last = currentStroke.last()
        val px = map.projection.toPixels(last, null)
        if (hypot((x - px.x).toDouble(), (y - px.y).toDouble()) >= dp(12)) currentStroke.add(GeoPoint(p.latitude, p.longitude))
    }

    private fun downsample(points: List<GeoPoint>, max: Int): List<GeoPoint> {
        if (points.size <= max) return points.toList()
        val out = mutableListOf<GeoPoint>()
        val step = (points.size - 1).toDouble() / (max - 1)
        for (i in 0 until max) out.add(points[(i * step).toInt()])
        return out
    }

    private fun calculateRoute() {
        if (markedPoints.size < 2) {
            Toast.makeText(this, "Mark at least a start and end point.", Toast.LENGTH_SHORT).show(); return
        }
        status.text = "ROUTING ALONG ROADS..."
        executor.execute {
            try {
                val coords = markedPoints.joinToString(";") {
                    String.format(Locale.US, "%.6f,%.6f", it.longitude, it.latitude)
                }
                val url = URL(OSRM + coords + "?overview=full&geometries=geojson&steps=false&alternatives=false")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; connectTimeout = 15000; readTimeout = 20000
                    setRequestProperty("User-Agent", "BackgroundOdometer/27.0")
                    setRequestProperty("Accept", "application/json")
                }
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("Routing service returned $code")
                val body = connection.inputStream.bufferedReader().use { it.readText() }; connection.disconnect()
                val json = JSONObject(body)
                if (json.optString("code") != "Ok") throw IllegalStateException(json.optString("message", "No road route found"))
                val route = json.getJSONArray("routes").getJSONObject(0)
                val meters = route.getDouble("distance")
                val geometry = route.getJSONObject("geometry").getJSONArray("coordinates")
                val calculated = mutableListOf<GeoPoint>()
                for (i in 0 until geometry.length()) {
                    val c = geometry.getJSONArray(i); calculated.add(GeoPoint(c.getDouble(1), c.getDouble(0)))
                }
                mainHandler.post {
                    calculatedDistanceKm = meters / 1000.0; routePoints.clear(); routePoints.addAll(calculated)
                    distance.text = String.format(Locale.US, "ROAD DISTANCE: %.2f km", calculatedDistanceKm)
                    redrawRoute(); status.text = "ROAD ROUTE READY • %.2f km".format(Locale.US, calculatedDistanceKm)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    status.text = "ROUTING FAILED"
                    Toast.makeText(this, "Could not calculate road route: \${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveTrip() {
        if (routePoints.size < 2 || calculatedDistanceKm <= 0.0) {
            Toast.makeText(this, "Calculate the road route first.", Toast.LENGTH_SHORT).show(); return
        }
        val date = selectedDate; val place = placeText.text.toString().trim()
        executor.execute {
            try {
                val db = OdometerDatabaseHelper(applicationContext)
                val start = existingStart.takeIf { it > 0 } ?: parseDate(date)
                val end = existingEnd.takeIf { it > 0 } ?: start
                if (tripId > 0) {
                    db.updateManualRouteTrip(tripId, date, place, calculatedDistanceKm, routePoints.map { it.latitude to it.longitude }, start, end)
                } else {
                    db.createManualRouteTrip(date, place, calculatedDistanceKm, routePoints.map { it.latitude to it.longitude }, start, end)
                }
                db.close()
                mainHandler.post { Toast.makeText(this, "Manual road trip saved.", Toast.LENGTH_SHORT).show(); setResult(RESULT_OK); finish() }
            } catch (e: Exception) {
                mainHandler.post { Toast.makeText(this, "Could not save trip: \${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun loadExistingTrip() {
        executor.execute {
            val db = OdometerDatabaseHelper(applicationContext)
            val trip = db.getTrip(tripId); val points = db.getTrackPoints(tripId); db.close()
            mainHandler.post {
                if (trip == null) { Toast.makeText(this, "Trip not found.", Toast.LENGTH_LONG).show(); finish(); return@post }
                selectedDate = trip.assignedDate.ifBlank { selectedDate }; dateText.text = formatDate(selectedDate)
                placeText.setText(trip.assignedPlace); existingStart = trip.startTime; existingEnd = trip.endTime
                calculatedDistanceKm = trip.distanceKm; routePoints.clear()
                routePoints.addAll(points.map { GeoPoint(it.latitude, it.longitude) }); markedPoints.clear()
                if (routePoints.size >= 2) {
                    markedPoints.add(routePoints.first()); markedPoints.add(routePoints.last())
                    distance.text = String.format(Locale.US, "ROAD DISTANCE: %.2f km", calculatedDistanceKm)
                    redrawRoute(); status.text = "SAVED ROUTE • edit by CLEAR + POINTS or DRAW"
                } else status.text = "No saved route geometry."
            }
        }
    }

    private fun redrawMarked() {
        clearMapOverlays()
        if (markedPoints.size >= 2) map.overlays.add(Polyline().apply {
            setPoints(markedPoints); outlinePaint.color = Color.YELLOW; outlinePaint.strokeWidth = 7f; outlinePaint.isAntiAlias = true
        })
        addMarkers(markedPoints); if (markedPoints.isNotEmpty()) fit(markedPoints); map.invalidate()
    }

    private fun redrawRoute() {
        clearMapOverlays()
        map.overlays.add(Polyline().apply {
            setPoints(routePoints); outlinePaint.color = Color.rgb(57, 217, 138); outlinePaint.strokeWidth = 10f; outlinePaint.isAntiAlias = true
        })
        addMarkers(listOf(routePoints.first(), routePoints.last())); fit(routePoints); map.invalidate()
    }

    private fun clearMapOverlays() { map.overlays.clear() }

    private fun addMarkers(points: List<GeoPoint>) {
        if (points.isEmpty()) return
        map.overlays.add(Marker(map).apply { position = points.first(); title = "START"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) })
        if (points.size > 1) map.overlays.add(Marker(map).apply { position = points.last(); title = "END"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) })
    }

    private fun fit(points: List<GeoPoint>) {
        if (points.isEmpty()) return
        map.post { try {
            if (points.size == 1) { map.controller.setCenter(points.first()); map.controller.setZoom(16.0) }
            else map.zoomToBoundingBox(BoundingBox.fromGeoPoints(ArrayList(points)), false, 70)
        } catch (_: Exception) {} }
    }

    private fun centerDefault() { map.controller.setCenter(GeoPoint(9.5, 76.5)); map.controller.setZoom(9.0) }

    private fun chooseDate() {
        val cal = Calendar.getInstance()
        try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedDate)?.let { cal.time = it } } catch (_: Exception) {}
        DatePickerDialog(this, { _, y, m, d ->
            selectedDate = "%04d-%02d-%02d".format(y, m + 1, d); dateText.text = formatDate(selectedDate)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun createCartoTileSource(): OnlineTileSourceBase = object : OnlineTileSourceBase(
        "CARTO Voyager V10", 0, 20, 256, ".png", arrayOf("https://basemaps.cartocdn.com/rastertiles/voyager/")
    ) {
        override fun getTileURLString(mapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(mapTileIndex); val x = MapTileIndex.getX(mapTileIndex); val y = MapTileIndex.getY(mapTileIndex)
            return getBaseUrl() + z + "/" + x + "/" + y + ".png?key=" + BuildConfig.CARTO_API_KEY
        }
    }

    private fun button(text: String, click: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 12f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER; setPadding(dp(5), dp(10), dp(5), dp(10)); minHeight = dp(48); setOnClickListener { click() }
    }
    private fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(2), 0, dp(2), 0) }
    private fun full() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun parseDate(date: String): Long = try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
    private fun formatDate(date: String): String = try { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!) } catch (_: Exception) { date }
    override fun onResume() { super.onResume(); if (::map.isInitialized) map.onResume() }
    override fun onPause() { if (::map.isInitialized) map.onPause(); super.onPause() }
    override fun onDestroy() { executor.shutdownNow(); if (::map.isInitialized) map.onDetach(); super.onDestroy() }
}
