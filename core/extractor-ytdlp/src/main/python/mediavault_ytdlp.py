"""Thin bridge between yt-dlp and the Kotlin ExtractorEngine implementation.

Kotlin only calls the functions below and gets back plain values (a bool, a JSON
string, or nothing). All yt-dlp-specific behavior — options, extractor internals,
error types — stays on this side of the boundary so the Kotlin code never has to know
anything about yt-dlp's Python API.
"""

import json
import threading

import yt_dlp
import yt_dlp.extractor

# taskId -> latest progress dict, written by yt-dlp's hook thread, read by Kotlin's
# polling coroutine. A lock is enough here: entries are small and short-lived.
_progress_state = {}
_progress_lock = threading.Lock()

# taskIds that should stop as soon as the next hook callback fires. Shared between
# pause and cancel — the caller decides what a stopped download means afterwards.
_stop_requested = set()

PAUSE_SENTINEL = "__mediavault_stopped__"


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
    """
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
