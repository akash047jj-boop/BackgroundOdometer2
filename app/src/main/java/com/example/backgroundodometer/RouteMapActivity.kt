package com.example.backgroundodometer

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import java.util.concurrent.Executors

class RouteMapActivity : Activity() {

    private lateinit var map:
        MapView

    private lateinit var database:
        OdometerDatabaseHelper

    private var tripId =
        -1L

    private lateinit var statusText:
        TextView

    private lateinit var distanceText:
        TextView

    private lateinit var gpsDistanceText:
        TextView

    private val executor =
        Executors.newSingleThreadExecutor()

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        try {

            Configuration.getInstance()
                .load(
                    applicationContext,
                    getSharedPreferences(
                        "osmdroid",
                        MODE_PRIVATE
                    )
                )

            Configuration.getInstance()
                .userAgentValue =
                "BackgroundOdometer/10.0 " +
                    "(Android; com.example.backgroundodometer)"

        } catch (_: Exception) {

            Toast.makeText(
                this,
                "Map configuration error",
                Toast.LENGTH_LONG
            ).show()

            finish()

            return
        }

        tripId =
            intent.getLongExtra(
                "trip_id",
                -1L
            )

        if (
            tripId <= 0L
        ) {

            Toast.makeText(
                this,
                "Invalid trip",
                Toast.LENGTH_LONG
            ).show()

            finish()

            return
        }

        try {

            buildScreen()

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Could not open map: ${e.message}",
                Toast.LENGTH_LONG
            ).show()

            finish()

            return
        }

        loadRoute()
    }

    private fun createCartoTileSource():
        OnlineTileSourceBase {

        return object :
            OnlineTileSourceBase(
                "CARTO Voyager V10",
                0,
                20,
                256,
                ".png",
                arrayOf(
                    "https://basemaps.cartocdn.com/rastertiles/voyager/"
                ),
                "© OpenStreetMap contributors © CARTO"
            ) {

            override fun getTileURLString(
                mapTileIndex: Long
            ): String {

                val zoom =
                    MapTileIndex.getZoom(
                        mapTileIndex
                    )

                val x =
                    MapTileIndex.getX(
                        mapTileIndex
                    )

                val y =
                    MapTileIndex.getY(
                        mapTileIndex
                    )

                return getBaseUrl() +
                    zoom +
                    "/" +
                    x +
                    "/" +
                    y +
                    ".png?key=" +
                    BuildConfig.CARTO_API_KEY
            }
        }
    }

    private fun buildScreen() {

        val root =
            LinearLayout(this)

        root.orientation =
            LinearLayout.VERTICAL

        root.setBackgroundColor(
            Color.BLACK
        )

        val title =
            TextView(this)

        title.text =
            "TRIP ROUTE"

        title.textSize =
            21f

        title.setTextColor(
            Color.WHITE
        )

        title.setPadding(
            20,
            20,
            20,
            10
        )

        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        statusText =
            TextView(this)

        statusText.text =
            "LOADING ROUTE..."

        statusText.textSize =
            15f

        statusText.setTextColor(
            Color.LTGRAY
        )

        statusText.setPadding(
            20,
            5,
            20,
            5
        )

        root.addView(
            statusText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        distanceText =
            TextView(this)

        distanceText.text =
            "ROAD DISTANCE: --"

        distanceText.textSize =
            17f

        distanceText.setTextColor(
            Color.WHITE
        )

        distanceText.setPadding(
            20,
            5,
            20,
            5
        )

        root.addView(
            distanceText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        gpsDistanceText =
            TextView(this)

        gpsDistanceText.text =
            "GPS DISTANCE: --"

        gpsDistanceText.textSize =
            15f

        gpsDistanceText.setTextColor(
            Color.LTGRAY
        )

        gpsDistanceText.setPadding(
            20,
            5,
            20,
            10
        )

        root.addView(
            gpsDistanceText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val mapFrame =
            FrameLayout(this)

        mapFrame.setBackgroundColor(
            Color.DKGRAY
        )

        map =
            MapView(this)

        /*
         * V10:
         *
         * Use CARTO Voyager instead of
         * the OpenStreetMap MAPNIK tiles.
         */
        map.setTileSource(
            createCartoTileSource()
        )

        map.setMultiTouchControls(
            true
        )

        map.setBuiltInZoomControls(
            true
        )

        map.setUseDataConnection(
            true
        )

        try {

            map.tileProvider
                .clearTileCache()

        } catch (_: Exception) {
        }

        mapFrame.addView(
            map,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        /*
         * CARTO requires CARTO and
         * OpenStreetMap attribution.
         */
        val attribution =
            TextView(this)

        attribution.text =
            "© OpenStreetMap contributors © CARTO"

        attribution.textSize =
            11f

        attribution.setTextColor(
            Color.BLACK
        )

        attribution.setBackgroundColor(
            Color.WHITE
        )

        attribution.setPadding(
            6,
            3,
            6,
            3
        )

        val attributionParams =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

        attributionParams.gravity =
            Gravity.BOTTOM or Gravity.END

        attributionParams.setMargins(
            0,
            0,
            8,
            8
        )

        mapFrame.addView(
            attribution,
            attributionParams
        )

        root.addView(
            mapFrame,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val backButton =
            Button(this)

        backButton.text =
            "BACK"

        backButton.setOnClickListener {

            finish()
        }

        root.addView(
            backButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(
            root
        )
    }

    private fun loadRoute() {

        executor.execute {

            try {

                database =
                    OdometerDatabaseHelper(
                        applicationContext
                    )

                val trip =
                    database.getTrip(
                        tripId
                    )

                val rawPoints =
                    database.getTrackPoints(
                        tripId
                    )

                mainHandler.post {

                    if (
                        isFinishing ||
                        isDestroyed
                    ) {
                        return@post
                    }

                    if (
                        trip == null
                    ) {

                        statusText.text =
                            "TRIP NOT FOUND"

                        return@post
                    }

                    distanceText.text =
                        if (trip.roadDistanceKm > 0.0) {
                            String.format(
                                Locale.US,
                                "ROAD-MATCHED DISTANCE: %.2f km",
                                trip.roadDistanceKm
                            )
                        } else {
                            "ROAD-MATCHED DISTANCE: --"
                        }

                    gpsDistanceText.text =
                        String.format(
                            Locale.US,
                            "GPS DISTANCE: %.2f km",
                            trip.gpsDistanceKm
                        )

                    if (rawPoints.isEmpty()) {
                        statusText.text =
                            "NO GPS ROUTE RECORDED"
                        return@post
                    }

                    val cleanedPoints =
                        MapMatchingHelper.cleanRoutePoints(
                            rawPoints
                        )

                    val cleanedGeoPoints =
                        cleanedPoints.map {
                            GeoPoint(
                                it.latitude,
                                it.longitude
                            )
                        }

                    /*
                     * The map shows a cleaned GPS route. The speed threshold
                     * is NOT used to hide slow-moving route points; it only
                     * controls odometer distance accumulation.
                     */
                    drawGpsRoute(
                        cleanedGeoPoints
                    )

                    val cached =
                        MapMatchingHelper
                            .getCachedResult(
                                applicationContext,
                                tripId
                            )

                    if (
                        cached != null &&
                        cached.points.size >= 2
                    ) {
                        distanceText.text =
                            String.format(
                                Locale.US,
                                "ROAD-MATCHED DISTANCE: %.2f km",
                                cached.distanceMeters / 1000.0
                            )

                        drawRoadRoute(
                            cached.points
                        )

                        return@post
                    }

                    statusText.text =
                        "GPS ROUTE SHOWN • MATCHING ROAD..."
                }

                if (
                    rawPoints.size >= 2
                ) {

                    val threshold =
                        database.getSpeedThreshold()

                    val result =
                        try {

                            MapMatchingHelper.matchTrip(
                                rawPoints,
                                threshold
                            )

                        } catch (_: Exception) {

                            null
                        }

                    mainHandler.post {

                        if (
                            isFinishing ||
                            isDestroyed
                        ) {
                            return@post
                        }

                        if (
                            result != null &&
                            result.points.size >= 2
                        ) {

                            try {

                                MapMatchingHelper
                                    .saveCachedResult(
                                        applicationContext,
                                        tripId,
                                        result
                                    )

                            } catch (_: Exception) {
                            }

                            distanceText.text =
                                String.format(
                                    Locale.US,
                                    "ROAD-MATCHED DISTANCE: %.2f km",
                                    result.distanceMeters / 1000.0
                                )

                            drawRoadRoute(
                                result.points
                            )

                        } else {

                            distanceText.text =
                                "ROAD-MATCHED DISTANCE: --"

                            statusText.text =
                                "GPS ROUTE SHOWN • ROAD MATCH UNAVAILABLE"
                        }
                    }
                }

            } catch (_: Exception) {

                mainHandler.post {

                    if (
                        isFinishing ||
                        isDestroyed
                    ) {
                        return@post
                    }

                    statusText.text =
                        "ROUTE ERROR"

                    Toast.makeText(
                        this,
                        "Could not load route",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun clearRouteLines() {

        val removeList =
            ArrayList<Any>()

        for (
            overlay in map.overlays
        ) {

            if (
                overlay is Polyline
            ) {

                removeList.add(
                    overlay
                )
            }
        }

        for (
            overlay in removeList
        ) {

            map.overlays.remove(
                overlay
            )
        }
    }

    private fun clearMarkers() {

        val removeList =
            ArrayList<Any>()

        for (
            overlay in map.overlays
        ) {

            if (
                overlay is Marker
            ) {

                removeList.add(
                    overlay
                )
            }
        }

        for (
            overlay in removeList
        ) {

            map.overlays.remove(
                overlay
            )
        }
    }

    private fun drawGpsRoute(
        points: List<GeoPoint>
    ) {

        if (
            points.isEmpty()
        ) {
            return
        }

        clearRouteLines()

        clearMarkers()

        if (
            points.size >= 2
        ) {

            val route =
                Polyline()

            route.setPoints(
                points
            )

            route.outlinePaint.color =
                Color.CYAN

            route.outlinePaint.strokeWidth =
                8f

            route.outlinePaint.isAntiAlias =
                true

            map.overlays.add(
                route
            )
        }

        addStartEndMarkers(points)

        statusText.text =
            "GPS ROUTE"

        fitMapToRoute(
            points
        )

        map.invalidate()
    }

    private fun drawRoadRoute(
        points: List<GeoPoint>
    ) {

        if (
            points.size < 2
        ) {
            return
        }

        clearRouteLines()
        clearMarkers()

        val route =
            Polyline()

        route.setPoints(
            points
        )

        route.outlinePaint.color =
            Color.rgb(
                0,
                188,
                212
            )

        route.outlinePaint.strokeWidth =
            10f

        route.outlinePaint.isAntiAlias =
            true

        map.overlays.add(
            route
        )

        addStartEndMarkers(points)

        statusText.text =
            "ROAD MATCHED ROUTE"

        fitMapToRoute(
            points
        )

        map.invalidate()
    }

    private fun addStartEndMarkers(points: List<GeoPoint>) {
        if (points.isEmpty()) return

        val start = Marker(map).apply {
            position = points.first()
            title = "START"
            snippet = "Trip starting point"
            icon = createRouteLabelIcon("START", Color.rgb(0, 170, 90))
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        map.overlays.add(start)

        if (points.size >= 2) {
            val end = Marker(map).apply {
                position = points.last()
                title = "END"
                snippet = "Trip ending point"
                icon = createRouteLabelIcon("END", Color.rgb(210, 70, 70))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(end)
        }
    }

    private fun createRouteLabelIcon(label: String, color: Int): BitmapDrawable {
        val density = resources.displayMetrics.density
        val width = (96f * density).toInt()
        val height = (44f * density).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        canvas.drawRoundRect(
            2f * density,
            2f * density,
            width - 2f * density,
            height - 8f * density,
            10f * density,
            10f * density,
            paint
        )

        paint.color = Color.WHITE
        paint.textSize = 16f * density
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        val baseline = 25f * density
        canvas.drawText(label, width / 2f, baseline, paint)

        val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        pathPaint.color = color
        val cx = width / 2f
        val top = height - 10f * density
        val path = android.graphics.Path().apply {
            moveTo(cx - 7f * density, top)
            lineTo(cx + 7f * density, top)
            lineTo(cx, height.toFloat())
            close()
        }
        canvas.drawPath(path, pathPaint)

        return BitmapDrawable(resources, bitmap)
    }

    private fun fitMapToRoute(
        points: List<GeoPoint>
    ) {

        if (
            points.isEmpty()
        ) {
            return
        }

        map.post {

            try {

                if (
                    points.size == 1
                ) {

                    map.controller.setCenter(
                        points.first()
                    )

                    map.controller.setZoom(
                        17.0
                    )

                } else {

                    val boundingBox =
                        BoundingBox
                            .fromGeoPoints(
                                ArrayList(
                                    points
                                )
                            )

                    map.zoomToBoundingBox(
                        boundingBox,
                        false,
                        80
                    )
                }

                map.invalidate()

            } catch (_: Exception) {
            }
        }
    }

    override fun onResume() {

        super.onResume()

        if (
            ::map.isInitialized
        ) {

            map.onResume()
        }
    }

    override fun onPause() {

        if (
            ::map.isInitialized
        ) {

            map.onPause()
        }

        super.onPause()
    }

    override fun onDestroy() {

        executor.shutdownNow()

        if (
            ::map.isInitialized
        ) {

            try {

                map.onDetach()

            } catch (_: Exception) {
            }
        }

        if (
            ::database.isInitialized
        ) {

            try {

                database.close()

            } catch (_: Exception) {
            }
        }

        super.onDestroy()
    }
}
