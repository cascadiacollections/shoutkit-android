# Submitting ShoutKit to F-Droid

Status on 2026-11-25: not listed. F-Droid builds and signs the F-Droid package itself.
The application id is `com.cascadiacollections.sir.foss`, a separate install from the Play
app (`com.cascadiacollections.sir`). `f-droid.org/packages/com.cascadiacollections.sir.foss/`
returns 404, fdroiddata has no `metadata/com.cascadiacollections.sir.foss.yml`, and neither
fdroiddata merge requests nor the RFP tracker mention the app.

The recipe is [`fdroid/com.cascadiacollections.sir.foss.yml`](fdroid/com.cascadiacollections.sir.foss.yml).
It builds the `foss` flavor. Listing text, the icon and screenshots come from
`fastlane/metadata/android/en-US/` in this repository. F-Droid scans that directory in the
app's own checkout and attaches what it finds to the metadata id, so the `.foss` suffix does
not need its own fastlane tree. This repo has no per-id layout (`fastlane/metadata/android/<id>/`
is not used). fdroidserver's scan of `fastlane/metadata/android/<locale>/` at the repo root
is what publishes the listing for `com.cascadiacollections.sir.foss`.

## What the recipe does to the source

F-Droid only builds `:app` in the `foss` flavor. Before building, the recipe removes the modules
and build lines that exist only for the Google Play build, so F-Droid's scanner finds no
reference to Google Play services or Firebase:

- deletes the `cast`, `wear` and `benchmark` modules and their `include(...)` lines;
- deletes the Foojay toolchain resolver plugin line from `settings.gradle.kts`;
- deletes the Google services and Crashlytics plugin lines from both build files;
- deletes the `dynamicFeatures` line and every `"playImplementation"` dependency.

None of this changes the FOSS APK (see #306). The Foojay `sed` only deletes a matching line.
If that line is already gone, the `plugins` block is left empty, which is valid.

## Version and commit

`versionName`, `versionCode`, `commit`, `CurrentVersion` and `CurrentVersionCode` must be the
first release that contains `applicationIdSuffix = ".foss"`. The file checked in here keeps the
v1.3.1 shape (`1.3.1`, `512`, commit `a89fdefba8ee4f51dfcb227ab0051d07f91a5b51`), which is what
`fdroid lint`, `fdroid scanner` and `fdroid checkupdates` were run against. That tag does not
contain the suffix, so its application id is still `com.cascadiacollections.sir`.

Replace those five fields when that release is cut. `commit` has to be the full 40-character
hash of the tagged commit. A short hash or the tag name fails the build. The owner cuts the
release. This tree does not tag one and does not publish one.

## Submit the merge request

1. Cut the release that contains the suffix (CONTRIBUTING.md, "Releasing"), then set the five
   version fields above to that tag.
2. Fork <https://gitlab.com/fdroid/fdroiddata> and create a branch named
   `com.cascadiacollections.sir.foss`. Never commit to `master`.
3. Copy the recipe to `metadata/com.cascadiacollections.sir.foss.yml` in the fork.
4. Check it locally with fdroidserver
   (`pip install git+https://gitlab.com/fdroid/fdroidserver.git`):

   ```bash
   fdroid readmeta
   fdroid rewritemeta com.cascadiacollections.sir.foss
   fdroid lint com.cascadiacollections.sir.foss
   fdroid checkupdates com.cascadiacollections.sir.foss
   fdroid build -v -l com.cascadiacollections.sir.foss
   ```

   `rewritemeta` should leave no diff and `lint` should print nothing. `build` needs the Android
   SDK. You can skip it and let the fork's CI pipeline build instead. F-Droid signs the APK
   its build produces.
5. Commit as `New app: ShoutKit`, push the branch and wait for the fork's pipeline to pass.
6. Open a merge request against `fdroid/fdroiddata` `master`, titled `New app: ShoutKit`, and
   fill in the "App inclusion" template. Points reviewers ask about:
   - License: the app is GPL-3.0-only with an additional permission for app stores
     (`LICENSE-EXCEPTION.md`); the `core` and `libs` modules are MIT.
   - The FOSS build has no Google Play services, Firebase, analytics or crash reporting.
   - Network use: Radio Browser (free, community-run) for the directory, and Apple's iTunes
     Search for album art, which is why the recipe declares `NonFreeNet`.
   - The application id is `com.cascadiacollections.sir.foss`, so the F-Droid package installs
     beside Play. F-Droid signs the build.
7. Answer review comments on the merge request. After it merges, the app appears on
   f-droid.org with the next index update, usually within a few days.

## After it is listed

- Set `fdroid` in `window.SHOUTKIT_LINKS` (`docs/index.html`) to
  `https://f-droid.org/packages/com.cascadiacollections.sir.foss/`. The F-Droid button on the site
  stays hidden until then.
- New versions need nothing further on the F-Droid side: `checkupdates` picks up each `vX.Y.Z`
  tag, as long as `versionCode` and `versionName` in `app/build.gradle.kts` keep moving up.
  F-Droid builds and signs each update from the tag.

## What was and was not verified

The recipe shape above was checked with `fdroid lint`, `fdroid scanner` and `fdroid checkupdates`
against v1.3.1, before the application id suffix. Point `commit` at the first tag that contains
the suffix, then run `fdroid build` (or the fork's CI) once before asking for review.

If the scanner still flags the Google entries in `gradle/libs.versions.toml`, delete those lines
in `prebuild` too.
