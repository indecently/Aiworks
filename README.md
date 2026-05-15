AIworks v0.7.0.2
Because your data deserves better than being Big Tech’s side chick.

Introduction: Why This Damn Thing Exists
AIworks was built for one glorious reason: to give you a genuinely powerful, 100% offline AI that doesn’t phone home, sell your soul, or treat your privacy like an optional DLC.

This isn’t some polished corporate product from a 200-person engineering team. Nah. This is a regular dude rage-building in Android Studio with an army of AI agents because the current options are either expensive, creepy, or both. Every update is me learning on the fly and still somehow shipping something that feels premium as hell. You’re welcome.

v0.7.0.2: The Monster Session
This update is massive. We ripped out the engine’s guts, standardized the feel, and finally made the internals as clean as the UI.

Haptic Engine Synchronization:

Tactile everything: Every button, toggle, and slider now gives a crisp "VIRTUAL_KEY" haptic impact.

Long-press feedback: Recording and heavy processing now trigger distinct vibrations. It feels premium because it is.

Back gesture support: Swiping back actually feels like something now. No more guessing if the system registered your gesture.

Engine Stabilization (aka we stopped crashing):

Peak memory management: Enforced a one-media-type rule (Image OR Audio) to prevent the GPU from having an absolute meltdown.

LMK prevention: Reverted the context buffer to 4,096 tokens. Turns out 8,192 was too much for the RAM to handle during multimodal peaks.

Responsive loading: Lowered initialization thread priority so the UI stays liquid while the 3.6GB model loads in the background.

Structural Modularization (The "Monster" Audit):

The 500-line rule: Any file over 500 lines was taken to the shed and decomposed into logical modules.

Cleaner architecture: Decomposed Onboarding, Isolates, and Settings into dedicated component packages.

Prompt Architect: Extracted the AI's "brain" logic into a standalone utility to ensure LTM injection and instructions stay prioritized.

Optimization Sweep:

Sampling Parameters fixed: Temperature, Top-K, and Top-P are now fully reactive mid-session. Change them in settings, and the next response follows suit immediately.

Import hygiene: Nuked a mountain of unused imports and orphaned code from the modularization move.

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
