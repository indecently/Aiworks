# AGENTS.md - AI Coding Agent Guide for Aiworks

**Project**: 100% offline Android AI assistant with local LiteRT inference, long-term memory, and privacy-first architecture.

## Architecture Overview

### Core Layers
1. **UI Layer** (`ui/`): Compose-based MVVM with reactive StateFlow patterns
   - `ChatScreen`, `IsolatesScreen` (session list), `SettingsScreen`
   - Adaptive layout using Material3 responsive design
   - Custom components: `MessageBubble`, `ChatInput`, `ModelStatusIndicator`

2. **ViewModel Layer** (`ChatViewModel`): Single god-ViewModel orchestrates all state
   - **Critical**: Uses separate flows for DB messages (`dbMessages`) vs streaming AI responses (`_streamingMessage`) to prevent UI thrashing on token arrival
   - Manages inference lifecycle, recording, TTS, session/message operations
   - Thread-safe inference via `engineMutex`

3. **AI Engine** (`ai/GemmaInference`): LiteRT-based local inference
   - Loads `.litertlm` models with hardware acceleration (GPU/NPU with fallback to CPU)
   - **Key optimization**: Dual executor prioritization—separate high-priority thread for user inference, background thread for auto-summarization (prevents memory context compression from blocking chat)
   - Conversation memory management with `SessionId`-based isolation
   - Token estimation for UX feedback

4. **Data Layer** (`data/`): Room + DataStore
   - `ChatDatabase` + `ChatRepository` for chat history persistence
   - Entities: `ChatSession` (metadata), `ChatMessageEntity` (content + role)
   - **Incognito mode**: Session marked with flag, auto-deleted on app close (via `deleteIncognitoSessions()`)

5. **Service Layer** (`service/`, `util/`, `settings/`):
   - `GemmaAssistantService`: Lock screen AI trigger via intent action
   - `SettingsManager`: DataStore-backed config (model path, hardware accelerator, lock password, LTM settings, etc.)
   - `AudioRecorder`, `TtsManager`: Media I/O
   - `ModelPersistenceService`: Background resumption of LTM compression

6. **DI Container** (`GemmaContainer`): Manual singleton pattern
   - All major singletons accessed here (no Hilt/Dagger)

### Data Flow (Message → Response)
1. User input → `ChatViewModel.sendMessage()` via UI
2. VM fetches settings + session context via coroutines
3. `PromptArchitect` builds final prompt with LTM summary + context window
4. `GemmaInference.generateResponse()` streams tokens
5. Tokens accumulated in `_streamingMessage` Flow (NOT UI-wide update)
6. On completion, saved to Room with finalized metadata
7. DB message added → `dbMessages` Flow notifies UI

## Build & Development

### Gradle Setup
- **KTS-based**: `build.gradle.kts` with version aliases from `gradle/libs.versions.toml`
- **Kotlin**: 2.2.10, Java 21
- **Compose**: BOM 2025.01.00, Material3 1.5.0-alpha19
- **LiteRT**: 0.12.0 (latest stable for Gemma 2B)
- **Room**: 2.8.4 with KSP compiler (no KAPT)

### Build Commands
```bash
# Clean build
./gradlew clean build

# Run app on device/emulator
./gradlew installDebug

# With live recomposition
./gradlew app:installDebug -c single-build-session

# Tests
./gradlew test
./gradlew connectedAndroidTest

# Model testing (manual)
# Place .litertlm file in `assets/` or use content:// URI picker in onboarding
```

### Key Build Configuration
- **minSdk**: 35 (Android 15) — requires modern Compose + newest LiteRT features
- **Optimization**: Minification enabled in release (ProGuard rules in `proguard-rules.pro`)
- **ABI**: arm64-v8a only (weight reduction for ~150MB model file)
- **NDK**: Implicit — LiteRT handles native dispatch

## Project Conventions & Patterns

### State Management (CRITICAL)
- **All UI state flows through `ChatViewModel`** — do NOT create local ViewModel instances per screen
- Use `StateFlow` for persistent state, `MutableStateFlow` for mutations
- **Observe with `.collectAsState()` inside Compose** — no raw `.collect()` in UI layer
- **Flow separation**: Expensive operations (search, sort) use `flowOn(Dispatchers.Default)` to avoid UI main thread blocking
  ```kotlin
  val filteredSessions = combine(sessions, searchQuery, sortOrder)
    .flowOn(Dispatchers.Default)  // ← Critical for scroll perf
    .stateIn(viewModelScope, ...)
  ```

### Coroutine Discipline
- **All async work in `viewModelScope`** — never use `GlobalScope`
- Use `withContext(Dispatchers.IO)` for DB/file operations
- **Inference runs on dedicated high-priority dispatcher**:
  ```kotlin
  val userInferenceDispatcher = createExecutorService("gemma-user-inference", Thread.MAX_PRIORITY).asCoroutineDispatcher()
  // Background tasks (LTM compression) use separate lower-priority dispatcher
  ```
- Cancellation: Always check `Job` status before re-queuing inference

### Database Operations
- Room migrations: **Currently uses `.fallbackToDestructiveMigration()`** (not production-safe)
- DAO queries return `Flow<T>` for reactivity
- **Incognito sessions**: Mark with flag, never persist to external storage
- Batch deletes on app lifecycle: `repository.deleteIncognitoSessions()` in ViewModel init/onCleared

### Navigation
- **Manual backstack with `rememberNavBackStack()`** (androidx.navigation3 runtime)
- No deep linking or URI schemes currently
- Screen types: `Onboarding`, `Isolates` (list pane), `Chat` (detail pane), `Settings`, `ChatSettings`, `Developer`
- Session cleanup: When leaving `Screen.Chat`, call `viewModel.closeSession()` to release inference context

### Compose Patterns
- **Recomposition shield**: `GlobalLoadingOverlay` observes only `modelStatus` internally — prevents root recomposition during model load
- Custom components use `remember` + `derivedStateOf` to avoid recreating heavy objects
- Material3 Adaptive support enabled (`adaptive`, `adaptive-layout`, `adaptive-navigation3`)
- FAB positioning logic is reactive to bottom search bar toggle (see CHANGELOG v0.7.0.4)

### Settings Persistence
- **DataStore** (not SharedPreferences): All user prefs in `SettingsManager`
- Hot-reload on change: `computeAccelerator` setting triggers automatic model reload (line 212-219 in ChatViewModel)
- Lock password stored in plain text (DataStore encrypted by OS)

### Media & Models
- **Model loading**: Supports both file paths + content:// URIs (copy to internal storage for access)
- Model cache cleanup via `cleanupMediaCache()` when memory pressure detected (see `MainActivity.onTrimMemory`)
- Audio files stored in cache directory with TTL-based cleanup
- TTS output cached per session to avoid re-rendering

## Critical Workflows

### Adding a New Feature
1. **UI**: Create screen in `ui/screens/` as `@Composable`
2. **State**: Add StateFlow to `ChatViewModel` if persistent; local state if ephemeral
3. **Data**: If persisting, add Room entity + DAO query
4. **Settings**: Add DataStore preference via `SettingsManager` if user-configurable
5. **Integration**: Wire into navigation in `MainActivity` + backstack handling
6. Example: See how Incognito Chat was added (flag in `ChatSession`, `_pendingIncognitoChat` flow, auto-delete logic)

### Debugging Inference
- **Enable via Developer Screen**: Toggle `Live Prompt Logging` + `Verbose Logging`
- **Inspection fields**: `_lastRawPrompt`, `_lastContextSummary`, `_ttftMs`, `_generationSpeed`
- **Check accelerator fallback**: Logs appear in logcat if backend fails; auto-switches to CPU
- **Model path**: Always stored in `SettingsManager.modelPath` after successful load

### Memory Optimization (Critical for On-Device Inference)
- **Do NOT create model copies**: Reuse singleton `GemmaInference` instance
- **LTM compression runs on background dispatcher** — never blocks UI
- **Session cleanup**: Call `closeSession()` when leaving chat, clears inference context + audio cache
- **Media cache**: `.litertlm` models can be 150MB+; internal storage capped at device space

### Testing
- Minimal test suite currently (see `ExampleUnitTest.kt`)
- **Manual testing path**: Place model in app's `assets/models/`, trigger onboarding, validate inference
- **Integration test**: Connect device, install APK, record audio input, verify TTS output
- **No unit tests for LiteRT inference** (JNI boundary; integration test only)

## Common Pitfalls & Gotchas

1. **Don't bypass `ChatViewModel`**: Create separate ViewModels only for Screen-specific non-shared state
2. **Don't use `.asReversed()` in @Composable body**: Message list is already reversed in ViewModel (line 55)
3. **Don't let background tasks starve inference**: Dual executor design is fragile; respect dispatcher precedence
4. **Don't persist test/debug settings**: Check `livePromptLogging` flag before writing inspection data
5. **Don't reload model without cancelling in-flight inference**: `stopGeneration()` first, then call `loadModel()`
6. **Don't forget `onIntentConsumed()` callback**: Digital Assistant lock-screen trigger tracks intent consumption to avoid chat stacking

## File Structure Quick Reference

```
app/src/main/java/com/birkneo/Aiworks/
├── MainActivity.kt                          # Entry point, nav setup, memory watchdog
├── ai/
│   ├── GemmaInference.kt                   # LiteRT engine + conversation management
│   ├── PromptArchitect.kt                  # Context window building + LTM summary
│   ├── ModelPersistenceService.kt          # Background LTM compression scheduler
│   └── ModelStatus.kt                      # Sealed class for model lifecycle
├── data/
│   ├── database/ChatDatabase.kt            # Room database singleton
│   ├── repository/ChatRepository.kt        # DAO abstraction
│   ├── dao/ChatDao.kt                      # Room queries (Flow-based)
│   └── entity/{ChatSession, ChatMessageEntity}.kt
├── ui/
│   ├── chat/
│   │   ├── ChatViewModel.kt                # Central state orchestrator
│   │   ├── ChatScreen.kt                   # Main chat UI
│   │   ├── {SessionOps, MessageOps, MediaOps, InferenceOps}.kt  # UI logic modules
│   │   └── components/{MessageBubble, ChatInput, ModelStatusIndicator}.kt
│   ├── isolates/IsolatesScreen.kt          # Session list
│   ├── settings/SettingsScreen.kt          # User config UI
│   ├── navigation/NavKeys.kt               # Screen sealed classes
│   └── theme/{Color, Type, Icons}.kt
├── util/
│   ├── AudioRecorder.kt                    # Audio I/O
│   └── TtsManager.kt                       # Voice synthesis
├── settings/SettingsManager.kt             # DataStore persistence
├── service/
│   ├── GemmaAssistantService.kt            # Lock screen integration
│   └── GemmaAssistantSessionService.kt     # Session audio handling
└── di/GemmaContainer.kt                    # Manual singleton DI
```

## Learning Path for New Contributors

1. **Read first**: README.md (product vision), CHANGELOG.md (recent patterns)
2. **Understand data flow**: Follow a message from `ChatScreen` → `ChatViewModel` → `GemmaInference` → Room → UI
3. **Study key files** (in order):
   - `MainActivity.kt` (structure)
   - `ChatViewModel.kt` (state)
   - `ChatScreen.kt` (UI pattern)
   - `GemmaInference.kt` (inference + threading)
4. **Try a feature**: Add a new inference parameter (e.g., `top-k` slider in Settings)
5. **Run it**: `./gradlew app:installDebug` → test in Android Studio Emulator

## Version Info
- **Aiworks**: v0.7.0.6
- **Kotlin**: 2.2.10
- **Compose**: 2025.01.00 (BOM)
- **LiteRT**: 0.12.0
- **Room**: 2.8.4
- **Min SDK**: 35 (Android 15)

