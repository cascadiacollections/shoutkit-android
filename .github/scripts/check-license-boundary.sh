#!/usr/bin/env bash
#
# Fails if an MIT-licensed Gradle module depends on a GPL-3.0 one.
#
# The convention is stated in README.md's Licensing section: a per-module
# `LICENSE` file makes that module MIT; every other module inherits GPL-3.0
# from the root `LICENSE`. An MIT module that links a GPL-3.0 one can't be
# distributed under MIT terms. Mirrors the check in the iOS repo
# (cascadiacollections/shoutkit).
#
# Only project dependencies are checked: `project(":a:b")` and the type-safe
# `projects.a.b` accessors. External libraries are reviewed in dependabot PRs.
#
# Usage: .github/scripts/check-license-boundary.sh

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

# ":core:model" -> "core/model"
to_dir() { echo "${1#:}" | tr ':' '/'; }

# "projects.core.mediaThing" -> "core/media-thing"
accessor_to_dir() {
    echo "${1#projects.}" | tr '.' '/' | sed -E 's/([a-z0-9])([A-Z])/\1-\L\2/g'
}

is_mit() { [ -f "$1/LICENSE" ]; }

violations=0
checked=0

while IFS= read -r module; do
    dir=$(to_dir "$module")
    is_mit "$dir" || continue
    checked=$((checked + 1))

    build_file="$dir/build.gradle.kts"
    [ -f "$build_file" ] || continue

    # `|| true`: a module with no project dependencies is fine, and pipefail
    # would otherwise turn grep's no-match into a silent exit.
    deps=$(
        { grep -oE 'project\("[^"]+"\)' "$build_file" || true; } | sed -E 's/project\("([^"]+)"\)/\1/' | while read -r p; do to_dir "$p"; done
        { grep -oE 'projects(\.[A-Za-z0-9]+)+' "$build_file" || true; } | while read -r a; do accessor_to_dir "$a"; done
    )

    while IFS= read -r dep; do
        [ -n "$dep" ] || continue
        if [ ! -d "$dep" ]; then
            echo "error: $module depends on '$dep', which does not exist" >&2
            violations=$((violations + 1))
        elif ! is_mit "$dep"; then
            echo "error: MIT module '$module' depends on GPL-3.0 module '$dep' ($build_file)" >&2
            echo "       Relicense '$dep' MIT (add $dep/LICENSE) or remove the dependency." >&2
            violations=$((violations + 1))
        fi
    done <<< "$deps"
done < <(grep -oE 'include\("[^"]+"\)' settings.gradle.kts | sed -E 's/include\("([^"]+)"\)/\1/')

if [ "$checked" -eq 0 ]; then
    echo "error: found no MIT modules — is the layout right?" >&2
    exit 2
fi

if [ "$violations" -gt 0 ]; then
    echo "" >&2
    echo "$violations license-boundary violation(s). See the Licensing section of README.md." >&2
    exit 1
fi

echo "License boundary OK: $checked MIT module(s), no GPL-3.0 dependencies."
