package com.example.backgroundodometer

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import java.util.concurrent.Executors

class RouteMapActivity : AppCompatActivity() {

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
            packageName

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

        buildScreen()

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
            20f

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

        map =
            MapView(this)

        map.setBackgroundColor(
            Color.DKGRAY
        )

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

        root.addView(
            map,
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

        setContentView(root)
    }

    private fun loadRoute() {

        database =
            OdometerDatabaseHelper(
                applicationContext
            )

        val trip =
            database.getTrip(
                tripId
            )

        if (trip != null) {

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

            statusText.text =
                if (
                    trip.distanceSource ==
                    "ROAD"
                ) {

                    String.format(
                        Locale.US,
                        "ROAD MATCHED • %.0f%% CONFIDENCE",
                        trip.matchingConfidence *
                            100.0
                    )

                } else {

                    "GPS DISTANCE USED"
                }
        }

        val rawPoints =
            database.getTrackPoints(
                tripId
            )

        if (rawPoints.isEmpty()) {

            statusText.text =
                "NO GPS ROUTE RECORDED"

            return
        }

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
         * Draw the raw GPS route FIRST.
         *
         * This means route display does not depend
         * on OSRM or internet access.
         */
        drawRoute(
            rawGeoPoints,
            "GPS ROUTE"
        )

        /*
         * Use an already cached matched route
         * if one exists.
         */
        val cached =
            MapMatchingHelper
                .getCachedResult(
                    applicationContext,
                    tripId
                )

        if (cached != null) {

            drawRoute(
                cached.points,
                "ROAD MATCHED ROUTE"
            )

            return
        }

        statusText.text =
            "GPS ROUTE • MATCHING ROAD..."

        executor.execute {

            val threshold =
                database.getSpeedThreshold()

            val result =
                MapMatchingHelper.matchTrip(
                    rawPoints,
                    threshold
                )

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

                    MapMatchingHelper
                        .saveCachedResult(
                            applicationContext,
                            tripId,
                            result
                        )

                    drawRoute(
                        result.points,
                        "ROAD MATCHED ROUTE"
                    )

                } else {

                    statusText.text =
                        "GPS ROUTE SHOWN • ROAD MATCH UNAVAILABLE"
                }
            }
        }
    }

    private fun clearRouteOverlays() {

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

    private fun drawRoute(
        points: List<GeoPoint>,
        label: String
    ) {

        if (points.isEmpty()) {
            return
        }

        clearRouteOverlays()

        val route =
            Polyline()

        route.setPoints(
            points
        )

        /*
         * V8 FIX:
         *
         * Explicit bright route colour.
         * V7 used the Polyline default, which
         * can be difficult to see.
         */
        route.outlinePaint.color =
            Color.parseColor(
                "#00BCD4"
            )

        route.outlinePaint.strokeWidth =
            10f

        route.outlinePaint.isAntiAlias =
            true

        map.overlays.add(
            route
        )

        statusText.text =
            label

        map.invalidate()

        /*
         * osmdroid needs the MapView to have
         * completed layout before fitting a
         * BoundingBox.
         */
        map.post {

            try {

                val array =
                    ArrayList(points)

                if (array.size == 1) {

                    map.controller.setCenter(
                        array[0]
                    )

                    map.controller.setZoom(
                        17.0
                    )

                } else {

                    val boundingBox =
                        BoundingBox
                            .fromGeoPoints(
                                array
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
            map.onDetach()
        }

        if (::database.isInitialized) {
            database.close()
        }

        super.onDestroy()
    }
}
