# Ironclad

An adult content blocker built because existing tools are too easy to bypass. Ironclad eliminates instant escape routes using an unskippable 24 hour hardware lock.

## What It Does

1. Blocks over 1.2 million adult domains locally on device.
2. Drops encrypted DNS bypasses on port 443 and port 853.
3. Enforces strict SafeSearch on Google, Bing, DuckDuckGo, and YouTube.
4. Prevents uninstallation and safe mode evasion using Android Device Owner.
5. Locks deactivation behind a 24 hour hardware timer and a zero mistake typing test.

## How It Works

1. Tier 1 Quick Shield: Runs a local DNS filtering VPN and an Accessibility service that kicks you to the home screen if you try to open Settings or uninstall.
2. Tier 2 Iron Fortress: Uses Android Enterprise Device Owner to physically block uninstallation, disable safe mode, and cut network traffic if the filter stops.
3. The Unlock Protocol: Unlocking requires waiting out a 24 hour timer anchored to hardware uptime, followed by typing a 120 character string without backspace.

## Installation

1. Download the prebuilt APK from release/ironclad.apk
2. Install and launch the application on your device.

## Setup

Tier 1 Phone Setup:
1. Tap Arm Shield to enable the local filter.
2. Tap Enable under Anti Tamper Guardian to turn on Accessibility protection.

Tier 2 Computer Setup:
1. Enable USB debugging on your device.
2. Run this command in your computer terminal:

adb shell dpm set-device-owner io.github.ironclad/.admin.IronDeviceAdminReceiver

## Building

Assemble debug package:
./gradlew assembleDebug

Output file:
app/build/outputs/apk/debug/app-debug.apk

## License

GPL version 3.0. Zero tracking, zero ads, zero external servers.
