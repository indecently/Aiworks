AIworks (v0.7.0.0 Beta)
AIworks is a sovereign, unbranded, and entirely offline AI engine for Android. It is designed for those who want the power of a Large Language Model (LLM) without the cloud dependency, or branding bloat found in mainstream assistants.

AIworks focuses on mechanical utility—turning your mobile device into a high-performance local inference station.

🧠 The Philosophy
Privacy First: 100% on-device. No telemetry, no cloud logging, no data "phoning home."

No Branding: Minimalist UI. No corporate logos.


🛠 Features
Gemma 4 E2B-it Support: Optimized for Google’s latest local-first weights via the LiteRT-LM runtime.

Persistent Memory: Engineered for long-term context retention without sacrificing performance.

No-Asterisk Formatting: Hardcoded logic to ensure clean, human-readable output without unnecessary markdown fluff.

Hardware Accelerated: Fully utilizes GPU acceleration (OpenCL) for responsive, real-time streaming.

Modern Android Support: Native support for Android 15 and up, utilizing the latest system APIs for background stability and audio pipeline integration.

🏗 Architect’s Note
This project was built using a Conceptual-First Architecture. While I am not a traditional developer, I have managed the system design, internal logic, and multi-stage audits through specialized AI agents. This is not a regular "Vibe-Coded" software—built by an ai wth a simple "gemini build me an ai app" prompt, this was made for users by a user who values architectural integrity over corporate branding.

🚀 Setup Instructions
1. Download the latest APK from the Releases section of this repository.

2. To maintain a small footprint and respect licensing, the AI model is not included in the app download.

Download a compatible .litertlm file (Recommended: Gemma 4 E2B IT).

Save it to a folder on your Android device.

3. Open AIworks.

Go through the onboarding process.

Once the status reads "Gemma Ready," the engine is live.

⚙️ Technical Requirements
OS: Android 15.0+ (API 35+)

RAM: 8GB+ recommended for 2B models.

Storage: ~3GB of free space for the model weights.

---
Built with Jetpack Compose, Navigation 3, and LiteRT-LM.
