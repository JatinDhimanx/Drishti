package org.drishti.app.ui.destination

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drishti.core.database.DestinationRepository
import org.drishti.core.feedback.HapticManager
import org.drishti.core.feedback.SpeechManager
import org.drishti.core.model.Waypoint
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private val PureBlack = Color(0xFF0A0C12)
private val PrimaryOrange = Color(0xFFFF6D00)
private val NavBlue = Color(0xFF007AFE)
private val CardBg = Color(0xF0141722)
private val CardBorderColor = Color(0xFF262E40)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9096A2)
private val SafetyCyan = Color(0xFF00E5FF)
private val SuccessGreen = Color(0xFF00E676)

data class SearchResultItem(
    val name: String,
    val lat: Double,
    val lon: Double,
    val distanceMeters: Int = 0
)

data class TurnStep(
    val type: String,
    val modifier: String,
    val street: String,
    val distanceMeters: Int,
    val instruction: String,
    val location: GeoPoint
)

@SuppressLint("MissingPermission")
@Composable
fun LiveMapRouteSelectorScreen(
    repository: DestinationRepository,
    speechManager: SpeechManager,
    hapticManager: HapticManager,
    onStartRoute: (Waypoint) -> Unit,
    onCancel: () -> Unit,
    onSpeakIntro: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Real device GPS location
    var userLat by remember { mutableDoubleStateOf(28.6139) } // Default Delhi center until GPS resolves
    var userLon by remember { mutableDoubleStateOf(77.2090) }
    var isGpsResolved by remember { mutableStateOf(false) }

    // Selected Pin / Destination
    var selectedLat by remember { mutableDoubleStateOf(28.6139) }
    var selectedLon by remember { mutableDoubleStateOf(77.2090) }
    var destinationName by remember { mutableStateOf("Pinned Location") }
    var hasDestinationSet by remember { mutableStateOf(false) }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Routing and Turn Guidance
    var isRouteCalculating by remember { mutableStateOf(false) }
    var isNavigating by remember { mutableStateOf(false) }
    var routeSteps by remember { mutableStateOf<List<TurnStep>>(emptyList()) }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var totalRouteDistanceMeters by remember { mutableIntStateOf(0) }

    // MapView references
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var userMarker by remember { mutableStateOf<Marker?>(null) }
    var destMarker by remember { mutableStateOf<Marker?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }

    // 1. Initialize osmdroid configuration safely
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = "Drishti-Mobility-Assistant-App"
        onSpeakIntro()
        speechManager.speakGuidance("Map loading... Kisi bhi jagah pin lagayein ya search karein, fir navigation start karein.")

        // Fetch real device GPS location
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        userLat = loc.latitude
                        userLon = loc.longitude
                        isGpsResolved = true
                        mapViewInstance?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                        userMarker?.position = GeoPoint(loc.latitude, loc.longitude)
                        mapViewInstance?.invalidate()
                    }
                }
        } catch (e: Exception) {}
    }

    DisposableEffect(Unit) {
        onDispose {
            mapViewInstance?.onDetach()
        }
    }

    // Function to calculate pedestrian turn-by-turn route via free OSRM Foot Routing API
    fun calculateAndStartRoute(destLat: Double, destLon: Double, destLabel: String) {
        isRouteCalculating = true
        coroutineScope.launch {
            try {
                val startLat = userLat
                val startLon = userLon
                val urlString = "https://router.project-osrm.org/route/v1/foot/$startLon,$startLat;$destLon,$destLat?steps=true&geometries=geojson&overview=full"

                val (points, steps, totalDist) = withContext(Dispatchers.IO) {
                    val url = URL(urlString)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "Drishti-Mobility-Assistant-App")
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    val body = conn.inputStream.bufferedReader().readText()

                    val root = JSONObject(body)
                    val routes = root.getJSONArray("routes")
                    if (routes.length() == 0) return@withContext Triple(emptyList<GeoPoint>(), emptyList<TurnStep>(), 0)

                    val firstRoute = routes.getJSONObject(0)
                    val dist = firstRoute.optDouble("distance", 0.0).toInt()

                    // Extract coordinates
                    val coordsArray = firstRoute.getJSONObject("geometry").getJSONArray("coordinates")
                    val polyPoints = mutableListOf<GeoPoint>()
                    for (i in 0 until coordsArray.length()) {
                        val pt = coordsArray.getJSONArray(i)
                        polyPoints.add(GeoPoint(pt.getDouble(1), pt.getDouble(0)))
                    }

                    // Extract steps
                    val legs = firstRoute.getJSONArray("legs")
                    val parsedSteps = mutableListOf<TurnStep>()
                    if (legs.length() > 0) {
                        val stepsArr = legs.getJSONObject(0).getJSONArray("steps")
                        for (i in 0 until stepsArr.length()) {
                            val st = stepsArr.getJSONObject(i)
                            val street = st.optString("name", "").trim()
                            val stepDist = st.optDouble("distance", 0.0).toInt()
                            val maneuver = st.getJSONObject("maneuver")
                            val type = maneuver.optString("type", "continue")
                            val modifier = maneuver.optString("modifier", "straight")
                            val locArr = maneuver.getJSONArray("location")
                            val stepLoc = GeoPoint(locArr.getDouble(1), locArr.getDouble(0))

                            val textInstruction = createTurnInstruction(type, modifier, street, stepDist)
                            parsedSteps.add(
                                TurnStep(
                                    type = type,
                                    modifier = modifier,
                                    street = street,
                                    distanceMeters = stepDist,
                                    instruction = textInstruction,
                                    location = stepLoc
                                )
                            )
                        }
                    }
                    Triple(polyPoints, parsedSteps, dist)
                }

                isRouteCalculating = false

                if (points.isNotEmpty()) {
                    routeSteps = steps
                    currentStepIndex = 0
                    totalRouteDistanceMeters = totalDist
                    isNavigating = true

                    // Draw route polyline on map
                    mapViewInstance?.let { map ->
                        routePolyline?.let { map.overlays.remove(it) }
                        val poly = Polyline(map).apply {
                            outlinePaint.color = AndroidColor.parseColor("#00E5FF")
                            outlinePaint.strokeWidth = 14f
                            setPoints(points)
                        }
                        routePolyline = poly
                        map.overlays.add(poly)
                        map.controller.animateTo(GeoPoint(startLat, startLon))
                        map.controller.setZoom(17.5)
                        map.invalidate()
                    }

                    hapticManager.vibrateClick()
                    val firstStep = steps.firstOrNull()?.instruction ?: "Rasta shuru ho gaya hai, seedhe chalein."
                    speechManager.speakGuidance("Navigation shuru: Kul doori $totalDist meter hai. $firstStep", flush = true)
                } else {
                    speechManager.speakGuidance("Is jagah ke liye pedestrian route nahi mila. Seedhe chalte rahein.")
                }
            } catch (e: Exception) {
                isRouteCalculating = false
                speechManager.speakGuidance("Route load karne me samasya aayi. Kripya dobara koshish karein.")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        // 1. Native OpenStreetMap MapView (Zero WebView crash, 100% Free, Native Android Rendering)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(16.5)
                    controller.setCenter(GeoPoint(userLat, userLon))

                    // User Location Marker (Cyan)
                    val uMarker = Marker(this).apply {
                        position = GeoPoint(userLat, userLon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Aapki Current Location"
                    }
                    userMarker = uMarker
                    overlays.add(uMarker)

                    // Destination Pin Marker (Orange)
                    val dMarker = Marker(this).apply {
                        position = GeoPoint(selectedLat, selectedLon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = destinationName
                    }
                    destMarker = dMarker
                    overlays.add(dMarker)

                    // Tap on Map to Drop/Move Pin
                    val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            if (p != null) {
                                selectedLat = p.latitude
                                selectedLon = p.longitude
                                destinationName = String.format("Pin (%.4f, %.4f)", p.latitude, p.longitude)
                                hasDestinationSet = true
                                dMarker.position = p
                                dMarker.title = destinationName
                                invalidate()
                                hapticManager.vibrateClick()
                                speechManager.speakGuidance("Naya pin set kiya gaya.")
                            }
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    })
                    overlays.add(0, eventsOverlay)

                    mapViewInstance = this
                }
            }
        )

        // 2. Top Header & Place Search Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Back Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CardBg)
                        .border(1.dp, CardBorderColor, CircleShape)
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        if (query.length >= 3) {
                            coroutineScope.launch {
                                isSearching = true
                                searchResults = searchNominatim(query, userLat, userLon)
                                isSearching = false
                            }
                        } else {
                            searchResults = emptyList()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Search place (e.g. Metro, Hospital)...", color = TextSecondary, fontSize = 12.sp)
                    },
                    leadingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PrimaryOrange, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryOrange, modifier = Modifier.size(18.dp))
                        }
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable {
                                        searchQuery = ""
                                        searchResults = emptyList()
                                    }
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedBorderColor = PrimaryOrange,
                        unfocusedBorderColor = CardBorderColor,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                // GPS My-Location Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CardBg)
                        .border(1.dp, CardBorderColor, CircleShape)
                        .clickable {
                            hapticManager.vibrateClick()
                            try {
                                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                    .addOnSuccessListener { loc ->
                                        if (loc != null) {
                                            userLat = loc.latitude
                                            userLon = loc.longitude
                                            isGpsResolved = true
                                            userMarker?.position = GeoPoint(loc.latitude, loc.longitude)
                                            mapViewInstance?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                                            mapViewInstance?.controller?.setZoom(17.0)
                                            mapViewInstance?.invalidate()
                                            speechManager.speakGuidance("Aapki current location par map center kiya gaya.")
                                        }
                                    }
                            } catch (e: Exception) {}
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "My Location",
                        tint = if (isGpsResolved) SafetyCyan else PrimaryOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Search Results Dropdown List
            if (searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorderColor, RoundedCornerShape(12.dp))
                        .padding(6.dp)
                ) {
                    items(searchResults) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    searchQuery = item.name
                                    selectedLat = item.lat
                                    selectedLon = item.lon
                                    destinationName = item.name.split(",").firstOrNull()?.trim() ?: item.name
                                    hasDestinationSet = true
                                    searchResults = emptyList()

                                    destMarker?.position = GeoPoint(item.lat, item.lon)
                                    destMarker?.title = destinationName
                                    mapViewInstance?.controller?.animateTo(GeoPoint(item.lat, item.lon))
                                    mapViewInstance?.controller?.setZoom(16.5)
                                    mapViewInstance?.invalidate()

                                    val distStr = if (item.distanceMeters > 0) " (Doori: ${formatDistance(item.distanceMeters)})" else ""
                                    hapticManager.vibrateClick()
                                    speechManager.speakGuidance("$destinationName select kiya gaya$distStr. Niche Camera Navigation ya Map Route chunen.")
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = PrimaryOrange, modifier = Modifier.size(18.dp))
                                Column {
                                    Text(
                                        text = item.name.split(",").firstOrNull()?.trim() ?: item.name,
                                        color = TextWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.name,
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (item.distanceMeters > 0) {
                                Text(
                                    text = formatDistance(item.distanceMeters),
                                    color = SafetyCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Preset Chips (if not navigating)
            if (!isNavigating) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickChip("🚇 Metro Station") {
                        searchQuery = "Metro Station"
                        coroutineScope.launch {
                            isSearching = true
                            searchResults = searchNominatim("Metro Station", userLat, userLon)
                            isSearching = false
                        }
                    }
                    QuickChip("🏥 Hospital") {
                        searchQuery = "Hospital"
                        coroutineScope.launch {
                            isSearching = true
                            searchResults = searchNominatim("Hospital", userLat, userLon)
                            isSearching = false
                        }
                    }
                    QuickChip("🛒 Market") {
                        searchQuery = "Market"
                        coroutineScope.launch {
                            isSearching = true
                            searchResults = searchNominatim("Market", userLat, userLon)
                            isSearching = false
                        }
                    }
                    QuickChip("🚌 Bus Stand") {
                        searchQuery = "Bus Stand"
                        coroutineScope.launch {
                            isSearching = true
                            searchResults = searchNominatim("Bus Stand", userLat, userLon)
                            isSearching = false
                        }
                    }
                    QuickChip("🏦 Bank / ATM") {
                        searchQuery = "ATM"
                        coroutineScope.launch {
                            isSearching = true
                            searchResults = searchNominatim("ATM", userLat, userLon)
                            isSearching = false
                        }
                    }
                }
            }
        }

        // 3. Active Turn-by-Turn Guidance HUD (When Navigation is Running)
        if (isNavigating && routeSteps.isNotEmpty()) {
            val currentStep = routeSteps[currentStepIndex.coerceIn(0, routeSteps.size - 1)]

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 70.dp, start = 16.dp, end = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xF50D111A))
                        .border(1.5.dp, SafetyCyan, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Turn Arrow Icon
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(SafetyCyan.copy(alpha = 0.15f))
                                    .border(1.dp, SafetyCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getManeuverIcon(currentStep.modifier, currentStep.type),
                                    contentDescription = null,
                                    tint = SafetyCyan,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            // Distance and Step Index
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${currentStep.distanceMeters}m aage",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite
                                )
                                Text(
                                    text = "Mod ${currentStepIndex + 1} of ${routeSteps.size}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Main Spoken Instruction
                        Text(
                            text = currentStep.instruction,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextWhite,
                            lineHeight = 20.sp
                        )

                        // Step Controller (Previous / Next Turn)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Repeat Speech Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E2535))
                                    .clickable {
                                        speechManager.speakGuidance(currentStep.instruction, flush = true)
                                        hapticManager.vibrateClick()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = SafetyCyan, modifier = Modifier.size(14.dp))
                                    Text("Sunen (Repeat)", fontSize = 11.sp, color = SafetyCyan, fontWeight = FontWeight.Bold)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (currentStepIndex > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1E2535))
                                            .clickable {
                                                currentStepIndex--
                                                val st = routeSteps[currentStepIndex]
                                                speechManager.speakGuidance(st.instruction, flush = true)
                                                mapViewInstance?.controller?.animateTo(st.location)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("Peeche", fontSize = 11.sp, color = TextWhite)
                                    }
                                }

                                if (currentStepIndex < routeSteps.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SafetyCyan)
                                            .clickable {
                                                currentStepIndex++
                                                val st = routeSteps[currentStepIndex]
                                                speechManager.speakGuidance(st.instruction, flush = true)
                                                mapViewInstance?.controller?.animateTo(st.location)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Agla Mod ➡️", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureBlack)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Bottom Action Card
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorderColor, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Pinned Location Info Row
                    val directDist = calculateDistanceMeters(userLat, userLon, selectedLat, selectedLon)
                    val distText = formatDistance(directDist)
                    val etaText = formatEta(directDist)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(PrimaryOrange.copy(alpha = 0.18f))
                                .border(1.dp, PrimaryOrange, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isNavigating) Icons.Default.Navigation else Icons.Default.Place,
                                contentDescription = null,
                                tint = PrimaryOrange,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = destinationName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "📍 $distText",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SafetyCyan
                                )
                                Text(text = "•", fontSize = 11.sp, color = TextSecondary)
                                Text(
                                    text = "🚶 $etaText",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // Main Action Buttons
                    if (!isNavigating) {
                        // 1. PRIMARY BUTTON: START LIVE CAMERA AI NAVIGATION (High-Contrast Emerald / Cyan Gradient)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF00E5FF), Color(0xFF00E676))
                                    )
                                )
                                .clickable {
                                    hapticManager.vibrateClick()
                                    speechManager.speakCritical(
                                        if (speechManager.currentLanguage == "hi")
                                            "$destinationName ke liye Camera Navigation shuru ho gaya hai. Phone ko samne seedha rakhein."
                                        else
                                            "Starting Camera AI Navigation to $destinationName. Hold your phone forward."
                                    )
                                    onStartRoute(
                                        Waypoint(
                                            id = System.currentTimeMillis(),
                                            label = destinationName,
                                            latitude = selectedLat,
                                            longitude = selectedLon
                                        )
                                    )
                                }
                                .semantics {
                                    contentDescription = "Start Live Camera AI Navigation to $destinationName"
                                    role = Role.Button
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(PureBlack.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = PureBlack, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text("START CAMERA AI NAVIGATION", fontSize = 13.sp, fontWeight = FontWeight.Black, color = PureBlack)
                                    Text("Live Location Guidance • Obstacle Avoidance • Vibration", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PureBlack.copy(alpha = 0.85f))
                                }
                            }
                        }

                        // 2. SECONDARY BUTTON: PREVIEW ROUTE ON MAP
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF191E2C))
                                .border(1.dp, CardBorderColor, RoundedCornerShape(12.dp))
                                .clickable {
                                    calculateAndStartRoute(selectedLat, selectedLon, destinationName)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isRouteCalculating) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SafetyCyan, strokeWidth = 2.dp)
                                    Text("Rasta calculate ho raha hai...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SafetyCyan)
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null, tint = SafetyCyan, modifier = Modifier.size(18.dp))
                                    Text("Preview Route on Map", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
                                }
                            }
                        }
                    } else {
                        // Navigation is active: Toggle to AI Camera Scanner with this route
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF00E5FF), Color(0xFF00E676))
                                    )
                                )
                                .clickable {
                                    hapticManager.vibrateClick()
                                    onStartRoute(
                                        Waypoint(
                                            id = System.currentTimeMillis(),
                                            label = destinationName,
                                            latitude = selectedLat,
                                            longitude = selectedLon
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = PureBlack, modifier = Modifier.size(20.dp))
                                Text("SWITCH TO CAMERA AI NAVIGATION", fontSize = 13.sp, fontWeight = FontWeight.Black, color = PureBlack)
                            }
                        }

                        // Stop Navigation Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF261010))
                                .border(1.dp, Color(0xFFD32F2F), RoundedCornerShape(12.dp))
                                .clickable {
                                    isNavigating = false
                                    routeSteps = emptyList()
                                    routePolyline?.let { mapViewInstance?.overlays?.remove(it) }
                                    mapViewInstance?.invalidate()
                                    speechManager.speakGuidance("Navigation samapt kar di gayi hai.")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Stop Route Navigation", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .border(1.dp, CardBorderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Place, contentDescription = null, tint = PrimaryOrange, modifier = Modifier.size(13.dp))
            Text(label, fontSize = 11.sp, color = TextWhite, fontWeight = FontWeight.Medium)
        }
    }
}

private fun createTurnInstruction(type: String, modifier: String, street: String, distMeters: Int): String {
    val distStr = if (distMeters > 0) "$distMeters meter aage " else ""
    val stStr = if (street.isNotBlank() && street != "Path") " $street par" else ""

    return when {
        type == "arrive" -> "Aap apni manzil par pahunch chuke hain (Arrived at destination)."
        modifier.contains("left") -> "${distStr}Baayein mudein (Turn Left)$stStr."
        modifier.contains("right") -> "${distStr}Daayein mudein (Turn Right)$stStr."
        modifier.contains("u-turn") || modifier.contains("uturn") -> "${distStr}Peeche mudein (Take U-Turn)."
        else -> "${distStr}Seedhe chalein (Head Straight)$stStr."
    }
}

private fun getManeuverIcon(modifier: String, type: String): ImageVector {
    return when {
        type == "arrive" -> Icons.Default.CheckCircle
        modifier.contains("left") -> Icons.Default.TurnLeft
        modifier.contains("right") -> Icons.Default.TurnRight
        modifier.contains("u-turn") || modifier.contains("uturn") -> Icons.Default.ArrowDownward
        else -> Icons.Default.ArrowUpward
    }
}

private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
    val r = 6371000.0 // Earth radius in meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return (r * c).toInt()
}

private fun formatDistance(meters: Int): String {
    return if (meters >= 1000) String.format("%.1f km", meters / 1000.0) else "$meters m"
}

private fun formatEta(meters: Int): String {
    val mins = (meters / 75).coerceAtLeast(1)
    return if (mins >= 60) "${mins / 60}h ${mins % 60}m walk" else "~$mins min walk"
}

private suspend fun searchNominatim(query: String, userLat: Double = 0.0, userLon: Double = 0.0): List<SearchResultItem> = withContext(Dispatchers.IO) {
    try {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=6&addressdetails=1")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Drishti-Mobility-Assistant-App")
        conn.connectTimeout = 6000
        conn.readTimeout = 6000

        val response = conn.inputStream.bufferedReader().readText()
        val arr = JSONArray(response)
        val list = mutableListOf<SearchResultItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val name = obj.optString("display_name", "Location")
            val lat = obj.getDouble("lat")
            val lon = obj.getDouble("lon")
            val dist = if (userLat != 0.0 && userLon != 0.0) calculateDistanceMeters(userLat, userLon, lat, lon) else 0
            list.add(SearchResultItem(name, lat, lon, dist))
        }
        list.sortedBy { if (it.distanceMeters > 0) it.distanceMeters else Int.MAX_VALUE }
    } catch (e: Exception) {
        emptyList()
    }
}

