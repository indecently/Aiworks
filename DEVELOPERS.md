# Developer Guide: AIworks Internals

Welcome to the internal engine of AIworks. This document provides a high-level overview of the architecture, component organization, and guidelines for maintaining and extending this codebase.

## 🏛 Architecture Overview

AIworks follows a modern, performance-first **MVVM (Model-View-ViewModel)** architecture, optimized for high-frequency on-device AI inference.

### Key Pillars:
- **Performance First**: Extensive use of background dispatchers and hardware-specific optimizations (NPU/GPU delegates).
- **Offline-First**: Zero external API dependencies; all processing happens via LiteRT (formerly TensorFlow Lite).
- **State Predictability**: Uni-directional data flow using Kotlin `StateFlow`.
- **Modular UI**: Jetpack Compose with Material 3 Adaptive for multi-pane support.

---

## 📂 Project Structure

```text
com.birkneo.Aiworks
├── ai          # AI Core: LiteRT integration, prompt engineering, model persistence.
├── data        # Persistence: Room Database (DAOs, Entities), Repository layer.
├── di          # Dependency Injection: Simple manual container (GemmaContainer).
├── service     # Android Services: Assistant session management.
├── settings    # Configuration: DataStore-backed SettingsManager.
├── ui          # Presentation Layer: Screens, ViewModels, and Components.
│   ├── chat    # Main chat logic, segmented into Operations files.
│   ├── isolates# Session list (Home) and session management.
│   ├── settings# App-wide settings and developer tools.
│   └── theme   # Design system: Colors, Shapes, and Haptic-synced Typography.
└── util        # Helpers: Audio recording, TTS, Media handling.
```

---

## 🧠 Core Components

### 1. AI Engine (`com.birkneo.Aiworks.ai`)
- **`GemmaInference.kt`**: The heart of the app. Manages the lifecycle of the LiteRT engine, handles model loading with hardware fallbacks (NPU -> GPU -> CPU), and manages the KV cache for efficient multi-turn conversations.
- **`PromptArchitect.kt`**: Handles the construction of system prompts, integrating Long-Term Memory (LTM) and personas into the context window.
- **Recursive Condensation**: The engine implements a background "summarization demon" that distills history into core memory pills to prevent context overflow.

### 2. State Management (`com.birkneo.Aiworks.ui.chat`)
- **`ChatViewModel.kt`**: Holds the UI state. To prevent the "God ViewModel" anti-pattern, logic is segmented into extension functions:
    - `InferenceOperations.kt`: Handles the actual token generation loop.
    - `MessageOperations.kt`: CRUD for messages and memory distillation.
    - `SessionOperations.kt`: Session lifecycle and metadata updates.
    - `MediaOperations.kt`: Image and audio input handling.

### 3. Data Layer (`com.birkneo.Aiworks.data`)
- **Room Database**: Persistent storage for sessions and messages.
- **`ChatRepository.kt`**: Mediates between the DB and the ViewModel. It handles both persistent (DB) and volatile (in-memory) messages.

### 4. Navigation (`com.birkneo.Aiworks.ui.navigation`)
- Uses **Navigation3** for type-safe, backstack-aware navigation.
- **Adaptive Layouts**: Implements `ListDetailSceneStrategy` to provide a seamless tablet/foldable experience (List on left, Chat on right).

---

## 🛠 Working within the Codebase

### Adding a New Inference Feature
1. If it requires new prompt logic, modify `PromptArchitect`.
2. Update `GemmaInference` if the LiteRT configuration needs to change.
3. Add the UI trigger in `ChatScreen` and the corresponding operation in a new `Operations.kt` file.

### Modifying the UI
- **Pill-First Design**: Stick to the "Control Island" aesthetic. Components should be rounded, elevated, and utilize `animateContentSize` for state transitions.
- **Haptics**: Always trigger `view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)` for tactile interactions.

### Dependency Management
- All dependencies are managed via **Version Catalogs** (`gradle/libs.versions.toml`).
- Use `ksp` for Room and Kotlin serialization.

---

## 🔋 Performance & Safety Guidelines

- **Thermal Throttling**: `GemmaInference` includes a `checkThermalAndThrottle()` loop. If adding heavy background tasks, respect the device's thermal state.
- **Memory Safety**: Local AI is memory-intensive. Always close the LiteRT `Conversation` and `Engine` when switching models or clearing sessions to prevent native OOMs.
- **Main Thread Hardening**: Never perform DB or AI operations on the Main thread. Use `viewModelScope.launch(Dispatchers.Default)` or specific executors.

---

## 🧪 Developer Mode
Long-press the **Settings Gear** in the app to access the Developer Suite. This allows you to toggle:
- **Verbose Logging**: See raw engine outputs in Logcat.
- **Live Prompt Logging**: View the exact distilled context being sent to the AI in real-time.
- **Hardware Selection**: Force specific backends (NPU/GPU) for testing.
