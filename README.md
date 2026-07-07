# maCat

A simple personal wardrobe catalogue for Android. Photograph your clothes, shoes,
and jewellery, then browse them full-screen with a TikTok-style vertical swipe feed.

- Add items one at a time (in-app camera or gallery) or in bulk (shoot everything
  first, name each item after)
- Organize and filter by category: clothes, shoes, jewellery
- Full-screen looping swipe feed as the main view, plus a grid overview
- Works on phones and foldables
- Everything stays on your device — no accounts, no network, no tracking

## Build

```
./gradlew assembleDebug
```

Requires JDK 17+ and the Android SDK (compileSdk 36). The unsigned release build
is `./gradlew assembleRelease`.

## License

[GPL-3.0-only](LICENSE)
