#!/usr/bin/env python3
"""Fetch the printer capability databases and emit them as one HOCON file.

The bulk comes from escpos-printer-db: the latest release's "Source code (zip)"
from https://github.com/receipt-print-hq/escpos-printer-db, whose
``dist/capabilities.json`` is converted to HOCON and written to
``faradn-core/src/main/resources/capabilities.conf``.

escpos-printer-db does not usefully cover Star Micronics printers (the TSP100IV
family is absent, and the Star entries it has are ESC/POS-emulation profiles
whose code-page slots are Epson ``ESC t`` numbers, not Star native
``ESC GS t`` numbers). So the Star ``star-prnt`` profiles are merged in from
ReceiptPrinterEncoder (https://github.com/at-point-of-sale/ReceiptPrinterEncoder,
MIT): its per-model ``data/printers/*.json`` plus the native
``data/mappings/star-prnt/star.txt`` selector table. Each merged profile carries
a ``language = "star-prnt"`` field so the Java loader routes it to the StarPRNT
renderer; every other profile has no such field and stays ESC/POS. This replaces
the hand-authored ``StarProfiles`` Java (except its Japanese Kanji-ROM variant,
which no database records).

The generated file records both source versions in header comments. On a later
run the script resolves the latest of each, compares against those comments, and
skips the (large) downloads and conversion when both are already current -
unless ``--force`` is given.

Standard library only; no third-party packages required. Set ``GITHUB_TOKEN``
(or pass ``--token``) to lift the unauthenticated GitHub API rate limit.
"""

from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

REPO = "receipt-print-hq/escpos-printer-db"
REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUTPUT = REPO_ROOT / "faradn-core" / "src" / "main" / "resources" / "capabilities.conf"
CAPABILITIES_ENTRY = "dist/capabilities.json"
VERSION_MARKER = "# escpos-printer-db version:"

# Star native profiles are merged from ReceiptPrinterEncoder (see module docstring).
STAR_REPO = "at-point-of-sale/ReceiptPrinterEncoder"
STAR_VERSION_MARKER = "# ReceiptPrinterEncoder version:"
STAR_PRINTERS_PREFIX = "data/printers/"
STAR_MAPPING_ENTRY = "data/mappings/star-prnt/star.txt"

# A HOCON key may stay unquoted only when it is a simple identifier; anything
# else (numeric slots, dots, symbols) must be quoted to parse unambiguously.
SAFE_KEY = re.compile(r"[A-Za-z_][A-Za-z0-9_-]*")


def http_get(url: str, token: str | None, accept: str = "application/vnd.github+json") -> bytes:
    headers = {"User-Agent": "faradn-capabilities-fetch", "Accept": accept}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request) as response:
        return response.read()


def resolve_latest(repo: str, token: str | None) -> tuple[str, str]:
    """Return ``(version_tag, zipball_url)`` for the latest release.

    Falls back to the newest tag when the repository publishes tags but no
    formal GitHub releases.
    """
    try:
        data = json.loads(http_get(f"https://api.github.com/repos/{repo}/releases/latest", token))
        return data["tag_name"], data["zipball_url"]
    except urllib.error.HTTPError as exc:
        if exc.code != 404:
            raise
    tags = json.loads(http_get(f"https://api.github.com/repos/{repo}/tags", token))
    if not tags:
        raise SystemExit(f"{repo} has no releases or tags to download")
    return tags[0]["name"], tags[0]["zipball_url"]


def resolve_latest_tag(repo: str, token: str | None) -> tuple[str, str]:
    """Return ``(tag, zipball_url)`` for the highest ``vX.Y.Z`` tag of ``repo``.

    ReceiptPrinterEncoder ships its meaningful versions as git tags rather than
    formal GitHub releases (and its schema is version-sensitive), so its source
    is selected by the highest semantic-version tag rather than by
    ``releases/latest``. Only the first page of tags is consulted, which is ample
    for a repository with a handful of releases.
    """
    tags = json.loads(http_get(f"https://api.github.com/repos/{repo}/tags", token))
    if not tags:
        raise SystemExit(f"{repo} has no tags to download")

    def semver(tag: dict) -> tuple[int, int, int]:
        match = re.match(r"v?(\d+)\.(\d+)\.(\d+)", tag["name"])
        return tuple(int(part) for part in match.groups()) if match else (-1, -1, -1)

    latest = max(tags, key=semver)
    return latest["name"], latest["zipball_url"]


def recorded_version(path: Path, marker: str = VERSION_MARKER) -> str | None:
    """The version noted after ``marker`` in an existing output's header, if any."""
    if not path.exists():
        return None
    with path.open(encoding="utf-8") as handle:
        for raw in handle:
            line = raw.strip()
            if line.startswith(marker):
                return line[len(marker):].strip()
            if line and not line.startswith("#"):
                break  # reached the body without finding a marker
    return None


def extract_capabilities(zipball: bytes) -> dict:
    with zipfile.ZipFile(io.BytesIO(zipball)) as archive:
        entry = next((name for name in archive.namelist() if name.endswith(CAPABILITIES_ENTRY)), None)
        if entry is None:
            raise SystemExit(f"{CAPABILITIES_ENTRY} not found in the source archive")
        with archive.open(entry) as handle:
            return json.load(handle)


def java_charset_name(rpe_name: str) -> str:
    """Translate a ReceiptPrinterEncoder code-page name to a Java charset name.

    The ``cpNNN`` names resolve directly through ``Charset.forName`` (``cp858``
    is an alias of ``IBM00858`` etc.), so they pass through unchanged; only the
    ``windowsNNNN`` names need the hyphen Java expects. Star-specific pages
    (``star/standard``, ``star/katakana``, ``star/cp928`` …) and code pages Java
    has no charset for are emitted verbatim - the Java loader's ``charsetFor``
    drops any slot it cannot resolve, exactly as it does for escpos-printer-db's
    own unknown pages.
    """
    if rpe_name.startswith("windows") and not rpe_name.startswith("windows-"):
        return "windows-" + rpe_name[len("windows"):]
    return rpe_name


def parse_star_mapping(text: str) -> dict[int, str]:
    """Parse ``star.txt`` (``<slot-hex>\\t<encoding>``) into slot id → Java charset.

    Commented (``#``) and blank lines are skipped, so a slot the source has
    disabled is simply absent.
    """
    mapping: dict[int, str] = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        parts = stripped.split()
        if len(parts) < 2:
            continue
        mapping[int(parts[0], 16)] = java_charset_name(parts[1])
    return mapping


def extract_star_profiles(zipball: bytes) -> dict:
    """Build HOCON profile objects for the Star ``star-prnt`` models in the zip.

    Reads every ``data/printers/*.json`` plus the native ``star-prnt`` selector
    table, keeping Star models whose language is ``star-prnt`` (the family the
    StarPRNT renderer speaks). Each profile is shaped like an escpos-printer-db
    profile - the fields ``CapabilityProfiles`` reads - so both sources merge
    into one ``profiles`` map. The printable width is derived as Font A columns ×
    the font's cell width (e.g. 48 × 12 = 576 dots), since the source records
    only paper millimetres, not printable dots.
    """
    font_names = {"A": "Font A", "B": "Font B", "C": "Font C"}
    profiles: dict = {}
    with zipfile.ZipFile(io.BytesIO(zipball)) as archive:
        mapping_entry = next((n for n in archive.namelist() if n.endswith(STAR_MAPPING_ENTRY)), None)
        if mapping_entry is None:
            raise SystemExit(f"{STAR_MAPPING_ENTRY} not found in the Star source archive")
        mapping = parse_star_mapping(archive.read(mapping_entry).decode("utf-8"))
        code_pages = {str(slot): name for slot, name in sorted(mapping.items())}

        for name in archive.namelist():
            marker = name.find(STAR_PRINTERS_PREFIX)
            if marker < 0 or not name.endswith(".json"):
                continue
            model = json.loads(archive.read(name))
            caps = model.get("capabilities", {})
            if model.get("vendor") != "Star" or caps.get("language") != "star-prnt":
                continue
            if caps.get("codepages") != "star":
                continue  # only the native star table is expanded here

            fonts = caps.get("fonts", {})
            font_a = fonts.get("A")
            if not font_a:
                continue  # no Font A budget: CapabilityProfiles would reject it anyway
            cell_width = int(font_a["size"].split("x")[0])
            pixels = font_a["columns"] * cell_width

            fonts_block = {
                str(index): {"columns": fonts[letter]["columns"], "name": font_names[letter]}
                for index, letter in enumerate(("A", "B", "C"))
                if letter in fonts
            }
            # The cutter lives under capabilities in current schemas; tolerate the
            # older top-level features.cutter placement too.
            has_cutter = "cutter" in caps or "cutter" in model.get("features", {})
            barcodes = bool(caps.get("barcodes", {}).get("supported"))
            profiles[model["model"]] = {
                "codePages": code_pages,
                "colors": {"0": "black"},
                "features": {
                    "barcodeA": barcodes,
                    "barcodeB": barcodes,
                    "bitImageColumn": False,
                    "bitImageRaster": True,
                    "graphics": True,
                    "highDensity": True,
                    "paperFullCut": has_cutter,
                    "paperPartCut": has_cutter,
                    "pdf417Code": bool(caps.get("pdf417", {}).get("supported")),
                    "pulseBel": False,
                    "pulseStandard": True,
                    "qrCode": bool(caps.get("qrcode", {}).get("supported")),
                    "starCommands": True,
                },
                "fonts": fonts_block,
                "language": "star-prnt",
                "media": {
                    "dpi": model["media"]["dpi"],
                    "width": {"mm": model["media"]["width"], "pixels": pixels},
                },
                "name": f"{model['vendor']} {model['model']}",
                "notes": f"Star {model['model']} profile (ReceiptPrinterEncoder)\n",
                "vendor": model["vendor"],
            }
    return profiles


def hocon_key(key: str) -> str:
    return key if SAFE_KEY.fullmatch(key) else json.dumps(key, ensure_ascii=False)


def render_object(obj: dict, indent: int, out: list[str]) -> None:
    """Append the HOCON body of ``obj`` (no enclosing braces) to ``out``.

    Nested objects use ``key { ... }``; every other value (scalars and arrays)
    is emitted as ``key = <json>``, which is valid HOCON since HOCON is a JSON
    superset.
    """
    pad = "  " * indent
    for key, value in obj.items():
        name = hocon_key(key)
        if isinstance(value, dict) and value:
            out.append(f"{pad}{name} {{")
            render_object(value, indent + 1, out)
            out.append(f"{pad}}}")
        elif isinstance(value, dict):
            out.append(f"{pad}{name} {{}}")
        else:
            out.append(f"{pad}{name} = {json.dumps(value, ensure_ascii=False)}")


def to_hocon(data: dict, version: str, repo: str, star_version: str, star_count: int) -> str:
    if not isinstance(data, dict):
        raise SystemExit("capabilities.json root is not a JSON object")
    out = [
        "# Generated by scripts/fetch_capabilities.py. Do not edit by hand.",
        f"# Source: https://github.com/{repo}",
        f"{VERSION_MARKER} {version}",
        f"# Star profiles ({star_count}) merged from https://github.com/{STAR_REPO}",
        f"{STAR_VERSION_MARKER} {star_version}",
        "",
    ]
    render_object(data, 0, out)
    out.append("")
    return "\n".join(out)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--force", action="store_true",
                        help="re-download and regenerate even if already current")
    parser.add_argument("--profiles-only", action="store_true",
                        help="drop the large 'encodings' tables, keeping only printer profiles")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT,
                        help=f"output .conf path (default: {DEFAULT_OUTPUT})")
    parser.add_argument("--repo", default=REPO, help=f"GitHub owner/repo (default: {REPO})")
    parser.add_argument("--token", default=os.environ.get("GITHUB_TOKEN"),
                        help="GitHub token, or set $GITHUB_TOKEN, to lift the API rate limit")
    args = parser.parse_args(argv)

    print(f"Resolving the latest release of {args.repo} ...")
    version, zipball_url = resolve_latest(args.repo, args.token)
    print(f"Latest version: {version}")

    print(f"Resolving the latest tag of {STAR_REPO} ...")
    star_version, star_zipball_url = resolve_latest_tag(STAR_REPO, args.token)
    print(f"Latest version: {star_version}")

    current = recorded_version(args.output)
    current_star = recorded_version(args.output, STAR_VERSION_MARKER)
    if current == version and current_star == star_version and not args.force:
        print(f"{args.output} is already at {version} / Star {star_version}; nothing to do "
              "(pass --force to regenerate).")
        return 0
    if current and current != version:
        print(f"Updating escpos-printer-db {current} -> {version}.")
    if current_star and current_star != star_version:
        print(f"Updating ReceiptPrinterEncoder {current_star} -> {star_version}.")

    print("Downloading the source archives ...")
    zipball = http_get(zipball_url, args.token, accept="application/zip")
    star_zipball = http_get(star_zipball_url, args.token, accept="application/zip")

    print("Extracting and converting capabilities.json ...")
    data = extract_capabilities(zipball)
    if args.profiles_only:
        data = {key: value for key, value in data.items() if key != "encodings"}

    print("Merging Star star-prnt profiles ...")
    star_profiles = extract_star_profiles(star_zipball)
    profiles = data.setdefault("profiles", {})
    for name, profile in star_profiles.items():
        if name in profiles:
            print(f"  warning: Star profile '{name}' shadows an escpos-printer-db entry")
        profiles[name] = profile
    print(f"  merged {len(star_profiles)} Star profile(s): {', '.join(sorted(star_profiles))}")

    hocon = to_hocon(data, version, args.repo, star_version, len(star_profiles))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(hocon, encoding="utf-8")
    print(f"Wrote {args.output} ({len(hocon):,} bytes) at version {version} / Star {star_version}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
