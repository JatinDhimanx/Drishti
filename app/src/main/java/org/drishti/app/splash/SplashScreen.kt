package org.drishti.app.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.drishti.app.R

// Drishti Brand Colors
private val SplashBgDark = Color(0xFF07080A)
private val SplashBgCharcoal = Color(0xFF111318)
private val PrimaryOrange = Color(0xFFFF7028)
private val SecondaryOrange = Color(0xFFE85D20)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB8B8BE)
private val ProgressTrack = Color(0xFF34343A)

/**
 * Opening Screen (Splash Screen) of Drishti.
 * Matches the premium dark aesthetic with orange eye-and-signal branding,
 * subtle radial glow, and minimal loading indicator.
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier
) {
    // Fade-in and gentle scale-in animation for content
    var startAnimation by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 850,
            easing = FastOutSlowInEasing
        ),
        label = "SplashFadeIn"
    )

    val contentScaleAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.92f,
        animationSpec = tween(
            durationMillis = 850,
            easing = FastOutSlowInEasing
        ),
        label = "SplashScale"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(SplashBgCharcoal, SplashBgDark)
                )
            )
            .semantics {
                contentDescription = "Drishti App Opening: See Beyond With Confidence"
            }
    ) {
        // Subtle background light trails and ambient glow
        SplashBackground(
            modifier = Modifier.fillMaxSize(),
            glowAlpha = contentAlpha * 0.9f
        )

        // Main Brand Identity Group (Centered in upper-middle portion)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .alpha(contentAlpha)
                .scale(contentScaleAnim),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Official Drishti Brand Logo (App Open)
            Image(
                painter = painterResource(id = R.drawable.drishti_splash_logo),
                contentDescription = "Drishti Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(110.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Tagline
            Text(
                text = "See Beyond With Confidence",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                color = TextSecondary,
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center
            )
        }

        // Minimal Bottom Loading Indicator
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 44.dp)
                .alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SplashLoadingIndicator(
                modifier = Modifier
                    .width(130.dp)
                    .height(4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Loading...",
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                color = TextSecondary,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Native Canvas-drawn Drishti Logo combining an eye-shaped outline
 * (representing computer vision) and sound-wave signal arcs inside
 * (representing acoustic feedback & echolocation).
 */
@Composable
fun DrishtiLogo(
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.semantics {
            contentDescription = "Drishti Logo: An eye outline enclosing acoustic sound wave signals"
            role = Role.Image
        }
    ) {
        val w = size.width
        val h = size.height

        // 1. Eye Outline Path
        val eyePath = Path().apply {
            // Start at left corner
            moveTo(0f, h * 0.5f)
            // Top curve to right corner
            cubicTo(
                x1 = w * 0.28f, y1 = -h * 0.05f,
                x2 = w * 0.72f, y2 = -h * 0.05f,
                x3 = w, y3 = h * 0.5f
            )
            // Bottom curve back to left corner
            cubicTo(
                x1 = w * 0.72f, y1 = h * 1.05f,
                x2 = w * 0.28f, y2 = h * 1.05f,
                x3 = 0f, y3 = h * 0.5f
            )
            close()
        }

        val strokeWidth = 3.6.dp.toPx()
        val orangeGradient = Brush.linearGradient(
            colors = listOf(PrimaryOrange, SecondaryOrange),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )

        drawPath(
            path = eyePath,
            brush = orangeGradient,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 2. Central Pupil / Sensor Core
        val centerX = w * 0.5f
        val centerY = h * 0.5f
        val pupilRadius = 5.5.dp.toPx()

        drawCircle(
            brush = orangeGradient,
            radius = pupilRadius,
            center = Offset(centerX, centerY)
        )

        // 3. Sound-Wave / Sonar Signal Arcs Inside Eye
        val waveStroke = 2.4.dp.toPx()
        val waveBrush = Brush.verticalGradient(
            colors = listOf(PrimaryOrange.copy(alpha = 0.95f), SecondaryOrange.copy(alpha = 0.8f))
        )

        // Left inner wave arc
        val leftInnerPath = Path().apply {
            moveTo(centerX - 14.dp.toPx(), centerY - 10.dp.toPx())
            cubicTo(
                centerX - 18.dp.toPx(), centerY - 5.dp.toPx(),
                centerX - 18.dp.toPx(), centerY + 5.dp.toPx(),
                centerX - 14.dp.toPx(), centerY + 10.dp.toPx()
            )
        }
        drawPath(
            path = leftInnerPath,
            brush = waveBrush,
            style = Stroke(width = waveStroke, cap = StrokeCap.Round)
        )

        // Right inner wave arc
        val rightInnerPath = Path().apply {
            moveTo(centerX + 14.dp.toPx(), centerY - 10.dp.toPx())
            cubicTo(
                centerX + 18.dp.toPx(), centerY - 5.dp.toPx(),
                centerX + 18.dp.toPx(), centerY + 5.dp.toPx(),
                centerX + 14.dp.toPx(), centerY + 10.dp.toPx()
            )
        }
        drawPath(
            path = rightInnerPath,
            brush = waveBrush,
            style = Stroke(width = waveStroke, cap = StrokeCap.Round)
        )

        // Left outer subtle wave arc
        val leftOuterPath = Path().apply {
            moveTo(centerX - 24.dp.toPx(), centerY - 6.dp.toPx())
            cubicTo(
                centerX - 28.dp.toPx(), centerY - 3.dp.toPx(),
                centerX - 28.dp.toPx(), centerY + 3.dp.toPx(),
                centerX - 24.dp.toPx(), centerY + 6.dp.toPx()
            )
        }
        drawPath(
            path = leftOuterPath,
            color = PrimaryOrange.copy(alpha = 0.55f),
            style = Stroke(width = waveStroke * 0.85f, cap = StrokeCap.Round)
        )

        // Right outer subtle wave arc
        val rightOuterPath = Path().apply {
            moveTo(centerX + 24.dp.toPx(), centerY - 6.dp.toPx())
            cubicTo(
                centerX + 28.dp.toPx(), centerY - 3.dp.toPx(),
                centerX + 28.dp.toPx(), centerY + 3.dp.toPx(),
                centerX + 24.dp.toPx(), centerY + 6.dp.toPx()
            )
        }
        drawPath(
            path = rightOuterPath,
            color = PrimaryOrange.copy(alpha = 0.55f),
            style = Stroke(width = waveStroke * 0.85f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Background ambient elements:
 * - Soft orange radial glow centered behind the logo.
 * - Subtle abstract curved lines near bottom and edges.
 * Hidden from accessibility services as decorative artwork.
 */
@Composable
fun SplashBackground(
    modifier: Modifier = Modifier,
    glowAlpha: Float = 0.9f
) {
    Canvas(
        modifier = modifier.clearAndSetSemantics { }
    ) {
        val w = size.width
        val h = size.height

        // 1. Soft Central Orange Radial Glow behind logo
        val glowCenter = Offset(w * 0.5f, h * 0.42f)
        val glowRadius = w * 0.45f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    PrimaryOrange.copy(alpha = 0.16f * glowAlpha),
                    PrimaryOrange.copy(alpha = 0.06f * glowAlpha),
                    Color.Transparent
                ),
                center = glowCenter,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = glowCenter
        )

        // 2. Subtle curved abstract light trail near bottom-right edge
        val trailPath1 = Path().apply {
            moveTo(w * 0.1f, h * 0.92f)
            cubicTo(
                w * 0.4f, h * 0.85f,
                w * 0.7f, h * 0.95f,
                w * 1.05f, h * 0.88f
            )
        }
        drawPath(
            path = trailPath1,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    PrimaryOrange.copy(alpha = 0.08f * glowAlpha),
                    SecondaryOrange.copy(alpha = 0.05f * glowAlpha),
                    Color.Transparent
                )
            ),
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )

        // 3. Subtle curved line near bottom-left edge
        val trailPath2 = Path().apply {
            moveTo(-w * 0.05f, h * 0.84f)
            cubicTo(
                w * 0.25f, h * 0.89f,
                w * 0.55f, h * 0.81f,
                w * 0.85f, h * 0.86f
            )
        }
        drawPath(
            path = trailPath2,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    SecondaryOrange.copy(alpha = 0.07f * glowAlpha),
                    PrimaryOrange.copy(alpha = 0.04f * glowAlpha),
                    Color.Transparent
                )
            ),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Minimal horizontal loading indicator with rounded ends and
 * a smooth, battery-friendly progress glide animation.
 */
@Composable
fun SplashLoadingIndicator(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "LoadingGlide")
    val progressOffset by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ProgressOffset"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(ProgressTrack)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackWidth = size.width
            val indicatorWidth = trackWidth * 0.38f
            val startX = (trackWidth * progressOffset) - (indicatorWidth * 0.5f)

            // Draw animated smooth glowing segment
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        PrimaryOrange.copy(alpha = 0.6f),
                        PrimaryOrange,
                        PrimaryOrange.copy(alpha = 0.6f)
                    ),
                    startX = startX,
                    endX = startX + indicatorWidth
                ),
                topLeft = Offset(startX, 0f),
                size = Size(indicatorWidth, size.height)
            )
        }
    }
}
