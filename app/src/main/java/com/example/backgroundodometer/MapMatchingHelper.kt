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
        60

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
    /**
     * Matches the cleaned route, not the odometer threshold.
     * The speed threshold controls odometer accumulation only.
     */
    fun matchTrip(
        points: List<TrackPoint>,
        speedThreshold: Double
    ): MapMatchingResult? {

        if (points.size < 2) {
            return null
        }

        val cleaned =
            cleanRoutePoints(points)

        if (cleaned.size < 2) {
            return null
        }

        val inputs =
            cleaned.map { point ->
                MatchInput(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    timeSeconds = point.time / 1000L,
                    accuracy = point.accuracy
                )
            }

        return matchInputs(
            resample(inputs)
        )
    }

    /**
     * Removes GPS noise from the displayed/matched route while preserving
     * genuine slow movement. It does NOT apply the odometer speed threshold.
     */
    /**
     * Cleans the route for MAP DISPLAY / ROAD MATCHING.
     *
     * IMPORTANT:
     * - This is deliberately independent of the odometer speed threshold.
     * - We keep genuine vehicle movement even when it is below the odometer
     *   threshold.
     * - Stationary GPS drift is removed using a separate low-speed movement
     *   filter and a minimum displacement test.
     * - The raw database points are never modified.
     */
    fun cleanRoutePoints(
        points: List<TrackPoint>
    ): List<TrackPoint> {

        if (points.size < 2) {
            return points.filter(::isUsablePoint)
        }

        val valid =
            points
                .filter(::isUsablePoint)
                .sortedBy { it.time }

        if (valid.size < 2) {
            return valid
        }

        // First remove physically impossible GPS jumps.
        val filtered = ArrayList<TrackPoint>()
        filtered.add(valid.first())

        for (i in 1 until valid.size) {
            val current = valid[i]
            val previous = filtered.last()
            val seconds = (current.time - previous.time) / 1000.0

            if (seconds <= 0.0) continue

            val distance = haversine(
                previous.latitude,
                previous.longitude,
                current.latitude,
                current.longitude
            )

            val impliedSpeed = distance / seconds * 3.6

            if (distance > 300.0 || impliedSpeed > 180.0) {
                continue
            }

            filtered.add(current)
        }

        if (filtered.size < 2) {
            return filtered
        }

        /*
         * A separate route-display movement threshold.
         * This is NOT the user's odometer threshold.
         * 2 km/h is high enough to suppress most stationary GPS drift while
         * still retaining very slow vehicle movement.
         */
        val routeMovementSpeed = 2.0
        val minimumMovementMeters = 12.0
        val strongMovementMeters = 25.0

        val result = ArrayList<TrackPoint>()
        result.add(filtered.first())

        var lastMovementPoint = filtered.first()
        var haveConfirmedMovement = false

        for (i in 1 until filtered.lastIndex) {
            val point = filtered[i]
            val previous = filtered[i - 1]
            val next = filtered[i + 1]

            val prevSeconds = (point.time - previous.time) / 1000.0
            val nextSeconds = (next.time - point.time) / 1000.0

            val prevDistance = if (prevSeconds > 0.0) {
                haversine(
                    previous.latitude,
                    previous.longitude,
                    point.latitude,
                    point.longitude
                )
            } else 0.0

            val nextDistance = if (nextSeconds > 0.0) {
                haversine(
                    point.latitude,
                    point.longitude,
                    next.latitude,
                    next.longitude
                )
            } else 0.0

            val prevSpeed = if (prevSeconds > 0.0) {
                prevDistance / prevSeconds * 3.6
            } else 0.0

            val nextSpeed = if (nextSeconds > 0.0) {
                nextDistance / nextSeconds * 3.6
            } else 0.0

            val gpsSpeed = point.speedKmh.coerceAtLeast(0.0)

            val displacementFromMovementAnchor = haversine(
                lastMovementPoint.latitude,
                lastMovementPoint.longitude,
                point.latitude,
                point.longitude
            )

            val clearlyMoving =
                gpsSpeed >= routeMovementSpeed ||
                    prevSpeed >= routeMovementSpeed ||
                    nextSpeed >= routeMovementSpeed

            val enoughDisplacement =
                displacementFromMovementAnchor >= minimumMovementMeters

            val strongDisplacement =
                displacementFromMovementAnchor >= strongMovementMeters

            /*
             * Require movement evidence rather than simply accepting a point
             * because it is several metres away. This is what removes the
             * characteristic GPS "spiderweb" around a parked vehicle.
             */
            val accept =
                (clearlyMoving && enoughDisplacement) ||
                    strongDisplacement

            if (accept) {
                result.add(point)
                lastMovementPoint = point
                haveConfirmedMovement = true
            }
        }

        /*
         * Handle the final point separately. The old implementation always
         * appended it when it was within 300 m, which allowed a stationary
         * GPS cloud at the end of a trip to become visible as scribbling.
         */
        val last = filtered.last()
        val previous = filtered[filtered.lastIndex - 1]

        val lastSeconds = (last.time - previous.time) / 1000.0
        val lastDistance = if (lastSeconds > 0.0) {
            haversine(
                previous.latitude,
                previous.longitude,
                last.latitude,
                last.longitude
            )
        } else 0.0

        val lastImpliedSpeed = if (lastSeconds > 0.0) {
            lastDistance / lastSeconds * 3.6
        } else 0.0

        val lastFromAnchor = haversine(
            lastMovementPoint.latitude,
            lastMovementPoint.longitude,
            last.latitude,
            last.longitude
        )

        val lastMoving =
            last.speedKmh >= routeMovementSpeed ||
                lastImpliedSpeed >= routeMovementSpeed

        if (
            lastMoving &&
            lastFromAnchor >= minimumMovementMeters
        ) {
            result.add(last)
        } else if (
            !haveConfirmedMovement &&
            lastDistance >= strongMovementMeters
        ) {
            // Very short trips that contain no separately confirmed movement.
            result.add(last)
        }

        // Remove accidental duplicate consecutive points.
        return result
            .distinctBy { it.id }
    }

    private fun isUsablePoint(point: TrackPoint): Boolean {
        return point.latitude.isFinite() &&
            point.longitude.isFinite() &&
            point.accuracy.isFinite() &&
            point.accuracy > 0.0 &&
            point.accuracy <= 50.0
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

        if (inputs.size <= MAX_MATCH_POINTS) {
            return matchSingleRequest(inputs)
        }

        var start = 0
        var totalDistance = 0.0
        var confidenceTotal = 0.0
        var confidenceCount = 0
        val routePoints = ArrayList<GeoPoint>()

        while (start < inputs.lastIndex) {
            val end =
                min(
                    inputs.lastIndex,
                    start + MAX_MATCH_POINTS - 1
                )

            val chunk =
                inputs.subList(
                    start,
                    end + 1
                )

            val result =
                matchSingleRequest(chunk)
                    ?: return null

            totalDistance += result.distanceMeters

            if (result.confidence > 0.0) {
                confidenceTotal += result.confidence
                confidenceCount++
            }

            if (routePoints.isEmpty()) {
                routePoints.addAll(result.points)
            } else {
                routePoints.addAll(result.points.drop(1))
            }

            if (end == inputs.lastIndex) {
                break
            }

            // One-point overlap keeps adjacent road-matched chunks connected.
            start = end
        }

        if (routePoints.size < 2 || totalDistance <= 0.0) {
            return null
        }

        return MapMatchingResult(
            points = routePoints,
            distanceMeters = totalDistance,
            confidence =
                if (confidenceCount > 0) {
                    confidenceTotal / confidenceCount
                } else {
                    0.0
                }
        )
    }

    /**
     * OSRM's public matching endpoint is used with GET.
     * The previous implementation posted JSON to the endpoint, which is
     * commonly rejected by the public OSRM server.
     */
    private fun matchSingleRequest(
        inputs: List<MatchInput>
    ): MapMatchingResult? {

        if (inputs.size < 2) {
            return null
        }

        return try {
            val coordinates =
                inputs.joinToString(";") {
                    "${it.longitude},${it.latitude}"
                }

            val timestamps =
                inputs.joinToString(";") {
                    it.timeSeconds.toString()
                }

            val radiuses =
                inputs.joinToString(";") {
                    val radius =
                        max(
                            MIN_ACCURACY,
                            min(
                                MAX_ACCURACY,
                                if (it.accuracy > 0.0) {
                                    it.accuracy
                                } else {
                                    10.0
                                }
                            )
                        )
                    radius.toInt().toString()
                }

            val urlString =
                "$OSRM_URL/match/v1/driving/" +
                    "$coordinates" +
                    "?overview=full" +
                    "&geometries=geojson" +
                    "&steps=false" +
                    "&gaps=split" +
                    "&tidy=true" +
                    "&timestamps=$timestamps" +
                    "&radiuses=$radiuses"

            val connection =
                URL(urlString)
                    .openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty(
                "Accept",
                "application/json"
            )
            connection.setRequestProperty(
                "User-Agent",
                "BackgroundOdometer/18.0"
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
                    .use { it.readText() }

            connection.disconnect()

            parseResponse(response)

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
