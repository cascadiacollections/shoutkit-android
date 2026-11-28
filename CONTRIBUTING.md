# Contributing

Thanks for helping out. See [README.md](README.md#building) for build and test commands, and
[SECURITY.md](SECURITY.md) for reporting vulnerabilities.

## Developer Certificate of Origin (DCO)

Sign off your commits:

```sh
git commit -s
```

This adds a `Signed-off-by:` trailer certifying the [Developer Certificate of
Origin](https://developercertificate.org): you wrote the change, or otherwise have the right to
submit it under the project's licenses. That keeps the copyright provenance clean, which is what
makes store distribution and any future relicensing possible, without a CLA.

## Licensing of contributions

By contributing, you agree your changes are licensed under the license of the module they touch:

- **GPL-3.0**, including the [app store permission](LICENSE-EXCEPTION.md), for `:app`, `:cast`,
  `:wear`, `:benchmark`, and anything else without its own `LICENSE` file.
- **MIT** for `:core:*`, `:libs:*`, and `build-logic` (each has its own `LICENSE` file).

The app store permission lets the official Google Play build, and any fork's store build, include
your contribution, as long as the source stays available under GPL-3.0. See the
[Licensing section of the README](README.md#license) for the full rule.

Contributions don't grant rights to the Shoutkit name or icon. See [TRADEMARK.md](TRADEMARK.md).

## Pull requests

- Keep PRs focused, and separate refactors from behavior changes.
- Run `just test` and `just lint` (or `just verify`) before pushing.

## Releasing

`versionCode` and `versionName` are literals in `app/build.gradle.kts`. F-Droid's `checkupdates`
reads them from that file at each `v*` tag (`UpdateCheckMode: Tags`, `AutoUpdateMode: Version`), so
the tag is the release.

1. In one commit on `main`, bump `versionName` (for example `1.3.0` → `1.4.0`) and increase
   `versionCode` by at least 1. It must never go down or repeat: Play and F-Droid both refuse it.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (at most 500 characters).
3. Merge it. A merge that changes `versionCode` is the only one that uploads to the Play internal
   track.
4. Tag that commit `v<versionName>` and push the tag. `release.yml` builds the signed FOSS APK,
   checks the tag matches `versionName`, and attaches the APK to the GitHub release.

Only `v<major>.<minor>.<patch>` tags count as releases. The `nightly-*` tags are ignored.
