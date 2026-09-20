package com.example.backgroundodometer

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class RouteMapActivity : Activity() {

    private lateinit var database:
        OdometerDatabaseHelper

    private lateinit var map:
        MapView

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        Configuration.getInstance()
            .userAgentValue =
            packageName

        database =
            OdometerDatabaseHelper(this)

        val tripId =
            intent.getLongExtra(
                "trip_id",
                -1
            )

        if (tripId <= 0) {

            finish()

            return
        }

        buildInterface(
            tripId
        )
    }

    private fun buildInterface(
        tripId: Long
    ) {

        val root =
            LinearLayout(this)

        root.orientation =
            LinearLayout.VERTICAL

        root.setBackgroundColor(
            Color.BLACK
        )

        val header =
            LinearLayout(this)

        header.orientation =
            LinearLayout.HORIZONTAL

        header.setPadding(
            15,
            15,
            15,
            15
        )

        header.setBackgroundColor(
            Color.rgb(
                15,
                15,
                15
            )
        )

        val back =
            TextView(this)

        back.text =
            "‹ BACK"

        back.textSize =
            17f

        back.setTextColor(
            Color.WHITE
        )

        back.gravity =
            Gravity.CENTER_VERTICAL

        back.setOnClickListener {
            finish()
        }

        header.addView(
            back,
            LinearLayout.LayoutParams(
                90,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
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

        title.gravity =
            Gravity.CENTER

        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            )
        )

        val spacer =
            View(this)

        header.addView(
            spacer,
            LinearLayout.LayoutParams(
                90,
                1
            )
        )

        root.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                65
            )
        )

        map =
            MapView(this)

        map.setMultiTouchControls(
            true
        )

        map.setBuiltInZoomControls(
            false
        )

        map.setTileSource(
            org.osmdroid.tileprovider.tilesource
                .TileSourceFactory.MAPNIK
        )

        root.addView(
            map,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val trip =
            database.getTrip(
                tripId
            )

        val points =
            database.getTrackPoints(
                tripId
            )

        if (
            trip != null &&
            points.isNotEmpty()
        ) {

            drawRoute(
                trip,
                points
            )

        } else {

            showNoRoute()
        }

        setContentView(root)
    }

    private fun drawRoute(
        trip: TripSummary,
        points: List<TrackPoint>
    ) {

        val geoPoints =
            mutableListOf<GeoPoint>()

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

        val route =
            Polyline(mapView)

        route.setPoints(
            geoPoints
        )

        route.width =
            8f

        route.color =
            Color.rgb(
                0,
                188,
                212
            )

        map.overlays.add(
            route
        )

        // START MARKER

        val start =
            Marker(map)

        start.position =
            geoPoints.first()

        start.title =
            "Trip start"

        start.snippet =
            "Start of recorded route"

        start.setAnchor(
            Marker.ANCHOR_CENTER,
            Marker.ANCHOR_BOTTOM
        )

        map.overlays.add(
            start
        )

        // END MARKER

        if (geoPoints.size > 1) {

            val end =
                Marker(map)

            end.position =
                geoPoints.last()

            end.title =
                "Trip end"

            end.snippet =
                "%.2f km".format(
                    trip.distanceKm
                )

            end.setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_BOTTOM
            )

            map.overlays.add(
                end
            )
        }

        val first =
            geoPoints.first()

        map.controller.setCenter(
            first
        )

        map.controller.setZoom(
            15.0
        )

        map.invalidate()
    }

    private fun showNoRoute() {

        val message =
            TextView(this)

        message.text =
            "No GPS route points available."

        message.textSize =
            18f

        message.setTextColor(
            Color.WHITE
        )

        message.gravity =
            Gravity.CENTER

        message.setBackgroundColor(
            Color.BLACK
        )

        setContentView(
            message
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

        database.close()

        super.onDestroy()
    }
}
