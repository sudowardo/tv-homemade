# JVC (Changhong/Homwee) AI PONT

| | |
|---|---|
| Brand / maker | JVC, built by Changhong (Homwee firmware) |
| Android | Android TV 11 (SDK 30), `RTO2.230416.001` |
| RAM | 1.5 GB (about 1.38 GB usable), `ro.config.low_ram=true` |
| Live TV app | `com.mediatek.wwtv.tvcenter`, tuner input `TunerInputService/HW0` |
| Stock launcher | `com.google.android.tvlauncher` (Android TV, not Google TV) |

## Results

Measured with `dumpsys meminfo` on the home screen:

| | Launcher RAM | MemAvailable at home |
|---|---|---|
| Stock | ~190 MB | ~390 MB |
| tv-homemade + [debloat.txt](debloat.txt) | ~35-65 MB | ~430-500 MB |

## Notes

- **Keep** `com.homwee.chtvmanager` (TV Manager), `com.mediatek.wwtv.tvcenter` (Live TV and HDMI inputs),
  Chromecast built-in and the Play Store; everything there is used.
- **Keep** `com.google.android.tts` even with TalkBack and the Assistant disabled: the Live TV app
  binds to it while you watch.
- "Lost RAM" in `dumpsys meminfo` is video hardware memory: about 160 MB at home and 400 MB while
  watching live TV. It is not a leak and cannot be reclaimed.
- Opening "TV en vivo" with a plain launch intent resumes whatever source was last used (often an
  HDMI port). The launcher opens the tuner input explicitly instead.
- Screenshots come out black while DRM video (Netflix, DGO, ...) is playing.
