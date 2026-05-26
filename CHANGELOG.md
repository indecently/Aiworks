# Changelog

## v0.7.0.6: Hardware & Security Hardening
We’re pushing into "Vault" territory. Faster inference, tighter security, and zero-friction entry.

Hardware & Performance:
- **NPU Acceleration**: Added native NPU (Neural Processing Unit) support. The app now scans for `liblitert_dispatch` and `libQnn` libraries. If your hardware is packing an NPU, you’ll see the toggle in settings for high-efficiency, low-thermal inference.
- **Accelerator Fallbacks**: Smart logic ensures that if your preferred backend (NPU/GPU) fails, the engine automatically settles on the best available fallback without crashing the session.
- **LTM Inference Starvation Fix**: Resolved a concurrency bug where background memory distillation could interrupt active user inference sessions.
- **Token Estimation Optimization**: Refactored the context window loop to use a character-budget strategy, eliminating redundant tokenization passes and saving ~300ms of latency.
- **Deep Reasoning UI Migration**: Moved the reasoning toggle from deep settings to a tactical "Thinking" pill inside the chat bar for zero-friction access.

Security & Privacy Overhaul:
- **Integrated Lock Setup**: Nuked the App Password Lock pop-up window. Creating a password is now an inline, "pill-inside-pill" expansion within the Security card. It’s smooth, contextually grounded, and matches the app’s fluid aesthetic.
- **Custom Keypad Vault**: Redesigned the Lock Screen from scratch. No more clunky system keyboards—just a custom, tactile 3x4 numerical grid built directly into the UI.
- **Auto-Enter Logic**: The manual "Unlock App" button is dead. The app now listens to your input in real-time and grants access the instant the correct password is recognized.
- **Haptic Pulse Engine**: Every tap on the numerical keypad and the new action keys provides a crisp physical vibration response, making the unlock experience feel mechanical and premium.
- **Incognito Quick-Entry**: Added a dedicated 'Chat' action key to the Lock Screen. One tap jumps you straight into an anonymous, burn-after-reading session for those "need to know now" moments.
Developer Utilities & Inspection:
- **Engine Vitals Overlay**: Implemented real-time performance tracking. Developers can now monitor TTFT (Time To First Token) and generation speed (tokens/sec) directly in the UI.
- **Prompt Inspection Engine**: Added the ability to peek behind the curtain. The app now exposes the raw distilled context and final prompt payloads being dispatched to LiteRT.
- **Developer Mode Access**: Integrated a long-press gesture on the settings icon to jump into the internal hardware and inference configuration suite.
- **Memory Distillation Engine**: Upgraded the Long-Term Memory (LTM) system from a simple "append" model to a recursive distillation model. The AI now merges new conversation segments into a single, high-density "Core Memory" pill, eliminating factual redundancy and maximizing context window efficiency.
Core Engine Upgrades:
- **LiteRT-LM 0.12.0 Migration**: Jumped to the latest runtime release. This brings under-the-hood stability improvements and better coordination with the Gemma 2B architecture.
- **Dependency Hardening**: Updated version catalogs to ensure all AI-related libraries are synced with the 0.12 baseline.

## v0.7.0.5: Deep-Tissue Performance Audit
We went under the hood with a scalpel. If it wasn't contributing to a fluid scroll, it was refactored or buried.

Scroll Fluidity & List Optimization:
- **Lazy Menu Injection**: Dropped the memory footprint of the session list. Dropdown menus are now lazily composed only upon interaction, saving massive composition overhead during rapid scrolls.
- **Background Filtering Engine**: Shifted all search and sort logic off the UI thread. The ViewModel now computes the filtered list on a background dispatcher, ensuring the main thread only handles rendering.
- **Animation Softening**: Retuned the stiffness of item selection animations. It’s still snappy, but uses fewer GPU cycles to get there.

State Management & Threading:
- **Root-Level Recomposition Shield**: Isolated the `modelStatus` observation. The entire app no longer redraws when the AI engine is loading; only the overlay gets the update. 
- **Main Thread Hardening**: Guaranteed zero-block startup. Database cleanup and session pruning moved to dedicated IO threads, keeping the home screen liquid from the first frame.
- **Flow-Based Search**: Transitioned search queries to persistent StateFlows. One-way data flow, zero side effects.

## v0.7.0.4: SmoothPillyPilly
We went full "Control Island" on this one. If it isn't a rounded pill, it doesn't belong in the UI.

Appearance & Personalization (The Layout Shift):

Home Screen Wallpapers: You can now set a custom background for the Home screen. We added a 24dp aesthetic blur and a tonal overlay so your chats stay legible while your home screen looks fire.

The Dynamic Layout Toggle: Hate the top bar? Move it. Added a "Bottom Search Bar" toggle that relocates the entire search/settings pill to the bottom.

Dynamic FAB Logic: The Floating Action Button is now reactive. If the bar is at the bottom, the FAB stacks center-stage above it. If the bar is at the top, it anchors back to the bottom-right corner. It’s smart, balanced, and one-hand friendly.

Extended FAB Refactor: Nuked the old label-icon separation. Expanded chat options are now unified Material 3 pills that stack with perfect coordinate stability. No jumps, no skips, just smooth expansion.

The Control Island (Header Redesign):

Single-Row Power: Nuked the stacked dual-row header. Everything you need—Home, Search, Filter, and Settings—is now wrapped in a single, elevated master capsule.

Bubble-on-Bubble: Nested the search bar inside the master island. It looks like a premium hardware-software hybrid and gives you back massive vertical space for your chats.

Spinning Gear: Added a 360-degree tactile rotation to the settings gear. Because clicking things should feel good.

Expandable Capsule Settings (The Accordion Pass):

Localized Expansion: Settings are now collapsible capsules. No more scrolling through a mile of toggles. Open what you need, collapse what you don't.

Fluid Dimension Animations: Integrated animateContentSize everywhere. The capsules glide open and shut instead of snapping like a cheap web app.

Chevron Rotation: The expansion arrows now rotate 180 degrees in real-time, providing clear visual feedback on state.

120FPS Performance & Fluidity Optimization:

GPU Offloading: Shifted complex layout masks and message bubble clipping to the hardware-accelerated graphics layer. We're taxing the GPU so the CPU can focus on the AI.

Recomposition Caching: Implemented aggressive caching for date formatting and list filtering. The UI thread stays idle and liquid even when you have hundreds of sessions.

Zero-Flicker FAB: Fixed a shadow pop glitch in the FAB menu. It now settles perfectly with zero terminal-frame artifacts.

Navigation Stability (Backstack Hardening):

Router Guards: Implemented a universal destination guard in the navigation core. No more duplicate screens pushing onto the backstack.

Rigid Click Throttling: Once you tap a transition icon, input freezes until you're there. One tap, one result.

Fixed Back-Tap: Resolved the "double back-tap" bug. A single swipe now reliably takes you home.

Favorites Redesign:

Mini-Pill List: Overhauled the clunky circular avatars. Favorites are now sleek horizontal mini-capsules with integrated star icons and centered labels. It's high-density, high-class, and fits perfectly under the header island.

## v0.7.0.2: The Monster Session
This update is massive. We ripped out the engine’s guts, standardized the feel, and finally made the internals as clean as the UI.

Haptic Engine Synchronization: Every button, toggle, and slider now gives a crisp "VIRTUAL_KEY" haptic impact.

Engine Stabilization: Enforced a one-media-type rule (Image OR Audio) and reverted context buffer to 4,096 tokens to prevent GPU/RAM meltdowns.

Structural Modularization: Any file over 500 lines was decomposed into logical modules.
