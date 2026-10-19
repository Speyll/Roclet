# Roclet

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="128" alt="Roclet app icon" />
</p>

**Turn your Android phone into a wireless microphone.**

Roclet captures the phone's microphone and streams it over Wi-Fi to a compatible ROC
receiver, using [Roc Toolkit](https://roc-streaming.org)'s reliable, low-latency audio
transport.

[Source](https://github.com/Speyll/Roclet) · [Issues](https://github.com/Speyll/Roclet/issues) · [Releases](https://github.com/Speyll/Roclet/releases)

## Features

- Wireless microphone over Wi-Fi — no cable or extra hardware.
- Reliable transport with ROC and RS8M forward erasure correction.
- Configurable receiver address and source/repair ports.
- Keeps streaming while the screen is off.
- Small UI with a foreground notification and quick start/stop.

## Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_20261019_043238_Roclet.jpg" width="30%" alt="Roclet screenshot 1" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_20261019_043244_Roclet.jpg" width="30%" alt="Roclet screenshot 2" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_20261019_043252_Roclet.jpg" width="30%" alt="Roclet screenshot 3" />
</p>

## How it works

```
Android phone  →  Roclet  →  Wi-Fi (ROC)  →  ROC receiver
```

Roclet sends an `rtp+rs8m` source stream and an `rs8m` repair stream. Point it at a
receiver on the same network (default ports `10001` source and `10002` repair) and the
phone shows up there as a normal audio source.

## Requirements

- Android 10 (API 29) or newer, on an `arm64` device.
- A Wi-Fi network shared with the receiver.
- A compatible ROC receiver on that network.

## Receiver setup

Roclet is a sender only, so it needs a ROC receiver at the other end — for example
Roc Toolkit's `roc-recv`, or the PipeWire / PulseAudio ROC module. Start the receiver,
note its IP address and source/repair ports, and enter those in Roclet. See the
[Roc Toolkit documentation](https://roc-streaming.org/toolkit/docs/) for receiver
installation and usage.

## Installation

1. Download the APK from the [releases page](https://github.com/Speyll/Roclet/releases).
2. Install it on the phone.
3. Open Roclet and set the receiver address and ports.
4. Tap the microphone button to start streaming.

Release builds are signed with Roclet's release key, so uninstall a debug build before installing a release build.

## Usage

- Tap the settings icon to set the receiver address and the source/repair ports. They
  are remembered between launches.
- Tap the microphone button to start, and grant the microphone permission when asked.
  On Android 13+ also allow notifications so the ongoing notification can appear.
- The status line shows *Starting*, *Streaming*, or *Stopped*. Tap the button again, or
  use **Stop** in the notification, to stop.
- Streaming continues while the screen is off or the app is in the background.

## Building

Roclet builds its native dependency (Roc Toolkit, OpenFEC, roc-java, libuv, SpeexDSP)
from the pinned source archives and patches committed under `scripts/native/`:

```sh
scripts/native/build-native.sh all
```

Then build the app with the Gradle wrapper:

```sh
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:assembleRelease   # release APK (arm64-v8a, R8 + resource shrinking)
```

You need JDK 17+, Android SDK platform/build-tools 36, and Android NDK `21.1.6352462`.
A release build is signed only when a signing-properties file is present; otherwise it
produces an unsigned APK. See `scripts/native/README.md` for the full native build
details.

### F-Droid

F-Droid metadata is included at `fdroid/com.roclet.mic.yml`, and Roclet is designed to
build entirely from source.

## Privacy

- Audio is sent only to the receiver you configure.
- No analytics, ads, telemetry, accounts, or cloud service.
- Only your destination IP and ports are stored, locally on the device.

## Contributing / Issues

Pull requests and [issues](https://github.com/Speyll/Roclet/issues) are welcome. Build
and test with `./gradlew :app:assembleDebug` before submitting, and keep changes focused.

## License

Roclet is licensed under [Apache-2.0](LICENSE). Third-party components retain their
respective licenses — see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
