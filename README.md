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

## Releasing (F-Droid)

maCat is distributed through F-Droid. The build recipe lives in
[fdroiddata](https://gitlab.com/fdroid/fdroiddata) (`metadata/com.ahad.macat.yml`) and
builds from **git tags**, not from `main` — so pushing to `main` never affects
published builds. Updates are picked up automatically (`AutoUpdateMode: Version`);
no new merge request is needed. To ship a release:

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
3. Commit, tag `vX.Y`, and push the commit and tag

Never move or delete a released tag — F-Droid builds are pinned to it. The code must
keep passing F-Droid's scanner: no proprietary dependencies, trackers, or prebuilt
binaries fetched at build time. See [CLAUDE.md](CLAUDE.md) for the full notes.

## License

[GPL-3.0-only](LICENSE)
