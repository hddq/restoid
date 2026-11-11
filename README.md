# Restoid
Secure, deduplicated backup tool for Android powered by [`restic`](https://github.com/restic/restic/).

[![F-Droid Downloads](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fraw.githubusercontent.com%2Fkitswas%2Ffdroid-metrics-dashboard%2Frefs%2Fheads%2Fmain%2Fprocessed%2Ftotal%2Fio.github.hddq.restoid.json&query=%24.total_downloads&logo=fdroid&label=F-Droid%20downloads)](https://f-droid.org/packages/io.github.hddq.restoid)
[![GitHub Downloads](https://img.shields.io/github/downloads/hddq/restoid/total?logo=github&label=GitHub%20downloads)](https://github.com/hddq/restoid/releases)

Restoid is a modern, privacy-focused backup solution for Android powered by the robust, open-source restic engine. Keep your apps, data, photos, and documents safe with enterprise-grade encryption and lightning-fast, space-saving deduplication.

Whether you want to back up photos to a home NAS, store encrypted snapshots in the cloud, or preserve full app data on a rooted device, Restoid gives you complete control without trackers, ads, or proprietary lock-in.

## 📥 Download

<p align="center">
  <a href="https://f-droid.org/packages/io.github.hddq.restoid"><img src="assets/badges/get-it-on-fdroid.png" alt="Get it on F-Droid" height="40"></a>&nbsp;
  <a href="https://github.com/hddq/restoid/releases"><img src="assets/badges/get-it-on-github.svg" alt="Get it on GitHub" height="40"></a>&nbsp;
  <a href="http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/hddq/restoid"><img src="assets/badges/get-it-on-obtainium.png" alt="Get it on Obtainium" height="40"></a>
</p>

<p align="center">
  Official F-Droid updates may be delayed. For instant updates directly from CI, <a href="https://fdroid.link/#https://hddq.github.io/restoid/fdroid/repo?fingerprint=E6629E27D6CDC87B15F68C43F7D7DF8904AC8EFC76D87C7903976C3A38233AC5">add the custom repository</a>
</p>

## 📸 Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="Restoid app screenshot 1" width="32%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="Restoid app screenshot 2" width="32%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" alt="Restoid app screenshot 3" width="32%" />
</p>

## ✨ Features

* ⚡ **Blazing Fast & Deduplicated**: Content-defined deduplication uploads only unique changed blocks, making subsequent backups blazingly fast and drastically more efficient than traditional `tar.gz` archives.
* 🔒 **Zero-Knowledge Encryption**: All snapshots are encrypted with `restic` using state-of-the-art cryptography. Your encryption keys remain under your control.
* ☁️ **Flexible Storage Backends**: Store backups on local storage (internal memory, SD card, USB OTG) or over the network (SFTP/SSH, REST server, and Amazon S3-compatible object storage like Garage, MinIO, Wasabi, Cloudflare R2, or Backblaze B2).
* 📱 **Full App & Data Backup (Root Required)**: Selectively back up installed user applications with granular control: APKs, app user data, device-protected data, and external/OBB storage.
* 📁 **Custom Files & Media**: Back up personal folders and files (DCIM, Documents, Downloads, or any directory) without requiring root.
* ⏰ **Automated Schedules**: Set up recurring backups with customizable intervals and smart trigger constraints (charging only, unmetered Wi-Fi).
* 🔄 **Surgical & Safe Restore**: Restore entire apps or specific parts (like app data without overwriting the installed APK), with downgrade protection to prevent version conflicts.
* 🔓 **Zero Vendor Lock-in**: Restoid uses the standard, native `restic` repository format. You can always access, inspect, mount (`restic mount`), or restore your data on any computer (Linux, macOS, Windows) using the official `restic` CLI—no proprietary container or phone required.
* 🧹 **Maintenance & Repository Health**: Automated snapshot retention policies (prune old snapshots by day, week, month), repository integrity checks, and stale lock management.
* 🎨 **Material 3 Expressive UI**: Built with modern Jetpack Compose and Material 3 Expressive design, featuring dynamic theming, smooth animations, and edge-to-edge layout.
* 🛡️ **100% Free & Open Source**: Licensed under GPLv3 with zero analytics, telemetry, or third-party tracking.

## ⚠️ Requirements

* **Root Access (Optional)**: Root access is only required to back up installed apps and their private data. Custom folders, photos, and documents can be backed up completely without root.
* **Android Version**: Minimum SDK 29 (Android 10), with SDK 37 (Android 17) recommended.

## 🤝 How to Contribute

If you find a bug, have a feature request, or want to help clean up the code, please:

1.  **Open an issue** to discuss the change.
2.  Fork the repository and submit a pull request.

All contributions are welcome!

## 🌐 Translations

Translations for Restoid are managed via Crowdin. If you'd like to help translate the app into your language, [please visit the project page](https://crowdin.com/project/restoid).

<a href="https://crowdin.com/project/restoid"><img src="https://badges.crowdin.net/restoid/localized.svg" alt="Translation status"></a>

### 🌍 How to contribute translations:

- Create an account on [Crowdin](https://crowdin.com/) (if you don't already have one).
- Join the Restoid project, pick a language, and translate strings using Crowdin's web editor.
- Translated strings are automatically synchronized with the repository via GitHub Actions.

## 📜 License

This project is licensed under the **GNU General Public License v3.0**. See the `LICENSE` file for details.
