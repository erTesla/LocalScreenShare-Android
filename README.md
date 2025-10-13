# 🖥️ LocalScreenShare  
### Zero-Setup Screen Sharing Between Android Devices (and PCs)

[![Made with Kotlin](https://img.shields.io/badge/Made%20with-Kotlin-7F52FF.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20PC-blue)](#)
[![Status](https://img.shields.io/badge/Status-Experimental-orange)](#)

---

**LocalScreenShare** is a minimal, open-source Android project that lets you instantly share your phone screen with another phone — or even mirror your PC’s screen — **over local Wi-Fi or hotspot**, with **no internet required** and **no ads**.

---

## 🚀 Features

- 📱 **Phone-to-Phone Screen Share** — Works seamlessly over Wi-Fi or hotspot using local sockets.  
- 🔐 **Secure Pairing** — Each session requires a random 6-digit PIN.  
- 🧭 **No External Servers** — 100 % local; no cloud, no tracking.  
- 🎚️ **Adjustable Frame Rate** — Choose between 10–60 fps for smoothness or battery efficiency.  
- 🔍 **10 % Zoom-Out Receiver View** — Adds a small border for a better full-screen experience.  
- 🖥️ **PC-to-Phone Mirroring Mode** — Mirror your Windows/Mac/Linux desktop to your phone using the included sender script.  
- 🧠 **Hotspot-Friendly** — The sender can host its own hotspot; the receiver simply joins.  
- ⚡ **Lightweight & Ad-Free** — Built in pure Kotlin with zero SDK bloat or tracking.

---

## 🧩 How It Works

1. The **sender** captures the display using Android’s [`MediaProjection`](https://developer.android.com/reference/android/media/projection/MediaProjection).  
2. Frames are compressed to **JPEG** and streamed over **TCP sockets**.  
3. The **receiver** decodes and renders the frames live in real time.  
4. Both devices communicate directly on the **same LAN or hotspot** — no servers involved.

---

## 🛠️ Tech Stack

| Category | Tools / Libraries |
|-----------|-------------------|
| **Language** | Kotlin |
| **UI** | Jetpack Compose |
| **Async** | Coroutines / LiveData |
| **Screen Capture** | MediaProjection / ImageReader |
| **Zoom & Pan** | PhotoView |
| **Networking** | TCP + NSD (Network Service Discovery) |

---

## 🧰 Why I Built It

After seeing countless Reddit posts about screen-mirroring apps filled with **ads, setup hassles, and cloud logins**,  
I wanted to make something that just *works*.

This started as a **weekend experiment** to learn Android’s `MediaProjection` API and socket networking —  
and it turned out surprisingly smooth.

---

## 🔧 Future Roadmap

- 🎞️ H.264 video encoding for higher efficiency  
- 🔊 Audio streaming support  
- 🌐 WebRTC mode for browser-based viewing  
- 💻 Cross-platform UI polish

---

## ⚙️ Setup & Usage

### 📲 Android-to-Android
1. Clone the repository:
   ```bash
   git clone https://github.com/aneesxy/LocalScreenShare.git
   ```
2. Open in **Android Studio**.
3. Build and install the app on both devices.
4. Connect both to the same Wi-Fi or hotspot.
5. On the **sender**, start broadcasting.
6. On the **receiver**, connect using the displayed IP + PIN.

### 💻 PC-to-Phone
1. Run the included Python sender script:
   ```bash
   python sender.py
   ```
2. Connect your phone as the receiver.
3. Enjoy local desktop mirroring!

---

## 🧪 Disclaimer

This is an **experimental educational project**, not a production-grade casting solution.  
System-level features like **Miracast** or **AirPlay** require privileged APIs,  
so this app uses its own lightweight local protocol.

---

## 📜 License

This project is licensed under the [MIT License](LICENSE).

---

## 💡 Author

**Anees** — Android Developer & Creative Technologist  
📍 [GitHub](https://github.com/realaneesani) • [LinkedIn](https://www.linkedin.com/in/aneesan/) 

> “I built this for fun after seeing how complicated simple screen-sharing apps had become.”

---

⭐ If you like this project, consider giving it a **star** — it helps others discover it!
