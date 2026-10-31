# Development Roadmap & Milestone Plan
## DRISHTI: AI-Powered Offline Mobility Assistant

**Document Version:** 1.0.0  
**Status:** Baseline Plan  
**Target Delivery:** Robust, production-grade offline mobility platform

---

## 1. Phased Execution Roadmap

| Phase | Milestone Name | Key Deliverables | Completion Criteria |
| :--- | :--- | :--- | :--- |
| **Phase 0** | **Project Analysis & Planning** | Comprehensive PRD, Architecture, AI Model Selection, Roadmap, Progress log. Codebase inspection, Gradle build verification. | All documentation verified. Build verified on Gradle. Baseline established. |
| **Phase 1** | **UI/UX & Application Foundation** | Complete 12-screen navigation: Splash, Onboarding, Permissions, Home Dashboard, AI Assistance, Live Camera Nav, Destination Nav, Family Safety, SOS, Saved Locations, Settings, About/Accessibility. TalkBack semantics, high-contrast dark theme, large touch targets. | All 12 screens accessible and navigable. Zero fake buttons. App builds cleanly. |
| **Phase 2** | **Camera & On-Device AI Engine** | CameraX streaming pipeline (`STRATEGY_KEEP_ONLY_LATEST`), RGBA/YUV conversion, LiteRT integration, ML Kit on-device fallback, real-time object classification and bounding box normalization. | Live camera preview works. Real AI model inference on device. Bounding boxes rendered accurately. |
| **Phase 3** | **Spatial Understanding & Environment** | 3-Zone lateral spatial partition (Left, Center Corridor, Right), ground-contact pinhole camera projection, distance classification (Very Near, Near, Medium, Far), drop-off edge analysis. | Accurate left-center-right classification. Calibrated distance zones. No fabricated measurements. |
| **Phase 4** | **Object Tracking & Motion Analysis** | ByteTrack multi-object association, Kalman filter tracking, stable object IDs, approach velocity calculation ($v = \Delta d / \Delta t$), approaching vs receding detection. | Stable object IDs across frames. Track flickering suppressed. Reliable approaching vehicle detection. |
| **Phase 5** | **Hazard Intelligence Engine** | 4-Tier Risk Engine (`CLEAR`, `CAUTION`, `WARNING`, `CRITICAL`), walking corridor path overlap calculation, intelligent turn suggestions, 4.0s cooldown deduplication. | Dynamic risk calculation. Emergency stops triggered only for immediate collision hazards. |
| **Phase 6** | **Voice & Haptic Feedback** | Bilingual Android `TextToSpeech` (Hindi & English), safety audio preemption (`QUEUE_FLUSH`), AudioFocus ducking, directional haptic waveforms (`VibratorManager`). | Voice announcements work offline in Hindi and English. Haptic patterns distinct and responsive. |
| **Phase 7** | **Live Destination Navigation** | OpenStreetMap / Mapsforge offline vector maps, offline pedestrian routing engine, GPS FusedLocation updates, turn-by-turn voice instructions, route deviation alerts. | Waypoint selection works. Turn instructions speak at waypoints. Recalculation on route deviation. |
| **Phase 8** | **AI Navigation Integration** | Combined navigation + hazard intelligence engine. Hazard preemption over turn instructions ("Turn right ahead, but obstacle on right. Slow down"). | Navigation continues seamlessly during hazard alerts. Critical safety overrides routine turns. |
| **Phase 9** | **Family Safety & Live Location** | Emergency contact management (Room DB), configurable location-sharing intervals (1 to 30 min), SMS location link generator, offline location queuing. | Family contacts saved locally. Location SMS prepared and tested. Compliant Android service. |
| **Phase 10** | **Emergency SOS** | Dedicated accessible SOS interface, 3-second hold or triple-tap trigger, instant GPS acquisition, automated SMS dispatch to verified contacts, voice & haptic alarms. | SOS triggers reliably. Accidental activation prevented. Emergency coordinates dispatched. |
| **Phase 11** | **Settings & Accessibility** | Full settings panel: language toggle, speech rate, haptic intensity, sensitivity thresholds, offline map downloader, privacy & battery optimization. | Settings persist via DataStore. Instant effect on speech and haptic engines. |
| **Phase 12** | **Performance Optimization** | INT8 quantization, frame skipping, thread pool optimizations (`Dispatchers.Default` / `Executors`), memory leak auditing, thermal throttling management. | Sustained 15–20 FPS. Target latency under 300 ms. Memory RSS < 250 MB. |
| **Phase 13** | **Testing & Validation** | Unit tests for Risk Engine, ByteTrack, SpeechManager, DestinationRepository. Real-world scenario simulation (crowded streets, potholes, night mode). | 100% test pass rate on unit tests. Successful APK build and verified runtime stability. |

---

## 2. Verification Protocol

For every phase:
1. Write reusable, modular code following Clean Architecture principles.
2. Implement unit tests for core logic.
3. Verify compilation with `./gradlew assembleDebug`.
4. Validate functional behavior against acceptance criteria.
5. Update `DEVELOPMENT_PROGRESS.md` with features, modified files, test results, and limitations.

