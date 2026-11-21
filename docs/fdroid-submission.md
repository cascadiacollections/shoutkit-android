# Submitting ShoutKit to F-Droid

Status on 2026-11-20: not listed. `f-droid.org/packages/com.cascadiacollections.sir/` returns
404, fdroiddata has no `metadata/com.cascadiacollections.sir.yml`, and neither fdroiddata merge
requests nor the RFP tracker mention the app.

The recipe is [`fdroid/com.cascadiacollections.sir.yml`](fdroid/com.cascadiacollections.sir.yml).
It builds the `foss` flavor at the `v1.3.1` tag and tells F-Droid to publish the APK from GitHub
Releases with our signature once its own build matches it byte for byte (`Binaries:` plus
`AllowedAPKSigningKeys:`). The listing text, icon and screenshots come from
`fastlane/metadata/android/en-US/` in this repository; F-Droid reads them from the tagged source.

## What the recipe does to the source

F-Droid only builds `:app` in the `foss` flavor. Before building, the recipe removes the modules
and build lines that exist only for the Google Play build, so F-Droid's scanner finds no
reference to Google Play services or Firebase:

- deletes the `cast`, `wear` and `benchmark` modules and their `include(...)` lines;
- deletes the Google services and Crashlytics plugin lines from both build files;
- deletes the `dynamicFeatures` line and every `"playImplementation"` dependency.

None of this changes the FOSS APK (see #306).

## Before you submit

1. **Release key in CI.** Add these secrets to the repository, or to its `release` environment:
   `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
   `RELEASE_KEY_PASSWORD`. To make the first value:

   ```bash
   base64 < release.jks | tr -d '\n' | pbcopy
   ```

   If no release key exists yet, create one and back it up somewhere safe. Every later update
   on F-Droid and GitHub must be signed with it.

   ```bash
   keytool -genkeypair -v -keystore release.jks -alias shoutkit -keyalg RSA -keysize 4096 -validity 10000
   ```

2. **Attach the signed APK to v1.3.1.** The tag's `Release APK` run skipped signing because the
   secrets were missing. Re-run it now that they exist:

   ```bash
   gh run rerun 43810283100 --repo cascadiacollections/shoutkit-android
   ```

   The release should then carry `shoutkit-android-1.3.1.apk` and `SHA256SUMS`. If you would
   rather not re-run an old tag, cut the next release (CONTRIBUTING.md, "Releasing") and change
   the recipe's `versionName`, `versionCode`, `commit`, `CurrentVersion` and
   `CurrentVersionCode` to match.

3. **Put the certificate digest in the recipe.** The run's "Verify and name it" step prints
   `Signer #1 certificate SHA-256 digest`, and newer runs also write it to the run summary.
   Or read it from the APK:

   ```bash
   apksigner verify --print-certs shoutkit-android-1.3.1.apk | grep SHA-256
   ```

   Replace `REPLACE_WITH_RELEASE_CERTIFICATE_SHA256` with that value: lowercase hex, no colons.

## Submit the merge request

1. Fork <https://gitlab.com/fdroid/fdroiddata> and create a branch named
   `com.cascadiacollections.sir`. Never commit to `master`.
2. Copy the recipe to `metadata/com.cascadiacollections.sir.yml` in the fork.
3. Check it locally with fdroidserver
   (`pip install git+https://gitlab.com/fdroid/fdroidserver.git`):

   ```bash
   fdroid readmeta
   fdroid rewritemeta com.cascadiacollections.sir
   fdroid lint com.cascadiacollections.sir
   fdroid checkupdates com.cascadiacollections.sir
   fdroid build -v -l com.cascadiacollections.sir
   ```

   `rewritemeta` should leave no diff and `lint` should print nothing. `build` needs the Android
   SDK; you can skip it and let the fork's CI pipeline build instead.
4. Commit as `New app: ShoutKit`, push the branch and wait for the fork's pipeline to pass.
   The build job reports whether F-Droid's APK matches the one on GitHub Releases.
5. Open a merge request against `fdroid/fdroiddata` `master` and fill in the "App inclusion"
   template. Points reviewers ask about:
   - License: the app is GPL-3.0-only with an additional permission for app stores
     (`LICENSE-EXCEPTION.md`); the `core` and `libs` modules are MIT.
   - The FOSS build has no Google Play services, Firebase, analytics or crash reporting.
   - Network use: Radio Browser (free, community-run) for the directory, and Apple's iTunes
     Search for album art, which is why the recipe declares `NonFreeNet`.
   - The build is reproducible and the recipe asks F-Droid to ship the developer-signed APK.
6. Answer review comments on the merge request. After it merges, the app appears on
   f-droid.org with the next index update, usually within a few days.

## After it is listed

- Set `fdroid` in `window.SHOUTKIT_LINKS` (`docs/index.html`) to
  `https://f-droid.org/packages/com.cascadiacollections.sir/`. The F-Droid button on the site
  stays hidden until then.
- New versions need nothing from you on F-Droid: `checkupdates` picks up each `vX.Y.Z` tag, as
  long as the tag's release carries the signed APK.

## Not verified here

The recipe has not been run through `fdroid lint`, `fdroid scanner` or `fdroid build`. An earlier
draft with the same approach built reproducibly on fdroidserver (#306, #317), but this file was
rewritten from those notes, so treat step 3 above as required.
