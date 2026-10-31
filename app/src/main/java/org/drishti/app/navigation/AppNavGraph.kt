package org.drishti.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import org.drishti.app.splash.SplashScreen
import org.drishti.app.ui.about.AboutAccessibilityScreen
import org.drishti.app.ui.ai.AiAssistanceScreen
import org.drishti.app.ui.dashboard.HomeDashboardScreen
import org.drishti.app.ui.destination.DestinationHubScreen
import org.drishti.app.ui.destination.LiveMapRouteSelectorScreen
import org.drishti.app.ui.help.HelpGuidanceScreen
import org.drishti.app.ui.locations.SavedLocationsScreen
import org.drishti.app.ui.navigation.LiveCameraNavScreen
import org.drishti.app.ui.navigation.ObstacleDetailModal
import org.drishti.app.ui.onboarding.OnboardingScreen
import org.drishti.app.ui.permissions.PermissionSetupScreen
import org.drishti.app.ui.safety.FamilySafetyScreen
import org.drishti.app.ui.settings.SettingsScreen
import org.drishti.app.ui.sos.EmergencySosScreen
import org.drishti.core.database.DestinationRepository
import org.drishti.core.database.FamilySafetyRepository
import org.drishti.core.database.UserPreferencesRepository
import org.drishti.core.feedback.HapticManager
import org.drishti.core.feedback.SpeechManager
import org.drishti.core.feedback.VoiceGuideManager
import org.drishti.core.model.TrackedObstacle
import org.drishti.core.model.Waypoint

enum class AppScreen {
    SPLASH,
    ONBOARDING,
    PERMISSION_SETUP,
    DASHBOARD,
    AI_ASSISTANCE,
    LIVE_NAV,
    OBSTACLE_DETAILS,
    DESTINATIONS,
    LIVE_MAP_SELECTOR,
    SAVED_LOCATIONS,
    FAMILY_SAFETY,
    EMERGENCY_SOS,
    SETTINGS,
    ABOUT_ACCESSIBILITY,
    HELP
}

@Composable
fun AppNavGraph() {
    val context = LocalContext.current

    val speechManager = remember { SpeechManager(context) }
    val hapticManager = remember { HapticManager(context) }
    val voiceGuideManager = remember { VoiceGuideManager(speechManager) }
    val destinationRepo = remember { DestinationRepository() }
    val safetyRepo = remember { FamilySafetyRepository() }
    val preferencesRepo = remember { UserPreferencesRepository() }

    var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }
    var activeRouteDestination by remember { mutableStateOf<Waypoint?>(null) }
    var detailObstacles by remember { mutableStateOf<List<TrackedObstacle>>(emptyList()) }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.shutdown()
        }
    }

    when (currentScreen) {
        AppScreen.SPLASH -> {
            SplashScreen()

            // Skip onboarding/permissions — go directly to dashboard after splash
            LaunchedEffect(Unit) {
                delay(1500)
                currentScreen = AppScreen.DASHBOARD
            }
        }

        AppScreen.ONBOARDING -> {
            // Kept in enum for backward compat; navigation skips this screen
            currentScreen = AppScreen.DASHBOARD
        }

        AppScreen.PERMISSION_SETUP -> {
            // Kept in enum for backward compat; navigation skips this screen
            currentScreen = AppScreen.DASHBOARD
        }

        AppScreen.DASHBOARD -> {
            HomeDashboardScreen(
                onStartAiAssistance = {
                    activeRouteDestination = null
                    currentScreen = AppScreen.LIVE_NAV
                    voiceGuideManager.announceLiveScanningStarted()
                },
                onNavigateDestination = {
                    currentScreen = AppScreen.LIVE_MAP_SELECTOR
                },
                onOpenFamilySafety = {
                    currentScreen = AppScreen.FAMILY_SAFETY
                },
                onOpenEmergencySos = {
                    currentScreen = AppScreen.EMERGENCY_SOS
                },
                onOpenSettings = {
                    currentScreen = AppScreen.SETTINGS
                },
                onOpenHelp = {
                    currentScreen = AppScreen.HELP
                },
                onOpenAbout = {
                    currentScreen = AppScreen.ABOUT_ACCESSIBILITY
                },
                onSpeakDashboardIntro = {
                    voiceGuideManager.announceDashboard()
                },
                onOpenSavedPlaces = {
                    currentScreen = AppScreen.SAVED_LOCATIONS
                },
                onDownloadMaps = {
                    currentScreen = AppScreen.LIVE_MAP_SELECTOR
                }
            )
        }

        AppScreen.AI_ASSISTANCE -> {
            AiAssistanceScreen(
                speechManager = speechManager,
                onLaunchCamera = {
                    activeRouteDestination = null
                    currentScreen = AppScreen.LIVE_NAV
                    voiceGuideManager.announceLiveScanningStarted()
                },
                onBack = {
                    currentScreen = AppScreen.DASHBOARD
                }
            )
        }

        AppScreen.LIVE_NAV -> {
            LiveCameraNavScreen(
                speechManager = speechManager,
                hapticManager = hapticManager,
                activeRouteName = activeRouteDestination?.label,
                onOpenObstacleDetails = { obstacles ->
                    detailObstacles = obstacles
                    currentScreen = AppScreen.OBSTACLE_DETAILS
                },
                onStopNavigation = {
                    speechManager.speakGuidance("Navigation stopped. Returning to home dashboard.", flush = true)
                    currentScreen = AppScreen.DASHBOARD
                }
            )
        }

        AppScreen.OBSTACLE_DETAILS -> {
            ObstacleDetailModal(
                obstacles = detailObstacles,
                onClose = {
                    currentScreen = AppScreen.LIVE_NAV
                }
            )
        }

        AppScreen.DESTINATIONS -> {
            DestinationHubScreen(
                repository = destinationRepo,
                speechManager = speechManager,
                hapticManager = hapticManager,
                onOpenLiveMapSelector = {
                    currentScreen = AppScreen.LIVE_MAP_SELECTOR
                },
                onStartRouteNavigation = { waypoint ->
                    activeRouteDestination = waypoint
                    currentScreen = AppScreen.LIVE_NAV
                    speechManager.speakCritical(
                        if (speechManager.currentLanguage == "hi")
                            "${waypoint.label} ke liye rasta shuru ho raha hai. Seedhe chalein."
                        else
                            "Starting route to ${waypoint.label}. Proceed straight."
                    )
                },
                onBackToDashboard = {
                    currentScreen = AppScreen.DASHBOARD
                },
                onSpeakIntro = {
                    voiceGuideManager.announceDestinationHub()
                }
            )
        }

        AppScreen.LIVE_MAP_SELECTOR -> {
            LiveMapRouteSelectorScreen(
                repository = destinationRepo,
                speechManager = speechManager,
                hapticManager = hapticManager,
                onStartRoute = { waypoint ->
                    activeRouteDestination = waypoint
                    currentScreen = AppScreen.LIVE_NAV
                },
                onCancel = {
                    currentScreen = AppScreen.DESTINATIONS
                },
                onSpeakIntro = {
                    voiceGuideManager.announceLiveMapSelector()
                }
            )
        }

        AppScreen.SAVED_LOCATIONS -> {
            SavedLocationsScreen(
                repository = destinationRepo,
                speechManager = speechManager,
                onNavigateToWaypoint = { waypoint ->
                    activeRouteDestination = waypoint
                    currentScreen = AppScreen.LIVE_NAV
                },
                onBack = {
                    currentScreen = AppScreen.DESTINATIONS
                }
            )
        }

        AppScreen.FAMILY_SAFETY -> {
            FamilySafetyScreen(
                safetyRepo = safetyRepo,
                speechManager = speechManager,
                onBack = {
                    currentScreen = AppScreen.DASHBOARD
                }
            )
        }

        AppScreen.EMERGENCY_SOS -> {
            EmergencySosScreen(
                safetyRepo = safetyRepo,
                speechManager = speechManager,
                hapticManager = hapticManager,
                onBack = {
                    currentScreen = AppScreen.DASHBOARD
                }
            )
        }

        AppScreen.SETTINGS -> {
            SettingsScreen(
                preferencesRepo = preferencesRepo,
                speechManager = speechManager,
                hapticManager = hapticManager,
                onOpenHelp = {
                    currentScreen = AppScreen.HELP
                },
                onBack = {
                    currentScreen = AppScreen.DASHBOARD
                }
            )
        }

        AppScreen.ABOUT_ACCESSIBILITY -> {
            AboutAccessibilityScreen(
                onBack = {
                    currentScreen = AppScreen.DASHBOARD
                }
            )
        }

        AppScreen.HELP -> {
            HelpGuidanceScreen(
                onBack = {
                    currentScreen = AppScreen.DASHBOARD
                }
            )
        }
    }
}
