# maCat — notes for AI agents

Personal wardrobe catalogue app for Android (Kotlin/Compose), package `com.ahad.macat`,
distributed via F-Droid. GPL-3.0-only.

## How F-Droid distribution works for this repo

- The F-Droid build recipe does NOT live here. It lives in the fdroiddata repo:
  `metadata/com.ahad.macat.yml` at https://gitlab.com/fdroid/fdroiddata. This repo is
  only the app source code that the recipe points at.
- F-Droid builds from **tagged commits**, never from `main`. The recipe's `commit:`
  field must be the **full commit hash** of the tag's commit, not the tag or branch
  name (reviewer requirement). Pushing to `main` has no effect on published builds,
  so development can continue freely at any time.
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
- The fastlane files are the **only** place summary/description live. F-Droid pulls
  them from this repo **at the tagged build commit**, so every release tag must contain
  the current fastlane files. Do not add `Summary:`/`Description:` (or localized
  `metadata/<appid>/en-US/` text files) to fdroiddata — the reviewer rejects that.
- `metadata/com.ahad.macat.yml` in this repo is a reference copy of the fdroiddata
  recipe; the authoritative copy lives in the fdroiddata repo. Keep the two in sync.
- Short description must stay ≤ 80 characters (F-Droid CI enforces this).

## History / links

- Initial inclusion MR: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/42319
  (submitted from fork https://gitlab.com/ItsAhadun/ma-cat, branch
  `ItsAhadun-master-patch-12565`).
- Review feedback hit during submission: no `Summary:`/`Description:` in fdroiddata at
  all (they are pulled from this repo's fastlane structure at the build commit), and
  `commit:` must be the full commit hash, not a tag or branch. v1.1 (versionCode 2,
  commit c8acdde) was cut because the v1.0 commit predated the fastlane files.
