package com.example.backgroundodometer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class MapMatchingResult(
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val confidence: Double
)

object MapMatchingHelper {

    private const val OSRM_URL =
        "https://router.project-osrm.org"

    /*
     * OSRM matching normally allows up to 100
     * locations per request.
     */
    private const val MAX_MATCH_POINTS =
        100

    private const val CONNECT_TIMEOUT =
        15000

    private const val READ_TIMEOUT =
        45000

    private const val MIN_ACCURACY =
        3.0

    private const val MAX_ACCURACY =
        50.0

    private fun cacheFile(
        context: Context,
        tripId: Long
    ): File {

        return File(
            context.filesDir,
            "matched_trip_$tripId.json"
        )
    }

    fun getCachedResult(
        context: Context,
        tripId: Long
    ): MapMatchingResult? {

        return try {

            val file =
                cacheFile(
                    context,
                    tripId
                )

            if (!file.exists()) {
                return null
            }

            val json =
                JSONObject(
                    file.readText()
                )

            val distance =
                json.optDouble(
                    "distanceMeters",
                    0.0
                )

            val confidence =
                json.optDouble(
                    "confidence",
                    0.0
                )

            val array =
                json.optJSONArray(
                    "points"
                ) ?: return null

            val points =
                ArrayList<GeoPoint>()

            for (
                i in 0 until array.length()
            ) {

                val item =
                    array.getJSONObject(i)

                points.add(
                    GeoPoint(
                        item.getDouble(
                            "latitude"
                        ),
                        item.getDouble(
                            "longitude"
                        )
                    )
                )
            }

            if (points.size < 2) {
                return null
            }

            MapMatchingResult(
                points,
                distance,
                confidence
            )

        } catch (_: Exception) {

            null
        }
    }

    fun saveCachedResult(
        context: Context,
        tripId: Long,
        result: MapMatchingResult
    ) {

        try {

            val json =
                JSONObject()

            json.put(
                "distanceMeters",
                result.distanceMeters
            )

            json.put(
                "confidence",
                result.confidence
            )

            val array =
                JSONArray()

            for (point in result.points) {

                val item =
                    JSONObject()

                item.put(
                    "latitude",
                    point.latitude
                )

                item.put(
                    "longitude",
                    point.longitude
                )

                array.put(item)
            }

            json.put(
                "points",
                array
            )

            cacheFile(
                context,
                tripId
            ).writeText(
                json.toString()
            )

        } catch (_: Exception) {
        }
    }

    /*
     * Used when RouteMapActivity only has
     * raw GeoPoints.
     */
    fun match(
        points: List<GeoPoint>
    ): MapMatchingResult? {

        if (points.size < 2) {
            return null
        }

        val inputs =
            points.mapIndexed {
                    index,
                    point ->

                MatchInput(
                    latitude =
                        point.latitude,

                    longitude =
                        point.longitude,

                    timeSeconds =
                        index.toLong() * 2L,

                    accuracy =
                        10.0
                )
            }

        return matchInputs(
            resample(inputs)
        )
    }

    /*
     * Main V8 matching method.
     *
     * We use movement-related points so that
     * long stationary periods do not dominate
     * the road matching.
     */
    fun matchTrip(
        points: List<TrackPoint>,
        speedThreshold: Double
    ): MapMatchingResult? {

        if (points.size < 2) {
            return null
        }

        val selected =
            selectMovementPoints(
                points,
                speedThreshold
            )

        if (selected.size < 2) {
            return null
        }

        val inputs =
            selected.map { point ->

                MatchInput(
                    latitude =
                        point.latitude,

                    longitude =
                        point.longitude,

                    timeSeconds =
                        point.time / 1000L,

                    accuracy =
                        point.accuracy
                )
            }

        /*
         * V8 uses ONE request containing <=100
         * carefully sampled points.
         *
         * This avoids the V7 problem of firing
         * many large GET requests.
         */
        return matchInputs(
            resample(inputs)
        )
    }

    private data class MatchInput(
        val latitude: Double,
        val longitude: Double,
        val timeSeconds: Long,
        val accuracy: Double
    )

    private fun selectMovementPoints(
        points: List<TrackPoint>,
        threshold: Double
    ): List<TrackPoint> {

        val result =
            ArrayList<TrackPoint>()

        for (
            i in points.indices
        ) {

            val point =
                points[i]

            if (
                i == 0 ||
                i == points.lastIndex
            ) {

                result.add(point)

                continue
            }

            val previous =
                points[i - 1]

            val next =
                points[i + 1]

            val previousSegmentSpeed =
                impliedSpeed(
                    previous,
                    point
                )

            val nextSegmentSpeed =
                impliedSpeed(
                    point,
                    next
                )

            val moving =
                point.speedKmh >= threshold ||
                previous.speedKmh >= threshold ||
                next.speedKmh >= threshold ||
                previousSegmentSpeed >= threshold ||
                nextSegmentSpeed >= threshold

            if (moving) {
                result.add(point)
            }
        }

        return result
    }

    private fun impliedSpeed(
        a: TrackPoint,
        b: TrackPoint
    ): Double {

        val seconds =
            (
                b.time - a.time
            ) / 1000.0

        if (seconds <= 0.0) {
            return 0.0
        }

        val distance =
            haversine(
                a.latitude,
                a.longitude,
                b.latitude,
                b.longitude
            )

        return (
            distance /
                seconds
        ) * 3.6
    }

    /*
     * Reduce any long trip to at most 100
     * representative points.
     *
     * First and last points are always retained.
     */
    private fun resample(
        inputs: List<MatchInput>
    ): List<MatchInput> {

        if (
            inputs.size <=
            MAX_MATCH_POINTS
        ) {

            return inputs
        }

        val result =
            ArrayList<MatchInput>()

        val maxIndex =
            inputs.lastIndex

        for (
            i in 0 until MAX_MATCH_POINTS
        ) {

            val ratio =
                i.toDouble() /
                    (MAX_MATCH_POINTS - 1)

            val index =
                (
                    ratio *
                        maxIndex
                ).roundToInt()

            val safeIndex =
                index.coerceIn(
                    0,
                    maxIndex
                )

            result.add(
                inputs[safeIndex]
            )
        }

        return result
    }

    /*
     * V8 uses POST instead of putting the entire
     * trace into the URL.
     */
    private fun matchInputs(
        inputs: List<MatchInput>
    ): MapMatchingResult? {

        if (inputs.size < 2) {
            return null
        }

        return try {

            val body =
                JSONObject()

            val coordinates =
                JSONArray()

            val timestamps =
                JSONArray()

            val radiuses =
                JSONArray()

            for (input in inputs) {

                val coordinate =
                    JSONArray()

                /*
                 * OSRM requires longitude first.
                 */
                coordinate.put(
                    input.longitude
                )

                coordinate.put(
                    input.latitude
                )

                coordinates.put(
                    coordinate
                )

                timestamps.put(
                    input.timeSeconds
                )

                val radius =
                    max(
                        MIN_ACCURACY,
                        min(
                            MAX_ACCURACY,
                            if (
                                input.accuracy > 0.0
                            ) {
                                input.accuracy
                            } else {
                                10.0
                            }
                        )
                    )

                radiuses.put(radius)
            }

            body.put(
                "coordinates",
                coordinates
            )

            body.put(
                "timestamps",
                timestamps
            )

            body.put(
                "radiuses",
                radiuses
            )

            body.put(
                "overview",
                "full"
            )

            body.put(
                "geometries",
                "geojson"
            )

            body.put(
                "steps",
                false
            )

            body.put(
                "gaps",
                "split"
            )

            body.put(
                "tidy",
                true
            )

            val connection =
                URL(
                    "$OSRM_URL/match/v1/driving"
                )
                    .openConnection()
                    as HttpURLConnection

            connection.requestMethod =
                "POST"

            connection.doOutput =
                true

            connection.connectTimeout =
                CONNECT_TIMEOUT

            connection.readTimeout =
                READ_TIMEOUT

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "User-Agent",
                "BackgroundOdometer/8.0"
            )

            connection.outputStream
                .bufferedWriter()
                .use {
                    it.write(
                        body.toString()
                    )
                }

            val responseCode =
                connection.responseCode

            if (
                responseCode != 200
            ) {

                connection.disconnect()

                return null
            }

            val response =
                connection.inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            connection.disconnect()

            parseResponse(
                response
            )

        } catch (_: Exception) {

            null
        }
    }

    private fun parseResponse(
        response: String
    ): MapMatchingResult? {

        return try {

            val json =
                JSONObject(response)

            if (
                json.optString(
                    "code"
                ) != "Ok"
            ) {

                return null
            }

            val matchings =
                json.optJSONArray(
                    "matchings"
                ) ?: return null

            val routePoints =
                ArrayList<GeoPoint>()

            var distance =
                0.0

            var confidenceTotal =
                0.0

            var confidenceCount =
                0

            for (
                i in 0 until matchings.length()
            ) {

                val matching =
                    matchings.getJSONObject(i)

                distance +=
                    matching.optDouble(
                        "distance",
                        0.0
                    )

                val confidence =
                    matching.optDouble(
                        "confidence",
                        0.0
                    )

                if (confidence > 0.0) {

                    confidenceTotal +=
                        confidence

                    confidenceCount++
                }

                val geometry =
                    matching.optJSONObject(
                        "geometry"
                    ) ?: continue

                val coordinates =
                    geometry.optJSONArray(
                        "coordinates"
                    ) ?: continue

                for (
                    j in 0 until coordinates.length()
                ) {

                    val coordinate =
                        coordinates.getJSONArray(j)

                    if (
                        coordinate.length() < 2
                    ) {
                        continue
                    }

                    val longitude =
                        coordinate.getDouble(0)

                    val latitude =
                        coordinate.getDouble(1)

                    routePoints.add(
                        GeoPoint(
                            latitude,
                            longitude
                        )
                    )
                }
            }

            if (
                routePoints.size < 2 ||
                distance <= 0.0
            ) {

                return null
            }

            val confidence =
                if (
                    confidenceCount > 0
                ) {

                    confidenceTotal /
                        confidenceCount

                } else {

                    0.0
                }

            MapMatchingResult(
                points =
                    routePoints,

                distanceMeters =
                    distance,

                confidence =
                    confidence
            )

        } catch (_: Exception) {

            null
        }
    }

    private fun haversine(
        lat1Value: Double,
        lon1Value: Double,
        lat2Value: Double,
        lon2Value: Double
    ): Double {

        val earth =
            6_371_000.0

        val lat1 =
            Math.toRadians(
                lat1Value
            )

        val lat2 =
            Math.toRadians(
                lat2Value
            )

        val dLat =
            Math.toRadians(
                lat2Value -
                    lat1Value
            )

        val dLon =
            Math.toRadians(
                lon2Value -
                    lon1Value
            )

        val a =
            sin(
                dLat / 2
            ) * sin(
                dLat / 2
            ) +
            cos(lat1) *
            cos(lat2) *
            sin(
                dLon / 2
            ) * sin(
                dLon / 2
            )

        val c =
            2 *
                atan2(
                    sqrt(a),
                    sqrt(1 - a)
                )

        return earth * c
    }

    private fun sin(value: Double) =
        kotlin.math.sin(value)

    private fun cos(value: Double) =
        kotlin.math.cos(value)

    private fun sqrt(value: Double) =
        kotlin.math.sqrt(value)

    private fun atan2(
        y: Double,
        x: Double
    ) =
        kotlin.math.atan2(y, x)
}
