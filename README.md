# xBlock AdBlocker - Android Xposed Module

xBlock is a lightweight, data-driven Xposed module designed to block advertisements and trackers at the DNS, UI, and WebView levels. It follows modern Android 15 standards and is compatible with **LSPosed** (Root) and **NPatch / LSPatch** (Non-Root).

## 🚀 Features

- **Double Defense Logic**: Combines network-level DNS blocking with application-level UI/SDK hiding.
- **DNS Sinkholing**: Intercepts `java.net.InetAddress` and `DnsResolver` to redirect ad domains to `0.0.0.0` (Sinkhole).
- **UI & Intent Hiding**: Uses the **Exodus SDK** database to identify and hide ad-related views and prevent ad activity/service launches.
- **WebView Interception**: Blocks ad requests directly in `WebView` using `shouldInterceptRequest`.
- **Purely Data-Driven**: No hardcoded ad lists. All blocking logic is driven by your downloaded AdGuard and Exodus filter lists.
- **Modern UI**: Clean Android 15 Edge-to-Edge interface with independent update tracking for each filter list.

## 📖 Usage

### Method 1: LSPosed (Rooted)
1. Install the `xBlock` APK on your device.
2. Open the **LSPosed Manager**.
3. Enable the **xBlock** module.
4. Select the **Scope** (apps you want to block ads in).
5. Force stop or restart the target apps.

### Method 2: NPatch / LSPatch (Non-Root)
1. Install the `xBlock` APK on your device.
2. Open **NPatch** or **LSPatch**.
3. Create a new patch for your target application.
4. Select **xBlock** as the module to inject.
5. Install and run the patched application.

## 🏗 Technical Details
- **Hooking API**: Uses the modern **LibXposed API** (`v102.0.0`) for high-performance and stable hooking.
- **Minimum Xposed Version**: 101.
- **Data Sources (Online Filters)**:
    - [AdGuard DNS Filter](https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt) for network-level blocking.
    - [Exodus Privacy Trackers](https://reports.exodus-privacy.eu.org/api/trackers) for SDK/Package identification.
- **Data Sharing**: Implements a `ContentProvider` for real-time, efficient data sharing between the manager app and hooked processes.
- **Memory Efficiency**: Uses streaming parsers to handle large filter lists without high memory overhead.
- **Android 15 Ready**: Supports Edge-to-Edge display and handled Window Insets for the latest Android versions.

## ⚠️ Disclaimer
This tool is intended for **educational and personal use**. Some applications may have terms of service that prohibit ad-blocking. The developers are not responsible for any misuse or legal consequences of using this software.

## 📄 License
This project is licensed under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for details.
