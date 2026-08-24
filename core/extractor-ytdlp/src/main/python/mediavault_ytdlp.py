"""Thin bridge between yt-dlp and the Kotlin ExtractorEngine implementation.

Kotlin only calls the two functions below and gets back plain values (a bool, or a
JSON string). All yt-dlp-specific behavior — options, extractor internals, error
types — stays on this side of the boundary so the Kotlin code never has to know
anything about yt-dlp's Python API.
"""

import json

import yt_dlp
import yt_dlp.extractor


def can_handle(url):
    """True if a real (non-generic) yt-dlp extractor recognizes this URL.

    This is a local, offline regex check — it does not touch the network — so it is
    safe to call just to decide whether a URL is worth analyzing.
    """
    if not url:
        return False
    for extractor_class in yt_dlp.extractor.gen_extractor_classes():
        if getattr(extractor_class, "IE_NAME", "") == "generic":
            continue
        try:
            if extractor_class.suitable(url):
                return True
        except Exception:
            continue
    return False


def analyze(url):
    """Extracts metadata for a single URL without downloading anything.

    Returns yt-dlp's own sanitized info-dict, JSON-encoded. If [url] is a single video,
    the dict is fully resolved (formats, tracks, ...). If it is a playlist/channel,
    `extract_flat="in_playlist"` keeps each entry lightweight (id/title/thumbnail/url)
    instead of fully resolving every item — resolving one item's formats happens later,
    on demand, by calling this function again with that item's own URL.
    """
    options = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "simulate": True,
        "extract_flat": "in_playlist",
    }
    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=False)
        if info is None:
            raise yt_dlp.utils.ExtractorError("No information could be extracted for this URL.")
        info = ydl.sanitize_info(info)
    return json.dumps(info)
