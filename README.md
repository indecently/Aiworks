AIworks v0.7.0.4
Because your data deserves better than being Big Tech’s side chick.

Introduction: Why This Damn Thing Exists
AIworks was built for one glorious reason: to give you a genuinely powerful, 100% offline AI that doesn’t phone home, sell your soul, or treat your privacy like an optional DLC.

This isn’t some polished corporate product from a 200-person engineering team. Nah. This is a regular dude rage-building in Android Studio with an army of AI agents because the current options are either expensive, creepy, or both. Every update is me learning on the fly and still somehow shipping something that feels premium as hell. You’re welcome.

v0.7.0.4: SmoothPillyPilly
We went full "Control Island" on this one. If it isn't a rounded pill, it doesn't belong in the UI.

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

v0.7.0.2: The Monster Session
This update is massive. We ripped out the engine’s guts, standardized the feel, and finally made the internals as clean as the UI.

Haptic Engine Synchronization: Every button, toggle, and slider now gives a crisp "VIRTUAL_KEY" haptic impact.

Engine Stabilization: Enforced a one-media-type rule (Image OR Audio) and reverted context buffer to 4,096 tokens to prevent GPU/RAM meltdowns.

Structural Modularization: Any file over 500 lines was decomposed into logical modules.

Core Features (The Stuff That Actually Slaps)
1. 100% Offline AI

Running Gemma 4 E2B locally with native audio understanding.

No internet. No tracking. No “please wait while we consult the mothership.” Just raw, private intelligence in your pocket.

2. Long-Term Memory (LTM)

We run a background summarization demon that distills your conversation history so the AI actually remembers who you are without having a stroke after 20 messages.

You stay in full control—view it, edit it, delete it, or tell it to forget that one weird phase you went through.

3. Privacy Weapons

Incognito Mode: Burn-after-reading chats that don’t touch your long-term memory. For when you need to be unhinged.

Secure Assistant: Summon the AI from anywhere (yes, even on the lock screen) without exposing the rest of your digital life.

Project Status
Built in: Android Studio on Windows.

By: A guy who definitely wasn’t a professional Android dev when this started.

Engine: LiteRT (TensorFlow Lite).

Current vibe: Shipping fast, breaking things responsibly, and making the most private mobile AI experience on the planet.
