package com.example.backgroundodometer

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

class RouteMapActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var database: OdometerDatabaseHelper

    private var tripId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance()
            .load(
                applicationContext,
                getSharedPreferences(
                    "osmdroid",
                    MODE_PRIVATE
                )
            )

        Configuration.getInstance().userAgentValue =
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
            android.widget.LinearLayout(this)

        root.orientation =
            android.widget.LinearLayout.VERTICAL

        root.setBackgroundColor(
            android.graphics.Color.BLACK
        )

        val title =
            TextView(this)

        title.text =
            "TRIP ROUTE"

        title.textSize = 20f

        title.setTextColor(
            android.graphics.Color.WHITE
        )

        title.setPadding(
            20,
            20,
            20,
            20
        )

        root.addView(
            title,
            android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        map = MapView(this)

        map.setTileSource(
            TileSourceFactory.MAPNIK
        )

        map.setMultiTouchControls(true)

        map.setBuiltInZoomControls(true)

        root.addView(
            map,
            android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val backButton =
            android.widget.Button(this)

        backButton.text =
            "BACK"

        backButton.setOnClickListener {
            finish()
        }

        root.addView(
            backButton,
            android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    private fun loadRoute() {

        database =
            OdometerDatabaseHelper(
                applicationContext
            )

        val points =
            database.getTrackPoints(
                tripId
            )

        if (points.isEmpty()) {

            Toast.makeText(
                this,
                "No GPS route recorded for this trip",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val geoPoints =
            ArrayList<GeoPoint>()

        for (point in points) {

            geoPoints.add(
                GeoPoint(
                    point.latitude,
                    point.longitude
                )
            )
        }

        if (geoPoints.isEmpty()) {
            return
        }

        /*
         * Draw the recorded GPS route.
         *
         * IMPORTANT:
         * Use Polyline() instead of Polyline(this).
         *
         * This avoids the constructor mismatch with
         * the osmdroid version used by this project.
         */
        val route =
            Polyline()

        route.setPoints(
            geoPoints
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
            geoPoints
        )
    }

    private fun showRouteOnScreen(
        points: List<GeoPoint>
    ) {

        if (points.isEmpty()) {
            return
        }

        /*
         * Calculate the route bounds manually.
         * This avoids depending on different
         * BoundingBox helper APIs between osmdroid
         * versions.
         */

        var minLat =
            points[0].latitude

        var maxLat =
            points[0].latitude

        var minLon =
            points[0].longitude

        var maxLon =
            points[0].longitude

        for (point in points) {

            if (point.latitude < minLat) {
                minLat =
                    point.latitude
            }

            if (point.latitude > maxLat) {
                maxLat =
                    point.latitude
            }

            if (point.longitude < minLon) {
                minLon =
                    point.longitude
            }

            if (point.longitude > maxLon) {
                maxLon =
                    point.longitude
            }
        }

        val centerLat =
            (minLat + maxLat) / 2.0

        val centerLon =
            (minLon + maxLon) / 2.0

        map.controller.setCenter(
            GeoPoint(
                centerLat,
                centerLon
            )
        )

        /*
         * Choose a reasonable zoom.
         */

        val latSpan =
            maxLat - minLat

        val lonSpan =
            maxLon - minLon

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

        if (::map.isInitialized) {
            map.onDetach()
        }

        super.onDestroy()
    }
}
