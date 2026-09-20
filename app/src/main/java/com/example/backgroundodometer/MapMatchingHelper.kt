package com.example.backgroundodometer

import android.content.Context
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class MapMatchingResult(
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val confidence: Double
)

object MapMatchingHelper {

    private const val OSRM_URL =
        "https://router.project-osrm.org"

    private const val MAX_POINTS_PER_REQUEST =
        100

    private const val CONNECT_TIMEOUT =
        15000

    private const val READ_TIMEOUT =
        30000

    private const val MAX_GAP_SECONDS =
        20L

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

            val pointsArray =
                json.optJSONArray(
                    "points"
                )
                    ?: return null

            val points =
                ArrayList<GeoPoint>()

            for (
                i in 0 until pointsArray.length()
            ) {

                val item =
                    pointsArray.getJSONObject(i)

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

        } catch (
            _: Exception
        ) {

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

            val pointsArray =
                org.json.JSONArray()

            for (
                point in result.points
            ) {

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

                pointsArray.put(
                    item
                )
            }

            json.put(
                "points",
                pointsArray
            )

            cacheFile(
                context,
                tripId
            ).writeText(
                json.toString()
            )

        } catch (
            _: Exception
        ) {
            // Cache failure must not break
            // tracking or route display.
        }
    }

    /*
     * V6-compatible method.
     *
     * Used by RouteMapActivity.
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
            inputs
        )
    }

    /*
     * V7 authoritative matching.
     *
     * Only points whose recorded speed is at or
     * above the odometer threshold are used.
     *
     * This preserves the V6 rule that the odometer
     * should not accumulate below the threshold.
     */
    fun matchTrip(
        points: List<TrackPoint>,
        speedThreshold: Double
    ): MapMatchingResult? {

        if (points.size < 2) {
            return null
        }

        val segments =
            mutableListOf<
                MutableList<MatchInput>
            >()

        var currentSegment =
            mutableListOf<MatchInput>()

        var previousTime =
            -1L

        for (point in points) {

            if (
                point.speedKmh <
                speedThreshold
            ) {

                if (
                    currentSegment.size >= 2
                ) {

                    segments.add(
                        currentSegment
                    )
                }

                currentSegment =
                    mutableListOf()

                previousTime =
                    -1L

                continue
            }

            val timeSeconds =
                point.time / 1000L

            if (
                previousTime >= 0 &&
                timeSeconds -
                    previousTime >
                MAX_GAP_SECONDS
            ) {

                if (
                    currentSegment.size >= 2
                ) {

                    segments.add(
                        currentSegment
                    )
                }

                currentSegment =
                    mutableListOf()
            }

            currentSegment.add(
                MatchInput(
                    latitude =
                        point.latitude,

                    longitude =
                        point.longitude,

                    timeSeconds =
                        timeSeconds,

                    accuracy =
                        point.accuracy
                )
            )

            previousTime =
                timeSeconds
        }

        if (
            currentSegment.size >= 2
        ) {

            segments.add(
                currentSegment
            )
        }

        if (segments.isEmpty()) {
            return null
        }

        val allPoints =
            ArrayList<GeoPoint>()

        var totalDistance =
            0.0

        var confidenceTotal =
            0.0

        var confidenceCount =
            0

        for (
            segment in segments
        ) {

            if (segment.size < 2) {
                continue
            }

            val result =
                matchInputs(
                    segment
                )
                    ?: continue

            if (
                allPoints.isEmpty()
            ) {

                allPoints.addAll(
                    result.points
                )

            } else {

                allPoints.addAll(
                    result.points.drop(1)
                )
            }

            totalDistance +=
                result.distanceMeters

            if (
                result.confidence > 0.0
            ) {

                confidenceTotal +=
                    result.confidence

                confidenceCount++
            }
        }

        if (
            allPoints.size < 2
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

        return MapMatchingResult(
            points =
                allPoints,

            distanceMeters =
                totalDistance,

            confidence =
                confidence
        )
    }

    private data class MatchInput(
        val latitude: Double,
        val longitude: Double,
        val timeSeconds: Long,
        val accuracy: Double
    )

    private fun matchInputs(
        inputs: List<MatchInput>
    ): MapMatchingResult? {

        if (inputs.size < 2) {
            return null
        }

        val allPoints =
            ArrayList<GeoPoint>()

        var totalDistance =
            0.0

        var confidenceTotal =
            0.0

        var confidenceCount =
            0

        /*
         * Overlap chunks by one point.
         *
         * 0..99
         * 99..198
         * etc.
         *
         * This prevents a gap between chunks.
         */
        var start =
            0

        while (
            start <
            inputs.size - 1
        ) {

            val end =
                min(
                    start +
                        MAX_POINTS_PER_REQUEST,
                    inputs.size
                )

            val chunk =
                inputs.subList(
                    start,
                    end
                )

            if (
                chunk.size >= 2
            ) {

                val result =
                    matchChunk(
                        chunk
                    )

                if (
                    result != null
                ) {

                    if (
                        allPoints.isEmpty()
                    ) {

                        allPoints.addAll(
                            result.points
                        )

                    } else {

                        allPoints.addAll(
                            result.points.drop(1)
                        )
                    }

                    totalDistance +=
                        result.distanceMeters

                    if (
                        result.confidence > 0.0
                    ) {

                        confidenceTotal +=
                            result.confidence

                        confidenceCount++
                    }
                }
            }

            /*
             * Overlap the next request by one
             * input point.
             */
            start =
                end - 1
        }

        if (
            allPoints.size < 2
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

        return MapMatchingResult(
            points =
                allPoints,

            distanceMeters =
                totalDistance,

            confidence =
                confidence
        )
    }

    private fun matchChunk(
        inputs: List<MatchInput>
    ): MapMatchingResult? {

        return try {

            val coordinates =
                inputs.joinToString(";") {

                    String.format(
                        Locale.US,
                        "%.7f,%.7f",
                        it.longitude,
                        it.latitude
                    )
                }

            val timestamps =
                inputs.joinToString(";") {
                    it.timeSeconds
                        .toString()
                }

            val radiuses =
                inputs.joinToString(";") {

                    val accuracy =
                        if (
                            it.accuracy > 0.0
                        ) {

                            it.accuracy

                        } else {

                            10.0
                        }

                    max(
                        MIN_ACCURACY,
                        min(
                            MAX_ACCURACY,
                            accuracy
                        )
                    ).toString()
                }

            val urlString =
                "$OSRM_URL/match/v1/driving/" +
                    coordinates +
                    "?overview=full" +
                    "&geometries=geojson" +
                    "&steps=false" +
                    "&gaps=split" +
                    "&tidy=true" +
                    "&timestamps=$timestamps" +
                    "&radiuses=$radiuses"

            val connection =
                URL(
                    urlString
                ).openConnection()
                    as HttpURLConnection

            connection.requestMethod =
                "GET"

            connection.connectTimeout =
                CONNECT_TIMEOUT

            connection.readTimeout =
                READ_TIMEOUT

            connection.setRequestProperty(
                "User-Agent",
                "BackgroundOdometer/7.0"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

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

        } catch (
            _: Exception
        ) {

            null
        }
    }

    private fun parseResponse(
        response: String
    ): MapMatchingResult? {

        return try {

            val json =
                JSONObject(
                    response
                )

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
                )
                    ?: return null

            val matchedPoints =
                ArrayList<GeoPoint>()

            var totalDistance =
                0.0

            var confidenceTotal =
                0.0

            var confidenceCount =
                0

            for (
                i in 0 until
                    matchings.length()
            ) {

                val matching =
                    matchings.getJSONObject(
                        i
                    )

                totalDistance +=
                    matching.optDouble(
                        "distance",
                        0.0
                    )

                val confidence =
                    matching.optDouble(
                        "confidence",
                        0.0
                    )

                if (
                    confidence > 0.0
                ) {

                    confidenceTotal +=
                        confidence

                    confidenceCount++
                }

                val geometry =
                    matching.optJSONObject(
                        "geometry"
                    )
                        ?: continue

                val coordinates =
                    geometry.optJSONArray(
                        "coordinates"
                    )
                        ?: continue

                for (
                    j in 0 until
                        coordinates.length()
                ) {

                    val coordinate =
                        coordinates.getJSONArray(
                            j
                        )

                    if (
                        coordinate.length() < 2
                    ) {
                        continue
                    }

                    val longitude =
                        coordinate.getDouble(
                            0
                        )

                    val latitude =
                        coordinate.getDouble(
                            1
                        )

                    matchedPoints.add(
                        GeoPoint(
                            latitude,
                            longitude
                        )
                    )
                }
            }

            if (
                matchedPoints.size < 2
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
                    matchedPoints,

                distanceMeters =
                    totalDistance,

                confidence =
                    confidence
            )

        } catch (
            _: Exception
        ) {

            null
        }
    }
}
