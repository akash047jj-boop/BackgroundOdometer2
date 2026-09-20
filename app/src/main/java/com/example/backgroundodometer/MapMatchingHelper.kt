package com.example.backgroundodometer

import android.content.Context
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.*

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

            for (i in 0 until pointsArray.length()) {

                val item =
                    pointsArray.getJSONObject(i)

                val latitude =
                    item.getDouble(
                        "latitude"
                    )

                val longitude =
                    item.getDouble(
                        "longitude"
                    )

                points.add(
                    GeoPoint(
                        latitude,
                        longitude
                    )
                )
            }

            if (points.isEmpty()) {
                return null
            }

            MapMatchingResult(
                points,
                distance,
                confidence
            )

        } catch (
            e: Exception
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
            // Cache failure should never
            // break route display.
        }
    }

    fun match(
        points: List<GeoPoint>
    ): MapMatchingResult? {

        if (points.size < 2) {
            return null
        }

        val allMatchedPoints =
            ArrayList<GeoPoint>()

        var totalDistance =
            0.0

        var confidenceTotal =
            0.0

        var confidenceCount =
            0

        /*
         * OSRM map matching normally accepts
         * up to 100 locations.
         *
         * Split larger trips into chunks.
         */

        var start =
            0

        while (start < points.size - 1) {

            val end =
                min(
                    start +
                            MAX_POINTS_PER_REQUEST,
                    points.size
                )

            val chunk =
                points.subList(
                    start,
                    end
                )

            if (chunk.size >= 2) {

                val result =
                    matchChunk(
                        chunk
                    )

                if (result != null) {

                    /*
                     * Avoid duplicating the first
                     * point when chunks meet.
                     */
                    if (allMatchedPoints.isEmpty()) {

                        allMatchedPoints.addAll(
                            result.points
                        )

                    } else {

                        allMatchedPoints.addAll(
                            result.points.drop(1)
                        )
                    }

                    totalDistance +=
                        result.distanceMeters

                    if (result.confidence > 0.0) {

                        confidenceTotal +=
                            result.confidence

                        confidenceCount++
                    }
                }
            }

            start =
                end
        }

        if (allMatchedPoints.size < 2) {
            return null
        }

        val averageConfidence =
            if (confidenceCount > 0) {
                confidenceTotal /
                        confidenceCount
            } else {
                0.0
            }

        return MapMatchingResult(
            points =
                allMatchedPoints,
            distanceMeters =
                totalDistance,
            confidence =
                averageConfidence
        )
    }

    private fun matchChunk(
        points: List<GeoPoint>
    ): MapMatchingResult? {

        return try {

            val coordinates =
                points.joinToString(";") {

                    String.format(
                        Locale.US,
                        "%.7f,%.7f",
                        it.longitude,
                        it.latitude
                    )
                }

            val urlString =
                "$OSRM_URL/match/v1/driving/" +
                        "$coordinates" +
                        "?overview=full" +
                        "&geometries=geojson" +
                        "&steps=false" +
                        "&gaps=split" +
                        "&tidy=true"

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
                "BackgroundOdometer/6.0"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val responseCode =
                connection.responseCode

            if (responseCode != 200) {

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
                i in 0 until matchings.length()
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

                if (confidence > 0.0) {

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
                    j in 0 until coordinates.length()
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
                        coordinate.getDouble(0)

                    val latitude =
                        coordinate.getDouble(1)

                    matchedPoints.add(
                        GeoPoint(
                            latitude,
                            longitude
                        )
                    )
                }
            }

            if (matchedPoints.size < 2) {
                return null
            }

            val confidence =
                if (confidenceCount > 0) {
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
