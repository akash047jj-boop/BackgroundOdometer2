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
            "LOADING..."

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

        map.setTileSource(
            TileSourceFactory.MAPNIK
        )

        map.setMultiTouchControls(
            true
        )

        map.setBuiltInZoomControls(
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

        setContentView(
            root
        )
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

        if (
            trip != null
        ) {

            distanceText.text =
                String.format(
                    Locale.US,
                    "ROAD DISTANCE: %.2f km",
                    if (
                        trip.roadDistanceKm > 0.0
                    ) {

                        trip.roadDistanceKm

                    } else {

                        trip.distanceKm
                    }
                )

            gpsDistanceText.text =
                String.format(
                    Locale.US,
                    "GPS DISTANCE: %.2f km",
                    trip.gpsDistanceKm
                )

            if (
                trip.distanceSource ==
                "ROAD"
            ) {

                statusText.text =
                    String.format(
                        Locale.US,
                        "ROAD MATCHED • %.0f%% CONFIDENCE",
                        trip.matchingConfidence *
                            100.0
                    )

            } else {

                statusText.text =
                    "GPS DISTANCE USED"
            }
        }

        val rawPoints =
            database.getTrackPoints(
                tripId
            )

        if (
            rawPoints.isEmpty()
        ) {

            statusText.text =
                "NO GPS ROUTE RECORDED"

            return
        }

        val rawGeoPoints =
            ArrayList<GeoPoint>()

        for (
            point in rawPoints
        ) {

            rawGeoPoints.add(
                GeoPoint(
                    point.latitude,
                    point.longitude
                )
            )
        }

        if (
            rawGeoPoints.size < 2
        ) {

            drawRoute(
                rawGeoPoints
            )

            return
        }

        /*
         * First use the cached V7 matched route.
         */
        val cached =
            MapMatchingHelper
                .getCachedResult(
                    applicationContext,
                    tripId
                )

        if (
            cached != null
        ) {

            drawRoute(
                cached.points
            )

            return
        }

        /*
         * If there is no cached route, match
         * the complete GPS trace for display.
         *
         * This does not alter the already stored
         * authoritative trip distance.
         */
        statusText.text =
            "MATCHING ROUTE..."

        drawRoute(
            rawGeoPoints
        )

        executor.execute {

            val result =
                MapMatchingHelper.match(
                    rawGeoPoints
                )

            mainHandler.post {

                if (
                    isFinishing ||
                    isDestroyed
                ) {

                    return@post
                }

                if (
                    result != null
                ) {

                    MapMatchingHelper
                        .saveCachedResult(
                            applicationContext,
                            tripId,
                            result
                        )

                    drawRoute(
                        result.points
                    )

                    if (
                        trip?.distanceSource ==
                        "ROAD"
                    ) {

                        statusText.text =
                            String.format(
                                Locale.US,
                                "ROAD MATCHED • %.0f%% CONFIDENCE",
                                trip.matchingConfidence *
                                    100.0
                            )

                    } else {

                        statusText.text =
                            "GPS DISTANCE USED"
                    }

                } else {

                    statusText.text =
                        "GPS ROUTE SHOWN"
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
        points: List<GeoPoint>
    ) {

        if (
            points.isEmpty()
        ) {

            return
        }

        clearRouteOverlays()

        val route =
            Polyline()

        route.setPoints(
            points
        )

        route.outlinePaint.strokeWidth =
            8f

        route.outlinePaint.isAntiAlias =
            true

        map.overlays.add(
            route
        )

        map.invalidate()

        showRouteOnScreen(
            points
        )
    }

    private fun showRouteOnScreen(
        points: List<GeoPoint>
    ) {

        if (
            points.isEmpty()
        ) {

            return
        }

        var minLat =
            points[0].latitude

        var maxLat =
            points[0].latitude

        var minLon =
            points[0].longitude

        var maxLon =
            points[0].longitude

        for (
            point in points
        ) {

            if (
                point.latitude < minLat
            ) {

                minLat =
                    point.latitude
            }

            if (
                point.latitude > maxLat
            ) {

                maxLat =
                    point.latitude
            }

            if (
                point.longitude < minLon
            ) {

                minLon =
                    point.longitude
            }

            if (
                point.longitude > maxLon
            ) {

                maxLon =
                    point.longitude
            }
        }

        val centerLat =
            (
                minLat +
                    maxLat
                ) / 2.0

        val centerLon =
            (
                minLon +
                    maxLon
                ) / 2.0

        map.controller.setCenter(
            GeoPoint(
                centerLat,
                centerLon
            )
        )

        val latSpan =
            maxLat -
                minLat

        val lonSpan =
            maxLon -
                minLon

        val largestSpan =
            maxOf(
                latSpan,
                lonSpan
            )

        val zoom =
            when {

                largestSpan > 1.0 ->
                    8.0

                largestSpan > 0.5 ->
                    9.0

                largestSpan > 0.2 ->
                    10.0

                largestSpan > 0.1 ->
                    11.0

                largestSpan > 0.05 ->
                    12.0

                largestSpan > 0.02 ->
                    13.0

                largestSpan > 0.01 ->
                    14.0

                largestSpan > 0.005 ->
                    15.0

                largestSpan > 0.002 ->
                    16.0

                else ->
                    17.0
            }

        map.controller.setZoom(
            zoom
        )
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

            map.onDetach()
        }

        super.onDestroy()
    }
}
