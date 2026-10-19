# Changelog

Release notes for Roclet.

## [0.1.0] - unreleased

Initial public release.

- Stream the Android microphone to a ROC receiver over Wi-Fi using Roc's `rtp+rs8m` (source) and `rs8m` (repair) profiles.
- Forward erasure correction (RS8M) with a separate repair stream for resilience on lossy Wi-Fi.
- Configurable destination IP address and source/repair ports, remembered between launches.
- Runs as a foreground service, so streaming continues with the screen off or the app in the background.
- Start and stop from the app or from the ongoing notification.
- Light and dark themes.
