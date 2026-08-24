#!/usr/bin/env python3
"""Generates MediaVault's Supported Sources catalog from the currently-installed yt-dlp.

This is a controlled, offline generation step — it is NOT run by the app, and it is NOT
run automatically by Gradle. Run it by hand whenever yt-dlp is upgraded, and commit the
resulting JSON asset alongside the version bump:

    python core/extractor-ytdlp/scripts/generate_source_catalog.py

It must be run with the same yt-dlp version pinned in
`core/extractor-ytdlp/build.gradle.kts` installed (`pip install yt-dlp==<pinned version>`)
so the generated catalog matches what actually ships in the app. The script reads
`yt_dlp.extractor`'s own extractor registry (~1750 extractor classes covering ~1700
distinct services) and writes one row per *service*, not per extractor class — most
services register several extractor variants (e.g. `youtube`, `youtube:tab`,
`youtube:search`, ...) which this script groups into a single catalog entry so the UI
never shows near-duplicate rows for one site.

Output: core/extractor-ytdlp/src/main/assets/source_catalog.json
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

import yt_dlp
import yt_dlp.extractor as yt_extractor

OUTPUT_PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "assets" / "source_catalog.json"

# A domain (or bare hostname label, e.g. "youtube" for "youtube.com") is mapped to a
# category via keyword membership, checked in this priority order — first match wins.
# This is intentionally a short, curated list of well-known signals, not a per-site
# database: everything not matched by a rule here falls back to VIDEO (yt-dlp's core
# purpose) or OTHER for known generic/utility extractors. See CATEGORY_OVERRIDES below
# for exact per-service corrections and multi-category assignments.
CATEGORY_KEYWORD_RULES: list[tuple[str, list[str]]] = [
    ("PODCASTS", ["podcast", "podbean", "spreaker", "buzzsprout", "acast", "anchor"]),
    ("MUSIC", [
        "music", "spotify", "soundcloud", "bandcamp", "deezer", "mixcloud", "audiomack",
        "tidal", "napster", "genius", "audius", "bandsintown", "kkbox", "qobuz",
    ]),
    ("AUDIO", ["audioboom", "clyp", "soundgasm", "audiodraft", "radio"]),
    ("EDUCATION", [
        "coursera", "udemy", "khanacademy", "edx", "skillshare", "udacity",
        "pluralsight", "classcentral", "lecture", "masterclass", "ted",
        "curiositystream", "school", "academy", "university",
    ]),
    ("NEWS", [
        "news", "bbc", "cnn", "nytimes", "reuters", "foxnews", "theguardian",
        "aljazeera", "npr", "washingtonpost", "bloomberg", "skynews", "abcnews",
        "cbsnews", "nbcnews", "cbc", "afp", "ap.org", "wsj", "forbes", "time.com",
    ]),
    ("SPORTS", [
        "espn", "nba", "nfl", "mlb", "nhl", "fifa", "uefa", "skysports",
        "bleacherreport", "dazn", "wwe", "motorsport", "olympics", "formula1",
    ]),
    ("ANIME", ["crunchyroll", "funimation", "animelab", "wakanim", "aniplus", "anime"]),
    ("CLOUD_HOSTING", [
        "drive.google", "googledrive", "dropbox", "mega.nz", "mediafire", "box.com",
        "onedrive", "pcloud", "wetransfer", "yandexdisk", "4shared",
    ]),
    ("LIVE_STREAMING", ["twitch", "kick.com", "dlive", "trovo", "nimo", "younow", "bigo", "chaturbate"]),
    ("SOCIAL_MEDIA", [
        "facebook", "instagram", "twitter", "x.com", "tiktok", "reddit", "snapchat",
        "pinterest", "linkedin", "tumblr", "weibo", "vk.com", "threads.net",
        "mastodon", "bluesky", "bsky", "discord",
    ]),
]

ADULT_DOMAIN_HINTS = [
    "porn", "xvideos", "xnxx", "xhamster", "redtube", "youporn", "spankbang",
    "chaturbate", "brazzers", "onlyfans", "stripchat",
]

# Known generic/utility extractors that aren't really "a service" a user would search
# for by name — kept out of VIDEO's default bucket.
GENERIC_FAMILY_KEYS = {"generic", "html5mediaembed", "quotedhtml", "commonmistakes", "unsupported"}

# A short list of well-known services where the automatic category and/or display name
# needs a manual correction, or where a second, equally-justified category applies. Keyed
# by the grouping "family key" (see `family_key_for`). This list intentionally stays
# small — it corrects the handful of top services users are most likely to search for,
# it does not attempt to hand-classify all ~1700 entries.
CATEGORY_OVERRIDES: dict[str, list[str]] = {
    "youtube": ["VIDEO", "MUSIC", "LIVE_STREAMING"],
    "tiktok": ["SOCIAL_MEDIA", "VIDEO"],
    "instagram": ["SOCIAL_MEDIA", "VIDEO"],
    "facebook": ["SOCIAL_MEDIA", "VIDEO"],
    "twitch": ["LIVE_STREAMING", "VIDEO"],
    "bilibili": ["VIDEO", "ANIME"],
    "vimeo": ["VIDEO"],
    "reddit": ["SOCIAL_MEDIA"],
    "soundcloud": ["MUSIC", "AUDIO"],
    "spotify": ["MUSIC"],
    "twitter": ["SOCIAL_MEDIA"],
    "x": ["SOCIAL_MEDIA"],
    # yt-dlp's catch-all embed extractor defensively sets age_limit=18 (it can match
    # arbitrary sites, including adult ones) — that default would otherwise misclassify
    # the fallback mechanism itself as an adult site.
    "generic": ["OTHER"],
}

DISPLAY_NAME_OVERRIDES: dict[str, str] = {
    "youtube": "YouTube",
    "tiktok": "TikTok",
    "vimeo": "Vimeo",
    "soundcloud": "SoundCloud",
    "twitch": "Twitch",
    "reddit": "Reddit",
    "facebook": "Facebook",
    "instagram": "Instagram",
    "twitter": "Twitter",
    "x": "X (Twitter)",
    "bilibili": "BiliBili",
    "dailymotion": "Dailymotion",
    "linkedin": "LinkedIn",
    "tumblr": "Tumblr",
    "pinterest": "Pinterest",
    "espn": "ESPN",
    "bbc": "BBC",
    "cnn": "CNN",
    "ted": "TED",
    "wwe": "WWE",
    "nba": "NBA",
    "nfl": "NFL",
    "mlb": "MLB",
    "nhl": "NHL",
    "khanacademy": "Khan Academy",
    "pornhub": "Pornhub",
    "soundcloud": "SoundCloud",
    "nrk": "NRK",
    "srgssr": "SRG SSR",
}


def normalize_domain(netloc: str) -> str | None:
    """'www.youtube.com' -> 'youtube.com'; '' -> None. Strips a small set of common
    non-identifying subdomain prefixes so variants of one site share one domain."""
    host = netloc.split(":")[0].lower().strip()
    if not host or "." not in host:
        return None
    for prefix in ("www.", "m.", "mobile.", "amp."):
        if host.startswith(prefix):
            host = host[len(prefix):]
            break
    return host or None


# Two-label suffixes where the *third*-from-last label is the real registrable name
# (e.g. "espn.go.com" -> "espn", "bbc.co.uk" -> "bbc") — without this, naively taking
# domain.split(".")[0] on a subdomain like "tv.nrk.no" or "v.baidu.com" would produce the
# subdomain ("tv", "v") instead of the actual service ("nrk", "baidu"), incorrectly
# merging unrelated services that happen to share a generic subdomain prefix.
KNOWN_COMPOUND_SUFFIXES = {
    "co.uk", "co.kr", "co.jp", "co.in", "co.nz", "co.za", "co.il", "co.id",
    "com.br", "com.au", "com.mx", "com.tr", "com.cn", "com.tw", "com.hk",
    "or.kr", "or.jp", "ne.jp", "ac.jp", "org.uk", "gov.uk", "ac.uk", "go.jp", "go.com",
}

# A tiny number of extractors resolve a real domain from their first test URL that is
# misleading for grouping purposes (e.g. yt-dlp's catch-all `Generic` extractor happens to
# use a w3.org URL in its own test suite). Forced to a fixed family key instead of the
# usual domain-derived one.
FORCE_FAMILY_KEY_BY_IE_KEY = {
    "Generic": "generic",
}


def registrable_label(domain: str) -> str:
    """The single label that identifies the service itself, e.g. 'nrk' for 'tv.nrk.no'."""
    parts = domain.split(".")
    if len(parts) <= 2:
        return parts[0]
    if ".".join(parts[-2:]) in KNOWN_COMPOUND_SUFFIXES and len(parts) >= 3:
        return parts[-3]
    return parts[-2]


def family_key_for(ie_name: str, domain: str | None) -> str:
    """The grouping key that decides which extractor classes collapse into one service.

    Prefers the domain's registrable label (e.g. "youtube.com" -> "youtube") since that is
    the strongest same-service signal available. Falls back to the extractor name's family
    prefix (text before the first ':', or the leading word of a CamelCase name) for the
    ~7% of extractors with no real test URL to derive a domain from (keyword-only
    extractors like `ytsearch:`, `:ythistory`, etc.).
    """
    if domain:
        label = registrable_label(domain)
        return re.sub(r"[^a-z0-9]", "", label.lower()) or domain
    prefix = ie_name.split(":")[0]
    # CamelCase without a colon (e.g. "FacebookRedirectURL") — take the leading word.
    match = re.match(r"[A-Z][a-z0-9]*", prefix)
    key = match.group(0) if match else prefix
    if len(key) <= 2:
        # An acronym-style name (e.g. "FOX9", "SRGSSR") — the leading-word regex only
        # grabs one letter before the next capital; keep the whole name instead of
        # collapsing unrelated acronym-named extractors into the same single letter.
        key = prefix
    return re.sub(r"[^a-z0-9]", "", key.lower()) or prefix.lower()


def classify(family_key: str, domain: str | None, age_limit: int) -> list[str]:
    if family_key in CATEGORY_OVERRIDES:
        return CATEGORY_OVERRIDES[family_key]

    haystack = f"{domain or ''} {family_key}".lower()
    if age_limit and age_limit >= 18:
        return ["ADULT"]
    if any(hint in haystack for hint in ADULT_DOMAIN_HINTS):
        return ["ADULT"]

    for category, keywords in CATEGORY_KEYWORD_RULES:
        if any(keyword in haystack for keyword in keywords):
            return [category]

    if family_key in GENERIC_FAMILY_KEYS:
        return ["OTHER"]
    return ["VIDEO"]


def display_name_for(family_key: str, domain: str | None, descriptions: list[str]) -> str:
    if family_key in DISPLAY_NAME_OVERRIDES:
        return DISPLAY_NAME_OVERRIDES[family_key]
    # A short, real IE_DESC (when one exists and isn't itself a long sentence) is usually
    # the cleanest human name yt-dlp ships for a service.
    for desc in descriptions:
        if desc and len(desc) <= 40 and ":" not in desc:
            return desc
    return family_key.replace("-", " ").replace("_", " ").title()


def first_test_domain(extractor_class) -> str | None:
    try:
        tests = extractor_class.get_testcases(include_onlymatching=True)
    except Exception:
        return None
    for test in tests:
        url = test.get("url") if isinstance(test, dict) else None
        if not url:
            continue
        domain = normalize_domain(urlparse(url).netloc)
        if domain:
            return domain
    return None


def build_catalog() -> dict:
    groups: dict[str, dict] = {}

    for extractor_class in yt_extractor.gen_extractor_classes():
        if getattr(extractor_class, "_VALID_URL", None) is False:
            continue  # Pseudo-extractors with no real URL matching (e.g. QuotedHTML).

        ie_key = extractor_class.ie_key()
        ie_name = getattr(extractor_class, "IE_NAME", None) or ie_key
        desc = getattr(extractor_class, "IE_DESC", None)
        desc = desc if isinstance(desc, str) else None
        try:
            working = bool(extractor_class.working())
        except Exception:
            working = True
        age_limit = getattr(extractor_class, "age_limit", 0) or 0
        if ie_key in FORCE_FAMILY_KEY_BY_IE_KEY:
            domain = None
            family_key = FORCE_FAMILY_KEY_BY_IE_KEY[ie_key]
        else:
            domain = first_test_domain(extractor_class)
            family_key = family_key_for(ie_name, domain)

        group = groups.setdefault(family_key, {
            "extractor_ids": [],
            "descriptions": [],
            "domains": [],
            "working_any": False,
            "age_limit": 0,
        })
        group["extractor_ids"].append(ie_key)
        if desc:
            group["descriptions"].append(desc)
        if domain:
            group["domains"].append(domain)
        group["working_any"] = group["working_any"] or working
        group["age_limit"] = max(group["age_limit"], age_limit)

    sources = []
    for family_key, group in groups.items():
        domain = max(set(group["domains"]), key=group["domains"].count) if group["domains"] else None
        display_name = display_name_for(family_key, domain, group["descriptions"])
        categories = classify(family_key, domain, group["age_limit"])

        aliases = {family_key, display_name.lower()}
        if domain:
            aliases.add(domain)
            aliases.add(domain.split(".")[0])
        for extractor_id in group["extractor_ids"]:
            aliases.add(extractor_id.lower())

        sources.append({
            "id": family_key,
            "displayName": display_name,
            "domain": domain,
            "extractorIds": sorted(set(group["extractor_ids"])),
            "categories": categories,
            "aliases": sorted(a for a in aliases if a),
            "isSupported": group["working_any"],
            "faviconUrl": f"https://www.google.com/s2/favicons?domain={domain}&sz=64" if domain else None,
        })

    sources.sort(key=lambda s: s["displayName"].lower())

    return {
        "engineId": "ytdlp",
        "engineVersion": yt_dlp.version.__version__,
        "generatedAtEpochMs": 0,  # filled in below, kept out of the diff-noisy hot path
        "sources": sources,
    }


def main() -> None:
    import time

    catalog = build_catalog()
    catalog["generatedAtEpochMs"] = int(time.time() * 1000)

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(json.dumps(catalog, indent=None, separators=(",", ":")), encoding="utf-8")

    total_extractors = sum(len(s["extractorIds"]) for s in catalog["sources"])
    print(f"yt-dlp version: {catalog['engineVersion']}")
    print(f"Raw extractor classes: {total_extractors}")
    print(f"Grouped services written: {len(catalog['sources'])}")
    print(f"Output: {OUTPUT_PATH}")


if __name__ == "__main__":
    sys.exit(main())
