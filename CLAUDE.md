# maCat — notes for AI agents

Personal wardrobe catalogue app for Android (Kotlin/Compose), package `com.ahad.macat`,
distributed via F-Droid. GPL-3.0-only.

## How F-Droid distribution works for this repo

- The F-Droid build recipe does NOT live here. It lives in the fdroiddata repo:
  `metadata/com.ahad.macat.yml` at https://gitlab.com/fdroid/fdroiddata. This repo is
  only the app source code that the recipe points at.
- F-Droid builds from **git tags** (`commit: vX.Y` in the recipe), never from `main`.
  Pushing to `main` has no effect on published builds, so development can continue
  freely at any time.
- Version updates are **automatic** — no fdroiddata merge request is needed. The recipe
  has `AutoUpdateMode: Version` and `UpdateCheckMode: Tags ^v[\d.]+$`, so F-Droid's
  checkupdates bot picks up new tags by itself.

## Release checklist (new version)

1. Bump `versionCode` (integer, +1) and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<new versionCode>.txt`.
3. Commit, tag `vX.Y` (must match `^v[\d.]+$`), push the commit and the tag.

## Hard constraints — do not break these

- **Never move, delete, or force-push over a released tag** (e.g. `v1.0`). F-Droid
  builds are pinned to tags; changing one breaks the published build.
- The code must pass F-Droid's scanner: no proprietary dependencies, no
  analytics/tracking SDKs, no network calls to nonfree services, and no prebuilt
  binaries fetched at build time. (This is why the foojay toolchain resolver was
  removed from Gradle — it downloads JDK binaries during the build.)
- Keep the app offline-only (no INTERNET permission expectations in store copy).

## App store metadata

- Maintained in this repo under `fastlane/metadata/android/en-US/`
  (`title.txt`, `short_description.txt`, `full_description.txt`, `changelogs/`).
- `metadata/com.ahad.macat.yml` and `metadata/com.ahad.macat/en-US/summary.txt` in this
  repo are reference copies of what was submitted to fdroiddata; the authoritative
  copies live in the fdroiddata repo, where localized files take precedence over the
  in-repo fastlane ones. Keep the two in sync when editing descriptions.
- Short description must stay ≤ 80 characters (F-Droid CI enforces this).

## History / links

- Initial inclusion MR: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/42319
  (submitted from fork https://gitlab.com/ItsAhadun/ma-cat, branch
  `ItsAhadun-master-patch-12565`).
- fdroiddata CI gotchas hit during submission: `Description:` blocks must use 2-space
  indentation (canonical `fdroid rewritemeta` format), and `Summary:` must not appear
  in the yml — it belongs in `metadata/<appid>/en-US/summary.txt` so it is translatable.
