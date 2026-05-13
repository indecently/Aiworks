# LocalGemmaChat

LocalGemmaChat is a streamlined, privacy-focused Android application designed to provide a fully offline AI chat experience. By leveraging Google's Gemma model and the LiteRT-LM runtime, the app enables high-quality text generation directly on your device without requiring an internet connection.

## Features
*   **On-Device AI Chat:** 100% offline processing for maximum privacy.
*   **Streaming Responses:** Real-time text generation for a responsive feel.
*   **Vibrant Material 3 Design:** Beautiful, energetic UI with full edge-to-edge display and expressive motion.
*   **Adaptive Layout:** Seamlessly scales from mobile phones to tablets and foldables.
*   **Customizable Inference:** Adjust Temperature and Max Tokens to fine-tune AI behavior.

## Setup Instructions

### 1. Download the Gemma Model
To use this app, you need a compatible `.litertlm` or `.bin` model file.
*   Download the **Gemma 4 E2B IT** model from [Google AI Edge](https://huggingface.co/google/gemma-4-e2b-it-litertlm).
*   Transfer the file to your Android device's storage.

### 2. Configure the App
1.  Open LocalGemmaChat.
2.  Navigate to **Settings** (icon in the top right).
3.  Use the **Model Path** picker to select the downloaded model file.
4.  Tap **Load / Reload Model**.
5.  Wait for the status to change to "Gemma Ready".

### 3. Start Chatting
Go back to the **Welcome** or **Chat** screen and start asking questions!

## Building the Project
*   Open the project in **Android Studio Ladybug (2024.2.1)** or newer.
*   Ensure you have **JDK 21** installed and configured in Gradle settings.
*   Sync the project with Gradle files.
*   Run the `:app` module on an Android 12+ device (API 31+) for the best experience.

## Performance Notes
*   **GPU Acceleration:** The app is configured to use GPU acceleration via OpenCL. This provides a significant speedup on compatible devices.
*   **Memory Usage:** LLM inference is memory-intensive. For best results, use a device with at least 8GB of RAM.
*   **First Run:** The first message after loading a model might take a few extra seconds as the engine initializes the KV cache.

## Privacy
Your conversations are never uploaded to any server. All processing happens locally on your device.

---
Built with Jetpack Compose, Navigation 3, and LiteRT-LM.
