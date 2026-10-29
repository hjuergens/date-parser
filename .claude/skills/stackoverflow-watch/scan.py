#!/usr/bin/env python3
"""Find new Stack Overflow questions where the date-parser comparison could help.

  scan.py --hook     SessionStart hook: scan (at most every 3 hours) and print hook JSON
  scan.py            scan if due, print the pending candidates as JSON
  scan.py --done ID  remove a candidate from the pending list (answered or skipped)
"""
import gzip
import json
import re
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

STATE = Path(__file__).resolve().parent / "state.json"
API = "https://api.stackexchange.com/2.3/search/advanced"
TAGS = ["antlr4", "antlr", "javacc", "bison", "yacc", "jflex", "cup", "sablecc", "cocor", "peg", "pegjs",
        "parser-combinators", "parser-generator", "lalr", "ll-grammar", "left-recursion", "lark-parser"]
MIN_INTERVAL = 3 * 3600          # between network scans
FIRST_LOOKBACK = 30 * 86400      # on the very first scan
PENDING_MAX_AGE = 30 * 86400     # drop candidates nobody looked at
MIN_SCORE = 1

COMPARE = re.compile(r"\b(vs\.?|versus|compar\w*|difference|differ|which (parser|tool|generator)|choos\w*|"
                     r"better|alternative|recommend\w*|instead of|pros and cons)\b", re.I)
DATES = re.compile(r"\b(dates?|schedules?|calendar|periods?|durations?|month|iso.?8601)\b", re.I)
THEORY = re.compile(r"\b(ll\(?[1k*]\)?|lalr|lr\(1\)|ambigu\w*|conflicts?|left[- ]recurs\w*|precedence|"
                    r"shift[/-]reduce|reduce[/-]reduce|ordered choice|backtrack\w*|lookahead)\b", re.I)


def load():
    try:
        return json.loads(STATE.read_text())
    except (OSError, ValueError):
        return {"last_run": 0, "seen": [], "pending": []}


def save(state):
    STATE.write_text(json.dumps(state, indent=1))


def fetch(tag, since):
    url = (f"{API}?order=desc&sort=creation&site=stackoverflow&pagesize=50&closed=False"
           f"&fromdate={since}&tagged={tag}")
    with urllib.request.urlopen(url, timeout=10) as r:
        body = r.read()
    data = json.loads(gzip.decompress(body) if body[:2] == b"\x1f\x8b" else body)
    if "error_id" in data:
        raise RuntimeError(f"{tag}: {data.get('error_name')} {data.get('error_message')}")
    return data


def score(q):
    tools = {t for t in q["tags"] if t in TAGS}
    title = q["title"]
    s = len(tools) + (3 if len(tools) >= 2 else 0)
    s += 3 if COMPARE.search(title) else 0
    s += 2 if DATES.search(title) or "date" in q["tags"] else 0
    s += 1 if THEORY.search(title) else 0
    s -= 2 if q.get("accepted_answer_id") else 0
    return s


def scan(state):
    now = int(time.time())
    since = state["last_run"] or now - FIRST_LOOKBACK
    with ThreadPoolExecutor(4) as pool:
        results = list(pool.map(lambda t: fetch(t, since), TAGS))
    seen = set(state["seen"])
    pending = {p["id"]: p for p in state["pending"]}
    for result in results:
        for q in result.get("items", []):
            if q["question_id"] in seen:
                continue
            seen.add(q["question_id"])
            s = score(q)
            if s >= MIN_SCORE:
                pending[q["question_id"]] = {
                    "id": q["question_id"], "score": s, "title": re.sub(r"&#39;", "'", q["title"]),
                    "link": q["link"], "tags": q["tags"], "answers": q["answer_count"],
                    "accepted": bool(q.get("accepted_answer_id")), "created": q["creation_date"]}
    state["seen"] = sorted(seen)[-5000:]
    state["pending"] = sorted((p for p in pending.values() if now - p["created"] < PENDING_MAX_AGE),
                              key=lambda p: -p["score"])
    state["last_run"] = now
    quota = min((r.get("quota_remaining", 0) for r in results), default=None)
    return quota


def main():
    args = sys.argv[1:]
    state = load()
    if args[:1] == ["--done"]:
        state["pending"] = [p for p in state["pending"] if str(p["id"]) not in args[1:]]
        save(state)
        return
    quota = None
    if time.time() - state["last_run"] >= MIN_INTERVAL:
        quota = scan(state)
        save(state)
    pending = state["pending"]
    if args[:1] != ["--hook"]:
        print(json.dumps({"quota_remaining": quota, "pending": pending}, indent=1))
        return
    if not pending:
        return
    top = "\n".join(f"- [{p['score']}] {p['title']} ({p['answers']} answers) {p['link']}" for p in pending[:8])
    print(json.dumps({
        "systemMessage": f"Stack Overflow watch: {len(pending)} question(s) where date-parser might help. "
                         "Ask Claude to triage them (/stackoverflow-watch).",
        "hookSpecificOutput": {
            "hookEventName": "SessionStart",
            "additionalContext": "Stack Overflow watch found questions where the date-parser comparison might "
                                 "help (score, title, link):\n" + top + "\nAt the start of your first reply, "
                                 "mention this in one line and offer to run the stackoverflow-watch skill. "
                                 "Do not run it unasked if the user's first message is about something else."}}))


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # a hook must never break session start
        if "--hook" in sys.argv:
            print(json.dumps({"systemMessage": f"Stack Overflow watch skipped: {e}"}))
        else:
            raise
