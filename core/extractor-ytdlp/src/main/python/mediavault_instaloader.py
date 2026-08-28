"""Thin bridge between Instaloader and the Kotlin ExtractorEngine implementation.

Mirrors mediavault_ytdlp.py's contract exactly: Kotlin only calls the functions below and
gets back plain values (a bool or a JSON string). All Instaloader-specific behavior — URL
shapes, exception types, request details — stays on this side of the boundary so the Kotlin
code never has to know anything about Instaloader's Python API.

Anonymous by design: this module never logs in, imports cookies, or otherwise attempts to
access a private/login-gated account. A post that genuinely requires authentication raises
Instaloader's own exception, which propagates to Kotlin unchanged and is mapped to a clear,
honest error there (see InstaloaderErrorMapper.kt) — never bypassed.
"""

import json
import re

import instaloader

import mediavault_direct_download

_POST_URL_PATTERN = re.compile(r"instagram\.com/(?:p|reel|tv)/([^/?#]+)")


def _shortcode_from_url(url):
    match = _POST_URL_PATTERN.search(url or "")
    return match.group(1) if match else None


def can_handle(url):
    """True if this looks like an Instagram single-post/Reel/IGTV URL.

    A local, offline regex check — like yt-dlp's own can_handle, this does not touch the
    network, so it's safe to call just to decide whether a URL is worth analyzing.
    """
    return _shortcode_from_url(url) is not None


def _new_context():
    return instaloader.Instaloader(
        download_pictures=False,
        download_videos=False,
        download_video_thumbnails=False,
        download_geotags=False,
        download_comments=False,
        save_metadata=False,
        compress_json=False,
        quiet=True,
    ).context


def _node_url(node, is_video):
    if is_video:
        return node.video_url
    # A top-level Post exposes its own image via `.url`; a carousel child (PostSidecarNode)
    # exposes it via `.display_url` — different attribute names for the same concept.
    return getattr(node, "display_url", None) or node.url


def _collect_nodes(post):
    """Every downloadable node in [post], in source order — the post itself for a single
    image/video, or every carousel child for a sidecar. Never resolves lazily per-item twice:
    callers that need both metadata and a specific item's URL should call this once."""
    if post.typename == "GraphSidecar":
        return list(post.get_sidecar_nodes())
    return [post]


def analyze(url):
    """Returns this post's metadata (single image or full carousel) as a JSON string.

    Resolves whatever the post actually contains — MediaVault's own routing
    (CompositeExtractorEngine) only calls this once yt-dlp has already failed to find a
    downloadable video for the URL, so in practice this runs for image-only posts, but this
    function itself makes no assumption about that and just reports what it finds.
    """
    shortcode = _shortcode_from_url(url)
    if shortcode is None:
        raise ValueError("Not a recognized Instagram post URL.")

    context = _new_context()
    post = instaloader.Post.from_shortcode(context, shortcode)
    nodes = _collect_nodes(post)

    items = []
    for index, node in enumerate(nodes, start=1):
        is_video = bool(node.is_video)
        display_url = getattr(node, "display_url", None) or getattr(node, "url", None)
        items.append({
            "index": index,
            "isVideo": is_video,
            "imageUrl": _node_url(node, is_video),
            "thumbnailUrl": display_url,
        })

    result = {
        "id": post.shortcode,
        "sourceName": "Instagram",
        "title": post.caption or "",
        "thumbnailUrl": items[0]["thumbnailUrl"] if items else None,
        "webpageUrl": url,
        "items": items,
    }
    return json.dumps(result)


def download(task_id, url, format_id, output_path):
    """Downloads the item at 1-based index [format_id] within the post at [url] to
    [output_path]. A plain HTTP fetch (not Instaloader's own download helpers) — the direct
    CDN URL is already fully resolved by this point, so no further Instagram-specific
    handling is needed to save its bytes.
    """
    shortcode = _shortcode_from_url(url)
    if shortcode is None:
        raise ValueError("Not a recognized Instagram post URL.")
    index = int(format_id)

    context = _new_context()
    post = instaloader.Post.from_shortcode(context, shortcode)
    nodes = _collect_nodes(post)
    if index < 1 or index > len(nodes):
        raise ValueError("This item is no longer part of the post.")
    node = nodes[index - 1]
    source_url = _node_url(node, bool(node.is_video))

    return mediavault_direct_download.download_to_file(source_url, output_path)
