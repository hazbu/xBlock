# XBlock AdBlocker - Android Xposed Module

XBlock is a lightweight Xposed module designed to block advertisements and trackers at the DNS, UI, and WebView levels. It is compatible with **LSPosed** (Root) and **NPatch / LSPatch** (Non-Root).

## 🚀 Features

- **DNS Sinkholing**: Intercepts `java.net.InetAddress` to redirect ad domains to `0.0.0.0` (Sinkhole), preventing ads from loading without crashing the app.
- **UI Ad-SDK Hiding**: Hooks into `ViewGroup.addView` to detect and hide common ad SDK views (AdMob, Facebook Ads, AppLovin, etc.) by setting their dimensions to 0x0 and visibility to `GONE`.
- **WebView Interception**: Intercepts `WebView` requests to block ad domains directly at the browser level using `shouldInterceptRequest`.
- **Dynamic AdGuard Filters**: Fetch the latest ad-blocking database directly from AdGuard's official DNS filter list.
- **Management UI**: A simple interface to manually update filter lists and monitor the number of blocked domains.

## 🛠 Installation

### Prerequisites
- Android Studio Ladybug or newer.
- A device with **LSPosed** installed (for Root) or **NPatch/LSPatch** (for Non-Root).

### Build Instructions
1. Clone the repository.
2. Open in Android Studio.
3. Build the project:
   ```bash
   ./gradlew :app:assembleDebug
   ```
4. The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

## 📖 Usage

### Automated Releases (CI/CD)
This repository is configured with GitHub Actions. Every time you push a tag starting with `v` (e.g., `v1.0`), an APK will be automatically built and attached to a new GitHub Release.

### Method 1: LSPosed (Rooted)
1. Install the `app-debug.apk` on your device.
2. Open the **LSPosed Manager**.
3. Enable the **XBlock** module.
4. Select the **Scope** (apps you want to block ads in).
5. Force stop or restart the target apps.

### Method 2: NPatch / LSPatch (Non-Root)
1. Install the `app-debug.apk` on your device.
2. Open **NPatch** or **LSPatch**.
3. Create a new patch for your target application.
4. Select **XBlock** as the module to inject.
5. Install and run the patched application.

## 🏗 Technical Details
- **Hooking**: Uses `XposedHelpers` to hook `java.net.InetAddress`, `android.view.ViewGroup`, and `android.webkit.WebViewClient`.
- **IPC**: Uses `XSharedPreferences` (LSPosed API 93+) for secure, cross-process data sharing between the module app and hooked processes.
- **Filters**: Parses AdGuard DNS syntax (`||domain^`) into a high-performance `HashSet` for O(1) domain lookups.

## ⚠️ Disclaimer
This tool is intended for **educational and personal use**. Some applications may have terms of service that prohibit ad-blocking. The developers are not responsible for any misuse or legal consequences of using this software.

## 📄 License
This project is licensed under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for details.
