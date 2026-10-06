# Seki Quest Phone

The Android app for **phone points** in Seki Quest, the teacher's offline seating-chart and participation-points app for Windows.

Seki Quest on the PC serves a phone page on the local network (the phone's own hotspot). This app shows that page full screen in landscape and keeps the screen on, and it remembers the PC's address and secret code from the QR code in Seki Quest (**Settings → Phone**). No student names reach the phone: Seki Quest sends only anonymous desks, today's points and parties. Nothing goes to the internet.

- **App code:** `app/src/main/java/com/sekiquest/phone/MainActivity.java` (one plain Java activity with a WebView). The screens inside the WebView live in the desktop app (`src/phone/`), so most changes happen there, not here.
- **QR code:** Google's code scanner (Google Play services), so the app needs no camera permission. Fallback: open the QR code's link in Chrome and share it to the app.
- **Languages:** American English and Japanese (`res/values`, `res/values-ja`).
- **Builds:** GitHub Actions (`.github/workflows/build.yml`) builds a signed APK on every push to `main`, and publishes it with the build log to the `builds` branch (replaced each time) and as a workflow artifact. Each build's version code is the run number, so a new APK installs over the old one.
- **Signing:** the key is in the repository's Actions secrets (`SIGNING_KEY_B64`, `SIGNING_PASSWORD`), never in the files. Without them, the build falls back to a throwaway debug key (and an APK signed that way won't install over one signed with the real key).

## Installing

Download `seki-quest-phone.apk` from the `builds` branch and open it on the phone (allow installing from that source once). On first launch, tap **Scan QR code** and scan the code that Seki Quest shows under Settings → Phone.
