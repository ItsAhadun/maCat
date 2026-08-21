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

When the user asks for a new version (e.g. "make it v1.3"), run this **entire checklist
end-to-end without further prompting** — a release is not done until the signed APK is both
attached to the GitHub release *and* copied into `releaseapk/`. Build and sign from the
local tag first, then push the commit and tag and create the GitHub release right away, so
the public tag never exists without its binary.

1. Bump `versionCode` (integer, +1) and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<new versionCode>.txt`.
3. Commit, tag `vX.Y` (must match `^v[\d.]+$`), then push **both** with
   `git push origin main vX.Y` — a bare `git push` does not push tags. See
   [Committing and pushing](#committing-and-pushing-so-f-droid-actually-detects-the-release).
4. **Reproducible builds**: build `:app:assembleRelease` from a *clean checkout of the
   tag* (never a dirty working tree), sign the unsigned APK with the release keystore
   (`C:\Users\a\keystores\macat-release.keystore`, instructions in
   `C:\Users\a\keystores\README-macat.md` — must use **build-tools 34.0.0 apksigner**),
   and attach it to a GitHub release for the tag named `maCat-<versionName>.apk`.
   Do this immediately after pushing the tag: F-Droid downloads it from
   `releases/download/v%v/maCat-%v.apk` (the `Binaries:` field) and only publishes
   if their build is byte-identical. The APK is signed with the developer key
   (cert SHA-256 pinned in `AllowedAPKSigningKeys`); losing the keystore means the
   app can never be updated again.
5. Copy the signed APK to `releaseapk/` in this repo (replace the previous one) so
   the user always has the latest release APK at hand. The folder is gitignored —
   never commit APKs; F-Droid's scanner rejects prebuilt binaries in the source tree.

## Committing and pushing so F-Droid actually detects the release

`git push` alone is **not** a release. It pushes the current branch and **no tags**,
and F-Droid never looks at `main`. Every version that failed to appear on F-Droid so
far failed here.

- **Push the tag explicitly.** `git push origin main vX.Y` (or `git push --follow-tags`).
  A bare `git push` leaves the tag local-only and F-Droid sees nothing.
- **Verify the tag reached the remote** before believing the release happened:
  `git ls-remote --tags origin` must list the new `vX.Y`. Local `git tag -l` proves
  nothing — it lists tags that were never pushed.
- **A tag without a GitHub release is still broken.** The recipe's `Binaries:` URL
  (`releases/download/v%v/maCat-%v.apk`) 404s until the release exists with the asset
  named exactly `maCat-<versionName>.apk`. Confirm with `gh release view vX.Y`.
- **Never let `main` and a tag declare the same `versionCode`/`versionName`.** If you
  commit features to `main` after tagging, the very next release commit must bump the
  version. Two different trees claiming one version is how feature work ends up with
  nowhere to ship.
- **Feature commits on `main` are free** — they change nothing on F-Droid. Only the
  tag + GitHub release pair does. So never assume "I pushed, it's out".

**Post-release verification** (all four, in order):

1. `git ls-remote --tags origin` lists `vX.Y`.
2. `gh release view vX.Y` shows `maCat-<versionName>.apk` attached.
3. Within ~a day, F-Droid's checkupdates bot bumps `CurrentVersion` in the fdroiddata
   recipe. Until it runs, the app is *absent* from
   https://monitor.f-droid.org/builds/needsupdate — and absence there means F-Droid
   detected no new version at all, i.e. steps 1–2 were skipped or failed.
4. The build then has to come out byte-identical to the attached APK, or it is
   rejected silently. Check https://monitor.f-droid.org/builds if a version stalls.

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
