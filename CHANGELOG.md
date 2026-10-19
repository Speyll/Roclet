# Changelog

## 0.1.0

First public release.

- Streams the phone microphone to a ROC receiver over Wi-Fi using Roc's `rtp+rs8m`
  (source) and `rs8m` (repair) profiles.
- Configurable receiver address and source/repair ports, remembered between launches.
- Foreground service: streaming continues with the screen off or the app in the
  background.
- Start and stop from the app or the ongoing notification.
- Light and dark themes.
