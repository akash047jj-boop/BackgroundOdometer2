package com.example.backgroundodometer

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import java.util.concurrent.Executors

class RouteMapActivity : Activity() {

    private lateinit var map: MapView

    private lateinit var database:
        OdometerDatabaseHelper

    private var tripId:
        Long = -1L

    private lateinit var statusText:
        TextView

    private lateinit var distanceText:
        TextView

    private lateinit var gpsDistanceText:
        TextView

    private val executor =
        Executors.newSingleThreadExecutor()

    private val mainHandler =
        Handler(Looper.getMainLooper())

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        /*
         * Initialize osmdroid BEFORE creating MapView.
         */
        try {

            Configuration.getInstance().load(
                applicationContext,
                getSharedPreferences(
                    "osmdroid",
                    MODE_PRIVATE
                )
            )

            Configuration.getInstance()
                .userAgentValue =
                packageName

        } catch (e: Exception) {

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

        if (tripId <= 0L) {

            Toast.makeText(
                this,
                "Invalid trip",
                Toast.LENGTH_LONG
            ).show()

            finish()

            return
        }

        /*
         * Build the screen only after configuration
         * has successfully initialized.
         */
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

        /*
         * Create MapView.
         */
        map =
            MapView(this)

        map.setTileSource(
            TileSourceFactory.MAPNIK
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

        map.setBackgroundColor(
            Color.DKGRAY
        )

        /*
         * Prevent the map from receiving a zero-height
         * layout.
         */
        val mapParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
            )

        mapParams.weight =
            1f

        root.addView(
            map,
            mapParams
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

        setContentView(root)
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

                    if (trip == null) {

                        statusText.text =
                            "TRIP NOT FOUND"

                        return@post
                    }

                    /*
                     * Display distance information.
                     */
                    val displayedDistance =
                        if (
                            trip.roadDistanceKm > 0.0
                        ) {

                            trip.roadDistanceKm

                        } else {

                            trip.distanceKm
                        }

                    distanceText.text =
                        String.format(
                            Locale.US,
                            "ROAD DISTANCE: %.2f km",
                            displayedDistance
                        )

                    gpsDistanceText.text =
                        String.format(
                            Locale.US,
                            "GPS DISTANCE: %.2f km",
                            trip.gpsDistanceKm
                        )

                    /*
                     * No GPS points.
                     */
                    if (rawPoints.isEmpty()) {

                        statusText.text =
                            "NO GPS ROUTE RECORDED"

                        return@post
                    }

                    /*
                     * Convert database points to GeoPoints.
                     */
                    val rawGeoPoints =
                        rawPoints.map {

                            GeoPoint(
                                it.latitude,
                                it.longitude
                            )
                        }

                    /*
                     * IMPORTANT:
                     *
                     * Always draw the raw GPS route first.
                     *
                     * This does NOT require OSRM.
                     */
                    drawGpsRoute(
                        rawGeoPoints
                    )

                    /*
                     * Try cached road route.
                     */
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

                        drawRoadRoute(
                            cached.points
                        )

                        return@post
                    }

                    statusText.text =
                        "GPS ROUTE SHOWN • MATCHING ROAD..."
                }

                /*
                 * Road matching happens away from UI thread.
                 */
                if (rawPoints.size >= 2) {

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

                            drawRoadRoute(
                                result.points
                            )

                        } else {

                            statusText.text =
                                "GPS ROUTE SHOWN • ROAD MATCH UNAVAILABLE"
                        }
                    }
                }

            } catch (e: Exception) {

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

        if (points.isEmpty()) {
            return
        }

        clearRouteLines()
        clearMarkers()

        /*
         * Draw GPS route.
         */
        if (points.size >= 2) {

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

        /*
         * Start marker.
         */
        val start =
            Marker(map)

        start.position =
            points.first()

        start.title =
            "START"

        map.overlays.add(
            start
        )

        /*
         * End marker.
         */
        if (points.size >= 2) {

            val end =
                Marker(map)

            end.position =
                points.last()

            end.title =
                "END"

            map.overlays.add(
                end
            )
        }

        statusText.text =
            "GPS ROUTE"

        fitMapToRoute(
            points
        )
    }

    private fun drawRoadRoute(
        points: List<GeoPoint>
    ) {

        if (points.size < 2) {
            return
        }

        /*
         * Remove only the existing route line.
         * Markers remain.
         */
        clearRouteLines()

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

        statusText.text =
            "ROAD MATCHED ROUTE"

        fitMapToRoute(
            points
        )

        map.invalidate()
    }

    private fun fitMapToRoute(
        points: List<GeoPoint>
    ) {

        if (points.isEmpty()) {
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
                        BoundingBox.fromGeoPoints(
                            ArrayList(points)
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

        if (::map.isInitialized) {

            map.onResume()
        }
    }

    override fun onPause() {

        if (::map.isInitialized) {

            map.onPause()
        }

        super.onPause()
    }

    override fun onDestroy() {

        executor.shutdownNow()

        if (::map.isInitialized) {

            try {

                map.onDetach()

            } catch (_: Exception) {
            }
        }

        if (::database.isInitialized) {

            try {

                database.close()

            } catch (_: Exception) {
            }
        }

        super.onDestroy()
    }
}
