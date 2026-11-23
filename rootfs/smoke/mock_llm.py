#!/usr/bin/env python3
"""Tiny OpenAI-compatible mock LLM used by the CI smoke test (stdlib only).

 * GET  /v1/models
 * POST /v1/chat/completions   (stream and non-stream)

Behaviour: if the conversation has no tool result yet and the user message contains
"RUN_TOOL", answer with a tool call to the agent's terminal tool; once a tool result is
present, answer with text that echoes it. Otherwise answer "mock-reply: <last user text>".
Every request is appended (headers redacted, bodies trimmed) to --log for debugging.
"""
import argparse
import json
import sys
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG = None


def log(obj):
    if LOG:
        with open(LOG, "a") as f:
            f.write(json.dumps(obj)[:4000] + "\n")


def text_of(content):
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return " ".join(p.get("text", "") for p in content if isinstance(p, dict))
    return ""


def decide(body):
    msgs = body.get("messages", [])
    tools = body.get("tools") or []
    last_tool = next((m for m in reversed(msgs) if m.get("role") == "tool"), None)
    last_user = next((text_of(m.get("content")) for m in reversed(msgs) if m.get("role") == "user"), "")
    # Is the newest message a tool result? -> final answer
    if msgs and msgs[-1].get("role") == "tool" and last_tool is not None:
        return ("text", "tool-result-seen: " + text_of(last_tool.get("content"))[:200])
    if "RUN_TOOL" in last_user:
        names = [t.get("function", {}).get("name") for t in tools]
        name = "terminal" if "terminal" in names else (names[0] if names else None)
        if name:
            args = {"command": "echo hello-from-tool"} if name == "terminal" else {}
            return ("tool", (name, json.dumps(args)))
    return ("text", "mock-reply: " + last_user[:200])


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
        log({"GET": self.path})
        if self.path.rstrip("/").endswith("/models"):
            self._json(200, {"object": "list", "data": [{"id": "mock-model", "object": "model", "created": 0, "owned_by": "mock"}]})
        else:
            self._json(404, {"error": "not found"})

    def do_POST(self):
        n = int(self.headers.get("Content-Length") or 0)
        body = json.loads(self.rfile.read(n) or b"{}")
        log({"POST": self.path, "stream": body.get("stream"), "n_msgs": len(body.get("messages", [])),
             "roles": [m.get("role") for m in body.get("messages", [])], "tools": len(body.get("tools") or [])})
        if not self.path.endswith("/chat/completions"):
            return self._json(404, {"error": "not found"})
        kind, val = decide(body)
        cid = "chatcmpl-" + uuid.uuid4().hex[:12]
        created = int(time.time())
        base = {"id": cid, "object": "chat.completion.chunk", "created": created, "model": body.get("model", "mock-model")}
        if not body.get("stream"):
            if kind == "text":
                msg = {"role": "assistant", "content": val}
                fr = "stop"
            else:
                msg = {"role": "assistant", "content": None, "tool_calls": [
                    {"id": "call_1", "type": "function", "function": {"name": val[0], "arguments": val[1]}}]}
                fr = "tool_calls"
            return self._json(200, {"id": cid, "object": "chat.completion", "created": created, "model": base["model"],
                                    "choices": [{"index": 0, "message": msg, "finish_reason": fr}],
                                    "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}})
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Transfer-Encoding", "chunked")
        self.end_headers()

        def send(obj):
            data = ("data: " + (obj if isinstance(obj, str) else json.dumps(obj)) + "\n\n").encode()
            self.wfile.write(("%x\r\n" % len(data)).encode() + data + b"\r\n")
            self.wfile.flush()

        send({**base, "choices": [{"index": 0, "delta": {"role": "assistant"}, "finish_reason": None}]})
        if kind == "text":
            words = val.split(" ")
            for i, w in enumerate(words):
                send({**base, "choices": [{"index": 0, "delta": {"content": w + (" " if i < len(words) - 1 else "")}, "finish_reason": None}]})
            send({**base, "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}]})
        else:
            send({**base, "choices": [{"index": 0, "delta": {"tool_calls": [
                {"index": 0, "id": "call_1", "type": "function", "function": {"name": val[0], "arguments": ""}}]}, "finish_reason": None}]})
            send({**base, "choices": [{"index": 0, "delta": {"tool_calls": [
                {"index": 0, "function": {"arguments": val[1]}}]}, "finish_reason": None}]})
            send({**base, "choices": [{"index": 0, "delta": {}, "finish_reason": "tool_calls"}]})
        send("[DONE]")
        self.wfile.write(b"0\r\n\r\n")
        self.wfile.flush()


def main():
    global LOG
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=18080)
    ap.add_argument("--log")
    a = ap.parse_args()
    LOG = a.log
    srv = ThreadingHTTPServer(("127.0.0.1", a.port), H)
    print("mock llm listening on", a.port, flush=True)
    srv.serve_forever()


if __name__ == "__main__":
    sys.exit(main())
