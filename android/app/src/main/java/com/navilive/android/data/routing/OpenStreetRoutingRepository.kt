package com.navilive.android.data.routing

import android.content.Context
import androidx.annotation.StringRes
import com.navilive.android.R
import com.navilive.android.model.GeoPoint
import com.navilive.android.model.NearbyPoiCacheState
import com.navilive.android.model.Place
import com.navilive.android.model.RouteStepKind
import com.navilive.android.model.RouteStep
import com.navilive.android.model.RouteSummary
import com.navilive.android.model.SharedProductRules
import com.navilive.android.ui.NavigationScenarioCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class OpenStreetRoutingRepository(
    context: Context,
) {

    private data class SearchCandidate(
        val place: Place,
        val score: Int,
        val distanceMeters: Int,
        val importance: Double,
        val isNearbyCandidate: Boolean,
        val kind: PlaceKind = PlaceKind.Other,
    )

    private data class NearbyAddressCandidate(
        val address: String,
        val point: GeoPoint,
    )

    private enum class PlaceKind {
        Shop,
        ParcelLocker,
        RailStation,
        BusStop,
        TramStop,
        Other,
    }

    private data class SearchIntent(
        val normalizedQuery: String,
        val tokens: List<String>,
        val nameSearchTerms: List<String>,
        val wantsShop: Boolean,
        val wantsParcelLocker: Boolean,
        val wantsRailStation: Boolean,
        val wantsTransitStop: Boolean,
    ) {
        val wantsAnyCategory: Boolean = wantsShop || wantsParcelLocker || wantsRailStation || wantsTransitStop
        val isCategoryOnly: Boolean = wantsAnyCategory && nameSearchTerms.isEmpty()
    }

    private data class RouteCrossingCandidate(
        val point: GeoPoint,
        val distanceAlongRouteMeters: Double,
    )

    private data class RouteProjection(
        val distanceAlongRouteMeters: Double,
        val lateralDistanceMeters: Double,
    )

    private data class SegmentProjection(
        val ratio: Double,
        val lengthMeters: Double,
        val lateralDistanceMeters: Double,
    )

    private data class RouteBoundingBox(
        val south: Double,
        val west: Double,
        val north: Double,
        val east: Double,
    )

    private data class NamedRouteWay(
        val name: String,
        val highway: String?,
        val geometry: List<GeoPoint>,
    )

    private val appContext = context.applicationContext
    private val poiCacheStore = NearbyPoiCacheStore(appContext)

    private val shopQueryTerms = setOf(
        "sklep",
        "sklepy",
        "sklepu",
        "market",
        "supermarket",
        "spozywczy",
        "spożywczy",
        "grocery",
        "store",
    )
    private val parcelLockerQueryTerms = setOf(
        "paczkomat",
        "paczkomaty",
        "paczka",
        "parcel",
        "locker",
        "inpost",
    )
    private val railStationQueryTerms = setOf(
        "pkp",
        "kolej",
        "kolejowa",
        "kolejowy",
        "stacja",
        "dworzec",
        "pociag",
        "pociąg",
        "train",
        "railway",
        "station",
    )
    private val transitStopQueryTerms = setOf(
        "przystanek",
        "przystanki",
        "przystanku",
        "autobus",
        "autobusowy",
        "autobusowa",
        "bus",
        "tramwaj",
        "tramwajowy",
        "tramwajowa",
        "tram",
        "stop",
    )
    private val categoryQueryTerms = shopQueryTerms + parcelLockerQueryTerms + railStationQueryTerms + transitStopQueryTerms

    private companion object {
        const val LOCAL_POI_TOTAL_TIMEOUT_MS = 3_500L
        const val LOCAL_POI_REQUEST_TIMEOUT_MS = 1_800
        const val POI_CACHE_REFRESH_REQUEST_TIMEOUT_MS = 5_000
        const val ADDRESS_LOOKUP_TIMEOUT_MS = 5_000
        const val CHAIN_LOCATOR_TIMEOUT_MS = 4_000
        const val NEARBY_ADDRESS_LOOKUP_RADIUS_METERS = 80
        const val NEARBY_ADDRESS_LOOKUP_LIMIT = 220
        const val ROUTE_REQUEST_TIMEOUT_MS = 10_000
        const val ROUTE_ROAD_NAME_REQUEST_TIMEOUT_MS = 8_000
        const val CROSSING_REQUEST_TIMEOUT_MS = 2_000
        const val CROSSING_DUPLICATE_PROXIMITY_METERS = 3.0
        const val CROSSING_TURN_PROXIMITY_METERS = 30.0
        const val ROUTE_START_APPROACH_THRESHOLD_METERS = 18.0
        const val MIN_INFERRED_ROAD_STEP_DISTANCE_METERS = 45
        const val APPROACH_MANEUVER_TYPE = "approach"
        const val MINIMUM_USEFUL_SEARCH_RESULTS = 3
        const val OFFICIAL_CHAIN_SCORE = 3_000
        const val POI_CACHE_REFRESH_LIMIT = 350
        const val ZABKA_LOCATOR_URL = "https://www.zabka.pl/app/uploads/locator-store-data.json"
    }

    suspend fun searchPlaces(
        query: String,
        currentPoint: GeoPoint?,
        searchRadiusKm: Int = SharedProductRules.Search.defaultRadiusKm,
        resultLimit: Int = SharedProductRules.Search.resultLimit,
    ): List<Place> {
        if (query.isBlank()) {
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            val normalizedSearchRadiusKm = searchRadiusKm.coerceIn(
                SharedProductRules.Search.minimumRadiusKm,
                SharedProductRules.Search.maximumRadiusKm,
            )
            val normalizedResultLimit = resultLimit.coerceIn(
                SharedProductRules.Search.minimumResultLimit,
                SharedProductRules.Search.maximumResultLimit,
            )
            val searchRadiusMeters = normalizedSearchRadiusKm * 1_000
            val intent = searchIntent(query)
            val combined = linkedMapOf<String, SearchCandidate>()
            if (currentPoint != null && isZabkaQuery(intent)) {
                queryOfficialZabkaCandidates(
                    query = query,
                    currentPoint = currentPoint,
                    searchRadiusMeters = searchRadiusMeters,
                ).forEach { candidate ->
                    combined[candidate.place.id] = candidate
                }
            }
            if (currentPoint != null) {
                queryCachedPoiCandidates(
                    query = query,
                    currentPoint = currentPoint,
                    searchRadiusMeters = searchRadiusMeters,
                    intent = intent,
                    resultLimit = normalizedResultLimit,
                ).forEach { candidate ->
                    combined[candidate.place.id] = candidate
                }
            }
            if (currentPoint != null && combined.size < MINIMUM_USEFUL_SEARCH_RESULTS) {
                runCatching {
                    queryLocalPoiCandidates(
                        query = query,
                        currentPoint = currentPoint,
                        searchRadiusMeters = searchRadiusMeters,
                        intent = intent,
                        resultLimit = normalizedResultLimit,
                    )
                }.getOrDefault(emptyList()).forEach { candidate ->
                    combined[candidate.place.id] = candidate
                }
            }
            val shouldUseTextSearchFallback = true
            if ((combined.size < MINIMUM_USEFUL_SEARCH_RESULTS || currentPoint == null || !intent.isCategoryOnly) && shouldUseTextSearchFallback) {
                nearbyTextSearchRadiiKm(
                    normalizedSearchRadiusKm = normalizedSearchRadiusKm,
                    currentPoint = currentPoint,
                    intent = intent,
                ).forEach { radiusKm ->
                    val nearby = querySearchCandidates(
                        query = query,
                        currentPoint = currentPoint,
                        nearbyOnly = true,
                        searchRadiusKm = radiusKm,
                        intent = intent,
                        resultLimit = normalizedResultLimit,
                    )
                    nearby.forEach { candidate ->
                        putSearchCandidate(combined, candidate)
                    }
                }
                if (currentPoint != null &&
                    combined.size < MINIMUM_USEFUL_SEARCH_RESULTS &&
                    intent.wantsAnyCategory &&
                    !intent.isCategoryOnly &&
                    intent.nameSearchTerms.isNotEmpty()
                ) {
                    val simplifiedQuery = intent.nameSearchTerms.joinToString(" ")
                    if (simplifiedQuery.isNotBlank() && !simplifiedQuery.equals(query, ignoreCase = true)) {
                        runCatching {
                            querySearchCandidates(
                                query = simplifiedQuery,
                                currentPoint = currentPoint,
                                nearbyOnly = true,
                                searchRadiusKm = SharedProductRules.Search.minimumRadiusKm,
                                intent = intent,
                                resultLimit = normalizedResultLimit,
                            )
                        }.getOrDefault(emptyList()).forEach { candidate ->
                            putSearchCandidate(combined, candidate)
                        }
                    }
                }
                if (currentPoint != null && intent.isCategoryOnly) {
                    val expansionQueries = when {
                        intent.wantsShop -> shopCategoryExpansionQueries()
                        intent.wantsParcelLocker -> parcelLockerCategoryExpansionQueries()
                        intent.wantsTransitStop -> transitStopCategoryExpansionQueries()
                        else -> emptyList()
                    }
                    expansionQueries.forEach { expandedQuery ->
                        runCatching {
                            querySearchCandidates(
                                query = expandedQuery,
                                currentPoint = currentPoint,
                                nearbyOnly = true,
                                searchRadiusKm = normalizedSearchRadiusKm,
                                intent = intent,
                                resultLimit = normalizedResultLimit,
                            )
                        }.getOrDefault(emptyList()).forEach { candidate ->
                            putSearchCandidate(combined, candidate)
                        }
                    }
                }
            }
            val includeGlobalFallback = (currentPoint == null || combined.isEmpty()) && shouldUseTextSearchFallback
            if (includeGlobalFallback) {
                querySearchCandidates(
                    query = query,
                    currentPoint = currentPoint,
                    nearbyOnly = false,
                    searchRadiusKm = normalizedSearchRadiusKm,
                    intent = intent,
                    resultLimit = normalizedResultLimit,
                ).forEach { candidate ->
                    putSearchCandidate(combined, candidate)
                }
            }
            val sortedCandidates = deduplicatedSearchCandidates(combined.values)
                .sortedWith(::compareSearchCandidates)
                .take(normalizedResultLimit)
            enrichSearchResultAddresses(sortedCandidates)
        }
    }

    suspend fun nearbyPoiCacheState(): NearbyPoiCacheState = poiCacheStore.metadata()

    suspend fun clearNearbyPoiCache(): NearbyPoiCacheState = poiCacheStore.clear()

    suspend fun refreshNearbyPoiCache(
        currentPoint: GeoPoint,
        radiusKm: Int,
    ): NearbyPoiCacheState = withContext(Dispatchers.IO) {
        val normalizedRadiusKm = radiusKm.coerceIn(SharedProductRules.Search.minimumRadiusKm, 5)
        val fetchedAtMs = System.currentTimeMillis()
        val records = queryNearbyPoiCacheRecords(
            currentPoint = currentPoint,
            radiusMeters = normalizedRadiusKm * 1_000,
            fetchedAtMs = fetchedAtMs,
        )
        poiCacheStore.saveMerged(
            records = records,
            center = currentPoint,
            fetchedAtMs = fetchedAtMs,
        )
    }

    suspend fun buildWalkingRoute(
        from: GeoPoint,
        to: GeoPoint,
        includePedestrianCrossings: Boolean = true,
    ): RouteSummary {
        return withContext(Dispatchers.IO) {
            val coordinateString = "${from.longitude},${from.latitude};${to.longitude},${to.latitude}"
            val root = routingEndpoints(coordinateString)
                .firstNotNullOfOrNull { endpoint ->
                    runCatching {
                        JSONObject(requestText(endpoint, timeoutMs = ROUTE_REQUEST_TIMEOUT_MS))
                    }.getOrNull()
                } ?: throw IllegalStateException("Routing service returned no route response.")
            val routes = root.optJSONArray("routes")
            if (routes == null || routes.length() == 0) {
                throw IllegalStateException("Routing service returned no routes.")
            }
            val route = routes.getJSONObject(0)
            val distance = route.optDouble("distance", 0.0).roundToInt()
            val duration = route.optDouble("duration", 0.0)
            val etaMinutes = NavigationScenarioCore.routeEtaMinutes(
                distanceMeters = distance,
                providerDurationSeconds = duration,
            )

            val steps = route
                .optJSONArray("legs")
                ?.optJSONObject(0)
                ?.optJSONArray("steps")
            val pathPoints = parsePath(route.optJSONObject("geometry"))
            val namedRouteWays = queryNamedRouteWays(pathPoints)
            val baseSteps = stepsWithStartApproach(
                from = from,
                pathPoints = pathPoints,
                steps = simplifyRouteSteps(parseSteps(steps, namedRouteWays)),
            )
            val parsedSteps = if (includePedestrianCrossings) {
                addPedestrianCrossingSteps(
                    steps = baseSteps,
                    pathPoints = pathPoints,
                )
            } else {
                baseSteps
            }

            val currentInstruction = parsedSteps.firstOrNull()?.instruction ?: stepInstruction(steps, 0)
            val nextInstruction = parsedSteps.getOrNull(1)?.instruction ?: stepInstruction(steps, 1)

            RouteSummary(
                distanceMeters = distance,
                etaMinutes = etaMinutes,
                modeLabel = string(R.string.generic_route_mode_walk),
                currentInstruction = currentInstruction,
                nextInstruction = nextInstruction,
                steps = parsedSteps,
                pathPoints = pathPoints,
            )
        }
    }

    private fun routingEndpoints(coordinateString: String): List<String> {
        val query = "?overview=full&steps=true&alternatives=false&geometries=geojson"
        return listOf(
            "https://routing.openstreetmap.de/routed-foot/route/v1/foot/$coordinateString$query",
            "https://router.project-osrm.org/route/v1/foot/$coordinateString$query",
        )
    }

    suspend fun reverseGeocode(point: GeoPoint): String = withContext(Dispatchers.IO) {
        val endpoint =
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2" +
                "&lat=${point.latitude}&lon=${point.longitude}&zoom=18&addressdetails=1"
        val response = requestText(endpoint)
        val root = JSONObject(response)
        val displayName = root.optString("display_name")
        formatAddress(root.optJSONObject("address"), displayName).ifBlank {
            val lat = "%.5f".format(point.latitude)
            val lon = "%.5f".format(point.longitude)
            string(R.string.format_coordinates_label, lat, lon)
        }
    }

    private fun stepInstruction(steps: JSONArray?, index: Int): String {
        if (steps == null || steps.length() <= index) return string(R.string.generic_follow_route_guidance)
        val step = steps.optJSONObject(index) ?: return string(R.string.generic_follow_route_guidance)
        return instructionForStep(step)
    }

    private fun parseSteps(
        steps: JSONArray?,
        namedRouteWays: List<NamedRouteWay> = emptyList(),
    ): List<RouteStep> {
        if (steps == null || steps.length() == 0) {
            return listOf(RouteStep(instruction = string(R.string.generic_follow_route_guidance), distanceMeters = 0))
        }
        val parsed = mutableListOf<RouteStep>()
        for (index in 0 until steps.length()) {
            val step = steps.optJSONObject(index) ?: continue
            val maneuver = step.optJSONObject("maneuver")
            val location = maneuver?.optJSONArray("location")
            val maneuverPoint = if (location != null && location.length() >= 2) {
                GeoPoint(
                    latitude = location.optDouble(1),
                    longitude = location.optDouble(0),
                )
            } else {
                null
            }
            val stepDistanceMeters = step.optDouble("distance", 0.0).roundToInt()
            val stepGeometry = parsePath(step.optJSONObject("geometry"))
            val inferredRoadName = if (stepDistanceMeters >= MIN_INFERRED_ROAD_STEP_DISTANCE_METERS) {
                inferRoadNameForStep(
                    stepGeometry = stepGeometry,
                    maneuverPoint = maneuverPoint,
                    namedRouteWays = namedRouteWays,
                )
            } else {
                null
            }
            val roadName = step.optString("name").trim().ifBlank {
                inferredRoadName?.trim().orEmpty()
            }.takeIf { it.isNotBlank() }
            parsed += RouteStep(
                instruction = instructionForStep(step, roadName),
                distanceMeters = stepDistanceMeters,
                maneuverPoint = maneuverPoint,
                maneuverType = maneuver?.optString("type")?.takeIf { it.isNotBlank() },
                maneuverModifier = maneuver?.optString("modifier")?.takeIf { it.isNotBlank() },
                roadName = roadName,
            )
        }
        return parsed.ifEmpty {
            listOf(RouteStep(instruction = string(R.string.generic_follow_route_guidance), distanceMeters = 0))
        }
    }

    private fun parsePath(geometry: JSONObject?): List<GeoPoint> {
        val coordinates = geometry?.optJSONArray("coordinates") ?: return emptyList()
        val points = ArrayList<GeoPoint>(coordinates.length())
        for (index in 0 until coordinates.length()) {
            val coordinate = coordinates.optJSONArray(index) ?: continue
            if (coordinate.length() < 2) continue
            points += GeoPoint(
                latitude = coordinate.optDouble(1),
                longitude = coordinate.optDouble(0),
            )
        }
        return points
    }

    private fun stepsWithStartApproach(
        from: GeoPoint,
        pathPoints: List<GeoPoint>,
        steps: List<RouteStep>,
    ): List<RouteStep> {
        val routeStart = pathPoints.firstOrNull() ?: return steps
        val distanceToRouteStart = haversineMeters(from, routeStart)
        if (distanceToRouteStart < ROUTE_START_APPROACH_THRESHOLD_METERS) return steps
        val approachStep = RouteStep(
            instruction = string(R.string.route_step_reach_route_start),
            distanceMeters = distanceToRouteStart.roundToInt().coerceAtLeast(1),
            maneuverPoint = routeStart,
            maneuverType = APPROACH_MANEUVER_TYPE,
        )
        return listOf(approachStep) + steps
    }

    private fun simplifyRouteSteps(steps: List<RouteStep>): List<RouteStep> {
        if (steps.size <= 2) return steps
        val simplified = mutableListOf<RouteStep>()
        steps.forEachIndexed { index, step ->
            val previous = simplified.lastOrNull()
            if (shouldSuppressRouteStep(step, previous, index, steps.lastIndex)) {
                simplified[simplified.lastIndex] = previous!!.copy(
                    distanceMeters = previous.distanceMeters + step.distanceMeters,
                )
            } else {
                simplified += step
            }
        }
        return simplified.ifEmpty { steps }
    }

    private fun shouldSuppressRouteStep(
        step: RouteStep,
        previous: RouteStep?,
        index: Int,
        lastIndex: Int,
    ): Boolean = RouteStepSimplificationCore.shouldSuppressRouteStep(
        step = step,
        previous = previous,
        index = index,
        lastIndex = lastIndex,
    )

    private fun addPedestrianCrossingSteps(
        steps: List<RouteStep>,
        pathPoints: List<GeoPoint>,
    ): List<RouteStep> {
        if (steps.isEmpty() || pathPoints.size < 2) return steps
        val crossings = runCatching { queryPedestrianCrossings(pathPoints) }.getOrDefault(emptyList())
        if (crossings.isEmpty()) return steps

        val routeLengthMeters = routeLengthMeters(pathPoints)
        val stepDistances = stepDistancesAlongRoute(steps, pathPoints, routeLengthMeters)
        val augmented = mutableListOf<RouteStep>()
        var crossingIndex = 0
        var lastAlongMeters = stepDistances.firstOrNull() ?: 0.0

        augmented += steps.first()
        for (stepIndex in 1 until steps.size) {
            val stepAlongMeters = stepDistances[stepIndex]
            val nextStep = steps[stepIndex]
            while (
                crossingIndex < crossings.size &&
                crossings[crossingIndex].distanceAlongRouteMeters < stepAlongMeters
            ) {
                val crossing = crossings[crossingIndex]
                val previousStep = augmented.lastOrNull { it.kind == RouteStepKind.Instruction }
                val distanceFromPreviousRaw = crossing.distanceAlongRouteMeters - lastAlongMeters
                val distanceToNextStep = stepAlongMeters - crossing.distanceAlongRouteMeters
                if (
                    shouldSuppressCrossingAlert(
                        distanceFromPreviousMeters = distanceFromPreviousRaw,
                        previousStep = previousStep,
                        distanceToNextStepMeters = distanceToNextStep,
                        nextStep = nextStep,
                    )
                ) {
                    crossingIndex += 1
                    continue
                }
                val distanceFromPrevious = distanceFromPreviousRaw
                    .roundToInt()
                    .coerceAtLeast(1)
                augmented += RouteStep(
                    instruction = string(R.string.route_step_crossing),
                    distanceMeters = distanceFromPrevious,
                    maneuverPoint = crossing.point,
                    kind = RouteStepKind.PedestrianCrossing,
                )
                lastAlongMeters = crossing.distanceAlongRouteMeters
                crossingIndex += 1
            }
            augmented += steps[stepIndex]
            lastAlongMeters = maxOf(lastAlongMeters, stepAlongMeters)
        }
        return augmented
    }

    private fun shouldSuppressCrossingAlert(
        distanceFromPreviousMeters: Double,
        previousStep: RouteStep?,
        distanceToNextStepMeters: Double,
        nextStep: RouteStep,
    ): Boolean {
        if (distanceFromPreviousMeters < CROSSING_DUPLICATE_PROXIMITY_METERS) return true
        if (
            distanceFromPreviousMeters < CROSSING_TURN_PROXIMITY_METERS &&
            previousStep?.isTurnLikeManeuver() == true
        ) {
            return true
        }
        if (
            distanceToNextStepMeters < CROSSING_TURN_PROXIMITY_METERS &&
            nextStep.isTurnLikeManeuver()
        ) {
            return true
        }
        return false
    }

    private fun RouteStep.isTurnLikeManeuver(): Boolean =
        RouteStepSimplificationCore.isTurnLikeManeuver(this)

    private fun queryPedestrianCrossings(pathPoints: List<GeoPoint>): List<RouteCrossingCandidate> {
        val endpoints = buildPedestrianCrossingEndpoints(pathPoints)
        var response: String? = null
        for (endpoint in endpoints) {
            response = runCatching { requestText(endpoint, timeoutMs = CROSSING_REQUEST_TIMEOUT_MS) }.getOrNull()
            if (response != null) break
        }
        response ?: return emptyList()

        val routeLengthMeters = routeLengthMeters(pathPoints)
        val elements = JSONObject(response).optJSONArray("elements") ?: return emptyList()
        val candidates = mutableListOf<RouteCrossingCandidate>()
        for (index in 0 until elements.length()) {
            val item = elements.optJSONObject(index) ?: continue
            val point = overpassPoint(item) ?: continue
            val projection = projectOntoRoute(pathPoints, point) ?: continue
            if (projection.lateralDistanceMeters > 6.0) continue
            if (projection.distanceAlongRouteMeters < 20.0) continue
            if (projection.distanceAlongRouteMeters > routeLengthMeters - 20.0) continue
            candidates += RouteCrossingCandidate(
                point = point,
                distanceAlongRouteMeters = projection.distanceAlongRouteMeters,
            )
        }

        val deduplicated = mutableListOf<RouteCrossingCandidate>()
        for (candidate in candidates.sortedBy { it.distanceAlongRouteMeters }) {
            val previous = deduplicated.lastOrNull()
            if (previous == null || candidate.distanceAlongRouteMeters - previous.distanceAlongRouteMeters >= 25.0) {
                deduplicated += candidate
            }
        }
        return deduplicated
    }

    private fun buildPedestrianCrossingEndpoints(pathPoints: List<GeoPoint>): List<String> {
        val box = routeBoundingBox(pathPoints, paddingMeters = 25.0)
        val bbox = "(${formatCoordinate(box.south)},${formatCoordinate(box.west)}," +
            "${formatCoordinate(box.north)},${formatCoordinate(box.east)})"
        val query = "[out:json][timeout:${SharedProductRules.Search.overpassTimeoutSeconds}];" +
            "(" +
            "node[\"highway\"=\"crossing\"]$bbox;" +
            "node[\"crossing\"]$bbox;" +
            "way[\"highway\"=\"crossing\"]$bbox;" +
            "way[\"footway\"=\"crossing\"]$bbox;" +
            "way[\"crossing\"]$bbox;" +
            ");out center 160;"
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        return listOf(
            "https://overpass-api.de/api/interpreter?data=$encoded",
            "https://overpass.kumi.systems/api/interpreter?data=$encoded",
            "https://overpass.osm.ch/api/interpreter?data=$encoded",
        )
    }

    private fun queryNamedRouteWays(pathPoints: List<GeoPoint>): List<NamedRouteWay> {
        if (pathPoints.size < 2) return emptyList()
        for (endpoint in buildNamedRouteWayEndpoints(pathPoints)) {
            val response = runCatching {
                requestText(endpoint, timeoutMs = ROUTE_ROAD_NAME_REQUEST_TIMEOUT_MS)
            }.getOrNull() ?: continue
            val ways = parseNamedRouteWays(response)
            if (ways.isNotEmpty()) return ways
        }
        return emptyList()
    }

    private fun buildNamedRouteWayEndpoints(pathPoints: List<GeoPoint>): List<String> {
        val box = routeBoundingBox(pathPoints, paddingMeters = 60.0)
        val bbox = "(${formatCoordinate(box.south)},${formatCoordinate(box.west)}," +
            "${formatCoordinate(box.north)},${formatCoordinate(box.east)})"
        val query = "[out:json][timeout:${SharedProductRules.Search.overpassTimeoutSeconds}];" +
            "way[\"highway\"][\"name\"]$bbox;out geom;"
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        return listOf(
            "https://overpass-api.de/api/interpreter?data=$encoded",
            "https://overpass.kumi.systems/api/interpreter?data=$encoded",
            "https://overpass.osm.ch/api/interpreter?data=$encoded",
        )
    }

    private fun parseNamedRouteWays(response: String): List<NamedRouteWay> {
        val elements = JSONObject(response).optJSONArray("elements") ?: return emptyList()
        val ways = mutableListOf<NamedRouteWay>()
        for (index in 0 until elements.length()) {
            val item = elements.optJSONObject(index) ?: continue
            if (item.optString("type") != "way") continue
            val tags = item.optJSONObject("tags")?.toStringMap().orEmpty()
            val highway = tags["highway"]
            val name = tags["name"]?.trim().orEmpty()
            if (name.isBlank() || !isUsefulRouteWayName(highway, tags)) continue
            val geometry = parseOverpassGeometry(item.optJSONArray("geometry"))
            if (geometry.size < 2) continue
            ways += NamedRouteWay(
                name = name,
                highway = highway,
                geometry = geometry,
            )
        }
        return ways
    }

    private fun parseOverpassGeometry(geometry: JSONArray?): List<GeoPoint> {
        if (geometry == null) return emptyList()
        val points = ArrayList<GeoPoint>(geometry.length())
        for (index in 0 until geometry.length()) {
            val item = geometry.optJSONObject(index) ?: continue
            val latitude = item.optDouble("lat", Double.NaN)
            val longitude = item.optDouble("lon", Double.NaN)
            if (!latitude.isNaN() && !longitude.isNaN()) {
                points += GeoPoint(latitude, longitude)
            }
        }
        return points
    }

    private fun isUsefulRouteWayName(highway: String?, tags: Map<String, String>): Boolean {
        if (highway.isNullOrBlank()) return false
        if (highway in setOf("platform", "corridor", "elevator", "construction", "proposed")) return false
        if (tags["area"] == "yes") return false
        return true
    }

    private fun inferRoadNameForStep(
        stepGeometry: List<GeoPoint>,
        maneuverPoint: GeoPoint?,
        namedRouteWays: List<NamedRouteWay>,
    ): String? {
        if (namedRouteWays.isEmpty()) return null
        val samples = routeNameSamples(stepGeometry, maneuverPoint)
        if (samples.isEmpty()) return null
        return namedRouteWays
            .mapNotNull { way ->
                val distance = samples.minOfOrNull { sample ->
                    routeWayDistanceMeters(way, sample)
                } ?: return@mapNotNull null
                if (distance > 45.0) return@mapNotNull null
                way to (distance + routeWayPriorityPenalty(way.highway))
            }
            .minByOrNull { it.second }
            ?.first
            ?.name
    }

    private fun routeNameSamples(
        stepGeometry: List<GeoPoint>,
        maneuverPoint: GeoPoint?,
    ): List<GeoPoint> {
        if (stepGeometry.size >= 3) {
            return listOf(
                stepGeometry[stepGeometry.size / 2],
                stepGeometry.last(),
            )
        }
        if (stepGeometry.isNotEmpty()) return listOf(stepGeometry.last())
        return listOfNotNull(maneuverPoint)
    }

    private fun routeWayDistanceMeters(way: NamedRouteWay, point: GeoPoint): Double {
        return projectOntoRoute(way.geometry, point)?.lateralDistanceMeters ?: Double.MAX_VALUE
    }

    private fun routeWayPriorityPenalty(highway: String?): Double {
        return when (highway) {
            "primary", "secondary", "tertiary", "residential", "living_street", "unclassified" -> 0.0
            "pedestrian", "service" -> 8.0
            "footway", "path", "steps" -> 14.0
            else -> 20.0
        }
    }

    private fun stepDistancesAlongRoute(
        steps: List<RouteStep>,
        pathPoints: List<GeoPoint>,
        routeLengthMeters: Double,
    ): List<Double> {
        val distances = mutableListOf<Double>()
        var previous = 0.0
        for ((index, step) in steps.withIndex()) {
            val raw = step.maneuverPoint
                ?.let { projectOntoRoute(pathPoints, it)?.distanceAlongRouteMeters }
                ?: if (index == 0) 0.0 else routeLengthMeters
            val normalized = raw.coerceIn(0.0, routeLengthMeters).coerceAtLeast(previous)
            distances += normalized
            previous = normalized
        }
        return distances
    }

    private fun routeBoundingBox(pathPoints: List<GeoPoint>, paddingMeters: Double): RouteBoundingBox {
        val minLatitude = pathPoints.minOf { it.latitude }
        val maxLatitude = pathPoints.maxOf { it.latitude }
        val minLongitude = pathPoints.minOf { it.longitude }
        val maxLongitude = pathPoints.maxOf { it.longitude }
        val midLatitudeRadians = Math.toRadians((minLatitude + maxLatitude) / 2.0)
        val latitudePadding = paddingMeters / 111_320.0
        val longitudePadding = paddingMeters / (111_320.0 * cos(midLatitudeRadians).coerceAtLeast(0.2))
        return RouteBoundingBox(
            south = minLatitude - latitudePadding,
            west = minLongitude - longitudePadding,
            north = maxLatitude + latitudePadding,
            east = maxLongitude + longitudePadding,
        )
    }

    private fun projectOntoRoute(pathPoints: List<GeoPoint>, point: GeoPoint): RouteProjection? {
        if (pathPoints.size < 2) return null
        var bestProjection: RouteProjection? = null
        var distanceBeforeSegment = 0.0
        for (index in 0 until pathPoints.lastIndex) {
            val segmentProjection = projectOntoSegment(
                point = point,
                start = pathPoints[index],
                end = pathPoints[index + 1],
            )
            val projection = RouteProjection(
                distanceAlongRouteMeters = distanceBeforeSegment +
                    segmentProjection.lengthMeters * segmentProjection.ratio,
                lateralDistanceMeters = segmentProjection.lateralDistanceMeters,
            )
            val best = bestProjection
            if (best == null || projection.lateralDistanceMeters < best.lateralDistanceMeters) {
                bestProjection = projection
            }
            distanceBeforeSegment += segmentProjection.lengthMeters
        }
        return bestProjection
    }

    private fun projectOntoSegment(point: GeoPoint, start: GeoPoint, end: GeoPoint): SegmentProjection {
        val latitudeReference = Math.toRadians((point.latitude + start.latitude + end.latitude) / 3.0)
        val earthRadius = 6_371_000.0

        fun project(geoPoint: GeoPoint): Pair<Double, Double> {
            val x = Math.toRadians(geoPoint.longitude) * earthRadius * cos(latitudeReference)
            val y = Math.toRadians(geoPoint.latitude) * earthRadius
            return x to y
        }

        val (pointX, pointY) = project(point)
        val (startX, startY) = project(start)
        val (endX, endY) = project(end)
        val dx = endX - startX
        val dy = endY - startY
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 0.0) {
            val distance = sqrt((pointX - startX).pow(2.0) + (pointY - startY).pow(2.0))
            return SegmentProjection(ratio = 0.0, lengthMeters = 0.0, lateralDistanceMeters = distance)
        }
        val ratio = (((pointX - startX) * dx + (pointY - startY) * dy) / lengthSquared)
            .coerceIn(0.0, 1.0)
        val closestX = startX + dx * ratio
        val closestY = startY + dy * ratio
        val lateralDistance = sqrt((pointX - closestX).pow(2.0) + (pointY - closestY).pow(2.0))
        return SegmentProjection(
            ratio = ratio,
            lengthMeters = sqrt(lengthSquared),
            lateralDistanceMeters = lateralDistance,
        )
    }

    private fun routeLengthMeters(pathPoints: List<GeoPoint>): Double {
        var length = 0.0
        for (index in 0 until pathPoints.lastIndex) {
            length += haversineMeters(pathPoints[index], pathPoints[index + 1])
        }
        return length
    }

    private fun instructionForStep(step: JSONObject, inferredRoadName: String? = null): String {
        val maneuver = step.optJSONObject("maneuver")
        val maneuverType = maneuver?.optString("type").orEmpty()
        val roadName = step.optString("name").trim().ifBlank {
            inferredRoadName?.trim()?.takeIf { it.isNotBlank() }
        }
        val descriptor = NavigationInstructionCore.describe(
            maneuverType = maneuverType,
            modifier = maneuver?.optString("modifier"),
            roadName = roadName,
        )
        return when (descriptor.strategy) {
            NavigationInstructionDescriptor.Strategy.DepartNamed ->
                descriptor.roadName?.let { string(R.string.route_step_depart, it) }
                    ?: string(R.string.route_step_depart_default)
            NavigationInstructionDescriptor.Strategy.Arrive ->
                string(R.string.generic_arriving_destination)
            NavigationInstructionDescriptor.Strategy.TurnNamed -> {
                val localizedModifier = routeModifier(descriptor.normalizedModifier)
                val road = descriptor.roadName
                if (road.isNullOrBlank()) {
                    if (localizedModifier.isBlank()) {
                        string(R.string.route_step_turn_default)
                    } else {
                        string(R.string.route_step_turn_bare_modifier, localizedModifier)
                    }
                } else {
                    string(
                        R.string.route_step_turn_with_modifier,
                        localizedModifier.ifBlank { descriptor.normalizedModifier ?: "" },
                        road,
                    )
                }
            }
            NavigationInstructionDescriptor.Strategy.TurnGenericNamed ->
                descriptor.roadName?.let { string(R.string.route_step_turn_generic, it) }
                    ?: string(R.string.route_step_turn_default)
            NavigationInstructionDescriptor.Strategy.TurnBareModifier -> {
                val localizedModifier = routeModifier(descriptor.normalizedModifier)
                if (localizedModifier.isBlank()) {
                    string(R.string.route_step_turn_default)
                } else {
                    string(R.string.route_step_turn_bare_modifier, localizedModifier)
                }
            }
            NavigationInstructionDescriptor.Strategy.ContinueNamed ->
                descriptor.roadName?.let { string(R.string.route_step_continue, it) }
                    ?: string(R.string.route_step_continue_default)
            NavigationInstructionDescriptor.Strategy.ProceedTowardNamed ->
                descriptor.roadName?.let { string(R.string.route_step_proceed_toward, it) }
                    ?: string(R.string.route_step_continue_default)
        }
    }

    private fun routeModifier(modifier: String?): String {
        val normalized = modifier
            ?.let(SharedProductRules.Instructions::normalizeModifier)
            .orEmpty()
        if (normalized !in SharedProductRules.Instructions.supportedModifiers) {
            return modifier.orEmpty()
        }
        return when (normalized) {
            "left" -> string(R.string.route_modifier_left)
            "right" -> string(R.string.route_modifier_right)
            "slight left" -> string(R.string.route_modifier_slight_left)
            "slight right" -> string(R.string.route_modifier_slight_right)
            "sharp left" -> string(R.string.route_modifier_sharp_left)
            "sharp right" -> string(R.string.route_modifier_sharp_right)
            "straight" -> string(R.string.route_modifier_straight)
            "uturn" -> string(R.string.route_modifier_uturn)
            else -> modifier.orEmpty()
        }
    }

    private fun candidateName(item: JSONObject, fallback: String, kind: PlaceKind): String {
        val explicitName = item.optString("name").ifBlank {
            item.optJSONObject("namedetails")
                ?.optString("name")
                .orEmpty()
        }
        val baseName = explicitName.ifBlank {
            val firstPart = fallback.substringBefore(",").trim()
            if (AddressFormattingCore.isLikelyHouseNumber(firstPart)) {
                fallback.split(",")
                    .map { it.trim() }
                    .firstOrNull { it.isNotBlank() && !AddressFormattingCore.isLikelyHouseNumber(it) }
                    ?: fallback
            } else {
                firstPart.ifBlank { fallback }
            }
        }
        return labelForKind(baseName, kind)
    }

    private fun putSearchCandidate(
        combined: MutableMap<String, SearchCandidate>,
        candidate: SearchCandidate,
    ) {
        val existing = combined[candidate.place.id]
        if (existing == null || isBetterSearchCandidate(candidate, existing)) {
            combined[candidate.place.id] = candidate
        }
    }

    private fun nearbyTextSearchRadiiKm(
        normalizedSearchRadiusKm: Int,
        currentPoint: GeoPoint?,
        intent: SearchIntent,
    ): List<Int> {
        if (currentPoint == null || intent.isCategoryOnly) return listOf(normalizedSearchRadiusKm)
        return listOf(SharedProductRules.Search.minimumRadiusKm, normalizedSearchRadiusKm).distinct()
    }

    private fun deduplicatedSearchCandidates(candidates: Collection<SearchCandidate>): List<SearchCandidate> {
        val unique = mutableListOf<SearchCandidate>()
        candidates.sortedWith(::compareSearchCandidates).forEach { candidate ->
            val duplicateIndex = unique.indexOfFirst { existing ->
                areDuplicateSearchPlaces(candidate.place, existing.place)
            }
            if (duplicateIndex == -1) {
                unique += candidate
            } else if (isBetterSearchCandidate(candidate, unique[duplicateIndex])) {
                unique[duplicateIndex] = candidate
            }
        }
        return unique
    }

    private fun areDuplicateSearchPlaces(left: Place, right: Place): Boolean {
        if (normalizeForSearch(left.name) != normalizeForSearch(right.name)) return false
        val distanceBetweenPlaces = when {
            left.point != null && right.point != null -> haversineMeters(left.point, right.point).roundToInt()
            else -> kotlin.math.abs(left.walkDistanceMeters - right.walkDistanceMeters)
        }
        if (distanceBetweenPlaces > 35) return false
        val leftAddress = normalizeForSearch(left.address)
        val rightAddress = normalizeForSearch(right.address)
        return leftAddress.isBlank() || rightAddress.isBlank() || leftAddress == rightAddress
    }

    private suspend fun enrichSearchResultAddresses(candidates: List<SearchCandidate>): List<Place> {
        val lookupIds = candidates
            .mapNotNull { candidate ->
                if (needsSearchAddressEnrichment(candidate.place)) {
                    osmLookupIdFromPlaceId(candidate.place.id)
                } else {
                    null
                }
            }
            .distinct()
        val lookedUpAddresses = if (lookupIds.isEmpty()) {
            emptyMap()
        } else {
            lookupAddressesByOsmIds(lookupIds)
        }

        val candidatesAfterLookup = candidates.map { candidate ->
            val lookupId = osmLookupIdFromPlaceId(candidate.place.id)
            val address = lookupId?.let(lookedUpAddresses::get)
            if (!address.isNullOrBlank() && !isUnhelpfulSearchAddress(address, candidate.place.name)) {
                candidate.copy(place = candidate.place.copy(address = address))
            } else {
                candidate
            }
        }

        val needsNearbyAddress = candidatesAfterLookup.filter(::needsNearbyAddressEnrichment)
        if (needsNearbyAddress.isEmpty()) {
            return candidatesAfterLookup.map { it.place }
        }

        val nearbyAddresses = lookupNearbyAddressCandidates(needsNearbyAddress.map { it.place })
        if (nearbyAddresses.isEmpty()) {
            return candidatesAfterLookup.map { it.place }
        }

        return candidatesAfterLookup.map { candidate ->
            val nearbyAddress = nearestNearbyAddress(candidate.place, nearbyAddresses)
            if (nearbyAddress != null) {
                candidate.place.copy(address = nearbyAddress.address)
            } else {
                candidate.place
            }
        }
    }

    private fun needsSearchAddressEnrichment(place: Place): Boolean {
        return isUnhelpfulSearchAddress(place.address, place.name) || !hasPreciseStreetNumber(place.address)
    }

    private fun needsNearbyAddressEnrichment(candidate: SearchCandidate): Boolean {
        return candidate.kind == PlaceKind.Shop &&
            candidate.place.point != null &&
            needsSearchAddressEnrichment(candidate.place)
    }

    private suspend fun lookupNearbyAddressCandidates(places: List<Place>): List<NearbyAddressCandidate> {
        val query = buildNearbyAddressLookupQuery(places.mapNotNull { it.point }) ?: return emptyList()
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter?data=$encodedQuery",
            "https://overpass.kumi.systems/api/interpreter?data=$encodedQuery",
        )
        for (endpoint in endpoints) {
            val response = requestTextOrNull(endpoint, timeoutMs = ADDRESS_LOOKUP_TIMEOUT_MS) ?: continue
            val parsed = parseNearbyAddressCandidates(response)
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    private fun buildNearbyAddressLookupQuery(points: List<GeoPoint>): String? {
        val uniquePoints = points.distinct()
        if (uniquePoints.isEmpty()) return null
        return buildString {
            append("[out:json][timeout:")
            append(SharedProductRules.Search.overpassTimeoutSeconds)
            append("];")
            append("(")
            uniquePoints.forEach { point ->
                val lat = formatCoordinate(point.latitude)
                val lon = formatCoordinate(point.longitude)
                append("node(around:")
                append(NEARBY_ADDRESS_LOOKUP_RADIUS_METERS)
                append(",$lat,$lon)[\"addr:housenumber\"];")
                append("way(around:")
                append(NEARBY_ADDRESS_LOOKUP_RADIUS_METERS)
                append(",$lat,$lon)[\"addr:housenumber\"];")
                append("relation(around:")
                append(NEARBY_ADDRESS_LOOKUP_RADIUS_METERS)
                append(",$lat,$lon)[\"addr:housenumber\"];")
            }
            append(");out center ")
            append(NEARBY_ADDRESS_LOOKUP_LIMIT)
            append(";")
        }
    }

    private fun parseNearbyAddressCandidates(response: String): List<NearbyAddressCandidate> {
        val elements = JSONObject(response).optJSONArray("elements") ?: return emptyList()
        val candidates = mutableListOf<NearbyAddressCandidate>()
        for (index in 0 until elements.length()) {
            val item = elements.optJSONObject(index) ?: continue
            val tags = item.optJSONObject("tags")?.toStringMap().orEmpty()
            val point = overpassPoint(item) ?: continue
            val address = overpassAddress(tags, fallback = "")
            if (address.isBlank() || !hasPreciseStreetNumber(address)) continue
            candidates += NearbyAddressCandidate(address = address, point = point)
        }
        return candidates
    }

    private fun nearestNearbyAddress(place: Place, addresses: List<NearbyAddressCandidate>): NearbyAddressCandidate? {
        val point = place.point ?: return null
        val nearest = addresses.minByOrNull { candidate ->
            haversineMeters(point, candidate.point)
        } ?: return null
        val distance = haversineMeters(point, nearest.point)
        return nearest.takeIf { distance <= NEARBY_ADDRESS_LOOKUP_RADIUS_METERS }
    }

    private suspend fun lookupAddressesByOsmIds(lookupIds: List<String>): Map<String, String> {
        val encodedIds = URLEncoder.encode(lookupIds.joinToString(","), Charsets.UTF_8.name())
        val endpoint = "https://nominatim.openstreetmap.org/lookup" +
            "?format=jsonv2&addressdetails=1&namedetails=1&osm_ids=$encodedIds"
        val response = requestTextOrNull(endpoint, timeoutMs = ADDRESS_LOOKUP_TIMEOUT_MS) ?: return emptyMap()
        val array = JSONArray(response)
        val addresses = linkedMapOf<String, String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val lookupId = osmLookupIdFromNominatim(item) ?: continue
            val displayName = item.optString("display_name")
            val address = formatAddress(item.optJSONObject("address"), displayName)
            if (address.isNotBlank()) {
                addresses[lookupId] = address
            }
        }
        return addresses
    }

    private fun osmLookupIdFromPlaceId(placeId: String): String? {
        if (!placeId.startsWith("overpass_")) return null
        val parts = placeId.removePrefix("overpass_").split('_')
        if (parts.size != 2) return null
        val prefix = when (parts[0]) {
            "node" -> "N"
            "way" -> "W"
            "relation" -> "R"
            else -> return null
        }
        val numericId = parts[1].toLongOrNull() ?: return null
        return "$prefix$numericId"
    }

    private fun osmLookupIdFromNominatim(item: JSONObject): String? {
        if (!item.has("osm_id")) return null
        val prefix = when (item.optString("osm_type").lowercase(Locale.US)) {
            "node" -> "N"
            "way" -> "W"
            "relation" -> "R"
            else -> return null
        }
        return "$prefix${item.optLong("osm_id")}"
    }

    private fun isUnhelpfulSearchAddress(address: String, placeName: String): Boolean {
        val normalizedAddress = normalizeForSearch(address)
        if (normalizedAddress.isBlank()) return true
        val normalizedName = normalizeForSearch(placeName)
        val normalizedNameTail = normalizeForSearch(placeName.substringAfter(':').trim())
        return normalizedAddress == normalizedName ||
            normalizedAddress == normalizedNameTail
    }

    private fun hasPreciseStreetNumber(address: String): Boolean {
        val streetLine = address.substringBefore(',').trim()
        if (streetLine.isBlank()) return false
        return streetLine.split(Regex("\\s+|/"))
            .map { it.trim('.', ',', ';', ':', '(', ')') }
            .any(SharedProductRules.Address.houseNumberPattern::matches)
    }

    private suspend fun queryOfficialZabkaCandidates(
        query: String,
        currentPoint: GeoPoint,
        searchRadiusMeters: Int,
    ): List<SearchCandidate> {
        val response = requestTextOrNull(ZABKA_LOCATOR_URL, timeoutMs = CHAIN_LOCATOR_TIMEOUT_MS) ?: return emptyList()
        val stores = JSONArray(response)
        val candidates = mutableListOf<SearchCandidate>()
        for (index in 0 until stores.length()) {
            val store = stores.optJSONObject(index) ?: continue
            val point = GeoPoint(
                latitude = store.optDouble("lat", Double.NaN),
                longitude = store.optDouble("lon", Double.NaN),
            )
            if (point.latitude.isNaN() || point.longitude.isNaN()) continue
            val distance = haversineMeters(currentPoint, point).roundToInt()
            if (distance > searchRadiusMeters) continue
            val street = store.optString("street")
            val town = store.optString("town")
            val address = officialChainAddress(street, town).ifBlank { street.ifBlank { town } }
            val place = Place(
                id = "zabka_official_${store.optString("storeId").ifBlank { index.toString() }}",
                name = "\u017Babka",
                address = address,
                walkDistanceMeters = distance,
                walkEtaMinutes = if (distance > 0) {
                    NavigationScenarioCore.distanceBasedEtaMinutes(distance)
                } else {
                    0
                },
                point = point,
                website = store.optString("storeUrl").takeIf { it.isNotBlank() },
            )
            candidates += SearchCandidate(
                place = place,
                score = searchScore(place, query, currentPoint) + OFFICIAL_CHAIN_SCORE,
                distanceMeters = distance,
                importance = 1.0,
                isNearbyCandidate = true,
                kind = PlaceKind.Shop,
            )
        }
        return candidates.sortedWith(
            compareBy<SearchCandidate> { sortableDistance(it.distanceMeters) }
                .thenBy { it.place.address },
        )
    }

    private fun officialChainAddress(street: String, town: String): String {
        val cleanedStreet = titleCaseAddressPart(
            street.trim().replace(Regex("^ul\\.\\s*", RegexOption.IGNORE_CASE), ""),
        )
        val cleanedTown = titleCaseAddressPart(town.trim())
        return listOf(cleanedStreet, cleanedTown)
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }

    private fun titleCaseAddressPart(value: String): String {
        if (value.isBlank()) return value
        val polish = Locale.forLanguageTag("pl-PL")
        return value.split(Regex("\\s+"))
            .joinToString(" ") { token ->
                val lowerToken = token.lowercase(polish)
                when {
                    lowerToken in setOf("lok.", "nr", "nr.", "r.", "ul.", "al.", "pl.") -> lowerToken
                    token.none { it.isLetter() } -> token
                    else -> lowerToken.replaceFirstChar { first ->
                        if (first.isLowerCase()) first.titlecase(polish) else first.toString()
                    }
                }
            }
    }

    private suspend fun queryLocalPoiCandidates(
        query: String,
        currentPoint: GeoPoint,
        searchRadiusMeters: Int,
        intent: SearchIntent,
        resultLimit: Int,
    ): List<SearchCandidate> {
        val endpoints = buildOverpassEndpoints(currentPoint, intent, searchRadiusMeters)
        if (endpoints.isEmpty()) return emptyList()
        val candidates = linkedMapOf<String, SearchCandidate>()
        val startedAtMs = System.currentTimeMillis()
        for (endpoint in endpoints) {
            if (System.currentTimeMillis() - startedAtMs >= LOCAL_POI_TOTAL_TIMEOUT_MS) break
            val response = requestTextOrNull(endpoint, timeoutMs = LOCAL_POI_REQUEST_TIMEOUT_MS) ?: continue
        val elements = JSONObject(response).optJSONArray("elements") ?: continue
            for (index in 0 until elements.length()) {
                val item = elements.optJSONObject(index) ?: continue
                val tags = item.optJSONObject("tags")?.toStringMap().orEmpty()
                val point = overpassPoint(item) ?: continue
                val distance = haversineMeters(currentPoint, point).roundToInt()
                if (distance > searchRadiusMeters) continue
                val kind = overpassKind(tags)
                if (!shouldKeepLocalPoi(tags, kind, intent)) continue
                val name = overpassName(tags, kind)
                val address = overpassAddress(tags, name)
                val place = Place(
                    id = "overpass_${item.optString("type")}_${item.optLong("id")}",
                    name = labelForKind(name, kind),
                    address = address,
                    walkDistanceMeters = distance,
                    walkEtaMinutes = if (distance > 0) {
                        NavigationScenarioCore.distanceBasedEtaMinutes(distance)
                    } else {
                        0
                    },
                    point = point,
                    phone = tags["phone"] ?: tags["contact:phone"],
                    website = tags["website"] ?: tags["contact:website"],
                )
                val candidate = SearchCandidate(
                    place = place,
                    score = searchScore(place, query, currentPoint) +
                        SharedProductRules.Search.localPoiScore +
                        categoryAffinityScore(intent, kind),
                    distanceMeters = distance,
                    importance = 0.0,
                    isNearbyCandidate = true,
                    kind = kind,
                )
                val existing = candidates[place.id]
                if (existing == null || isBetterSearchCandidate(candidate, existing)) {
                    candidates[place.id] = candidate
                }
            }
            if (candidates.size >= resultLimit) break
        }
        return candidates.values
            .sortedWith(
                compareByDescending<SearchCandidate> { it.score }
                    .thenBy { if (it.distanceMeters > 0) it.distanceMeters else Int.MAX_VALUE }
                    .thenByDescending { it.importance },
            )
            .toList()
    }

    private suspend fun queryCachedPoiCandidates(
        query: String,
        currentPoint: GeoPoint,
        searchRadiusMeters: Int,
        intent: SearchIntent,
        resultLimit: Int,
    ): List<SearchCandidate> {
        return poiCacheStore.loadRecords()
            .mapNotNull { record ->
                val distance = haversineMeters(currentPoint, record.point).roundToInt()
                if (distance > searchRadiusMeters) return@mapNotNull null
                val kind = kindFromCacheKind(record.kind)
                if (!cachedPoiMatches(record, kind, intent)) return@mapNotNull null
                val place = Place(
                    id = record.id,
                    name = labelForKind(record.name, kind),
                    address = record.address,
                    walkDistanceMeters = distance,
                    walkEtaMinutes = if (distance > 0) {
                        NavigationScenarioCore.distanceBasedEtaMinutes(distance)
                    } else {
                        0
                    },
                    point = record.point,
                    phone = record.phone,
                    website = record.website,
                )
                SearchCandidate(
                    place = place,
                    score = searchScore(place, query, currentPoint) +
                        SharedProductRules.Search.localPoiScore +
                        categoryAffinityScore(intent, kind),
                    distanceMeters = distance,
                    importance = 0.0,
                    isNearbyCandidate = true,
                    kind = kind,
                )
            }
            .sortedWith(
                compareByDescending<SearchCandidate> { it.score }
                    .thenBy { if (it.distanceMeters > 0) it.distanceMeters else Int.MAX_VALUE }
                    .thenByDescending { it.importance },
            )
            .take(resultLimit)
    }

    private suspend fun queryNearbyPoiCacheRecords(
        currentPoint: GeoPoint,
        radiusMeters: Int,
        fetchedAtMs: Long,
    ): List<NearbyPoiCacheRecord> {
        val records = linkedMapOf<String, NearbyPoiCacheRecord>()
        for (endpoint in buildPoiCacheRefreshEndpoints(currentPoint, radiusMeters)) {
            val response = requestTextOrNull(endpoint, timeoutMs = POI_CACHE_REFRESH_REQUEST_TIMEOUT_MS) ?: continue
            val elements = JSONObject(response).optJSONArray("elements") ?: continue
            for (index in 0 until elements.length()) {
                val item = elements.optJSONObject(index) ?: continue
                val record = nearbyPoiCacheRecord(item, fetchedAtMs) ?: continue
                records[record.id] = record
            }
        }
        return records.values.toList()
    }

    private fun buildPoiCacheRefreshEndpoints(currentPoint: GeoPoint, radiusMeters: Int): List<String> {
        val lat = formatCoordinate(currentPoint.latitude)
        val lon = formatCoordinate(currentPoint.longitude)
        val nodeSelectors = poiCacheRefreshFilters().map { filter ->
            "node(around:$radiusMeters,$lat,$lon)$filter;"
        }
        val areaSelectors = poiCacheRefreshFilters().flatMap { filter ->
            listOf(
                "way(around:$radiusMeters,$lat,$lon)$filter;",
                "relation(around:$radiusMeters,$lat,$lon)$filter;",
            )
        }
        return listOf(nodeSelectors, areaSelectors).flatMap { selectors ->
            val encodedQuery = URLEncoder.encode(poiCacheRefreshQuery(selectors), Charsets.UTF_8.name())
            listOf(
                "https://overpass-api.de/api/interpreter?data=$encodedQuery",
                "https://overpass.kumi.systems/api/interpreter?data=$encodedQuery",
            )
        }
    }

    private fun poiCacheRefreshFilters(): List<String> {
        return listOf(
            "[\"shop\"]",
            "[\"amenity\"~\"^(parcel_locker|pharmacy|bank|atm|fuel|post_office|cafe|restaurant|fast_food|toilets)$\"]",
            "[\"railway\"~\"^(station|halt|tram_stop)$\"]",
            "[\"highway\"=\"bus_stop\"]",
            "[\"public_transport\"~\"^(platform|stop_position|station)$\"]",
        )
    }

    private fun poiCacheRefreshQuery(selectors: List<String>): String {
        return buildString {
            append("[out:json][timeout:")
            append(SharedProductRules.Search.overpassTimeoutSeconds)
            append("];")
            append("(")
            selectors.forEach(::append)
            append(");out center ")
            append(POI_CACHE_REFRESH_LIMIT)
            append(";")
        }
    }

    private fun nearbyPoiCacheRecord(item: JSONObject, fetchedAtMs: Long): NearbyPoiCacheRecord? {
        val tags = item.optJSONObject("tags")?.toStringMap().orEmpty()
        val point = overpassPoint(item) ?: return null
        val kind = overpassKind(tags)
        if (kind == PlaceKind.Other) return null
        val name = overpassName(tags, kind).trim()
        if (name.isBlank()) return null
        val address = overpassAddress(tags, name)
        val searchableText = normalizeForSearch(
            (
                searchableNameParts(tags) +
                    listOf(
                        name,
                        address,
                        tags["shop"].orEmpty(),
                        tags["amenity"].orEmpty(),
                        tags["railway"].orEmpty(),
                        tags["public_transport"].orEmpty(),
                        tags["highway"].orEmpty(),
                        tags["bus"].orEmpty(),
                        tags["tram"].orEmpty(),
                        tags["ref"].orEmpty(),
                        tags["local_ref"].orEmpty(),
                        tags["network"].orEmpty(),
                    )
                ).joinToString(" "),
        )
        return NearbyPoiCacheRecord(
            id = "overpass_${item.optString("type")}_${item.optLong("id")}",
            name = name,
            address = address,
            latitude = point.latitude,
            longitude = point.longitude,
            phone = tags["phone"] ?: tags["contact:phone"],
            website = tags["website"] ?: tags["contact:website"],
            kind = cacheKind(kind),
            searchableText = searchableText,
            fetchedAtMs = fetchedAtMs,
        )
    }

    private fun cachedPoiMatches(
        record: NearbyPoiCacheRecord,
        kind: PlaceKind,
        intent: SearchIntent,
    ): Boolean {
        if (intent.isCategoryOnly) {
            return when {
                intent.wantsShop -> kind == PlaceKind.Shop
                intent.wantsParcelLocker -> kind == PlaceKind.ParcelLocker
                intent.wantsRailStation -> kind == PlaceKind.RailStation
                intent.wantsTransitStop -> kind == PlaceKind.BusStop || kind == PlaceKind.TramStop
                else -> false
            }
        }
        val terms = intent.nameSearchTerms.ifEmpty { intent.tokens }
        if (terms.isEmpty()) return true
        return terms.all { term -> record.searchableText.contains(term) }
    }

    private fun cacheKind(kind: PlaceKind): String {
        return when (kind) {
            PlaceKind.Shop -> "shop"
            PlaceKind.ParcelLocker -> "parcel_locker"
            PlaceKind.RailStation -> "rail_station"
            PlaceKind.BusStop -> "bus_stop"
            PlaceKind.TramStop -> "tram_stop"
            PlaceKind.Other -> "other"
        }
    }

    private fun kindFromCacheKind(kind: String): PlaceKind {
        return when (kind) {
            "shop" -> PlaceKind.Shop
            "parcel_locker" -> PlaceKind.ParcelLocker
            "rail_station" -> PlaceKind.RailStation
            "bus_stop" -> PlaceKind.BusStop
            "tram_stop" -> PlaceKind.TramStop
            else -> PlaceKind.Other
        }
    }

    private suspend fun requestTextOrNull(rawUrl: String, timeoutMs: Int): String? = coroutineScope {
        val request = async(Dispatchers.IO) {
            runCatching { requestText(rawUrl, timeoutMs = timeoutMs) }.getOrNull()
        }
        withTimeoutOrNull(timeoutMs.toLong()) { request.await() } ?: run {
            request.cancel()
            null
        }
    }

    private fun buildOverpassEndpoints(
        currentPoint: GeoPoint,
        intent: SearchIntent,
        searchRadiusMeters: Int,
    ): List<String> {
        val selectorGroups = mutableListOf<List<String>>()
        val lat = formatCoordinate(currentPoint.latitude)
        val lon = formatCoordinate(currentPoint.longitude)
        val nameRegex = overpassNameRegex(intent)

        overpassSearchRadii(searchRadiusMeters).forEach { radius ->
            val priorityNodeSelectors = mutableListOf<String>()
            val priorityAreaSelectors = mutableListOf<String>()
            val secondaryNodeSelectors = mutableListOf<String>()
            val secondaryAreaSelectors = mutableListOf<String>()
            if (nameRegex != null) {
                priorityNodeSelectors += overpassNameSelectors(
                    radius = radius,
                    lat = lat,
                    lon = lon,
                    baseFilter = "[\"shop\"]",
                    nameRegex = nameRegex,
                    keys = listOf("name"),
                    includeAreas = false,
                )
                priorityAreaSelectors += overpassNameSelectors(
                    radius = radius,
                    lat = lat,
                    lon = lon,
                    baseFilter = "[\"shop\"]",
                    nameRegex = nameRegex,
                    keys = listOf("name"),
                    includeAreas = true,
                )
                secondaryNodeSelectors += overpassNameSelectors(
                    radius = radius,
                    lat = lat,
                    lon = lon,
                    baseFilter = "[\"shop\"]",
                    nameRegex = nameRegex,
                    keys = listOf("brand", "operator", "official_name", "alt_name"),
                    includeAreas = false,
                )
                secondaryAreaSelectors += overpassNameSelectors(
                    radius = radius,
                    lat = lat,
                    lon = lon,
                    baseFilter = "[\"shop\"]",
                    nameRegex = nameRegex,
                    keys = listOf("brand", "operator", "official_name", "alt_name"),
                    includeAreas = true,
                )
                secondaryNodeSelectors += overpassNameSelectors(
                    radius = radius,
                    lat = lat,
                    lon = lon,
                    baseFilter = "[\"amenity\"]",
                    nameRegex = nameRegex,
                    keys = listOf("name", "brand", "operator", "official_name", "alt_name"),
                    includeAreas = false,
                )
                secondaryAreaSelectors += overpassNameSelectors(
                    radius = radius,
                    lat = lat,
                    lon = lon,
                    baseFilter = "[\"amenity\"]",
                    nameRegex = nameRegex,
                    keys = listOf("name", "brand", "operator", "official_name", "alt_name"),
                    includeAreas = true,
                )
                secondaryNodeSelectors += overpassNameSelectors(
                    radius = radius,
                    lat = lat,
                    lon = lon,
                    baseFilter = "[\"tourism\"]",
                    nameRegex = nameRegex,
                    keys = listOf("name", "brand", "operator", "official_name", "alt_name"),
                    includeAreas = false,
                )
                secondaryAreaSelectors += overpassNameSelectors(
                    radius = radius,
                    lat = lat,
                    lon = lon,
                    baseFilter = "[\"tourism\"]",
                    nameRegex = nameRegex,
                    keys = listOf("name", "brand", "operator", "official_name", "alt_name"),
                    includeAreas = true,
                )
                secondaryNodeSelectors += overpassNameSelectors(
                    radius = radius,
                    lat = lat,
                    lon = lon,
                    baseFilter = "[\"leisure\"]",
                    nameRegex = nameRegex,
                    keys = listOf("name", "brand", "operator", "official_name", "alt_name"),
                    includeAreas = false,
                )
                secondaryAreaSelectors += overpassNameSelectors(
                    radius = radius,
                    lat = lat,
                    lon = lon,
                    baseFilter = "[\"leisure\"]",
                    nameRegex = nameRegex,
                    keys = listOf("name", "brand", "operator", "official_name", "alt_name"),
                    includeAreas = true,
                )
                secondaryNodeSelectors += overpassNameSelectors(
                    radius = radius,
                    lat = lat,
                    lon = lon,
                    baseFilter = "[\"railway\"]",
                    nameRegex = nameRegex,
                    keys = listOf("name", "brand", "operator", "official_name", "alt_name"),
                    includeAreas = false,
                )
                secondaryAreaSelectors += overpassNameSelectors(
                    radius = radius,
                    lat = lat,
                    lon = lon,
                    baseFilter = "[\"railway\"]",
                    nameRegex = nameRegex,
                    keys = listOf("name", "brand", "operator", "official_name", "alt_name"),
                    includeAreas = true,
                )
                secondaryNodeSelectors += overpassNameSelectors(
                    radius = radius,
                    lat = lat,
                    lon = lon,
                    baseFilter = "[\"public_transport\"]",
                    nameRegex = nameRegex,
                    keys = listOf("name", "brand", "operator", "official_name", "alt_name"),
                    includeAreas = false,
                )
                secondaryAreaSelectors += overpassNameSelectors(
                    radius = radius,
                    lat = lat,
                    lon = lon,
                    baseFilter = "[\"public_transport\"]",
                    nameRegex = nameRegex,
                    keys = listOf("name", "brand", "operator", "official_name", "alt_name"),
                    includeAreas = true,
                )
            }
            if (intent.wantsShop && intent.isCategoryOnly) {
                priorityNodeSelectors += overpassNodeSelectors(radius, lat, lon, "[\"shop\"]")
                priorityAreaSelectors += overpassAreaSelectors(radius, lat, lon, "[\"shop\"]")
            }
            if (intent.wantsParcelLocker) {
                priorityNodeSelectors += overpassNodeSelectors(radius, lat, lon, "[\"amenity\"=\"parcel_locker\"]")
                priorityAreaSelectors += overpassAreaSelectors(radius, lat, lon, "[\"amenity\"=\"parcel_locker\"]")
            }
            if (intent.wantsRailStation) {
                priorityNodeSelectors += overpassNodeSelectors(radius, lat, lon, "[\"railway\"~\"^(station|halt)$\"]")
                priorityNodeSelectors += overpassNodeSelectors(radius, lat, lon, "[\"public_transport\"=\"station\"]")
                priorityAreaSelectors += overpassAreaSelectors(radius, lat, lon, "[\"railway\"~\"^(station|halt)$\"]")
                priorityAreaSelectors += overpassAreaSelectors(radius, lat, lon, "[\"public_transport\"=\"station\"]")
            }
            if (intent.wantsTransitStop) {
                priorityNodeSelectors += overpassNodeSelectors(radius, lat, lon, "[\"highway\"=\"bus_stop\"]")
                priorityNodeSelectors += overpassNodeSelectors(radius, lat, lon, "[\"railway\"=\"tram_stop\"]")
                priorityNodeSelectors += overpassNodeSelectors(radius, lat, lon, "[\"public_transport\"=\"platform\"][\"bus\"=\"yes\"]")
                priorityNodeSelectors += overpassNodeSelectors(radius, lat, lon, "[\"public_transport\"=\"platform\"][\"tram\"=\"yes\"]")
                priorityNodeSelectors += overpassNodeSelectors(radius, lat, lon, "[\"public_transport\"=\"stop_position\"][\"bus\"=\"yes\"]")
                priorityNodeSelectors += overpassNodeSelectors(radius, lat, lon, "[\"public_transport\"=\"stop_position\"][\"tram\"=\"yes\"]")
                priorityAreaSelectors += overpassAreaSelectors(radius, lat, lon, "[\"public_transport\"=\"platform\"][\"bus\"=\"yes\"]")
                priorityAreaSelectors += overpassAreaSelectors(radius, lat, lon, "[\"public_transport\"=\"platform\"][\"tram\"=\"yes\"]")
            }

            if (priorityNodeSelectors.isNotEmpty()) {
                selectorGroups += priorityNodeSelectors.distinct()
            }
            if (priorityAreaSelectors.isNotEmpty()) {
                selectorGroups += priorityAreaSelectors.distinct()
            }
            if (secondaryNodeSelectors.isNotEmpty()) {
                selectorGroups += secondaryNodeSelectors.distinct()
            }
            if (secondaryAreaSelectors.isNotEmpty()) {
                selectorGroups += secondaryAreaSelectors.distinct()
            }
        }
        return selectorGroups.distinctBy { it.joinToString(separator = "") }.flatMap { selectors ->
            val encodedQuery = URLEncoder.encode(overpassQuery(selectors), Charsets.UTF_8.name())
            listOf(
                "https://overpass-api.de/api/interpreter?data=$encodedQuery",
                "https://overpass.kumi.systems/api/interpreter?data=$encodedQuery",
            )
        }
    }

    private fun overpassSearchRadii(maxRadiusMeters: Int): List<Int> {
        val maxRadius = maxRadiusMeters.coerceAtLeast(1)
        return listOf(500, 1_000, maxRadius)
            .map { it.coerceAtMost(maxRadius) }
            .filter { it > 0 }
            .distinct()
    }

    private fun overpassNameSelectors(
        radius: Int,
        lat: String,
        lon: String,
        baseFilter: String,
        nameRegex: String,
        keys: List<String>,
        includeAreas: Boolean,
    ): List<String> {
        return keys.flatMap { key ->
                val filter = "$baseFilter[\"$key\"~\"$nameRegex\",i]"
                if (includeAreas) {
                    overpassAreaSelectors(radius, lat, lon, filter)
                } else {
                    overpassNodeSelectors(radius, lat, lon, filter)
                }
            }
    }

    private fun overpassQuery(selectors: List<String>): String {
        return buildString {
            append("[out:json][timeout:")
            append(SharedProductRules.Search.overpassTimeoutSeconds)
            append("];")
            append("(")
            selectors.forEach(::append)
            append(");out center ")
            append(SharedProductRules.Search.localPoiLimit)
            append(";")
        }
    }

    private fun overpassNodeSelectors(radius: Int, lat: String, lon: String, filter: String): List<String> {
        val around = "(around:$radius,$lat,$lon)"
        return listOf(
            "node$around$filter;",
        )
    }

    private fun overpassAreaSelectors(radius: Int, lat: String, lon: String, filter: String): List<String> {
        val around = "(around:$radius,$lat,$lon)"
        return listOf(
            "way$around$filter;",
            "relation$around$filter;",
        )
    }

    private fun overpassNameRegex(intent: SearchIntent): String? {
        if (intent.isCategoryOnly) return null
        val terms = intent.nameSearchTerms.ifEmpty { intent.tokens }
            .filter { it.length >= 2 }
            .take(4)
        if (terms.isEmpty()) return null
        return terms.joinToString(".*") { overpassRegexTerm(it) }
    }

    private fun overpassRegexTerm(term: String): String {
        return when (term) {
            "zabka" -> "[zż]abka"
            else -> Regex.escape(term)
        }
    }

    private fun overpassPoint(item: JSONObject): GeoPoint? {
        val latitude = item.optDouble("lat", Double.NaN)
        val longitude = item.optDouble("lon", Double.NaN)
        if (!latitude.isNaN() && !longitude.isNaN()) {
            return GeoPoint(latitude, longitude)
        }
        val center = item.optJSONObject("center") ?: return null
        val centerLatitude = center.optDouble("lat", Double.NaN)
        val centerLongitude = center.optDouble("lon", Double.NaN)
        return if (!centerLatitude.isNaN() && !centerLongitude.isNaN()) {
            GeoPoint(centerLatitude, centerLongitude)
        } else {
            null
        }
    }

    private fun overpassName(tags: Map<String, String>, kind: PlaceKind): String {
        val name = searchableNameParts(tags)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        if (name.isNotBlank()) return name
        return when (kind) {
            PlaceKind.Shop -> string(R.string.search_type_unnamed_shop)
            PlaceKind.ParcelLocker -> string(R.string.search_type_unnamed_parcel_locker)
            PlaceKind.RailStation -> string(R.string.search_type_unnamed_rail_station)
            PlaceKind.BusStop -> string(R.string.search_type_unnamed_bus_stop)
            PlaceKind.TramStop -> string(R.string.search_type_unnamed_tram_stop)
            PlaceKind.Other -> string(R.string.generic_unknown_place)
        }
    }

    private fun overpassAddress(tags: Map<String, String>, fallback: String): String {
        val street = tags["addr:street"].orEmpty()
        val houseNumber = tags["addr:housenumber"].orEmpty()
        val unit = tags["addr:unit"] ?: tags["addr:door"] ?: tags["addr:flats"]
        val locality = tags["addr:city"] ?: tags["addr:town"] ?: tags["addr:village"] ?: tags["addr:suburb"]
        val houseNumberWithUnit = when {
            houseNumber.isNotBlank() && !unit.isNullOrBlank() -> "$houseNumber/${unit.trim()}"
            else -> houseNumber
        }
        val streetPart = when {
            street.isNotBlank() && houseNumberWithUnit.isNotBlank() -> "$street $houseNumberWithUnit"
            street.isNotBlank() -> street
            houseNumberWithUnit.isNotBlank() -> houseNumberWithUnit
            else -> ""
        }
        return listOf(streetPart, locality.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { fallback }
    }

    private fun shouldKeepLocalPoi(tags: Map<String, String>, kind: PlaceKind, intent: SearchIntent): Boolean {
        if (intent.wantsShop && kind == PlaceKind.Shop) return true
        if (intent.wantsParcelLocker && kind == PlaceKind.ParcelLocker) return true
        if (intent.wantsRailStation && (kind == PlaceKind.RailStation || kind == PlaceKind.BusStop || kind == PlaceKind.TramStop)) {
            return true
        }
        val normalizedName = normalizeForSearch(searchableNameParts(tags).joinToString(" "))
        if (intent.wantsTransitStop && (kind == PlaceKind.BusStop || kind == PlaceKind.TramStop)) {
            return intent.nameSearchTerms.isEmpty() || intent.nameSearchTerms.all { normalizedName.contains(it) }
        }
        return intent.nameSearchTerms.all { normalizedName.contains(it) }
    }

    private fun searchableNameParts(tags: Map<String, String>): List<String> {
        return listOf(
            tags["name"],
            tags["brand"],
            tags["operator"],
            tags["official_name"],
            tags["alt_name"],
            tags["short_name"],
            tags["ref"],
            tags["local_ref"],
            tags["network"],
        ).map { it?.trim().orEmpty() }
    }

    private fun overpassKind(tags: Map<String, String>): PlaceKind {
        val shop = tags["shop"].orEmpty()
        val amenity = tags["amenity"].orEmpty()
        val railway = tags["railway"].orEmpty()
        val publicTransport = tags["public_transport"].orEmpty()
        return when {
            shop.isNotBlank() -> PlaceKind.Shop
            amenity == "parcel_locker" -> PlaceKind.ParcelLocker
            railway == "station" || railway == "halt" -> PlaceKind.RailStation
            tags["station"] == "railway" || tags["train"] == "yes" && publicTransport == "station" -> PlaceKind.RailStation
            tags["highway"] == "bus_stop" || tags["bus"] == "yes" && (publicTransport == "platform" || publicTransport == "stop_position") -> PlaceKind.BusStop
            railway == "tram_stop" || tags["tram"] == "yes" && (publicTransport == "platform" || publicTransport == "stop_position") -> PlaceKind.TramStop
            else -> PlaceKind.Other
        }
    }

    private fun querySearchCandidates(
        query: String,
        currentPoint: GeoPoint?,
        nearbyOnly: Boolean,
        searchRadiusKm: Int,
        intent: SearchIntent,
        resultLimit: Int,
    ): List<SearchCandidate> {
        val endpoint = buildSearchEndpoint(
            query = query,
            currentPoint = currentPoint,
            nearbyOnly = nearbyOnly,
            searchRadiusKm = searchRadiusKm,
            resultLimit = resultLimit,
        )
        val response = requestText(endpoint)
        val array = JSONArray(response)
        val candidates = mutableListOf<SearchCandidate>()
        val maxDistanceMeters = currentPoint?.let { searchRadiusKm * 1_000 }
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val latitude = item.optString("lat").toDoubleOrNull() ?: continue
            val longitude = item.optString("lon").toDoubleOrNull() ?: continue
            val displayName = item.optString("display_name").ifBlank { string(R.string.generic_unknown_place) }
            val rawAddress = formatAddress(item.optJSONObject("address"), displayName)
            val kind = nominatimKind(item)
            val label = candidateName(item, displayName, kind)
            val address = removeDuplicatedAddressPrefix(rawAddress, label)
            val point = GeoPoint(latitude, longitude)
            val distance = if (currentPoint == null) {
                0
            } else {
                haversineMeters(currentPoint, point).roundToInt()
            }
            if (maxDistanceMeters != null && distance > maxDistanceMeters) {
                continue
            }
            val etaMinutes = if (distance <= 0) {
                0
            } else {
                NavigationScenarioCore.distanceBasedEtaMinutes(distance)
            }
            val place = Place(
                id = "nominatim_${latitude}_$longitude",
                name = label,
                address = address,
                walkDistanceMeters = distance,
                walkEtaMinutes = etaMinutes,
                point = point,
            )
            candidates += SearchCandidate(
                place = place,
                score = searchScore(place, query, currentPoint) +
                    categoryAffinityScore(intent, kind) +
                    if (nearbyOnly) SharedProductRules.Search.nearbyBonus else 0,
                distanceMeters = distance,
                importance = item.optDouble("importance", 0.0),
                isNearbyCandidate = nearbyOnly,
                kind = kind,
            )
        }
        return candidates
    }

    private fun buildSearchEndpoint(
        query: String,
        currentPoint: GeoPoint?,
        nearbyOnly: Boolean,
        searchRadiusKm: Int,
        resultLimit: Int,
    ): String {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val base = StringBuilder("https://nominatim.openstreetmap.org/search")
        base.append("?format=jsonv2")
        val requestLimit = maxOf(
            if (nearbyOnly) SharedProductRules.Search.nearbyLimit else SharedProductRules.Search.globalLimit,
            resultLimit,
        ).coerceAtMost(SharedProductRules.Search.maximumResultLimit)
        base.append("&limit=").append(requestLimit)
        base.append("&addressdetails=1&namedetails=1&dedupe=1")
        if (currentPoint != null) {
            val radiusKm = searchRadiusKm.toDouble()
            val (left, top, right, bottom) = searchViewBox(currentPoint, radiusKm)
            base.append("&viewbox=")
                .append(formatCoordinate(left))
                .append(',')
                .append(formatCoordinate(top))
                .append(',')
                .append(formatCoordinate(right))
                .append(',')
                .append(formatCoordinate(bottom))
            base.append("&bounded=1")
        }
        base.append("&q=").append(encoded)
        return base.toString()
    }

    private fun searchViewBox(
        center: GeoPoint,
        radiusKm: Double,
    ): DoubleArray {
        val latDelta = radiusKm / 111.32
        val cosLatitude = cos(Math.toRadians(center.latitude)).let {
            if (it < SharedProductRules.Search.viewBoxMinimumCosine) {
                SharedProductRules.Search.viewBoxMinimumCosine
            } else {
                it
            }
        }
        val lonDelta = radiusKm / (111.32 * cosLatitude)
        val left = center.longitude - lonDelta
        val right = center.longitude + lonDelta
        val top = center.latitude + latDelta
        val bottom = center.latitude - latDelta
        return doubleArrayOf(left, top, right, bottom)
    }

    private fun formatCoordinate(value: Double): String {
        return String.format(Locale.US, "%.6f", value)
    }

    private fun formatAddress(address: JSONObject?, fallback: String): String {
        return AddressFormattingCore.formatAddress(address?.toStringMap(), fallback)
    }

    private fun JSONObject.toStringMap(): Map<String, String> {
        val mapped = linkedMapOf<String, String>()
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            mapped[key] = optString(key)
        }
        return mapped
    }

    private fun searchIntent(query: String): SearchIntent {
        val normalized = normalizeForSearch(query)
        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        val wantsShop = tokens.any { it in shopQueryTerms }
        val wantsParcelLocker = tokens.any { it in parcelLockerQueryTerms }
        val wantsRailStation = tokens.any { it in railStationQueryTerms }
        val nameSearchTerms = tokens.filterNot { it in categoryQueryTerms }
        return SearchIntent(
            normalizedQuery = normalized,
            tokens = tokens,
            nameSearchTerms = nameSearchTerms,
            wantsShop = wantsShop,
            wantsParcelLocker = wantsParcelLocker,
            wantsRailStation = wantsRailStation,
            wantsTransitStop = tokens.any { it in transitStopQueryTerms },
        )
    }

    private fun isZabkaQuery(intent: SearchIntent): Boolean {
        return intent.tokens.any { token ->
            token == "zabka" || token == "zabki" || token == "zabke"
        }
    }

    private fun shopCategoryExpansionQueries(): List<String> {
        return listOf("zabka", "biedronka", "lidl", "supermarket", "market")
    }

    private fun parcelLockerCategoryExpansionQueries(): List<String> {
        return listOf("inpost", "orlen paczka", "dpd pickup")
    }

    private fun transitStopCategoryExpansionQueries(): List<String> {
        return listOf("przystanek", "przystanek autobusowy", "przystanek tramwajowy", "bus stop", "tram stop")
    }

    private fun nominatimKind(item: JSONObject): PlaceKind {
        val category = item.optString("category")
        val type = item.optString("type")
        return when {
            category == "shop" -> PlaceKind.Shop
            category == "amenity" && type == "parcel_locker" -> PlaceKind.ParcelLocker
            category == "railway" && (type == "station" || type == "halt") -> PlaceKind.RailStation
            category == "public_transport" && type == "station" -> PlaceKind.RailStation
            category == "highway" && type == "bus_stop" -> PlaceKind.BusStop
            category == "public_transport" && (type == "platform" || type == "stop_position") -> PlaceKind.BusStop
            category == "railway" && type == "tram_stop" -> PlaceKind.TramStop
            else -> PlaceKind.Other
        }
    }

    private fun categoryAffinityScore(intent: SearchIntent, kind: PlaceKind): Int {
        var score = 0
        if (intent.wantsShop && kind == PlaceKind.Shop) {
            score += SharedProductRules.Search.categoryMatchScore
        }
        if (intent.wantsParcelLocker && kind == PlaceKind.ParcelLocker) {
            score += SharedProductRules.Search.categoryMatchScore
        }
        if (intent.wantsTransitStop && (kind == PlaceKind.BusStop || kind == PlaceKind.TramStop)) {
            score += SharedProductRules.Search.categoryMatchScore
        }
        if (intent.wantsRailStation) {
            when (kind) {
                PlaceKind.RailStation -> score += SharedProductRules.Search.railQueryStationScore
                PlaceKind.BusStop -> score -= SharedProductRules.Search.railQueryBusStopPenalty
                PlaceKind.TramStop -> score -= SharedProductRules.Search.railQueryBusStopPenalty / 2
                else -> Unit
            }
        }
        return score
    }

    private fun labelForKind(name: String, kind: PlaceKind): String {
        val trimmed = name.trim()
        return when (kind) {
            PlaceKind.RailStation -> string(R.string.search_type_rail_station, trimmed)
            PlaceKind.BusStop -> string(R.string.search_type_bus_stop, trimmed)
            PlaceKind.TramStop -> string(R.string.search_type_tram_stop, trimmed)
            PlaceKind.ParcelLocker -> {
                if (normalizeForSearch(trimmed).contains("paczkomat") || normalizeForSearch(trimmed).contains("parcel locker")) {
                    trimmed
                } else {
                    string(R.string.search_type_parcel_locker, trimmed)
                }
            }
            else -> trimmed
        }
    }

    private fun removeDuplicatedAddressPrefix(address: String, placeName: String): String {
        val parts = address.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (parts.size <= 1) return address
        val normalizedFirstPart = normalizeForSearch(parts.first())
        val normalizedName = normalizeForSearch(placeName)
        val normalizedNameTail = normalizeForSearch(placeName.substringAfter(':').trim())
        return if (normalizedFirstPart == normalizedName || normalizedFirstPart == normalizedNameTail) {
            parts.drop(1).joinToString(", ")
        } else {
            address
        }
    }

    private fun isHouseNumberSearchToken(token: String): Boolean {
        return token.any { it.isDigit() }
    }

    private fun matchesAddressQueryToken(token: String, normalizedText: String, normalizedTokens: List<String>): Boolean {
        return if (isHouseNumberSearchToken(token)) {
            normalizedTokens.any { it == token }
        } else {
            normalizedText.contains(token)
        }
    }

    private fun searchScore(place: Place, query: String, currentPoint: GeoPoint?): Int {
        val normalizedQuery = normalizeForSearch(query)
        if (normalizedQuery.isBlank()) return 0

        val normalizedName = normalizeForSearch(place.name)
        val normalizedAddress = normalizeForSearch(place.address)
        val queryTokens = normalizedQuery.split(' ').filter { it.isNotBlank() }
        val nameTokens = normalizedName.split(' ').filter { it.isNotBlank() }
        val combinedAddressText = listOf(normalizedName, normalizedAddress)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val combinedAddressTokens = combinedAddressText.split(' ').filter { it.isNotBlank() }
        val exactAddressQuery = queryTokens.any(::isHouseNumberSearchToken)

        var score = 0
        if (exactAddressQuery) {
            if (queryTokens.all { matchesAddressQueryToken(it, combinedAddressText, combinedAddressTokens) }) {
                score += SharedProductRules.Search.exactNameScore + SharedProductRules.Search.nearbyBonus
            } else {
                score -= SharedProductRules.Search.nearbyBonus
            }
        }
        if (normalizedName == normalizedQuery) score += SharedProductRules.Search.exactNameScore
        if (normalizedName.startsWith(normalizedQuery)) {
            score += SharedProductRules.Search.prefixNameScore
        }

        queryTokens.forEach { token ->
            when {
                nameTokens.any { it == token } ->
                    score += SharedProductRules.Search.exactTokenScore
                nameTokens.any { it.startsWith(token) } ->
                    score += SharedProductRules.Search.prefixTokenScore
                normalizedName.contains(token) ->
                    score += SharedProductRules.Search.containsTokenScore
            }
            if (normalizedAddress.contains(token)) {
                score += SharedProductRules.Search.addressTokenScore
            }
        }

        if (currentPoint != null && place.point != null) {
            val distance = haversineMeters(currentPoint, place.point).roundToInt()
            score += SharedProductRules.Search.distanceBands
                .firstOrNull { distance <= it.maxMeters }
                ?.bonus
                ?: 0
            score -= (
                distance / SharedProductRules.Search.distancePenaltyDivisorMeters
                ).coerceAtMost(SharedProductRules.Search.distancePenaltyCap)
        }

        return score
    }

    private fun isBetterSearchCandidate(
        candidate: SearchCandidate,
        existing: SearchCandidate,
    ): Boolean {
        return when {
            candidate.score != existing.score -> candidate.score > existing.score
            candidate.isNearbyCandidate != existing.isNearbyCandidate -> candidate.isNearbyCandidate
            candidate.distanceMeters != existing.distanceMeters -> {
                val candidateDistance = if (candidate.distanceMeters > 0) candidate.distanceMeters else Int.MAX_VALUE
                val existingDistance = if (existing.distanceMeters > 0) existing.distanceMeters else Int.MAX_VALUE
                candidateDistance < existingDistance
            }
            else -> candidate.importance > existing.importance
        }
    }

    private fun compareSearchCandidates(left: SearchCandidate, right: SearchCandidate): Int {
        val leftDistance = sortableDistance(left.distanceMeters)
        val rightDistance = sortableDistance(right.distanceMeters)
        val scoreDifference = left.score - right.score
        if (kotlin.math.abs(scoreDifference) <= SharedProductRules.Search.nearbyBonus && leftDistance != rightDistance) {
            return leftDistance.compareTo(rightDistance)
        }
        if (scoreDifference != 0) return -scoreDifference
        if (left.isNearbyCandidate != right.isNearbyCandidate) return if (left.isNearbyCandidate) -1 else 1
        if (leftDistance != rightDistance) return leftDistance.compareTo(rightDistance)
        return -left.importance.compareTo(right.importance)
    }

    private fun sortableDistance(distanceMeters: Int): Int {
        return if (distanceMeters > 0) distanceMeters else Int.MAX_VALUE
    }

    private fun normalizeForSearch(text: String): String {
        val normalized = Normalizer.normalize(text.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
        return normalized
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^\\p{Alnum}]+".toRegex(), " ")
            .trim()
    }

    private fun string(@StringRes resId: Int, vararg args: Any): String = appContext.getString(resId, *args)

    private fun requestText(rawUrl: String, timeoutMs: Int = 12_000): String {
        val connection = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", "navi-live/0.1 (accessibility-navigation-prototype)")
            setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val payload = stream.bufferedReader().use { it.readText() }
            if (status !in 200..299) {
                throw IllegalStateException("HTTP $status: $payload")
            }
            payload
        } finally {
            connection.disconnect()
        }
    }

    private fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val earthRadius = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lon1 = Math.toRadians(a.longitude)
        val lat2 = Math.toRadians(b.latitude)
        val lon2 = Math.toRadians(b.longitude)
        val dLat = lat2 - lat1
        val dLon = lon2 - lon1
        val h = sin(dLat / 2).pow(2.0) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2.0)
        return 2 * earthRadius * asin(sqrt(h))
    }
}
