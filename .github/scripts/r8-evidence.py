#!/usr/bin/env python3
"""Summarize R8 evidence for Google Play's DEX code optimization requirement.

From February 2027, Play requires app bundles with more than 10 MB of DEX code to reach at
least 25% optimization, shrinking and obfuscation, as measured by Play Console's app bundle
explorer (https://support.google.com/googleplay/android-developer/answer/17492799). Play
Console has the authoritative numbers; this script records the closest CI-side evidence so a
regression shows up in a PR instead of after an upload:

* DEX size: uncompressed bytes of every .dex in an .aab or .apk, per bundle module.
* Final scores: R8 writes BUNDLE-METADATA/com.android.tools/r8.json into every .aab, with
  the share of code it could not obfuscate, optimize or shrink. This is the optimization
  metadata Play Console reads for its DEX code optimization insights.
* Keep-rule analysis: AGP's R8 Configuration Analyzer report
  (build/outputs/mapping/<variant>/configanalyzer.html). Its scores are taken before R8's
  optimization passes, so they run higher than r8.json; its value is the per-rule table
  showing which keep rules block the most code.

Usage:
  r8-evidence.py --artifact NAME=PATH.aab --analyzer NAME=configanalyzer.html \
      [--gate NAME] [--min-score 30] [--summary FILE]

--gate NAME fails the run when that artifact's r8.json scores (or, for an analyzer name, the
analyzer's scores) fall below --min-score. Gate only variants that ship to Play: the FOSS flavor turns obfuscation off on purpose
(app/proguard-rules-foss.pro) and scores 0% there.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

# "DEX code > 10 MB" for apps. Decimal megabytes: the stricter reading of "MB".
PLAY_DEX_FLOOR_BYTES = 10_000_000
CONSTRAINTS = {
    "obfuscation": ("DONT_OBFUSCATE", "-dontobfuscate"),
    "optimization": ("DONT_OPTIMIZE", "-dontoptimize"),
    "shrinking": ("DONT_SHRINK", "-dontshrink"),
}
TABLES = ("keptClassInfoTable", "keptFieldInfoTable", "keptMethodInfoTable")


def named_path(value: str) -> tuple[str, Path]:
    name, sep, path = value.partition("=")
    if not sep or not name or not path:
        raise argparse.ArgumentTypeError(f"expected NAME=PATH, got {value!r}")
    return name, Path(path)


def dex_sizes(path: Path) -> dict[str, int]:
    """Uncompressed .dex bytes per module ("base", "cast", ...; "apk" for an APK)."""
    sizes: dict[str, int] = defaultdict(int)
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            if not info.filename.endswith(".dex"):
                continue
            if path.suffix == ".aab":
                module = info.filename.split("/", 1)[0]
            else:
                module = "apk"
            sizes[module] += info.file_size
    return dict(sizes)


def r8_scores(path: Path) -> dict[str, float] | None:
    """Final scores from the bundle's r8.json, or None when the archive has none (APKs)."""
    with zipfile.ZipFile(path) as archive:
        try:
            stats = json.loads(archive.read("BUNDLE-METADATA/com.android.tools/r8.json"))["stats"]
        except KeyError:
            return None
    return {
        "obfuscation": 100.0 - stats["noObfuscationPercentage"],
        "optimization": 100.0 - stats["noOptimizationPercentage"],
        "shrinking": 100.0 - stats["noShrinkingPercentage"],
    }


def load_analyzer(path: Path) -> dict:
    text = path.read_text(encoding="utf-8")
    match = re.search(
        r'<script id="keepradius-data" type="application/json">(.*?)</script>', text, re.S
    )
    if not match:
        raise SystemExit(f"{path}: no keepradius-data block (not an R8 Configuration Analyzer report?)")
    return json.loads(match.group(1))


def analyze(data: dict) -> dict:
    build = data["buildInfo"]
    live = build["liveClassCount"] + build["liveFieldCount"] + build["liveMethodCount"]
    constraints = {c["id"]: set(c["constraints"]) for c in data["keepConstraintsTable"]}
    rules = {r["id"]: r for r in data["keepRuleKeepRadiusTable"]}
    files = {f["id"]: f for f in data["fileOriginTable"]}
    global_rules = {g["source"].strip() for g in data.get("globalKeepRuleKeepRadiusTable", [])}

    blocked = Counter()
    per_rule: dict[int, Counter] = defaultdict(Counter)
    for table in TABLES:
        for item in data.get(table, []):
            seen = set()
            for rule_id in item.get("keptBy", []):
                rule = rules.get(rule_id)
                if rule is None:
                    continue
                for constraint in constraints.get(rule["constraintsId"], ()):
                    per_rule[rule_id][constraint] += 1
                    seen.add(constraint)
            blocked.update(seen)

    scores = {}
    for name, (constraint, global_rule) in CONSTRAINTS.items():
        count = live if global_rule in global_rules else blocked[constraint]
        scores[name] = 100.0 * (1 - count / live) if live else 0.0

    def origin(rule: dict) -> str:
        info = files.get(rule["origin"]["fileOriginId"], {})
        coordinate = info.get("mavenCoordinate")
        where = (
            f'{coordinate["groupId"]}:{coordinate["artifactId"]}:{coordinate["version"]}'
            if coordinate
            else Path(info.get("filename", "?")).name
        )
        return f'{where}:{rule["origin"]["lineNumber"]}'

    top = sorted(per_rule.items(), key=lambda kv: -kv[1]["DONT_OPTIMIZE"])[:8]
    return {
        "live": live,
        "scores": scores,
        "global_rules": sorted(global_rules),
        "top_rules": [
            {
                "rule": " ".join(rules[rid]["source"].split()),
                "origin": origin(rules[rid]),
                "blocks_optimization": counts["DONT_OPTIMIZE"],
            }
            for rid, counts in top
            if counts["DONT_OPTIMIZE"]
        ],
    }


def mb(size: int) -> str:
    return f"{size / 1_000_000:.2f} MB"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--artifact", action="append", type=named_path, default=[])
    parser.add_argument("--analyzer", action="append", type=named_path, default=[])
    parser.add_argument("--gate", action="append", default=[])
    parser.add_argument("--min-score", type=float, default=30.0)
    parser.add_argument("--summary", type=Path)
    parser.add_argument("--json", type=Path, help="also write the numbers as JSON")
    args = parser.parse_args()

    out = ["## R8 / DEX optimization evidence", ""]
    record: dict = {"artifacts": {}, "analyzers": {}}
    failures = []

    if args.artifact:
        out += [
            "| Artifact | DEX (uncompressed) | Per module | Over Play's 10 MB floor? "
            "| Obfuscation | Optimization | Shrinking |",
            "|---|---|---|---|---:|---:|---:|",
        ]
        for name, path in args.artifact:
            if not path.is_file():
                if name in args.gate:
                    failures.append(f"{name}: gated, but {path} does not exist")
                out.append(f"| {name} | missing: `{path}` | | | | | |")
                continue
            sizes = dex_sizes(path)
            total = sum(sizes.values())
            scores = r8_scores(path)
            record["artifacts"][name] = {
                "file": path.name, "dex_bytes": total, "modules": sizes, "r8_scores": scores,
            }
            modules = ", ".join(f"{m} {mb(s)}" for m, s in sorted(sizes.items()))
            cells = (
                " | ".join(f"{scores[k]:.1f}%" for k in ("obfuscation", "optimization", "shrinking"))
                if scores
                else "n/a | n/a | n/a"
            )
            over = "yes" if total > PLAY_DEX_FLOOR_BYTES else "no"
            out.append(f"| {name} | {mb(total)} | {modules} | {over} | {cells} |")
            if name in args.gate:
                if scores is None:
                    failures.append(f"{name}: gated, but it has no r8.json (not an R8-built bundle?)")
                else:
                    failures += [
                        f"{name}: {metric} {value:.1f}% < {args.min_score:.0f}%"
                        for metric, value in scores.items()
                        if value < args.min_score
                    ]
        out += ["", "Scores in this table are R8's final numbers from the bundle's r8.json.", ""]

    for name, path in args.analyzer:
        if not path.is_file():
            out += [f"### {name}", "", f"No report at `{path}`.", ""]
            continue
        result = analyze(load_analyzer(path))
        record["analyzers"][name] = result
        s = result["scores"]
        gated = name in args.gate
        out += [
            f"### Keep rules: {name}" + (" (gated)" if gated else ""),
            "",
            "Analyzer scores (before optimization passes): "
            f"obfuscation **{s['obfuscation']:.1f}%** · optimization **{s['optimization']:.1f}%** · "
            f"shrinking **{s['shrinking']:.1f}%** of {result['live']:,} live classes, fields and methods"
            + (f" · global: `{'`, `'.join(result['global_rules'])}`" if result["global_rules"] else ""),
            "",
            "| Blocks optimization | Rule | From |",
            "|---:|---|---|",
        ]
        for rule in result["top_rules"]:
            text = rule["rule"] if len(rule["rule"]) <= 90 else rule["rule"][:87] + "..."
            out.append(f"| {rule['blocks_optimization']:,} | `{text}` | {rule['origin']} |")
        out.append("")
        if gated:
            for metric, value in s.items():
                if value < args.min_score:
                    failures.append(f"{name}: {metric} {value:.1f}% < {args.min_score:.0f}%")

    known = {name for name, _ in args.artifact} | {name for name, _ in args.analyzer}
    failures += [f"{name}: --gate names no --artifact or --analyzer" for name in args.gate if name not in known]

    out.append("Play Console's app bundle explorer has the numbers Play enforces for an uploaded bundle.")
    text = "\n".join(out) + "\n"
    print(text)
    if args.summary:
        with args.summary.open("a", encoding="utf-8") as handle:
            handle.write(text)
    if args.json:
        args.json.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    for failure in failures:
        print(f"::error title=R8 optimization score::{failure}", file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
