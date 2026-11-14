# sir-android developer recipes

# Default flavor for local development
flavor := "foss"

# Container runtime: Apple's `container` CLI on macOS, podman elsewhere.
# Override with CONTAINER_ENGINE=docker (etc.).
ENGINE := env("CONTAINER_ENGINE", if os() == "macos" { "container" } else { "podman" })
# `container` has no SELinux relabel option, and the Qodana image is
# amd64-only, so run it under Rosetta with more than the 1 GB default memory.
_vol_label := if ENGINE == "container" { "" } else { ":z" }
_qodana_opts := if ENGINE == "container" { "--platform linux/amd64 --rosetta --memory 4G " } else { "" }

# Build debug APK
build:
    ./gradlew assemble{{capitalize(flavor)}}Debug --no-daemon --console=rich --warning-mode=none

# Run unit tests
test:
    ./gradlew test{{capitalize(flavor)}}DebugUnitTest --no-daemon

# Install debug build on connected device.
# If android.device.serial is set in local.properties, targets that device;
# otherwise lets AGP pick (works when only one device is connected).
install:
    #!/usr/bin/env bash
    set -euo pipefail
    serial=$(grep -m1 '^android.device.serial=' local.properties 2>/dev/null \
        | cut -d= -f2 | tr -d '[:space:]' || true)
    if [[ -n "$serial" ]]; then
        ANDROID_SERIAL="$serial" ./gradlew :app:install{{capitalize(flavor)}}Debug --no-daemon
    else
        ./gradlew :app:install{{capitalize(flavor)}}Debug --no-daemon
    fi

# Build + install in one step
deploy: build install

# Build FOSS variant
build-foss:
    ./gradlew assembleFossDebug --no-daemon --console=rich --warning-mode=none

# Build Play variant (with Firebase)
build-full:
    ./gradlew assemblePlayDebug --no-daemon

# Refactor safety baseline (matches CI's core local checks)
verify:
    ./gradlew assembleFossDebug testPlayDebugUnitTest testFossDebugUnitTest :wear:testDebugUnitTest :core:model:testDebugUnitTest :core:directory:testDebugUnitTest :core:playback:testDebugUnitTest :core:persistence:testDebugUnitTest :core:artwork:testDebugUnitTest :libs:audio-levels:testDebugUnitTest detekt lintPlayDebug :wear:lintDebug --no-daemon --parallel --build-cache

# Static analysis: detekt (+ ktlint, Compose rules) and Android Lint across all modules
lint:
    ./gradlew detekt lintPlayDebug :wear:lintDebug --no-daemon --continue

# Apply ktlint formatting fixes
format:
    ./gradlew detekt --auto-correct --no-daemon --continue

# Regenerate detekt and Android Lint baselines (only after triaging the findings!)
baseline:
    ./gradlew detektBaseline updateLintBaseline --no-daemon --continue

# Qodana (JetBrains IDE inspections) in a container; report at http://localhost:8080
qodana:
    {{ENGINE}} run --rm -it {{_qodana_opts}}-p 8080:8080 -v "$PWD":/data/project{{_vol_label}} jetbrains/qodana-jvm-android:2026.2 --show-report
