package org.drishti.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.drishti.app.R

// Design System Palette based on the reference UI
private val DarkAppBackground = Color(0xFF0A0C12)
private val CardBorderColor = Color(0xFF1E2330)

// Accents
private val PrimaryOrange = Color(0xFFFF6D00)
private val PrimaryOrangeDark = Color(0xFFD84315)
private val NavBlue = Color(0xFF007AFE)
private val NavBlueDark = Color(0xFF0D47A1)
private val AlertRed = Color(0xFFF02D2D)
private val AlertRedDark = Color(0xFF8A0A0A)
private val SafetyGreen = Color(0xFF00C853)
private val SafetyGreenDark = Color(0xFF004D20)

// Quick Access Colors
private val QuickAccessPurple = Color(0xFF7C4DFF)
private val QuickAccessGreen = Color(0xFF00E676)
private val QuickAccessYellow = Color(0xFFFFD600)
private val QuickAccessPink = Color(0xFFFF4081)

private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9096A2)

@Composable
fun HomeDashboardScreen(
    onStartAiAssistance: () -> Unit,
    onNavigateDestination: () -> Unit,
    onOpenFamilySafety: () -> Unit,
    onOpenEmergencySos: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenAbout: () -> Unit,
    onSpeakDashboardIntro: () -> Unit,
    onOpenSavedPlaces: () -> Unit = onNavigateDestination,
    onDownloadMaps: () -> Unit = onNavigateDestination,
    onNotificationClick: () -> Unit = onSpeakDashboardIntro
) {
    LaunchedEffect(Unit) {
        onSpeakDashboardIntro()
    }

    Scaffold(
        containerColor = DarkAppBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            DrishtiBottomNavBar(
                activeTab = 0,
                onHomeClick = { /* already home */ },
                onMapsClick = onNavigateDestination,
                onSosClick = onOpenEmergencySos,
                onFamilyClick = onOpenFamilySafety,
                onSettingsClick = onOpenSettings
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 2.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top Bar Header
            DashboardTopBar(
                onNotificationClick = onNotificationClick,
                onSettingsClick = onOpenSettings
            )

            // 2. Hero Banner Card
            HeroBannerCard(
                onBannerClick = onStartAiAssistance
            )

            // 3. 2x2 Feature Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Feature Card 1: Start AI Assistance
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryGridCard(
                        title = "Start AI Assistance",
                        subtitle = "Live Camera • Obstacles\nPotholes • Vehicles",
                        icon = Icons.Default.CameraAlt,
                        badgeColor = PrimaryOrange,
                        gradientColors = listOf(Color(0xFF381604), Color(0xFF140801)),
                        borderColor = Color(0xFF5E2608),
                        cardType = CardArtType.ROAD_CAMERA,
                        onClick = onStartAiAssistance
                    )
                }

                // Feature Card 2: Navigate to Destination
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryGridCard(
                        title = "Navigate to\nDestination",
                        subtitle = "Offline Maps • Turn-by-Turn\nSaved Places",
                        icon = Icons.Default.NearMe,
                        badgeColor = NavBlue,
                        gradientColors = listOf(Color(0xFF071C38), Color(0xFF030D1C)),
                        borderColor = Color(0xFF0E3A70),
                        cardType = CardArtType.MAP_ROUTE,
                        onClick = onNavigateDestination
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Feature Card 3: Emergency SOS
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryGridCard(
                        title = "Emergency SOS",
                        subtitle = "Instant GPS Alert\nFamily Notification",
                        icon = null, // Will render SOS text inside badge
                        badgeText = "SOS",
                        badgeColor = AlertRed,
                        gradientColors = listOf(Color(0xFF380808), Color(0xFF150303)),
                        borderColor = Color(0xFF6B1212),
                        cardType = CardArtType.SOS_BEACON,
                        onClick = onOpenEmergencySos
                    )
                }

                // Feature Card 4: Family Safety & Live Sharing
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryGridCard(
                        title = "Family Safety &\nLive Sharing",
                        subtitle = "Verified Contacts\nAuto SMS Updates",
                        icon = Icons.Default.Group,
                        badgeColor = SafetyGreen,
                        gradientColors = listOf(Color(0xFF062814), Color(0xFF021208)),
                        borderColor = Color(0xFF0E4F28),
                        cardType = CardArtType.FAMILY_TRACK,
                        onClick = onOpenFamilySafety
                    )
                }
            }

            // 4. Settings & Preferences Full-Width Card
            SettingsRowCard(
                onClick = onOpenSettings
            )

            // 5. Quick Access Section
            QuickAccessSection(
                onSavedPlacesClick = onOpenSavedPlaces,
                onDownloadMapsClick = onDownloadMaps,
                onSafetyTipsClick = onOpenHelp,
                onAppReportsClick = onOpenAbout
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Top Bar Header
// ---------------------------------------------------------------------------
@Composable
private fun DashboardTopBar(
    onNotificationClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {
                heading()
                contentDescription = "Drishti - Your Offline AI Assistant"
            }
        ) {
            Image(
                painter = painterResource(id = R.drawable.drishti_splash_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(38.dp)
                    .width(128.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Notification Bell with red dot
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF161922))
                    .border(1.dp, CardBorderColor, CircleShape)
                    .clickable(onClick = onNotificationClick)
                    .semantics {
                        contentDescription = "Notifications and status updates"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp)
                )
                // Red Notification Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 8.dp)
                        .background(AlertRed, CircleShape)
                )
            }

            // Settings Gear Button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF161922))
                    .border(1.dp, CardBorderColor, CircleShape)
                    .clickable(onClick = onSettingsClick)
                    .semantics {
                        contentDescription = "Open Settings and Preferences"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Hero Banner Card
// ---------------------------------------------------------------------------
@Composable
private fun HeroBannerCard(
    onBannerClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(178.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF141926), Color(0xFF0C0F17))
                )
            )
            .border(1.dp, Color(0xFF262E40), RoundedCornerShape(20.dp))
            .clickable(onClick = onBannerClick)
            .semantics {
                contentDescription = "Road Awareness Anywhere, Offline. Drive and walk safer with AI Vision. Works offline, real-time obstacle detection."
                role = Role.Button
            }
    ) {
        // Background illustration of nighttime highway and AI vision bounding boxes
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Twilight sky gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1B2236), Color(0xFF0C0F17))
                )
            )

            // Distant mountain silhouette
            val mountainPath = Path().apply {
                moveTo(w * 0.45f, h * 0.65f)
                lineTo(w * 0.65f, h * 0.38f)
                lineTo(w * 0.85f, h * 0.55f)
                lineTo(w * 1.0f, h * 0.35f)
                lineTo(w, h)
                lineTo(w * 0.45f, h)
                close()
            }
            drawPath(mountainPath, color = Color(0xFF101420))

            // Road perspective curving towards center-right
            val roadPath = Path().apply {
                moveTo(w * 0.35f, h)
                lineTo(w * 0.72f, h * 0.45f)
                lineTo(w * 0.78f, h * 0.45f)
                lineTo(w * 1.05f, h)
                close()
            }
            drawPath(
                roadPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1C2230), Color(0xFF0D1017))
                )
            )

            // Glowing cyan lane guide line
            val laneLinePath = Path().apply {
                moveTo(w * 0.58f, h)
                cubicTo(
                    w * 0.65f, h * 0.78f,
                    w * 0.70f, h * 0.60f,
                    w * 0.75f, h * 0.46f
                )
            }
            drawPath(
                path = laneLinePath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF00E5FF), Color(0x6600E5FF))
                ),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // AI Bounding Box on Car ahead (Green Box)
            val carBoxLeft = w * 0.71f
            val carBoxTop = h * 0.38f
            val carBoxWidth = w * 0.14f
            val carBoxHeight = h * 0.16f
            drawRoundRect(
                color = Color(0xFF00E676),
                topLeft = Offset(carBoxLeft, carBoxTop),
                size = androidx.compose.ui.geometry.Size(carBoxWidth, carBoxHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                style = Stroke(width = 1.6.dp.toPx())
            )

            // AI Bounding Box on Pothole hazard (Red warning box on road)
            val potholeLeft = w * 0.76f
            val potholeTop = h * 0.58f
            val potholeWidth = w * 0.22f
            val potholeHeight = h * 0.22f
            drawRoundRect(
                color = AlertRed,
                topLeft = Offset(potholeLeft, potholeTop),
                size = androidx.compose.ui.geometry.Size(potholeWidth, potholeHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            // Red warning triangle icon indicator above pothole
            val triPath = Path().apply {
                val cx = potholeLeft + potholeWidth * 0.5f
                val cy = potholeTop - 12.dp.toPx()
                moveTo(cx, cy)
                lineTo(cx - 7.dp.toPx(), cy + 12.dp.toPx())
                lineTo(cx + 7.dp.toPx(), cy + 12.dp.toPx())
                close()
            }
            drawPath(triPath, color = AlertRed)
        }

        // Overlaid Text & Badges
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Tag & 3 Dots Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DRIVE SAFER\nWITH AI VISION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryOrange,
                    letterSpacing = 1.sp,
                    lineHeight = 14.sp
                )

                // 3 Dot Carousel Indicator
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(6.dp).background(PrimaryOrange, CircleShape))
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF4A5264), CircleShape))
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF4A5264), CircleShape))
                }
            }

            // Big Title
            Column {
                Text(
                    text = "Road Awareness",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextWhite,
                    letterSpacing = 0.3.sp
                )
                Text(
                    text = "Anywhere, Offline.",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PrimaryOrange,
                    letterSpacing = 0.3.sp
                )
            }

            // 3 Bottom Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HeroFeatureChip(
                    icon = Icons.Default.WifiOff,
                    text = "Works\nOffline",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.weight(1f)
                )
                HeroFeatureChip(
                    icon = Icons.Default.Bolt,
                    text = "Real-time\nDetection",
                    tint = SafetyGreen,
                    modifier = Modifier.weight(1f)
                )
                HeroFeatureChip(
                    icon = Icons.Default.Security,
                    text = "Safer\nJourneys",
                    tint = Color(0xFF69F0AE),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeroFeatureChip(
    icon: ImageVector,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xCC0E121B))
            .border(1.dp, Color(0xFF262D3D), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = text,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite,
                lineHeight = 11.sp
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 2x2 Feature Grid Card
// ---------------------------------------------------------------------------
private enum class CardArtType {
    ROAD_CAMERA,
    MAP_ROUTE,
    SOS_BEACON,
    FAMILY_TRACK
}

@Composable
private fun PrimaryGridCard(
    title: String,
    subtitle: String,
    icon: ImageVector?,
    badgeText: String? = null,
    badgeColor: Color,
    gradientColors: List<Color>,
    borderColor: Color,
    cardType: CardArtType,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(182.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.verticalGradient(colors = gradientColors)
            )
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "$title. $subtitle. Double tap to open."
                role = Role.Button
            }
    ) {
        // Background Contextual Graphics at bottom
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            when (cardType) {
                CardArtType.ROAD_CAMERA -> {
                    // Curved perspective road in warm orange glow
                    val road = Path().apply {
                        moveTo(0f, h * 0.95f)
                        cubicTo(w * 0.4f, h * 0.85f, w * 0.7f, h * 0.98f, w, h * 0.78f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(road, color = Color(0x33000000))
                    // Glowing edge lines
                    drawPath(
                        path = Path().apply {
                            moveTo(0f, h * 0.95f)
                            cubicTo(w * 0.4f, h * 0.85f, w * 0.7f, h * 0.98f, w, h * 0.78f)
                        },
                        color = PrimaryOrange.copy(alpha = 0.45f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                CardArtType.MAP_ROUTE -> {
                    // Winding blue GPS route path
                    val routePath = Path().apply {
                        moveTo(w * 0.85f, h * 0.35f)
                        cubicTo(w * 0.4f, h * 0.55f, w * 0.9f, h * 0.75f, w * 0.65f, h * 0.92f)
                    }
                    drawPath(
                        path = routePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(NavBlue.copy(alpha = 0.2f), NavBlue)
                        ),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Blue destination pin marker
                    drawCircle(
                        color = NavBlue,
                        radius = 8.dp.toPx(),
                        center = Offset(w * 0.88f, h * 0.78f)
                    )
                }

                CardArtType.SOS_BEACON -> {
                    // Concentric sonar shockwaves
                    val center = Offset(w * 0.5f, h * 0.95f)
                    drawCircle(
                        color = AlertRed.copy(alpha = 0.12f),
                        radius = w * 0.55f,
                        center = center
                    )
                    drawCircle(
                        color = AlertRed.copy(alpha = 0.22f),
                        radius = w * 0.38f,
                        center = center
                    )
                    drawCircle(
                        color = AlertRed.copy(alpha = 0.4f),
                        radius = w * 0.22f,
                        center = center
                    )
                }

                CardArtType.FAMILY_TRACK -> {
                    // Grid lines and connecting green friend-nodes
                    val trail = Path().apply {
                        moveTo(w * 0.9f, h * 0.65f)
                        cubicTo(w * 0.8f, h * 0.82f, w * 0.5f, h * 0.75f, w * 0.4f, h * 0.92f)
                    }
                    drawPath(
                        path = trail,
                        color = SafetyGreen.copy(alpha = 0.8f),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawCircle(
                        color = SafetyGreen,
                        radius = 7.dp.toPx(),
                        center = Offset(w * 0.88f, h * 0.64f)
                    )
                    drawCircle(
                        color = SafetyGreen,
                        radius = 8.dp.toPx(),
                        center = Offset(w * 0.4f, h * 0.92f)
                    )
                }
            }
        }

        // Foreground content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Badge & Chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Colored Icon Squircle
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = TextWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    } else if (badgeText != null) {
                        Text(
                            text = badgeText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextWhite
                        )
                    }
                }

                // Dark Right Chevron Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x4D000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextWhite.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Titles
            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextWhite.copy(alpha = 0.7f),
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Settings Row Card
// ---------------------------------------------------------------------------
@Composable
private fun SettingsRowCard(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF141722))
            .border(1.dp, Color(0xFF222736), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Settings and Preferences. App settings, AI mode, offline data, and notifications. Double tap to open."
                role = Role.Button
            }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF202636)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = TextWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Text(
                        text = "Settings & Preferences",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "App Settings • AI Mode • Offline Data • Notifications",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Quick Access Section (4 Action Tiles)
// ---------------------------------------------------------------------------
@Composable
private fun QuickAccessSection(
    onSavedPlacesClick: () -> Unit,
    onDownloadMapsClick: () -> Unit,
    onSafetyTipsClick: () -> Unit,
    onAppReportsClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Quick Access",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                modifier = Modifier.semantics { heading() }
            )

            Text(
                text = "See All >",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = NavBlue,
                modifier = Modifier
                    .clickable(onClick = onSavedPlacesClick)
                    .semantics {
                        contentDescription = "See all quick access shortcuts"
                        role = Role.Button
                    }
            )
        }

        // 4 Action Tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickAccessTile(
                title = "Saved Places",
                icon = Icons.Default.Place,
                iconColor = QuickAccessPurple,
                modifier = Modifier.weight(1f),
                onClick = onSavedPlacesClick
            )
            QuickAccessTile(
                title = "Download Maps",
                icon = Icons.Default.Download,
                iconColor = QuickAccessGreen,
                modifier = Modifier.weight(1f),
                onClick = onDownloadMapsClick
            )
            QuickAccessTile(
                title = "Safety Tips",
                icon = Icons.Default.Security,
                iconColor = QuickAccessYellow,
                modifier = Modifier.weight(1f),
                onClick = onSafetyTipsClick
            )
            QuickAccessTile(
                title = "App Reports",
                icon = Icons.Default.BarChart,
                iconColor = QuickAccessPink,
                modifier = Modifier.weight(1f),
                onClick = onAppReportsClick
            )
        }
    }
}

@Composable
private fun QuickAccessTile(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(86.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF131620))
            .border(1.dp, Color(0xFF202534), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "$title shortcut. Double tap to open."
                role = Role.Button
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Drishti Bottom Navigation Bar with Center Elevated SOS Button
// ---------------------------------------------------------------------------
@Composable
private fun DrishtiBottomNavBar(
    activeTab: Int,
    onHomeClick: () -> Unit,
    onMapsClick: () -> Unit,
    onSosClick: () -> Unit,
    onFamilyClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(74.dp)
            .background(Color(0xFF0C0E15))
            .border(1.dp, Color(0xFF1A1F2C))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home
            BottomNavItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = activeTab == 0,
                activeColor = PrimaryOrange,
                onClick = onHomeClick
            )

            // 2. Maps
            BottomNavItem(
                icon = Icons.Default.Map,
                label = "Maps",
                isSelected = activeTab == 1,
                activeColor = NavBlue,
                onClick = onMapsClick
            )

            // 3. Center Elevated SOS Button Placeholder
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .offset(y = (-8).dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF3D3D), AlertRed, AlertRedDark)
                        )
                    )
                    .border(3.dp, Color(0xFF6B1212), CircleShape)
                    .shadow(12.dp, CircleShape, ambientColor = AlertRed, spotColor = AlertRed)
                    .clickable(onClick = onSosClick)
                    .semantics {
                        contentDescription = "Emergency SOS button. Tap to trigger immediate GPS distress alert and siren."
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SOS",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = TextWhite,
                    letterSpacing = 1.sp
                )
            }

            // 4. Family
            BottomNavItem(
                icon = Icons.Default.Group,
                label = "Family",
                isSelected = activeTab == 3,
                activeColor = SafetyGreen,
                onClick = onFamilyClick
            )

            // 5. Settings
            BottomNavItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                isSelected = activeTab == 4,
                activeColor = TextWhite,
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics {
                contentDescription = "$label navigation tab"
                role = Role.Tab
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Orange top indicator line if selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(activeColor)
            )
            Spacer(modifier = Modifier.height(4.dp))
        } else {
            Spacer(modifier = Modifier.height(7.dp))
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) activeColor else Color(0xFF7E8494),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) activeColor else Color(0xFF7E8494)
        )
    }
}
