#!/usr/bin/env python3
"""The practice model: a tiny OpenAI-compatible scripted LLM (standard library only, Python 3.8+).

It has two jobs and it is ONE file, so they can never drift apart:
  * the CI smoke test boots Hermes against it (rootfs/smoke/smoke-test.sh), and
  * the app copies it into the Linux image (asset practice_model.py) and starts it beside Hermes when the
    "Practice model" switch is on (runtime/practice-start.sh), so the whole app can be driven for zero tokens.

  python3 mock_llm.py [--host 127.0.0.1] --port N [--log FILE] [--media-dir DIR] [--watch-parent] [--selftest]

  GET  /health                 {"status":"ok","name":"practice-model"}
  GET  /v1/models              lists mock-model and practice-model (with context_length)
  POST /v1/chat/completions    stream and non-stream

How it answers: the keyword is the first of PRIORITY that appears, as a whole upper-case word, in the LAST user message
(text after it on the same line is its argument). No keyword: "mock-reply: <last user text>", words streamed at once.
Every answer carries a fixed usage (see USAGE) so token numbers in the app are predictable. A request whose text contains
"You name chat sessions" is a title call: it is counted (STATS) and answered "Practice chat" whatever the keywords are.

One JSON line per POST goes to --log (valid JSON, fields capped); the file is cut to its last 200 lines past 1 MB. The CI
smoke test reads the fields "POST" and "n_msgs" and greps for the title-call phrase in "heads".
"""
import argparse
import json
import math
import os
import re
import struct
import sys
import tempfile
import threading
import time
import uuid
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# The Kotlin object PracticeModel.KEYWORDS is compared with this line as a set (PracticeModelTest).
KEYWORDS = ("RUN_TOOL", "ASK_APPROVAL", "LONG", "THINK", "SLOW", "FAIL", "MEMORY", "FORGE", "MEDIA", "PROMPT_HAS", "PROMPT_SIZE", "BALLAST", "TOOLS", "STATS", "PAUSE")

# Which keyword wins when a message contains several.
PRIORITY = ("FAIL", "ASK_APPROVAL", "RUN_TOOL", "MEMORY", "FORGE", "MEDIA", "THINK", "SLOW", "PAUSE", "LONG", "PROMPT_HAS", "PROMPT_SIZE", "BALLAST", "TOOLS", "STATS")

# Usage per model call as (prompt tokens, completion tokens). One row per keyword; LONG's completion is ceil(n / 4) and FAIL has
# none. A turn with a tool call costs the first row plus AFTER_TOOL.
USAGE = {
    "NONE": (100, 10),
    "RUN_TOOL": (100, 20),
    "ASK_APPROVAL": (100, 20),
    "LONG": (100, 0),
    "THINK": (100, 60),
    "SLOW": (100, 12),
    "FAIL": None,
    "MEMORY": (100, 25),
    "FORGE": (100, 260),
    "MEDIA": (100, 30),
    "PROMPT_HAS": (100, 10),
    "PROMPT_SIZE": (100, 10),
    "BALLAST": (100, 10),
    "TOOLS": (100, 10),
    "STATS": (100, 10),
    "PAUSE": (100, 12),
}
AFTER_TOOL = (130, 10)

TITLE_PHRASE = "You name chat sessions"
LONG_END = "[LONG-END]"
THOUGHT = "**Weighing the options**\n\nI compare two small plans and pick the calmer one before answering.\n\n"
BIG_COMMAND = "i=0; while [ $i -lt 130 ]; do printf '%080d\\n' $i; i=$((i+1)); done"
APPROVAL_COMMAND = "rm -rf /tmp/agiri-practice-approval"
FORGE_SPEC = {
    "name": "Practice Pal",
    "tagline": "A calm helper for practice runs",
    "avatar": "OWL",
    "color": "#2E7D6B",
    "systemPrompt": "You are Practice Pal, a calm and brief helper. Answer in two sentences at most.",
    "voice": "F2",
    "voiceStyle": "calm",
}
FORGE_TEXT = ("Here is a new bot for you.\n\n```pocket-bot\n" + json.dumps(FORGE_SPEC, indent=2) +
              "\n```\n\nPractice Pal is ready to chat.")
MAX_LOG_BYTES = 1024 * 1024

# Waits are multiplied by this; the self-test turns it down so that it runs in a second.
SPEED = 1.0

LOG_PATH = None
LOG_LOCK = threading.Lock()
MEDIA_DIR = None
COUNTERS = {"requests": 0, "title": 0}
COUNTER_LOCK = threading.Lock()
BALLAST = []


def pause(seconds):
    if seconds > 0:
        time.sleep(seconds * SPEED)


# ---- request log ---------------------------------------------------------------------------------------------------

def log(obj):
    if not LOG_PATH:
        return
    line = json.dumps(obj) + "\n"
    with LOG_LOCK:
        try:
            with open(LOG_PATH, "a") as f:
                f.write(line)
            if os.path.getsize(LOG_PATH) > MAX_LOG_BYTES:
                with open(LOG_PATH, "r") as f:
                    lines = f.readlines()
                with open(LOG_PATH, "w") as f:
                    f.writelines(lines[-200:])
        except OSError:
            pass


# ---- reading a request ---------------------------------------------------------------------------------------------

def text_of(content):
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return " ".join(p.get("text", "") for p in content if isinstance(p, dict) and isinstance(p.get("text", ""), str))
    return ""


def last_user_index(msgs):
    for i in range(len(msgs) - 1, -1, -1):
        if msgs[i].get("role") == "user":
            return i
    return -1


def tool_names(tools):
    names = []
    for t in tools or []:
        fn = t.get("function", {}) if isinstance(t, dict) else {}
        if isinstance(fn, dict) and isinstance(fn.get("name"), str):
            names.append(fn["name"])
    return names


def find_keyword(text):
    """(keyword, argument) of the first PRIORITY word found as a whole word in text, else (None, '')."""
    for kw in PRIORITY:
        m = re.search(r"(?<![A-Za-z0-9_])" + re.escape(kw) + r"(?![A-Za-z0-9_])", text)
        if m:
            return kw, text[m.end():].split("\n", 1)[0].strip()
    return None, ""


def first_int(text, default):
    m = re.search(r"\d+", text or "")
    return int(m.group(0)) if m else default


def request_text(msgs):
    return " ".join(text_of(m.get("content")) for m in msgs)


def prompt_chars(msgs):
    return sum(len(text_of(m.get("content"))) for m in msgs)


# ---- the scripted answers ------------------------------------------------------------------------------------------

def make_long(n):
    """Exactly n characters of deterministic Markdown that ends with [LONG-END]. The first 700 characters or so already hold a
    heading, bullets, a numbered list, ONE fenced code block, a table, bold, inline code and a link."""
    n = max(len(LONG_END), n)
    body = (
        "# Practice answer\n\n"
        "This is a **long practice answer** with `inline code` and a [link](https://example.com/practice).\n\n"
        "## Bullets\n\n- first bullet\n- second bullet with **bold**\n- third bullet with `code`\n\n"
        "## Steps\n\n1. first step\n2. second step\n3. third step\n\n"
        "## Code\n\n```python\nfor i in range(3):\n    print(\"practice\", i)\n```\n\n"
        "## Table\n\n| name | value |\n| --- | --- |\n| alpha | 1 |\n| beta | 2 |\n\n"
    )
    k = 1
    while len(body) < n:
        body += ("## Section %d\n\nParagraph %d has **bold words**, `code words` and enough plain text to wrap onto a second line "
                 "of the screen.\n\n- bullet a of %d\n- bullet b of %d\n\n" % (k, k, k, k))
        if k % 4 == 0:
            body += "| key | value |\n| --- | --- |\n| row %d | %d |\n\n" % (k, k * 7)
        k += 1
    return body[: n - len(LONG_END)] + LONG_END


def chunks_of(text, size, delay):
    out = []
    for i in range(0, len(text), size):
        out.append((delay if i else 0.0, text[i:i + size]))
    return out


def words_of(text):
    words = text.split(" ")
    return [(0.0, w + (" " if i < len(words) - 1 else "")) for i, w in enumerate(words)]


class Reply:
    """What to send back. kind 'text' (chunks of (delay, text)), 'tool' (tool = (name, json arguments)) or 'error' (status, error)."""

    def __init__(self, kind, chunks=None, tool=None, usage=None, status=200, error=None, keyword=None):
        self.kind = kind
        self.chunks = chunks or []
        self.tool = tool
        self.usage = usage
        self.status = status
        self.error = error
        self.keyword = keyword

    def text(self):
        return "".join(c[1] for c in self.chunks)


def text_reply(text, usage, keyword=None):
    return Reply("text", chunks=words_of(text), usage=usage, keyword=keyword)


def pick_tool(names, preferred):
    if preferred in names:
        return preferred
    return names[0] if names else None


def decide(body):
    """Pure apart from BALLAST and the counters: the Reply for one chat-completions request."""
    msgs = [m for m in (body.get("messages") or []) if isinstance(m, dict)]
    tools = body.get("tools") or []
    names = tool_names(tools)
    all_text = request_text(msgs)
    if TITLE_PHRASE in all_text:
        return Reply("text", chunks=[(0.0, "Practice chat")], usage=USAGE["NONE"], keyword="TITLE")
    i = last_user_index(msgs)
    last_user = text_of(msgs[i].get("content")) if i >= 0 else ""
    kw, arg = find_keyword(last_user)
    newest_is_tool = bool(msgs) and msgs[-1].get("role") == "tool"

    if newest_is_tool:
        result = text_of(msgs[-1].get("content"))
        if kw == "ASK_APPROVAL":
            return text_reply("approval-flow-done: " + result[:120], AFTER_TOOL, kw)
        if kw == "MEMORY":
            return text_reply("memory-saved: " + result[:120], AFTER_TOOL, kw)
        return text_reply("tool-result-seen: " + result[:200], AFTER_TOOL, kw)

    if kw == "FAIL":
        code = first_int(arg, 400)
        if not 400 <= code <= 599:
            code = 400
        err = {"error": {"message": "practice failure: the FAIL word was used", "type": "invalid_request_error", "code": "practice_fail"}}
        return Reply("error", status=code, error=err, keyword=kw)
    if kw == "ASK_APPROVAL":
        name = pick_tool(names, "terminal")
        if name:
            args = {"command": APPROVAL_COMMAND} if name == "terminal" else {}
            return Reply("tool", tool=(name, json.dumps(args)), usage=USAGE["ASK_APPROVAL"], keyword=kw)
    elif kw == "RUN_TOOL":
        name = pick_tool(names, "terminal")
        if name:
            command = BIG_COMMAND if arg.split(" ")[0] == "big" else "echo hello-from-tool"
            args = {"command": command} if name == "terminal" else {}
            return Reply("tool", tool=(name, json.dumps(args)), usage=USAGE["RUN_TOOL"], keyword=kw)
    elif kw == "MEMORY":
        if "memory" in names:
            args = {"action": "add", "target": "memory", "content": "Practice note %d" % int(time.time())}
            return Reply("tool", tool=("memory", json.dumps(args)), usage=USAGE["MEMORY"], keyword=kw)
        return text_reply("memory-unavailable: the agent has no memory tool", USAGE["MEMORY"], kw)
    elif kw == "FORGE":
        return Reply("text", chunks=chunks_of(FORGE_TEXT, 40, 0.0), usage=USAGE["FORGE"], keyword=kw)
    elif kw == "MEDIA":
        path = os.path.join(media_dir(), "practice.png").replace("\\", "/")
        return text_reply("Here is the practice picture: MEDIA:" + path, USAGE["MEDIA"], kw)
    elif kw == "THINK":
        chunks = [(0.0, THOUGHT)] + words_of("think-answer: the calmer plan wins.")
        return Reply("text", chunks=chunks, usage=USAGE["THINK"], keyword=kw)
    elif kw == "SLOW":
        words = "slow-reply: one two three four five six seven eight nine ten eleven twelve".split(" ")
        chunks = [(4.0 if i == 0 else 1.0, w + (" " if i < len(words) - 1 else "")) for i, w in enumerate(words)]
        return Reply("text", chunks=chunks, usage=USAGE["SLOW"], keyword=kw)
    elif kw == "PAUSE":
        return Reply("text", chunks=[(0.0, "First half of the answer."), (5.0, " Second half.")], usage=USAGE["PAUSE"], keyword=kw)
    elif kw == "LONG":
        n = first_int(arg, 3000)
        n = max(50, min(n, 200000))
        return Reply("text", chunks=chunks_of(make_long(n), 15, 0.05), usage=(USAGE["LONG"][0], int(math.ceil(n / 4.0))), keyword=kw)
    elif kw == "PROMPT_HAS":
        word = arg.split(" ")[0] if arg else ""
        others = " ".join(text_of(m.get("content")) for j, m in enumerate(msgs) if j != i)
        return text_reply("prompt-has %s: %s" % (word, "yes" if word and word in others else "no"), USAGE["PROMPT_HAS"], kw)
    elif kw == "PROMPT_SIZE":
        size = prompt_chars(msgs) + len(json.dumps(tools))
        return text_reply("prompt-size: %d" % size, USAGE["PROMPT_SIZE"], kw)
    elif kw == "BALLAST":
        mb = max(0, min(first_int(arg, 0), 1024))
        del BALLAST[:]
        if mb:
            block = bytearray(mb * 1024 * 1024)
            for off in range(0, len(block), 4096):
                block[off] = 1
            BALLAST.append(block)
        return text_reply("ballast: %d MB held" % mb, USAGE["BALLAST"], kw)
    elif kw == "TOOLS":
        return text_reply("tools: " + ",".join(sorted(names)), USAGE["TOOLS"], kw)
    elif kw == "STATS":
        with COUNTER_LOCK:
            text = "requests=%d titleCalls=%d" % (COUNTERS["requests"], COUNTERS["title"])
        return text_reply(text, USAGE["STATS"], kw)
    # No keyword (or RUN_TOOL, ASK_APPROVAL, MEMORY without a usable tool): the plain echo.
    return text_reply("mock-reply: " + last_user[:200], USAGE["NONE"], kw)


# ---- media ---------------------------------------------------------------------------------------------------------

def make_png(w=64, h=64):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw += bytes(((x * 4) % 256, (y * 4) % 256, 160 if (x // 8 + y // 8) % 2 == 0 else 80))

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) +
            chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def media_dir():
    # Without --media-dir the picture goes to the temp folder, never into the source tree next to this file.
    return MEDIA_DIR or os.path.join(tempfile.gettempdir(), "practice-model-media")


def write_media():
    d = media_dir()
    try:
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, "practice.png"), "wb") as f:
            f.write(make_png())
    except OSError:
        pass


# ---- the HTTP side -------------------------------------------------------------------------------------------------

def usage_obj(u):
    return {"prompt_tokens": u[0], "completion_tokens": u[1], "total_tokens": u[0] + u[1]}


MODELS = {"object": "list", "data": [
    {"id": mid, "object": "model", "created": 0, "owned_by": "practice", "context_length": 128000, "max_model_len": 128000}
    for mid in ("mock-model", "practice-model")]}


class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *a):
        pass

    def _json(self, code, obj):
        data = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = self.path.split("?", 1)[0].rstrip("/")
        log({"GET": self.path})
        if path.endswith("/health") or path == "/health":
            self._json(200, {"status": "ok", "name": "practice-model"})
        elif path.endswith("/models"):
            self._json(200, MODELS)
        else:
            self._json(404, {"error": "not found"})

    def do_POST(self):
        try:
            n = int(self.headers.get("Content-Length") or 0)
            body = json.loads(self.rfile.read(n) or b"{}")
            if not isinstance(body, dict):
                raise ValueError("not an object")
        except (ValueError, OSError):
            return self._json(400, {"error": {"message": "bad request body", "type": "invalid_request_error"}})
        if not self.path.split("?", 1)[0].endswith("/chat/completions"):
            log({"POST": self.path, "stream": False, "n_msgs": 0})
            return self._json(404, {"error": "not found"})
        msgs = [m for m in (body.get("messages") or []) if isinstance(m, dict)]
        with COUNTER_LOCK:
            COUNTERS["requests"] += 1
            if TITLE_PHRASE in request_text(msgs):
                COUNTERS["title"] += 1
        reply = decide(body)
        last = last_user_index(msgs)
        log({
            "POST": self.path, "stream": bool(body.get("stream")), "n_msgs": len(msgs),
            "roles": [str(m.get("role")) for m in msgs][:80], "tools": len(body.get("tools") or []),
            "keyword": reply.keyword, "prompt_chars": prompt_chars(msgs),
            "last_user": (text_of(msgs[last].get("content")) if last >= 0 else "")[:120],
            "heads": [text_of(m.get("content"))[:60] for m in msgs][:40],
            "usage": list(reply.usage) if reply.usage else None,
        })
        cid = "chatcmpl-" + uuid.uuid4().hex[:12]
        created = int(time.time())
        model = body.get("model", "practice-model")
        if reply.kind == "error":
            return self._json(reply.status, reply.error)
        if not body.get("stream"):
            return self._plain(cid, created, model, reply)
        try:
            self._stream(cid, created, model, reply, bool((body.get("stream_options") or {}).get("include_usage")))
        except (BrokenPipeError, ConnectionError, OSError):
            self.close_connection = True

    def _plain(self, cid, created, model, reply):
        if reply.kind == "text":
            msg = {"role": "assistant", "content": reply.text()}
            fr = "stop"
        else:
            msg = {"role": "assistant", "content": None, "tool_calls": [
                {"id": "call_1", "type": "function", "function": {"name": reply.tool[0], "arguments": reply.tool[1]}}]}
            fr = "tool_calls"
        out = {"id": cid, "object": "chat.completion", "created": created, "model": model,
               "choices": [{"index": 0, "message": msg, "finish_reason": fr}]}
        if reply.usage:
            out["usage"] = usage_obj(reply.usage)
        self._json(200, out)

    def _stream(self, cid, created, model, reply, include_usage):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Transfer-Encoding", "chunked")
        self.end_headers()
        base = {"id": cid, "object": "chat.completion.chunk", "created": created, "model": model}

        def send(obj):
            data = ("data: " + (obj if isinstance(obj, str) else json.dumps(obj)) + "\n\n").encode()
            self.wfile.write(("%x\r\n" % len(data)).encode() + data + b"\r\n")
            self.wfile.flush()

        send({**base, "choices": [{"index": 0, "delta": {"role": "assistant"}, "finish_reason": None}]})
        if reply.kind == "text":
            for delay, piece in reply.chunks:
                pause(delay)
                send({**base, "choices": [{"index": 0, "delta": {"content": piece}, "finish_reason": None}]})
            fr = "stop"
        else:
            send({**base, "choices": [{"index": 0, "delta": {"tool_calls": [
                {"index": 0, "id": "call_1", "type": "function", "function": {"name": reply.tool[0], "arguments": ""}}]}, "finish_reason": None}]})
            send({**base, "choices": [{"index": 0, "delta": {"tool_calls": [
                {"index": 0, "function": {"arguments": reply.tool[1]}}]}, "finish_reason": None}]})
            fr = "tool_calls"
        last = {**base, "choices": [{"index": 0, "delta": {}, "finish_reason": fr}]}
        if reply.usage and not include_usage:
            last["usage"] = usage_obj(reply.usage)
        send(last)
        if reply.usage and include_usage:
            send({**base, "choices": [], "usage": usage_obj(reply.usage)})
        send("[DONE]")
        self.wfile.write(b"0\r\n\r\n")
        self.wfile.flush()


def watch_parent():
    ppid = os.getppid()

    def run():
        while True:
            time.sleep(1.0)
            if os.getppid() != ppid:
                os._exit(0)

    threading.Thread(target=run, daemon=True).start()


def make_server(host, port):
    return ThreadingHTTPServer((host, port), H)


# ---- the self-test (CI and INTEGRATE run it; it needs no network beyond loopback) ----------------------------------------

def selftest():
    global SPEED, LOG_PATH, MEDIA_DIR
    import shutil
    import subprocess
    import tempfile
    import urllib.error
    import urllib.request

    SPEED = 0.01
    tmp = tempfile.mkdtemp(prefix="practice-selftest-")
    LOG_PATH = os.path.join(tmp, "requests.jsonl")
    MEDIA_DIR = os.path.join(tmp, "media")
    write_media()
    srv = make_server("127.0.0.1", 0)
    port = srv.server_address[1]
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    base = "http://127.0.0.1:%d" % port
    problems = []

    def check(ok, what):
        if not ok:
            problems.append(what)

    def call(path, payload=None):
        data = json.dumps(payload).encode() if payload is not None else None
        req = urllib.request.Request(base + path, data=data, headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                return r.status, r.read().decode()
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode()

    def chat(text, tools=None, stream=False, extra_msgs=None, include_usage=False, system=None):
        msgs = ([{"role": "system", "content": system}] if system else []) + (extra_msgs or []) + [{"role": "user", "content": text}]
        payload = {"model": "practice-model", "messages": msgs, "stream": stream}
        if tools:
            payload["tools"] = [{"type": "function", "function": {"name": t, "parameters": {}}} for t in tools]
        if include_usage:
            payload["stream_options"] = {"include_usage": True}
        return call("/v1/chat/completions", payload)

    def parse_stream(raw):
        pieces, usage, tool_name, tool_args, finish, last_choices = [], None, None, "", None, None
        for line in raw.split("\n"):
            if not line.startswith("data: ") or line == "data: [DONE]":
                continue
            ev = json.loads(line[6:])
            last_choices = ev.get("choices")
            if ev.get("usage"):
                usage = ev["usage"]
            for ch in ev.get("choices") or []:
                d = ch.get("delta") or {}
                if d.get("content"):
                    pieces.append(d["content"])
                for tc in d.get("tool_calls") or []:
                    fn = tc.get("function") or {}
                    tool_name = fn.get("name") or tool_name
                    tool_args += fn.get("arguments") or ""
                if ch.get("finish_reason"):
                    finish = ch["finish_reason"]
        return {"pieces": pieces, "text": "".join(pieces), "usage": usage, "tool": tool_name, "args": tool_args,
                "finish": finish, "last_choices": last_choices, "done": "data: [DONE]" in raw}

    def plain(text, **kw):
        code, raw = chat(text, **kw)
        return code, json.loads(raw)

    def content(obj):
        return obj["choices"][0]["message"].get("content")

    def usage_of(obj):
        u = obj.get("usage") or {}
        return (u.get("prompt_tokens"), u.get("completion_tokens"))

    try:
        # the table and the keyword list agree
        check(set(KEYWORDS) == set(PRIORITY) and len(KEYWORDS) == 15 == len(PRIORITY), "KEYWORDS and PRIORITY differ")
        check(all(k in USAGE for k in KEYWORDS), "a keyword has no USAGE row")

        # health and models
        code, raw = call("/health")
        check(code == 200 and json.loads(raw) == {"status": "ok", "name": "practice-model"}, "health")
        code, raw = call("/v1/models")
        ids = [m["id"] for m in json.loads(raw)["data"]]
        check("mock-model" in ids and "practice-model" in ids, "models list")
        check(all(m.get("context_length") == 128000 and m.get("max_model_len") == 128000 for m in json.loads(raw)["data"]), "models context")

        # no keyword, plain and streamed
        code, o = plain("hello")
        check(code == 200 and content(o) == "mock-reply: hello" and usage_of(o) == (100, 10), "plain echo")
        code, raw = chat("hello", stream=True)
        s = parse_stream(raw)
        check(s["text"] == "mock-reply: hello" and s["usage"]["total_tokens"] == 110 and s["finish"] == "stop" and s["done"], "streamed echo")
        code, raw = chat("hello", stream=True, include_usage=True)
        s = parse_stream(raw)
        check(s["last_choices"] == [] and s["usage"]["prompt_tokens"] == 100, "usage goes into a last chunk when asked")

        # RUN_TOOL, including big and the second call
        code, o = plain("please RUN_TOOL now", tools=["browser", "terminal"])
        call1 = o["choices"][0]["message"]["tool_calls"][0]["function"]
        check(o["choices"][0]["finish_reason"] == "tool_calls" and call1["name"] == "terminal" and
              json.loads(call1["arguments"]) == {"command": "echo hello-from-tool"} and usage_of(o) == (100, 20), "RUN_TOOL call")
        tool_turn = [{"role": "user", "content": "please RUN_TOOL now"}, {"role": "assistant", "content": None},
                     {"role": "tool", "content": "hello-from-tool"}]
        code, raw = call("/v1/chat/completions", {"model": "m", "messages": tool_turn, "tools": [], "stream": False})
        o = json.loads(raw)
        check(content(o) == "tool-result-seen: hello-from-tool" and usage_of(o) == (130, 10), "RUN_TOOL second call")
        code, o = plain("RUN_TOOL big", tools=["terminal"])
        cmd = json.loads(o["choices"][0]["message"]["tool_calls"][0]["function"]["arguments"])["command"]
        check(cmd == BIG_COMMAND, "RUN_TOOL big command")
        if shutil.which("sh"):
            out = subprocess.run(["sh", "-c", cmd], capture_output=True, text=True, timeout=20).stdout.split("\n")
            full = [l for l in out if len(l) == 80]
            check(len(full) >= 120, "RUN_TOOL big prints 120 lines of 80 characters (got %d)" % len(full))
        code, o = plain("RUN_TOOL")
        check(content(o) == "mock-reply: RUN_TOOL", "RUN_TOOL without tools falls back to the echo")

        # ASK_APPROVAL
        code, o = plain("ASK_APPROVAL please", tools=["terminal"])
        args = json.loads(o["choices"][0]["message"]["tool_calls"][0]["function"]["arguments"])
        check(args == {"command": APPROVAL_COMMAND} and usage_of(o) == (100, 20), "ASK_APPROVAL call")
        turn = [{"role": "user", "content": "ASK_APPROVAL please"}, {"role": "tool", "content": "denied by the user " * 20}]
        code, raw = call("/v1/chat/completions", {"model": "m", "messages": turn, "stream": False})
        text = content(json.loads(raw))
        check(text.startswith("approval-flow-done: denied") and len(text) == len("approval-flow-done: ") + 120, "ASK_APPROVAL second call")

        # MEMORY
        code, o = plain("MEMORY", tools=["memory", "terminal"])
        fn = o["choices"][0]["message"]["tool_calls"][0]["function"]
        a = json.loads(fn["arguments"])
        check(fn["name"] == "memory" and a["action"] == "add" and a["target"] == "memory" and a["content"].startswith("Practice note ") and
              usage_of(o) == (100, 25), "MEMORY call")
        turn = [{"role": "user", "content": "MEMORY"}, {"role": "tool", "content": "saved"}]
        code, raw = call("/v1/chat/completions", {"model": "m", "messages": turn, "stream": False})
        check(content(json.loads(raw)) == "memory-saved: saved", "MEMORY second call")

        # FORGE
        code, o = plain("FORGE")
        m = re.search(r"```pocket-bot\n(.*?)\n```", content(o), re.S)
        spec = json.loads(m.group(1)) if m else {}
        check(spec.get("name") == "Practice Pal" and spec.get("avatar") == "OWL" and spec.get("color") == "#2E7D6B" and
              spec.get("voice") == "F2" and spec.get("voiceStyle") == "calm" and usage_of(o) == (100, 260), "FORGE block")

        # MEDIA
        code, o = plain("MEDIA")
        m = re.search(r"MEDIA:(.+)$", content(o))
        check(bool(m) and os.path.isfile(m.group(1)), "MEDIA names a file that exists")
        if m and os.path.isfile(m.group(1)):
            with open(m.group(1), "rb") as f:
                check(f.read(8) == b"\x89PNG\r\n\x1a\n", "MEDIA file is a PNG")

        # THINK keeps the shape ThoughtFilter moves into the folded thought
        code, raw = chat("THINK", stream=True)
        s = parse_stream(raw)
        check(s["pieces"][0] == THOUGHT and s["pieces"][0].startswith("**") and s["pieces"][0].endswith("\n\n") and
              s["text"].endswith("think-answer: the calmer plan wins.") and s["usage"]["completion_tokens"] == 60, "THINK shape")

        # SLOW and PAUSE
        code, raw = chat("SLOW", stream=True)
        s = parse_stream(raw)
        check(s["text"] == "slow-reply: one two three four five six seven eight nine ten eleven twelve" and s["usage"]["completion_tokens"] == 12, "SLOW text")
        code, raw = chat("PAUSE", stream=True)
        s = parse_stream(raw)
        check(s["pieces"] == ["First half of the answer.", " Second half."], "PAUSE halves")

        # LONG: exact length, every markdown element, the same text streamed and not
        for n in (100, 500, 3000):
            code, o = plain("LONG %d" % n)
            t = content(o)
            check(len(t) == n and t.endswith(LONG_END), "LONG %d is exactly %d characters" % (n, n))
            check(usage_of(o) == (100, int(math.ceil(n / 4.0))), "LONG %d usage" % n)
        code, o = plain("LONG")
        t = content(o)
        check(len(t) == 3000, "LONG default is 3000 characters")
        check(t.count("```") == 2, "LONG has exactly one fenced code block")
        for needle, what in (("# Practice answer", "heading"), ("- first bullet", "bullets"), ("1. first step", "numbered list"),
                             ("| name | value |", "table"), ("**long practice answer**", "bold"), ("`inline code`", "inline code"),
                             ("[link](https://example.com/practice)", "link")):
            check(needle in t, "LONG has a " + what)
        code, raw = chat("LONG", stream=True)
        check(parse_stream(raw)["text"] == t, "LONG streamed equals LONG plain")

        # FAIL
        code, raw = chat("FAIL")
        check(code == 400 and json.loads(raw)["error"]["code"] == "practice_fail" and "practice failure" in json.loads(raw)["error"]["message"], "FAIL 400")
        code, raw = chat("FAIL 429")
        check(code == 429, "FAIL 429")
        code, raw = chat("FAIL 99")
        check(code == 400, "FAIL with a code out of range is 400")

        # PROMPT_HAS, PROMPT_SIZE
        code, o = plain("PROMPT_HAS SOUL-MARK-7", system="You are kind. SOUL-MARK-7 answer in rhymes")
        check(content(o) == "prompt-has SOUL-MARK-7: yes", "PROMPT_HAS yes")
        code, o = plain("PROMPT_HAS SOUL-MARK-7")
        check(content(o) == "prompt-has SOUL-MARK-7: no", "PROMPT_HAS no (the last user message does not count)")
        code, o = plain("PROMPT_SIZE", tools=["terminal"], system="abc")
        want = len("abc") + len("PROMPT_SIZE") + len(json.dumps([{"type": "function", "function": {"name": "terminal", "parameters": {}}}]))
        check(content(o) == "prompt-size: %d" % want, "PROMPT_SIZE (got %s, want %d)" % (content(o), want))

        # BALLAST
        code, o = plain("BALLAST 2")
        check(content(o) == "ballast: 2 MB held" and len(BALLAST) == 1 and len(BALLAST[0]) == 2 * 1024 * 1024, "BALLAST holds memory")
        code, o = plain("BALLAST 0")
        check(content(o) == "ballast: 0 MB held" and not BALLAST, "BALLAST 0 frees it")

        # TOOLS and STATS (a title call is counted and does not answer a keyword)
        code, o = plain("TOOLS", tools=["terminal", "memory", "browser"])
        check(content(o) == "tools: browser,memory,terminal", "TOOLS sorted")
        code, o = plain("hello", system=TITLE_PHRASE + " from the first message: LONG 9000")
        check(content(o) == "Practice chat", "a title call answers a short title")
        code, o = plain("STATS")
        m = re.match(r"requests=(\d+) titleCalls=(\d+)$", content(o) or "")
        check(bool(m) and int(m.group(2)) == 1 and int(m.group(1)) > 10, "STATS counts requests and title calls (%s)" % content(o))

        # priority
        code, raw = chat("FAIL RUN_TOOL LONG", tools=["terminal"])
        check(code == 400, "FAIL beats the other keywords")
        code, o = plain("RUN_TOOL then LONG", tools=["terminal"])
        check(o["choices"][0]["finish_reason"] == "tool_calls", "RUN_TOOL beats LONG")
        code, o = plain("a word like TOOLSET or SLOWER or RUN_TOOLS is no keyword")
        check(content(o).startswith("mock-reply:"), "only whole words count")

        # the request log is valid JSON with the fields the smoke test reads
        with open(LOG_PATH) as f:
            lines = [l for l in f.read().split("\n") if l.strip()]
        posts = [json.loads(l) for l in lines if '"POST"' in l]
        check(len(posts) > 20, "the log has the POST lines")
        check(all("n_msgs" in p and "heads" in p and "keyword" in p and "last_user" in p and "prompt_chars" in p for p in posts), "log fields")
        check(any(TITLE_PHRASE in " ".join(p["heads"]) for p in posts), "a title call shows in heads")
        check(all(re.search(r'"stream": (true|false), "n_msgs": \d+', l) for l in lines if '"POST"' in l and '"n_msgs"' in l), "log order the smoke test reads")
    except Exception as e:  # a bug in the self-test itself must fail loudly, not hang
        problems.append("self-test crashed: %r" % (e,))
    finally:
        srv.shutdown()
        srv.server_close()
        shutil.rmtree(tmp, ignore_errors=True)
    if problems:
        for p in problems:
            print("PRACTICE SELFTEST FAIL:", p)
        return 1
    print("PRACTICE SELFTEST OK")
    return 0


def main():
    global LOG_PATH, MEDIA_DIR
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=18080)
    ap.add_argument("--log")
    ap.add_argument("--media-dir")
    ap.add_argument("--watch-parent", action="store_true")
    ap.add_argument("--selftest", action="store_true")
    a = ap.parse_args()
    if a.selftest:
        return selftest()
    LOG_PATH = a.log
    MEDIA_DIR = os.path.abspath(a.media_dir) if a.media_dir else None
    write_media()
    try:
        srv = make_server(a.host, a.port)
    except OSError as e:
        print("practice model: cannot listen on %s:%d: %s" % (a.host, a.port, e), file=sys.stderr, flush=True)
        return 1
    if a.watch_parent:
        watch_parent()
    print("practice model listening on", a.port, flush=True)
    srv.serve_forever()
    return 0


if __name__ == "__main__":
    sys.exit(main())
