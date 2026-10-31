# Product Requirements Document (PRD)
## DRISHTI: AI-Powered Offline Mobility Assistant

**Document Version:** 1.0.0  
**Status:** Approved Technical Baseline  
**Platform:** Android 10.0+ (API Level 29 Minimum, API Level 34 Target)  
**Primary Users:** Blind and Low-Vision (BLV) Pedestrians  
**Tagline:** *"See Beyond With Confidence."*

---

## 1. Executive Summary & Vision

Drishti ("Vision") is an AI-powered, offline-first Android mobility assistant engineered for blind and low-vision (BLV) individuals. Operating entirely on-device without internet connectivity, Drishti converts live smartphone camera video into low-latency (< 300 ms) spatial guidance, obstacle alerts, and navigational waypoints delivered through prioritized bilingual voice feedback (Hindi & English) and spatial haptic waveforms.

Drishti is designed as a **complementary perception layer** to physical mobility aids (white canes and guide dogs), specifically eliminating the white cane's primary blind spots:
1. **Head-height protrusions:** Overhanging branches, signboards, scaffolding, open truck tailgates.
2. **Ground drop-offs and depressions:** Open manholes, potholes, uncovered drains, downward stairs.
3. **Dynamic approaching hazards:** Fast-closing silent two-wheelers, motorcycles, bicycles, stray animals, and oncoming pedestrians.

---

## 2. Core Architectural Principles

1. **Offline-First Functionality:** Core object detection, hazard intelligence, distance estimation, voice feedback, haptics, and map routing must function without any cellular or Wi-Fi network.
2. **On-Device AI Inference:** Neural network inferences run locally via Google LiteRT (and ML Kit fallback) utilizing device NPU/GPU/CPU delegates.
3. **Low-Latency Processing:** Real-time end-to-end alert latency under 300 ms, processing camera frames at 15–20 FPS.
4. **Privacy-by-Design:** Zero raw camera video or personal audio leaves the smartphone.
5. **Universal Accessibility:** Fully compliant with Android TalkBack, WCAG 2.2 AAA high-contrast visual modes, minimum 56x56 dp touch targets, and non-visual feedback.
6. **Low-End Hardware Compatibility:** Quantized INT8 models and adaptive frame-skipping designed for budget smartphones (< $150).
7. **Intelligent Alert Prioritization:** 4-tier risk classification engine suppresses repetitive auditory noise and prioritizes imminent collision warnings over routine updates.
8. **Reliable Navigation Assistance:** Offline OpenStreetMap / Mapsforge vector maps and offline pedestrian routing engine.

---

## 3. Assistive Class Taxonomy (24 Classes)

| Group | Class ID | Assistive Class Name | English Label | Hindi Label | Hazard Type |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Ground Hazards** | 1 | `pothole_open_trench` | Pothole / Trench | Gaddha | Critical Ground |
| | 2 | `curb_step_down` | Step Down / Curb | Neechi Sidhi | Warning Ground |
| | 3 | `curb_step_up` | Step Up / Curb | Oonchi Sidhi | Caution Ground |
| | 4 | `stairs_downward` | Downward Stairs | Neeche Jati Sidhiyan | Critical Ground |
| | 5 | `stairs_upward` | Upward Stairs | Upar Jati Sidhiyan | Warning Ground |
| | 6 | `puddle_slippery_surface`| Slippery Puddle | Pani ka Gaddha | Caution Ground |
| **Head-Level Hazards** | 7 | `overhanging_branch` | Tree Branch | Ped ki Daali | Critical Head-Level |
| | 8 | `protruding_signboard` | Signboard | Signboard | Warning Head-Level |
| | 9 | `open_awning_sunshade` | Low Awning | Chhat | Caution Head-Level |
| | 10 | `construction_scaffold`| Scaffolding | Scaffold | Critical Head-Level |
| | 11 | `open_truck_tailgate` | Open Truck Tailgate | Truck ka Khula Gate | Critical Head-Level |
| | 12 | `wire_cable_hazard` | Hanging Wire | Latakta Taar | Critical Head-Level |
| **Dynamic Actors** | 13 | `pedestrian_moving` | Person | Vyakti | Dynamic Caution |
| | 14 | `bicycle_escooter` | Bicycle / Scooter | Cycle | Dynamic Warning |
| | 15 | `motorcycle_rickshaw` | Motorcycle / Auto | Motorcycle / Auto | Dynamic Critical |
| | 16 | `car_van_truck` | Car / Truck | Gaadi | Dynamic Critical |
| | 17 | `animal_dog_cattle` | Animal (Dog/Cow) | Kutta / Gaay | Dynamic Caution |
| **Street Furniture** | 18 | `utility_pole_lamp` | Utility Pole | Khamba | Static Obstacle |
| | 19 | `bollard_short_post` | Bollard | Chhota Khamba | Static Obstacle |
| | 20 | `planter_box` | Planter | Gamla | Static Obstacle |
| | 21 | `dustbin_receptacle` | Dustbin | Kudedan | Static Obstacle |
| | 22 | `barricade_fence` | Barricade | Barricade | Static Obstacle |
| | 23 | `door_entrance` | Door / Entrance | Darwaza | Navigation Landmark|
| | 24 | `low_wall_median` | Divider Wall | Divider | Static Obstacle |

---

## 4. Complete Screen Inventory (12 Screens)

1. **Splash Screen:** App branding, initialization of speech synthesis, LiteRT runtime verification, and animated transition.
2. **Onboarding Screen:** Interactive audio-guided tutorial introducing gestures, TalkBack navigation, and camera positioning (chest-mount / hand orientation).
3. **Permission Setup Screen:** Accessible permission grant flows for Camera, Fine/Coarse Location, Foreground Services, and Notifications.
4. **Home Dashboard:** High-contrast 4-quadrant primary navigation hub (Start AI Assistance, Navigate, Family Safety, Emergency SOS, Settings).
5. **AI Assistance Screen:** Live environment status overview, active audio toggle, flash toggle, and hazard log summary.
6. **Live Camera Detection Screen:** Real-time CameraX preview, visual bounding box overlay with threat color codes, 3-zone spatial indicator (Left, Center, Right), clock-position guide, and live audio/haptic dispatch.
7. **Destination Navigation Screen:** Turn-by-turn pedestrian guide with compass heading, remaining distance, ETA, next step preview, and route deviation warning.
8. **Family Safety Screen:** Emergency contact management (add/edit/delete), live location-sharing status, interval selector (1, 5, 10, 15, 30 min), and SMS sharing triggers.
9. **Emergency SOS Interface:** Large accessible SOS button with 3-second hold or rapid triple-tap trigger, GPS coordinates acquisition, automated SMS dispatch to family contacts, voice & haptic confirmation.
10. **Saved Locations Screen:** Offline waypoint book (Home, Work, Clinic, Transit) with custom labels, GPS coordinates, and instant "Navigate Here" routing.
11. **Settings Screen:** Language switch (Hindi/English), speech rate, voice volume, vibration intensity, detection sensitivity, battery saver mode, and offline map storage management.
12. **About & Accessibility Information Screen:** WCAG guidelines adherence, disclaimer regarding white cane complementary usage, app version, and developer contact.

---

## 5. Hazard Intelligence & 4-Tier Risk Engine

- **Level 0 (CLEAR):** No obstacles in walking path. Zero speech output to prevent auditory clutter.
- **Level 1 (CAUTION):** Static obstacle outside immediate walking corridor (> 3.5 m or lateral edge). Soft single haptic vibration (pulse 60 ms).
- **Level 2 (WARNING):** Obstacle inside walking corridor (1.5 m – 3.5 m) or approaching at moderate speed. Short bilingual voice alert + distinct double-vibration pulse.
- **Level 3 (CRITICAL):** Obstacle in immediate collision zone (< 1.5 m / ~2 feet), approaching vehicle with Time-to-Collision < 2.0 s, or ground drop-off / open trench. High-priority voice preemption ("STOP! Danger ahead") + sharp 3-burst max-amplitude haptic vibration.

---

## 6. Functional & Non-Functional Specifications

- **Frame Processing Rate:** 15–20 FPS continuous CameraX ImageAnalysis stream (`STRATEGY_KEEP_ONLY_LATEST`).
- **Inference Latency Target:** $\le 65\text{ ms}$ on mid-range ARM CPU, $\le 25\text{ ms}$ on NPU/GPU delegates.
- **Memory Footprint:** $\le 250\text{ MB}$ RSS runtime memory.
- **Thermal Budget:** Sustained operation without thermal throttling for at least 45 minutes of continuous walking navigation.
- **Audio Ducking:** Temporary 12 dB attenuation of background music / TalkBack when Drishti emits safety-critical speech.

