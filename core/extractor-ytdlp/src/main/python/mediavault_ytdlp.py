"""Thin bridge between yt-dlp and the Kotlin ExtractorEngine implementation.

Kotlin only calls the functions below and gets back plain values (a bool, a JSON
string, or nothing). All yt-dlp-specific behavior — options, extractor internals,
error types — stays on this side of the boundary so the Kotlin code never has to know
anything about yt-dlp's Python API.
"""

import json
import threading
import urllib.parse

import yt_dlp
import yt_dlp.extractor

import mediavault_direct_download

# taskId -> latest progress dict, written by yt-dlp's hook thread, read by Kotlin's
# polling coroutine. A lock is enough here: entries are small and short-lived.
_progress_state = {}
_progress_lock = threading.Lock()

# taskIds that should stop as soon as the next hook callback fires. Shared between
# pause and cancel — the caller decides what a stopped download means afterwards.
_stop_requested = set()

PAUSE_SENTINEL = "__mediavault_stopped__"

# yt-dlp's Reddit extractor has no reliable multi-image gallery support (confirmed directly:
# running a gallery post through the normal pipeline below can hang for 60+ seconds instead of
# failing cleanly, and yt-dlp's own light/unprocessed extraction just falls through to a broken
# generic-extractor loop for the gallery's resolved sub-URL). So a single-image Reddit post is
# detected and served here, cheaply, *before* ever risking the normal pipeline below — see
# `_reddit_light_probe`/`_reddit_image_result`. A gallery post is refused outright with a clear
# message (see YtDlpErrorMapper.kt) instead of silently downloading only one of its images or
# hanging the app. Everything else (Reddit videos, every other site) is untouched — this only
# ever short-circuits when a Reddit URL's light probe resolves to a single direct image.
_REDDIT_HOSTS = ("reddit.com", "redd.it")
_IMAGE_EXTENSIONS = (".jpg", ".jpeg", ".png", ".gif", ".webp")


def _is_reddit_url(url):
    try:
        host = urllib.parse.urlparse(url).netloc.lower()
    except ValueError:
        return False
    return any(host == h or host.endswith("." + h) for h in _REDDIT_HOSTS)


def _reddit_light_probe(url):
    """Reddit's post metadata via yt-dlp's own unprocessed extractor output — `process=False`
    skips redirect-following and format resolution, which is exactly what makes it cheap
    enough to run before deciding whether the (sometimes hang-prone) normal pipeline is even
    safe to attempt. Returns `None` if nothing could be extracted at all.
    """
    options = {"quiet": True, "no_warnings": True, "skip_download": True, "simulate": True}
    with yt_dlp.YoutubeDL(options) as ydl:
        return ydl.extract_info(url, download=False, process=False)


def _reddit_image_result(probe):
    """`None` if [probe] isn't a single direct image — the caller should fall through to
    yt-dlp's normal pipeline unchanged (a Reddit video post, or a post linking an external
    embed like a streaming site, both resolve here too but aren't images). Raises `ValueError`
    for a multi-image gallery. Otherwise returns the dict this module's `analyze()`/`download()`
    should use, in the same info-dict shape the normal pipeline produces (see YtDlpInfoJson.kt).
    """
    if probe is None or probe.get("_type") != "url_transparent":
        return None

    resolved_url = probe.get("url") or ""
    if "/gallery/" in resolved_url:
        raise ValueError(
            "This is a multi-image Reddit gallery post — MediaVault can only download "
            "single-image Reddit posts today."
        )
    if not resolved_url.lower().split("?")[0].endswith(_IMAGE_EXTENSIONS):
        return None  # An external (non-image) embed, e.g. a linked video site.

    return {
        # The light probe (process=False) never resolves "id" itself, only "display_id" — the
        # Reddit post's actual short id (e.g. "1w0mfi4"), confirmed via direct testing. Falling
        # back to "id" too in case some other Reddit-like URL shape ever does populate it.
        "id": probe.get("id") or probe.get("display_id"),
        "title": probe.get("title"),
        "thumbnail": probe.get("thumbnail"),
        "thumbnails": probe.get("thumbnails"),
        "webpage_url": probe.get("webpage_url"),
        "extractor": probe.get("extractor"),
        "extractor_key": probe.get("extractor_key"),
        "imageUrl": resolved_url,
    }


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

    A Reddit URL is special-cased first: see the module-level comment above
    `_REDDIT_HOSTS` for why a single-image post is detected via a cheap light probe before
    ever reaching the normal pipeline below, and why a gallery post is rejected outright.
    """
    if _is_reddit_url(url):
        image_result = _reddit_image_result(_reddit_light_probe(url))
        if image_result is not None:
            return json.dumps(image_result)

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


def request_stop(task_id):
    """Asks an in-flight analyze/download call for [task_id] to stop as soon as possible.

    Safe to call for an id with no matching in-flight call — it just sits unused.
    """
    _stop_requested.add(task_id)


def get_progress(task_id):
    """Returns the most recent progress snapshot for [task_id] as a JSON string, or None."""
    with _progress_lock:
        state = _progress_state.get(task_id)
    return json.dumps(state) if state else None


def _make_progress_hook(task_id):
    def hook(d):
        if task_id in _stop_requested:
            raise yt_dlp.utils.DownloadError(PAUSE_SENTINEL)
        with _progress_lock:
            _progress_state[task_id] = {
                "status": d.get("status"),
                "downloaded_bytes": d.get("downloaded_bytes") or 0,
                "total_bytes": d.get("total_bytes") or d.get("total_bytes_estimate"),
                "speed": d.get("speed"),
                "eta": d.get("eta"),
            }
    return hook


def download(task_id, url, format_id, output_path):
    """Downloads a single, previously-analyzed format to an exact local file path.

    [output_path] is a real filesystem path (e.g. inside the app's cache dir) chosen by
    the caller — not a SAF URI, which yt-dlp/Python cannot write to directly. Progress is
    reported via [get_progress]; call [request_stop] from another thread to pause/cancel.
    Raises on failure, including a DownloadError with message [PAUSE_SENTINEL] when the
    caller requested a stop — the Kotlin side treats that specially, not as a real error.

    A single-image Reddit post (see `analyze()`) is re-detected the same way here and
    downloaded as a plain HTTP GET, not through yt-dlp's own (video-oriented) download
    machinery — there's no format to select and nothing for [format_id] to mean, so it's
    ignored for this case, same as `mediavault_instaloader.py`'s `download()`. This
    re-probes rather than trusting [url]/[format_id] alone because [url] here is always the
    *post's* webpage URL (see HomeViewModel.enqueueCollectionItems) — the resolved direct
    image URL itself is never round-tripped through the download queue's own storage.
    """
    if _is_reddit_url(url):
        image_result = _reddit_image_result(_reddit_light_probe(url))
        if image_result is not None:
            return mediavault_direct_download.download_to_file(image_result["imageUrl"], output_path)

    _stop_requested.discard(task_id)
    options = {
        "quiet": True,
        "no_warnings": True,
        "format": format_id,
        "outtmpl": output_path,
        "noplaylist": True,
        "continuedl": True,
        "progress_hooks": [_make_progress_hook(task_id)],
    }
    try:
        with yt_dlp.YoutubeDL(options) as ydl:
            ydl.download([url])
    finally:
        _stop_requested.discard(task_id)
        with _progress_lock:
            _progress_state.pop(task_id, None)
    return output_path
