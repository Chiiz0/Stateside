<div align="center">

# Stateside (美境)

**All-in-One Rootless US SIM Carrier Environment & Per-App US English Workbench for Android 14–17**

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-14%20~%2017%20(API%2034--37)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![16KB Page Size](https://img.shields.io/badge/16%20KB%20Page%20Size-Verified-0D9488)](#build-from-source)
[![CI](https://github.com/Chiiz0/Stateside/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Chiiz0/Stateside/actions/workflows/android-ci.yml)
[![Release](https://img.shields.io/github/v/release/Chiiz0/Stateside?label=Download%20APK&color=1D4ED8)](https://github.com/Chiiz0/Stateside/releases)

[简体中文](README.md) | **English** · [Architecture](docs/ARCHITECTURE.md) · [OEM Setup Guide](docs/OEM_SETUP_GUIDE.md) · [FAQ](docs/FAQ.md)

</div>

---

## Overview

**Stateside (`app.stateside.env`)** consolidates what previously required **three separate geek utilities** (`Shizuku` + `Simlet / Nrfr` + `Per-App Language`) into a single, zero-dependency, one-tap Android application (~6 MB R8 release APK):

- Keeps your **system UI language unchanged** (e.g., Simplified Chinese) while automatically locking **TikTok, X, Claude, ChatGPT**, and any user-selected apps to **`en-US` (US English)**—including cloned Dual-App profiles (`user 999`) and Google Play Dynamic Delivery language splits (`split_config.en.apk`).
- Spoofs active Dual-SIM country ISO at runtime to **US (`us`) while keeping your original status-bar carrier display untouched by default**—or lets you customize status-bar text **per SIM slot** (e.g., `X` / `Zero` / `T-Mobile`), cleanly overriding even **5G SA NAS NITZ VIP network labels** (such as China Mobile GoTone Diamond cards) via 3GPP `EF_PNN` injection without Root.
- Includes a **Dual-Mode US Timezone Engine** (`America/Los_Angeles`, `America/New_York`, etc.): choose **Keep Native Timezone** (default), **Global US Timezone Sync**, or **Per-App Companion Mode** (switches to US timezone automatically right before launching TikTok/selected apps and silently restores your home timezone ~4s after you exit).
- Natively overcomes **Android 15 QPR / Android 16 / Android 17 `CVE-2025-48617`** (`SecurityException: overrideConfig cannot be invoked by shell`) via in-process `Instrumentation` (`am instrument -w --no-restart`) + `UiAutomation.adoptShellPermissionIdentity()` running under the app's own `10xxx` UID.

---

## Key Highlights

1. **Self-Contained Local Wireless ADB + Root Fast-Track**:
   - Built-in `127.0.0.1` Wireless Debugging client (TLS 1.3 + SPAKE2 pairing) with loopback `EADDRINUSE` port radar that bypasses Android `targetSdk 36` `NETLINK_ROUTE` SELinux blocks and IPv6 scope-ID mismatches.
   - Supports manual `Port + 6-Digit Code` notification input for AP-isolated campus Wi-Fi or strict VPN `tun` environments, plus automatic zero-pairing pass-through on Rooted devices (`Magisk` / `KernelSU` / `APatch`).
2. **Non-Disruptive Per-Space `en-US` Engine**:
   - Inspects per-user package installation (`user 0` + OEM Clone Profile `user 999`) before applying `en-US`, force-stopping an app **only** when its locale actually changed—never interrupting active Claude or ChatGPT conversations during boot recovery.
3. **Safe-Launch Dock, Badged Desktop Guard Shortcuts & Wi-Fi Boot Auto-Heal**:
   - Generates home-screen shortcuts compositing the target app's real icon with a `US` shield badge: launches in `0.1s` when US mode is active, or intercepts post-reboot domestic fallback and auto-restores US mode in ~2s before launching.
   - Schedules `BootRestoreJobService` with `NetworkCapabilities.TRANSPORT_WIFI` so post-reboot recovery triggers automatically the moment trusted Wi-Fi connects.
4. **Two-Step Clean Domestic Restore**:
   - Derives the physical SIM's true ISO and carrier name from read-only hardware MCC/MNC, writes it back until settled on the Telephony handler, and drops the override layer (`null`) while resetting app locales to follow system default.

---

## Privacy & Security

- **Strictly Localhost (`127.0.0.1`)**: `INTERNET` permission is used exclusively to communicate with the on-device `adbd` loopback socket. Zero external servers, zero analytics, zero telemetry.
- **Memory-Only Non-Persistent SIM Override**: Uses `CarrierConfigManager.overrideConfig(subId, bundle, false)`—never modifies baseband NVRAM/EFS partitions. Rebooting naturally resets SIM state. See [SECURITY.md](SECURITY.md).

---

## Build from Source

Requires **JDK 17+** (JDK 21 recommended) and **Android SDK Platform 36**.

```bash
git clone https://github.com/Chiiz0/Stateside.git
cd Stateside

# Run unit tests and build both Debug and signed R8 Release APKs
./gradlew testDebugUnitTest :app:assembleDebug :app:assembleRelease

# Verify 16 KB ELF & ZIP alignment for Android 15/16/17 devices
$ANDROID_HOME/build-tools/36.1.0/zipalign -c -P 16 4 app/build/outputs/apk/release/app-release.apk
```

---

## License

Licensed under the **[Apache License 2.0](LICENSE)**. Third-party attributions and architectural acknowledgements are listed in **[NOTICE](NOTICE)**.
