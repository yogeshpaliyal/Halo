#!/usr/bin/env python3
"""Announce a new Halo release.

Always: a GitHub Discussions post in Announcements, and a ready-to-paste XDA post
(BBCode) in the workflow summary. Optional, each only when its secrets are set:
Telegram channel, Bluesky, Mastodon. A channel that fails prints a warning and the
others carry on; announcing never fails the release.

Env: TAG, REPO, GH_TOKEN, NOTES_FILE (release notes markdown), and optionally
TELEGRAM_BOT_TOKEN + TELEGRAM_CHAT_ID, BLUESKY_HANDLE + BLUESKY_APP_PASSWORD,
MASTODON_INSTANCE + MASTODON_TOKEN.
"""
import datetime as dt
import html
import json
import os
import re
import urllib.parse
import urllib.request

TAG = os.environ["TAG"]
REPO = os.environ["REPO"]
PLAY = "https://play.google.com/store/apps/details?id=com.yogeshpaliyal.halo"
RELEASE = f"https://github.com/{REPO}/releases/tag/{TAG}"
NOTES = open(os.environ.get("NOTES_FILE", "notes.md")).read()

# The "What's new" bullets, without the install/verify sections.
section = NOTES.split("## What's new", 1)[-1].split("\n## ", 1)[0]
CHANGES = [l[2:].strip() for l in section.splitlines() if l.startswith("- ")]


def post(url, data=None, headers=None, form=False):
    body = None
    h = {"User-Agent": "halo-release"}
    h.update(headers or {})
    if data is not None:
        if form:
            body = urllib.parse.urlencode(data).encode()
            h["Content-Type"] = "application/x-www-form-urlencoded"
        else:
            body = json.dumps(data).encode()
            h["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, headers=h, method="POST" if body else "GET")
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def short(limit, bullets=4):
    """A short post: headline, a few changes, Play link. Trimmed to fit `limit`."""
    items = CHANGES[:bullets]
    while True:
        lines = [f"Halo {TAG} is out 🎉", ""]
        lines += [f"• {c}" for c in items]
        if len(CHANGES) > len(items):
            lines.append("• …and more")
        lines += ["", f"Get it on Google Play: {PLAY}", "", "#Android #OpenSource"]
        text = "\n".join(lines)
        if len(text) <= limit or not items:
            return text
        items = items[:-1]


def channel(name, fn):
    try:
        fn()
        print(f"✓ {name}")
    except Exception as e:  # keep going; announcing must never fail the release
        print(f"::warning::{name} announcement failed: {e}")


def discussions():
    token = os.environ["GH_TOKEN"]
    owner, name = REPO.split("/")
    gql = lambda q, v: post(
        "https://api.github.com/graphql", {"query": q, "variables": v},
        {"Authorization": f"Bearer {token}"},
    )
    repo = gql(
        "query($o:String!,$n:String!){repository(owner:$o,name:$n){id discussionCategories(first:25){nodes{id slug}}}}",
        {"o": owner, "n": name},
    )["data"]["repository"]
    cat = next(c["id"] for c in repo["discussionCategories"]["nodes"] if c["slug"] == "announcements")
    body = f"{NOTES}\n\n---\nFull release: {RELEASE}\n\nTell us what you think below, or show your setup with #HaloRecap 👇"
    res = gql(
        "mutation($r:ID!,$c:ID!,$t:String!,$b:String!){createDiscussion(input:{repositoryId:$r,categoryId:$c,title:$t,body:$b}){discussion{url}}}",
        {"r": repo["id"], "c": cat, "t": f"Halo {TAG} is out", "b": body},
    )
    if res.get("errors"):
        raise RuntimeError(res["errors"])
    print(res["data"]["createDiscussion"]["discussion"]["url"])


def telegram():
    token, chat = os.environ["TELEGRAM_BOT_TOKEN"], os.environ["TELEGRAM_CHAT_ID"]
    lines = [f"<b>Halo {html.escape(TAG)} is out 🎉</b>", ""]
    lines += [f"• {html.escape(c)}" for c in CHANGES[:8]]
    lines += ["", f'<a href="{PLAY}">Google Play</a> · <a href="{RELEASE}">Release notes &amp; APK</a>']
    res = post(
        f"https://api.telegram.org/bot{token}/sendMessage",
        {"chat_id": chat, "text": "\n".join(lines), "parse_mode": "HTML", "disable_web_page_preview": "false"},
        form=True,
    )
    if not res.get("ok"):
        raise RuntimeError(res)


def bluesky():
    s = post(
        "https://bsky.social/xrpc/com.atproto.server.createSession",
        {"identifier": os.environ["BLUESKY_HANDLE"], "password": os.environ["BLUESKY_APP_PASSWORD"]},
    )
    text = short(290)
    # Links and hashtags only become clickable with "facets", using UTF-8 byte offsets.
    raw = text.encode()
    facets = []
    for m in re.finditer(re.escape(PLAY).encode(), raw):
        facets.append({"index": {"byteStart": m.start(), "byteEnd": m.end()},
                       "features": [{"$type": "app.bsky.richtext.facet#link", "uri": PLAY}]})
    for m in re.finditer(rb"#(\w+)", raw):
        facets.append({"index": {"byteStart": m.start(), "byteEnd": m.end()},
                       "features": [{"$type": "app.bsky.richtext.facet#tag", "tag": m.group(1).decode()}]})
    post(
        "https://bsky.social/xrpc/com.atproto.repo.createRecord",
        {"repo": s["did"], "collection": "app.bsky.feed.post", "record": {
            "$type": "app.bsky.feed.post", "text": text, "facets": facets,
            "createdAt": dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z"),
        }},
        {"Authorization": f"Bearer {s['accessJwt']}"},
    )


def mastodon():
    instance = os.environ["MASTODON_INSTANCE"].rstrip("/")
    if not instance.startswith("http"):
        instance = "https://" + instance
    post(f"{instance}/api/v1/statuses", {"status": short(480)},
         {"Authorization": f"Bearer {os.environ['MASTODON_TOKEN']}"}, form=True)


def xda():
    """XDA has no posting API, so write the post for a quick copy-paste."""
    bb = [f"[SIZE=5][B]Halo {TAG}[/B][/SIZE]", "", "[B]What's new[/B]", "[LIST]"]
    bb += [f"[*]{c}" for c in CHANGES]
    bb += ["[/LIST]", "", "[B]Download[/B]", "[LIST]",
           f"[*][URL={PLAY}]Google Play[/URL]",
           f"[*][URL={RELEASE}]GitHub release (APK)[/URL]",
           f"[*][URL=https://yogeshpaliyal.github.io/Halo/obtainium/]Obtainium[/URL] for automatic updates",
           "[/LIST]", "", f"Source code: [URL=https://github.com/{REPO}]github.com/{REPO}[/URL]"]
    post_text = "\n".join(bb)
    open("xda-post.txt", "w").write(post_text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(f"## XDA post for {TAG}\n\nCopy this into your XDA thread (editor's BBCode mode):\n\n````\n{post_text}\n````\n")


channel("GitHub Discussions", discussions)
if os.environ.get("TELEGRAM_BOT_TOKEN") and os.environ.get("TELEGRAM_CHAT_ID"):
    channel("Telegram", telegram)
if os.environ.get("BLUESKY_HANDLE") and os.environ.get("BLUESKY_APP_PASSWORD"):
    channel("Bluesky", bluesky)
if os.environ.get("MASTODON_INSTANCE") and os.environ.get("MASTODON_TOKEN"):
    channel("Mastodon", mastodon)
channel("XDA post text", xda)
