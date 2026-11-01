#!/usr/bin/env python3
"""Point the project site's nightly download links at one published release.

Immutable releases lock tags and assets, so each nightly keeps a unique tag.
The nightly workflow runs this after publishing that tag. Draft releases are
rejected by the URL shape: only a public nightly-* asset URL is accepted.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

APK_URL = re.compile(
    r"^https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/download/"
    r"nightly-[0-9]+-[0-9]+/shoutkit-android-nightly\.apk$"
)
NOTES_URL = re.compile(
    r"^https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/tag/"
    r"nightly-[0-9]+-[0-9]+$"
)


def replace_anchor_href(html: str, marker: str, href: str) -> str:
    pattern = re.compile(rf"(<a\b[^>]*\b{re.escape(marker)}\b[^>]*)>", re.IGNORECASE)

    def repl(match: re.Match[str]) -> str:
        tag = match.group(1)
        if 'href="' not in tag:
            raise SystemExit(f"{marker} anchor is missing an href")
        tag = re.sub(r'href="[^"]*"', f'href="{href}"', tag, count=1)
        return tag + ">"

    updated, count = pattern.subn(repl, html)
    if count == 0:
        raise SystemExit(f"found no {marker} anchors")
    return updated


def update_html(html: str, apk_url: str, notes_url: str) -> str:
    if not APK_URL.fullmatch(apk_url):
        raise SystemExit(f"refusing apk url: {apk_url}")
    if not NOTES_URL.fullmatch(notes_url):
        raise SystemExit(f"refusing notes url: {notes_url}")
    html = replace_anchor_href(html, "data-nightly-download", apk_url)
    html = replace_anchor_href(html, "data-nightly-notes", notes_url)
    return html


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--html", type=Path, required=True)
    parser.add_argument("--apk-url", required=True)
    parser.add_argument("--notes-url", required=True)
    args = parser.parse_args()
    path: Path = args.html
    original = path.read_text(encoding="utf-8")
    updated = update_html(original, args.apk_url, args.notes_url)
    if updated != original:
        path.write_text(updated, encoding="utf-8")
    print(f"updated {path}")


if __name__ == "__main__":
    try:
        main()
    except BrokenPipeError:
        sys.exit(0)
