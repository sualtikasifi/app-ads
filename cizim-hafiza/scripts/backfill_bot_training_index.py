#!/usr/bin/env python3
"""One-off backfill for botTrainingIndex/trained.

The Bot Eğitim screen needs to know which words already have a drawing.
Reading that from the botTrainedWords collection means downloading every
stored drawing with it (strokesJson, several KB each), so the client reads
a single index doc instead — see BotTrainingRepositoryImpl.

New drawings add themselves to that index, but the ones saved before the
index existed are only in the collection. This script copies their ids
across and sets `complete: true`, which is the flag the client requires
before it will trust the index at all (an index without it is treated as
missing and the client falls back to reading the whole collection).

Safe to re-run: it rewrites the same two fields from the current state of
the collection and touches nothing else. No drawing is read or modified.

Usage:
    python3 scripts/backfill_bot_training_index.py
"""

import json
import os
import sys
import urllib.error
import urllib.request

PROJECT_ID = "karalak-b6e11"
BASE = f"https://firestore.googleapis.com/v1/projects/{PROJECT_ID}/databases/(default)/documents"
GOOGLE_SERVICES = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "google-services.json"
)


def post_json(url, payload, token=None):
    data = json.dumps(payload).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    with urllib.request.urlopen(req) as resp:
        return json.load(resp)


def get_json(url, token):
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req) as resp:
        return json.load(resp)


def patch_json(url, payload, token):
    data = json.dumps(payload).encode()
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"},
        method="PATCH",
    )
    with urllib.request.urlopen(req) as resp:
        return json.load(resp)


def main():
    with open(GOOGLE_SERVICES) as fh:
        api_key = json.load(fh)["client"][0]["api_key"][0]["current_key"]

    # firestore.rules only requires `request.auth != null` for these two
    # collections, so a throwaway anonymous account is enough.
    token = post_json(
        f"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={api_key}",
        {"returnSecureToken": True},
    )["idToken"]

    # mask.fieldPaths=wordId keeps strokesJson out of the response — without
    # it this listing alone would pull down every drawing.
    ids, page_token = set(), ""
    while True:
        url = f"{BASE}/botTrainedWords?pageSize=300&mask.fieldPaths=wordId"
        if page_token:
            url += f"&pageToken={page_token}"
        page = get_json(url, token)
        for doc in page.get("documents", []):
            doc_id = doc["name"].rsplit("/", 1)[-1]
            if doc_id.isdigit():
                ids.add(int(doc_id))
        page_token = page.get("nextPageToken", "")
        if not page_token:
            break

    if not ids:
        print("botTrainedWords bos gorunuyor — indeks yazilmadi.", file=sys.stderr)
        return 1

    payload = {
        "fields": {
            "wordIds": {
                "arrayValue": {"values": [{"integerValue": str(i)} for i in sorted(ids)]}
            },
            "complete": {"booleanValue": True},
        }
    }
    result = patch_json(f"{BASE}/botTrainingIndex/trained", payload, token)
    written = len(result["fields"]["wordIds"]["arrayValue"]["values"])
    print(f"botTrainingIndex/trained yazildi — {written} kelime id'si, complete=true")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except urllib.error.HTTPError as exc:
        print(f"HTTP {exc.code}: {exc.read().decode()}", file=sys.stderr)
        sys.exit(1)
