# Restoid

A modern, root-based Android backup tool powered by [`restic`](https://github.com/restic/restic/).

Restoid gives you control over your app backups through a clean and simple user interface. It's built for users who want robust, encrypted, and deduplicated backups.

## 🚧 Project Status

**This is an early beta release.** The application is functional, but you may encounter bugs. The codebase is a work-in-progress and is undergoing active development and refactoring. Feedback, bug reports, and contributions are highly encouraged!

## ✨ Features

* **Restic-Powered**: Leverages the speed, security, and efficiency of `restic` for deduplicated and encrypted backups.
* **Selective App Backup**: Choose exactly which user-installed applications you want to back up.
* **Full Control Over What You Back Up**: Granularly select what to include for each app: APK files, user data, device-protected data, external/OBB/media files.
* **Flexible Repository Management**: Create and manage backup repositories on your device's local storage, SD card, or mounted drives.
* **Snapshot Management**: Easily browse backup snapshots, view details of what was backed up, and forget old snapshots.
* **Flexible Restore**: Restore entire apps or just specific parts (like only app data).
* **Downgrade Protection**: Prevents you from accidentally restoring an older app version over a newer one (can be overridden).
* **Zero-Hassle Dependencies**: Automatically downloads and manages the `restic` binary for your device's architecture.

## ⚡ Beyond Local Backups: Sync with Syncthing!

While Restoid creates local backup repositories, its true power shines when combined with tools like [**Syncthing**](https://syncthing.net/).

You can set up your repository in a folder that is synchronized by Syncthing. This allows you to automatically and securely transfer your encrypted Android backups to your server, laptop, or any other device in your private network. It's a perfect, decentralized setup for keeping your data safe and under your control.

## ⚠️ Requirements

* **Root Access**: This is non-negotiable. Restoid requires elevated privileges to access app data directories. It uses `libsu` for robust root command execution.
* **Android Version**: Minimum SDK 33 (Android 13).

## 🚀 Getting Started

1.  **Grant Root**: Launch the app and grant it Superuser access when prompted.
2.  **Install Restic**: Go to **Settings**. The app will show that `restic` is not installed. Tap **Download** to automatically fetch and set it up.
3.  **Create a Repository**:
   * In **Settings**, tap the `+` icon to add a new repository.
   * Select a folder on your device.
   * Set a strong password to encrypt your backups. You can choose to save it securely in the app.
4.  **Run Your First Backup**:
   * Go **Home** and tap the **Backup** FAB (the `+` button).
   * Choose what data types you want to back up (APK, Data, etc.).
   * Select the apps.
   * Tap **Start Backup** and watch the magic happen.
5.  **Restore From a Backup**:
   * From the **Home** screen, tap on a snapshot.
   * Tap **Restore**, select what you want to bring back, and confirm.

## 🤝 How to Contribute

This is a new project and there's a lot to do! If you find a bug, have a feature request, or want to help clean up the code, please:

1.  **Open an issue** to discuss the change.
2.  Fork the repository and submit a pull request.

All contributions are welcome!

## 📜 License

This project is licensed under the **GNU General Public License v3.0**. See the `LICENSE` file for details.