#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import difflib
import json
import os
import re
import ssl
import sys
import time
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


USER_AGENT = "HitsterCloneYearNormalizer/1.0 (Codex)"
REQUEST_SPACING_SECONDS = 1.1
SEARCH_LIMIT = 25

CYRILLIC_TO_LATIN = str.maketrans(
    {
        "а": "a",
        "б": "b",
        "в": "v",
        "г": "g",
        "д": "d",
        "е": "e",
        "ё": "e",
        "ж": "zh",
        "з": "z",
        "и": "i",
        "й": "y",
        "к": "k",
        "л": "l",
        "м": "m",
        "н": "n",
        "о": "o",
        "п": "p",
        "р": "r",
        "с": "s",
        "т": "t",
        "у": "u",
        "ф": "f",
        "х": "h",
        "ц": "ts",
        "ч": "ch",
        "ш": "sh",
        "щ": "sch",
        "ъ": "",
        "ы": "y",
        "ь": "",
        "э": "e",
        "ю": "yu",
        "я": "ya",
    }
)

TITLE_SEPARATORS = (" - ", " — ", " – ", ": ", " / ")
ARTIST_SPLIT_RE = re.compile(r",| feat\.? | ft\.? | x | & | и | with ", re.IGNORECASE)
YEAR_RE = re.compile(r"^(\d{4})")
SAFE_VERSION_SUFFIXES = {
    "album version",
    "single version",
    "mono version",
    "stereo version",
    "radio edit",
    "single edit",
    "album edit",
    "edit",
    "mono",
    "stereo",
    "version",
    "remaster",
    "remastered",
    "live",
    "live version",
    "acoustic",
    "instrumental",
    "extended mix",
    "original mix",
}


@dataclass(frozen=True)
class EntryKey:
    primary_artist: str
    title: str


@dataclass
class MatchCandidate:
    year: int
    title: str
    artists: list[str]
    score: int
    first_release_date: str


class MusicBrainzClient:
    def __init__(self) -> None:
        self.last_request_started_at = 0.0
        self.ssl_context = ssl.create_default_context()

    def search_recordings(self, query: str) -> list[dict[str, Any]]:
        payload = self._request_json(
            "https://musicbrainz.org/ws/2/recording?" + urlencode(
                {
                    "query": query,
                    "fmt": "json",
                    "limit": str(SEARCH_LIMIT),
                }
            )
        )
        recordings = payload.get("recordings")
        if isinstance(recordings, list):
            return recordings
        return []

    def _request_json(self, url: str) -> dict[str, Any]:
        retries = 6
        for attempt in range(retries):
            self._respect_rate_limit()
            request = Request(
                url,
                headers={
                    "User-Agent": USER_AGENT,
                    "Connection": "close",
                    "Accept": "application/json",
                },
            )
            try:
                with urlopen(request, timeout=30, context=self.ssl_context) as response:
                    return json.load(response)
            except HTTPError as error:
                if error.code == 503 and attempt + 1 < retries:
                    time.sleep(2.5 * (attempt + 1))
                    continue
                if error.code == 429 and attempt + 1 < retries:
                    retry_after = error.headers.get("Retry-After")
                    time.sleep(float(retry_after or 5))
                    continue
                raise
            except (URLError, TimeoutError, ssl.SSLError) as error:
                if attempt + 1 >= retries:
                    raise RuntimeError(f"MusicBrainz request failed for {url}: {error}") from error
                time.sleep(2.5 * (attempt + 1))
        raise RuntimeError(f"MusicBrainz request failed for {url}")

    def _respect_rate_limit(self) -> None:
        elapsed = time.monotonic() - self.last_request_started_at
        if elapsed < REQUEST_SPACING_SECONDS:
            time.sleep(REQUEST_SPACING_SECONDS - elapsed)
        self.last_request_started_at = time.monotonic()


class SpotifyClient:
    def __init__(self, client_id: str, client_secret: str) -> None:
        self.client_id = client_id
        self.client_secret = client_secret
        self.access_token: str | None = None

    def track_isrc(self, spotify_uri: str) -> str | None:
        track_id = spotify_uri.rsplit(":", 1)[-1].strip()
        if not track_id:
            return None
        payload = self._request_json(f"https://api.spotify.com/v1/tracks/{track_id}")
        external_ids = payload.get("external_ids")
        if not isinstance(external_ids, dict):
            return None
        isrc = external_ids.get("isrc")
        return isrc if isinstance(isrc, str) and isrc else None

    def _request_json(self, url: str) -> dict[str, Any]:
        token = self._access_token()
        request = Request(
            url,
            headers={
                "Authorization": f"Bearer {token}",
                "Accept": "application/json",
            },
        )
        with urlopen(request, timeout=30) as response:
            return json.load(response)

    def _access_token(self) -> str:
        if self.access_token is not None:
            return self.access_token

        credentials = f"{self.client_id}:{self.client_secret}".encode()
        encoded = base64.b64encode(credentials).decode()
        request = Request(
            "https://accounts.spotify.com/api/token",
            data=urlencode({"grant_type": "client_credentials"}).encode(),
            headers={
                "Authorization": f"Basic {encoded}",
                "Content-Type": "application/x-www-form-urlencoded",
            },
        )
        with urlopen(request, timeout=30) as response:
            payload = json.load(response)
        token = payload.get("access_token")
        if not isinstance(token, str) or not token:
            raise RuntimeError("Spotify client credentials flow did not return an access token")
        self.access_token = token
        return token


def transliterate(text: str) -> str:
    return text.translate(CYRILLIC_TO_LATIN)


def normalize_text(text: str, *, strip_brackets: bool) -> str:
    lowered = transliterate(text.lower().replace("ё", "е"))
    normalized = unicodedata.normalize("NFKD", lowered)
    without_marks = "".join(ch for ch in normalized if not unicodedata.combining(ch))
    if strip_brackets:
        without_marks = re.sub(r"\([^)]*\)|\[[^\]]*\]", " ", without_marks)
    without_symbols = re.sub(r"[^a-z0-9]+", " ", without_marks)
    return " ".join(without_symbols.split())


def strict_title_key(title: str) -> str:
    return normalize_text(title, strip_brackets=False)


def base_title_keys(title: str) -> set[str]:
    keys = {normalize_text(title, strip_brackets=True)}
    for separator in TITLE_SEPARATORS:
        if separator in title:
            keys.add(normalize_text(title.split(separator, 1)[0], strip_brackets=True))
    return {key for key in keys if key}


def title_matches(entry_title: str, candidate_title: str) -> bool:
    entry_strict = strict_title_key(entry_title)
    candidate_strict = strict_title_key(candidate_title)
    if entry_strict == candidate_strict:
        return True

    entry_base = base_title_keys(entry_title)
    candidate_base = base_title_keys(candidate_title)
    if not (entry_base & candidate_base):
        return False

    if candidate_strict.startswith(entry_strict):
        suffix = candidate_strict[len(entry_strict) :].strip()
        return suffix in SAFE_VERSION_SUFFIXES
    if entry_strict.startswith(candidate_strict):
        suffix = entry_strict[len(candidate_strict) :].strip()
        return suffix in SAFE_VERSION_SUFFIXES
    return False


def primary_artist(artist: str) -> str:
    return ARTIST_SPLIT_RE.split(artist, maxsplit=1)[0].strip()


def artist_variants(artist: str) -> set[str]:
    variants = set()
    for part in ARTIST_SPLIT_RE.split(artist):
        normalized = normalize_text(part, strip_brackets=True)
        if normalized:
            variants.add(normalized)
    return variants


def artist_matches(entry_artist: str, candidate_artists: list[str]) -> bool:
    wanted = artist_variants(entry_artist)
    observed = {normalize_text(name, strip_brackets=True) for name in candidate_artists}
    observed = {name for name in observed if name}
    if not wanted or not observed:
        return False
    for wanted_name in wanted:
        for candidate_name in observed:
            if wanted_name in candidate_name or candidate_name in wanted_name:
                return True
            wanted_compact = wanted_name.replace(" ", "")
            candidate_compact = candidate_name.replace(" ", "")
            if not wanted_compact or not candidate_compact:
                continue
            if wanted_compact[0] != candidate_compact[0]:
                continue
            similarity = difflib.SequenceMatcher(None, wanted_compact, candidate_compact).ratio()
            if similarity >= 0.84:
                return True
    return False


def first_release_year(value: str | None) -> int | None:
    if not value:
        return None
    match = YEAR_RE.match(value)
    if not match:
        return None
    year = int(match.group(1))
    if year < 1900 or year > 2100:
        return None
    return year


def choose_year_for_pair(
    client: MusicBrainzClient,
    artist: str,
    title: str,
    spotify_uri: str | None = None,
    spotify_client: SpotifyClient | None = None,
) -> dict[str, Any] | None:
    queries = [
        f'recording:"{title}" AND artist:"{artist}"',
        f'recording:"{title}"',
    ]

    best_candidates: list[MatchCandidate] = []
    for query in queries:
        try:
            recordings = client.search_recordings(query)
        except Exception as error:  # noqa: BLE001
            return {"error": str(error), "query": query}

        accepted: list[MatchCandidate] = []
        for recording in recordings:
            year = first_release_year(recording.get("first-release-date"))
            if year is None:
                continue
            candidate_title = recording.get("title", "")
            candidate_artists = [
                part["artist"]["name"]
                for part in recording.get("artist-credit", [])
                if isinstance(part, dict) and isinstance(part.get("artist"), dict)
            ]
            if not title_matches(title, candidate_title):
                continue
            if not artist_matches(artist, candidate_artists):
                continue
            accepted.append(
                MatchCandidate(
                    year=year,
                    title=candidate_title,
                    artists=candidate_artists,
                    score=int(recording.get("score", 0)),
                    first_release_date=recording.get("first-release-date", ""),
                )
            )

        if accepted:
            accepted.sort(key=lambda candidate: (candidate.year, -candidate.score, candidate.first_release_date))
            best_candidates = accepted
            break

    if not best_candidates and spotify_client is not None and spotify_uri is not None:
        try:
            isrc = spotify_client.track_isrc(spotify_uri)
        except Exception as error:  # noqa: BLE001
            return {"error": str(error), "query": "spotify-isrc"}
        if isrc:
            try:
                recordings = client.search_recordings(f"isrc:{isrc}")
            except Exception as error:  # noqa: BLE001
                return {"error": str(error), "query": f"isrc:{isrc}"}
            accepted = []
            for recording in recordings:
                year = first_release_year(recording.get("first-release-date"))
                if year is None:
                    continue
                candidate_artists = [
                    part["artist"]["name"]
                    for part in recording.get("artist-credit", [])
                    if isinstance(part, dict) and isinstance(part.get("artist"), dict)
                ]
                if not artist_matches(artist, candidate_artists) and len(recordings) != 1:
                    continue
                accepted.append(
                    MatchCandidate(
                        year=year,
                        title=recording.get("title", ""),
                        artists=candidate_artists,
                        score=int(recording.get("score", 0)),
                        first_release_date=recording.get("first-release-date", ""),
                    )
                )
            if accepted:
                accepted.sort(key=lambda candidate: (candidate.year, -candidate.score, candidate.first_release_date))
                best_candidates = accepted

    if not best_candidates:
        return None

    chosen = best_candidates[0]
    return {
        "year": chosen.year,
        "title": chosen.title,
        "artists": chosen.artists,
        "score": chosen.score,
        "firstReleaseDate": chosen.first_release_date,
        "queryCount": len(best_candidates),
    }


def load_cache(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    return json.loads(path.read_text())


def save_cache(path: Path, cache: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(cache, ensure_ascii=False, indent=2) + "\n")


def load_playlist(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text())


def write_playlist(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")


def build_pair_key(artist: str, title: str) -> str:
    return f"{primary_artist(artist)}|||{title}"


def read_local_properties(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    values: dict[str, str] = {}
    for raw_line in path.read_text().splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def should_apply_resolved_year(current_year: int, resolved_year: int) -> bool:
    if resolved_year <= current_year:
        return True
    return current_year < 1980


def update_years(
    playlist: dict[str, Any],
    cache: dict[str, Any],
    *,
    cache_path: Path,
    spotify_client: SpotifyClient | None,
    limit: int | None,
) -> tuple[dict[str, Any], dict[str, Any]]:
    entries = playlist["entries"]
    pair_uris: dict[str, str] = {}
    unique_pairs: list[EntryKey] = []
    seen_pairs: set[str] = set()
    for entry in entries:
        pair = EntryKey(primary_artist(entry["artist"]), entry["title"])
        cache_key = build_pair_key(pair.primary_artist, pair.title)
        pair_uris.setdefault(cache_key, entry.get("spotifyUri", ""))
        if cache_key not in seen_pairs:
            unique_pairs.append(pair)
            seen_pairs.add(cache_key)

    if limit is not None:
        unique_pairs = unique_pairs[:limit]

    client = MusicBrainzClient()
    changed = 0
    unresolved = 0
    updated_pairs = 0
    report_examples: list[dict[str, Any]] = []

    for index, pair in enumerate(unique_pairs, start=1):
        cache_key = build_pair_key(pair.primary_artist, pair.title)
        cached = cache.get(cache_key)
        if cached is None or (isinstance(cached, dict) and "error" in cached):
            cache[cache_key] = choose_year_for_pair(
                client,
                pair.primary_artist,
                pair.title,
                spotify_uri=pair_uris.get(cache_key),
                spotify_client=spotify_client,
            )
            save_cache(cache_path, cache)

        result = cache[cache_key]
        if result is None or "error" in result:
            unresolved += 1
            if result:
                report_examples.append(
                    {
                        "artist": pair.primary_artist,
                        "title": pair.title,
                        "error": result.get("error"),
                    }
                )
            print(f"[{index}/{len(unique_pairs)}] unresolved: {pair.primary_artist} - {pair.title}")
            continue

        updated_pairs += 1
        resolved_year = int(result["year"])
        for entry in entries:
            if build_pair_key(entry["artist"], entry["title"]) != cache_key:
                continue
            if entry["releaseYear"] == resolved_year:
                continue
            if not should_apply_resolved_year(entry["releaseYear"], resolved_year):
                continue
            report_examples.append(
                {
                    "artist": entry["artist"],
                    "title": entry["title"],
                    "oldYear": entry["releaseYear"],
                    "newYear": resolved_year,
                    "matchedReleaseDate": result.get("firstReleaseDate"),
                }
            )
            entry["releaseYear"] = resolved_year
            changed += 1
        print(
            f"[{index}/{len(unique_pairs)}] {pair.primary_artist} - {pair.title} -> {resolved_year}"
        )

    report = {
        "uniquePairs": len(unique_pairs),
        "resolvedPairs": updated_pairs,
        "unresolvedPairs": unresolved,
        "changedEntries": changed,
        "examples": report_examples[:200],
    }
    return playlist, report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Normalize playlist release years against MusicBrainz.")
    parser.add_argument(
        "--playlist",
        type=Path,
        default=Path("ui/src/main/resources/sample-playlist.json"),
    )
    parser.add_argument(
        "--cache",
        type=Path,
        default=Path("/tmp/hitster-musicbrainz-year-cache.json"),
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=Path("/tmp/hitster-musicbrainz-year-report.json"),
    )
    parser.add_argument(
        "--local-properties",
        type=Path,
        default=Path("local.properties"),
    )
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    playlist = load_playlist(args.playlist)
    cache = load_cache(args.cache)
    local_properties = read_local_properties(args.local_properties)
    spotify_client_id = os.environ.get("SPOTIFY_CLIENT_ID") or local_properties.get("spotifyClientId")
    spotify_client_secret = os.environ.get("SPOTIFY_CLIENT_SECRET") or local_properties.get("spotifyClientSecret")
    spotify_client = None
    if spotify_client_id and spotify_client_secret:
        spotify_client = SpotifyClient(spotify_client_id, spotify_client_secret)

    updated_playlist, report = update_years(
        playlist,
        cache,
        cache_path=args.cache,
        spotify_client=spotify_client,
        limit=args.limit,
    )
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")

    if args.dry_run:
        print(json.dumps(report, ensure_ascii=False, indent=2))
        sys.exit(0)

    write_playlist(args.playlist, updated_playlist)
    print(json.dumps(report, ensure_ascii=False, indent=2))
