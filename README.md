# VRBridge Input ⌨️🥽

![Android Version](https://img.shields.io/badge/Android-14%2B-3DDC84?logo=android)
![Meta Quest](https://img.shields.io/badge/Meta_Quest-3-045F92)
![License](https://img.shields.io/badge/License-MIT-blue)

**VRBridge Input** is an Android application that transforms your smartphone into a Bluetooth HID (Human Interface Device) bridge for the Meta Quest 3. 

Since the Meta Quest 3 lacks native support for physical AZERTY keyboards, this app solves the problem by intercepting your physical keyboard's inputs (connected via USB-C or Bluetooth to your phone), remapping them on-the-fly, and sending standard QWERTY HID reports to the VR headset. As a bonus, it turns your phone's touchscreen into a fully functional VR trackpad!

## ✨ Features

- **AZERTY to QWERTY Remapping:** Type naturally on your French physical keyboard; the app translates it instantly for the Quest 3.
- **Virtual Trackpad:** Use your phone's screen as a mouse with left/right click and scroll capabilities.
- **Physical Mouse Passthrough:** Connect a USB-C mouse to your phone and pass the raw movements directly to the VR headset.
- **Persistent Background Connection:** Utilizes Android 14/15 Foreground Services (`connectedDevice`) to ensure your keyboard stays connected even when the phone screen is off.

## 🛠️ Requirements

- **Android Device:** Running Android 14 or 15 (API Level 34+).
- **VR Headset:** Meta Quest 3 (or any device accepting standard Bluetooth Composite HID connections).
- **Input:** A physical AZERTY keyboard connected to your Android phone.

## 🚀 How It Works

1. The Android app requests the `BluetoothProfile.HID_DEVICE` proxy from the system.
2. It registers a **Composite HID Report Descriptor** (Keyboard + Mouse).
3. The Meta Quest 3 pairs with the phone, recognizing it as a standard input device.
4. The app intercepts `KeyEvent` and `MotionEvent`, maps them according to standard USB HID Usage Tables, and dispatches byte arrays over Bluetooth.

## 📦 Installation & Setup

*(Instructions to be added once the initial release is published)*
1. Clone the repository: `git clone https://github.com/yourusername/VRBridge Input.git`
2. Open the project in **Android Studio**.
3. Build and deploy to your Android 14+ device.
4. Grant the necessary Bluetooth permissions upon first launch.
5. Pair your phone with your Meta Quest 3 via the Quest's Bluetooth settings.

## 🗺️ Roadmap / Implementation Tasks

- [x] Define OpenSpec architecture.
- [x] Create Composite HID Report Descriptor (Keyboard & Mouse).
- [ ] Implement AZERTY-to-QWERTY Key Mapper.
- [ ] Setup Bluetooth HID Foreground Service.
- [ ] Build Input Interception UI (Trackpad).
- [ ] Polish Edge-to-Edge UI for Android 15.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.