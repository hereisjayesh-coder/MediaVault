"""Shared plain-HTTP file download helper.

Used by any bridge module whose backend resolves a post down to one direct, already-public
CDN URL rather than driving its own download machinery — Instaloader's resolved image/video
URLs, and yt-dlp's Reddit single-image fast path (see `mediavault_ytdlp.py`). Kept in one
place so both bridges stream a URL to a file the exact same way instead of maintaining two
copies of the same `requests.get(...)` loop.
"""

import requests

_CHUNK_SIZE = 65536


def download_to_file(url, output_path, timeout=30):
    """Streams [url] to [output_path], overwriting it, and returns [output_path]."""
    response = requests.get(url, stream=True, timeout=timeout)
    response.raise_for_status()
    with open(output_path, "wb") as f:
        for chunk in response.iter_content(chunk_size=_CHUNK_SIZE):
            if chunk:
                f.write(chunk)
    return output_path
