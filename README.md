# 🛡️ Ironclad

> **Unbreakable, anti-bypass adult content blocking for Android.**  
> Built for single-player accountability with zero escape hatches during impulsive urges.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Android-9.0%2B%20(API%2028%2B)-green.svg)](https://developer.android.com)
[![F-Droid Ready](https://img.shields.io/badge/F--Droid-Ready-brightgreen.svg)](https://f-droid.org)

---

## 🎯 The Core Problem

Traditional porn blockers fail because of a fundamental design flaw: **the user holds the keys.**
In moments of impulsive urge:
1. You already know the PIN or master password you set.
2. You can switch off the VPN in quick settings in under 2 seconds.
3. You can clear app storage or uninstall the blocker from Settings.
4. Accountability partner apps compromise your personal privacy and create social friction.

**Ironclad is engineered with a different philosophy: Single-Player Structural Friction.**  
There are **zero master passwords**, **zero stored unlock keys**, and **no bypass shortcuts**. The only way to unlock is an unskippable **24-hour compulsory cooling-off quarantine** followed by an intense **120-character zero-backspace typing gauntlet**.

---

## 🏗️ Architecture: Dual Protection Tiers

Ironclad offers two distinct modes depending on whether you have a computer available:

```
+-------------------------------------------------------------------------+
|                                IRONCLAD                                 |
+------------------------------------+------------------------------------+
| Tier 1: Quick Shield (No PC)       | Tier 2: Iron Fortress (ADB Owner)  |
+------------------------------------+------------------------------------+
| • 60-second setup directly on phone| • Enterprise Device Policy Manager |
| • Encrypted DNS filtering VPN      | • System-level Private DNS (DoT)   |
| • Accessibility Guardian ejections | • RethinkDNS companion killswitch  |
| • Ejects from Settings & App Info  | • Kernel-enforced uninstall block  |
| • Intercepts VPN disconnects       | • Safe Mode disabled completely    |
+------------------------------------+------------------------------------+
```

### 1. Tier 1: Quick Shield (Zero PC Required)
* **Local DNS Filtering:** Runs a local loopback VPN service that intercepts DNS lookups and resolves through privacy-hardened family resolvers:
  * Primary: **Cloudflare Family DNS** (`1.1.1.3` / `1.0.0.3`)
  * Secondary: **CleanBrowsing Family Filter** (`185.228.168.168`)
* **Anti-Tamper Accessibility Guardian:** A background `AccessibilityService` that constantly monitors window state changes:
  * Detects entry into Android Settings (`com.android.settings`), App Info pages, and VPN management panels.
  * Detects attempts to clear app data, force stop, or toggle off protection.
  * Instantly triggers `GLOBAL_ACTION_HOME` to fling the user back to the home screen before any changes can be committed.

### 2. Tier 2: Iron Fortress (ADB Device Owner Provisioning)
Provisioned once via USB using standard Android Enterprise `DevicePolicyManager` APIs:
* **Uninstall Blocked (`setUninstallBlocked = true`):** The package manager physically rejects uninstallation attempts at the OS level.
* **Safe Mode Disabled (`UserManager.DISALLOW_SAFE_BOOT`):** Booting into Safe Mode is disabled in the kernel; third-party blockers cannot be deactivated by holding the power button.
* **Private DNS Enforcement (`DISALLOW_CONFIG_PRIVATE_DNS`):** Android's native DNS-over-TLS is locked to `family.cloudflare-dns.com` or CleanBrowsing without consuming the VPN slot.
* **RethinkDNS Companion Killswitch:** If you use [RethinkDNS](https://rethinkdns.com) as your primary firewall/VPN:
  * Ironclad designates RethinkDNS as the system always-on VPN (`setAlwaysOnVpnPackage(rethinkPackage, true /* lockdownEnabled */)`).
  * **Kernel Killswitch:** If RethinkDNS is stopped, killed, or disabled, Android's networking stack cuts **100% of network traffic**. The internet ceases functioning until the blocker is running again.

---

## ⏳ The 24-Hour Quarantine & Typing Gauntlet

To ensure deliberate intention rather than impulsive regret, unlocking requires completing the Ironclad Gauntlet:

```
[ Tap Unlock ] ──► [ 24-Hour Quarantine ] ──► [ 15-Min Window ] ──► [ 1-Hour Grace ]
                          │                           │                     │
                     Unskippable,              120-Char Typing       Settings unlocked.
                   Monotonic hardware         Challenge (Zero-      Relocks automatically
                      uptime lock.          Backspace Tolerance)        after 1 hour.
```

1. **Quarantine Phase (24 Hours):**
   * Anchored to `SystemClock.elapsedRealtime()` and encrypted hardware keystore preferences.
   * Modifying system time, clock time zones, or going into airplane mode has zero effect on the monotonic hardware uptime counter.
2. **Gauntlet Challenge (15 Minutes):**
   * A strictly timed 15-minute window opens after 24 hours.
   * You must type a 120-character randomized, unambiguous alphanumeric string (lookalike characters `0`, `O`, `1`, `l`, `I` are removed).
   * **Zero Backspace Tolerance:** Backspace is disabled. A single typo instantly wipes all typed progress back to character 0.
3. **Grace Period (1 Hour):**
   * Passing the challenge unlocks protection for exactly 60 minutes.
   * If the 15-minute window expires without completion, Ironclad automatically locks down for another 24 hours.

---

## 🚀 Setup & ADB Provisioning

### Quick Setup (Tier 1)
1. Install `app-debug.apk` onto your Android device.
2. Open Ironclad and tap **Activate Quick Shield**.
3. Grant the VPN connection prompt.
4. Enable the **Ironclad Anti-Tamper Guardian** under Accessibility settings.

### Enterprise Setup (Tier 2 - Maximum Security)
1. Enable **Developer Options** and **USB Debugging** on your phone.
2. Temporarily remove accounts from **Settings > Accounts** (Google, Samsung, etc. require removal only during initial Device Owner provisioning; they can be re-added immediately afterward).
3. Connect your device via USB and verify ADB connection:
   ```bash
   adb devices
   ```
4. Run the device owner provisioning command:
   ```bash
   adb shell dpm set-device-owner io.github.ironclad/.admin.IronDeviceAdminReceiver
   ```
5. Open Ironclad. You will see **Status: Iron Fortress Active (Device Owner)**.
6. Re-add your Google/user accounts in Android Settings.

---

## 🛠️ Building From Source

### Prerequisites
* JDK 17 or JDK 21
* Android SDK (API 35 platform, Build-Tools 34.0.0+)

### Build Commands
```bash
# Clone the repository
git clone https://github.com/Hex007/ironclad.git
cd ironclad

# Run test suite
./gradlew test

# Compile Debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔒 Privacy & Threat Model

* **100% FLOSS:** Zero proprietary Google Play Services dependencies, zero analytics, zero crash-reporting telemetry.
* **No Account Required:** No phone numbers, no emails, no remote servers.
* **Full Autonomy:** Built for self-discipline without relying on accountability partners or external gatekeepers.

---

## 📄 License

Ironclad is licensed under the **GNU General Public License v3.0 (GPLv3)**.  
See [LICENSE](LICENSE) for details.
