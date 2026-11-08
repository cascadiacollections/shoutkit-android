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
- Run `just test` and `./gradlew lintDebug` before pushing.
