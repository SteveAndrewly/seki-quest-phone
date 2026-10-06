# CLAUDE.md — Seki Quest Phone

The Android app for phone points in Seki Quest (the teacher's offline Windows app; its project folder is `C:\Seki Quest Redo` on his PC). Read `README.md` first.

- **What the phone shows lives in the desktop app** (`src/phone/` there, served by its `main.js`). This app is only the frame: a full-screen landscape WebView, the QR code scan, the remembered address, and its own setup / can't-connect screens. Change the phone screen there, not here.
- **No student names on the phone.** The page gets an anonymous state; don't add anything here that asks the PC for more.
- **American English and Japanese** for every string: `res/values/strings.xml` and `res/values-ja/strings.xml`.
- **Builds run on GitHub Actions only** (the cloud workspace and the PC can't reach Google's Android servers). Push to `main`, wait about a minute, then fetch the `builds` branch: `git fetch origin +refs/heads/builds:refs/remotes/origin/builds` and read `STATUS`, `build.log` and `seki-quest-phone.apk`. The Actions runs list works through the API; the secrets API doesn't.
- **Signing:** the repository's secrets `SIGNING_KEY_B64` and `SIGNING_PASSWORD` (the teacher added them himself; a copy is in `Phone app signing key.txt` in his project folder). `build.log` ends with the certificate: check it says `CN=Seki Quest`, not `CN=Android Debug`.
- Plain Java, no Kotlin, no AndroidX code; keep it that way unless the teacher asks.
