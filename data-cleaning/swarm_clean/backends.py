"""Labeler backends. Each one takes a list of jobs and returns a label (or an
error string) per job id.

- mock:       deterministic heuristics, no model, no cost. For tests and dry runs.
- api:        Anthropic API with many concurrent requests (the "swarm"), or the
              Message Batches API (--batch) at half the price for big runs.
- claude-cli: shells out to `claude -p`, so the calls count against a Claude
              subscription's usage instead of API credits.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import tempfile
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from typing import Callable

from . import DOLLY_CATEGORIES
from .prompts import ISSUES, LABEL_SCHEMA, SYSTEMS, user_message

DEFAULT_MODEL = "claude-haiku-5-5"


@dataclass
class Job:
    id: str
    framing: str  # "A", "B" or "C"
    record: dict


def validate(label: object) -> dict:
    """Check a label against LABEL_SCHEMA by hand; raise ValueError if bad."""
    if not isinstance(label, dict):
        raise ValueError("label is not an object")
    extra = set(label) - set(LABEL_SCHEMA["properties"])
    missing = set(LABEL_SCHEMA["required"]) - set(label)
    if extra or missing:
        raise ValueError(f"bad keys: extra={sorted(extra)} missing={sorted(missing)}")
    if label["category"] not in DOLLY_CATEGORIES:
        raise ValueError(f"bad category {label['category']!r}")
    if label["quality"] not in (1, 2, 3, 4, 5) or isinstance(label["quality"], bool):
        raise ValueError(f"bad quality {label['quality']!r}")
    if not isinstance(label["issues"], list) or any(i not in ISSUES for i in label["issues"]):
        raise ValueError(f"bad issues {label['issues']!r}")
    if not isinstance(label["keep"], bool):
        raise ValueError("keep is not a boolean")
    if not isinstance(label["note"], str):
        raise ValueError("note is not a string")
    out = dict(label)
    out["issues"] = sorted(set(label["issues"]))
    out["note"] = label["note"][:200]
    return out


Result = dict  # {"label": dict} or {"error": str}


def _parallel(jobs: list[Job], fn: Callable[[Job], Result], workers: int,
              on_result: Callable[[Job, Result], None] | None) -> dict[str, Result]:
    results: dict[str, Result] = {}
    with ThreadPoolExecutor(max_workers=max(1, workers)) as pool:
        futures = {pool.submit(fn, job): job for job in jobs}
        for fut in as_completed(futures):
            job = futures[fut]
            try:
                res = fut.result()
            except Exception as exc:  # one bad call must not sink the run
                res = {"error": f"{type(exc).__name__}: {exc}"}
            results[job.id] = res
            if on_result:
                on_result(job, res)
    return results


# --------------------------------------------------------------------------- mock

_CONTEXT_WORDS = re.compile(
    r"\b(passage|reference text|the text|this text|the article|given text|"
    r"paragraph|from the following|based on the|provided)\b", re.I)
_WORD = re.compile(r"[a-z]{4,}")


class MockBackend:
    """Cheap stand-in for a model so the whole pipeline runs offline.

    The heuristics are crude on purpose; they exist to exercise the plumbing
    (caching, agreement, tie-breaks, canary and gold checks), not to label well.
    Framing B is a bit stricter than A so that disagreements actually happen.
    """

    name = "mock"

    def label_one(self, job: Job) -> Result:
        r = job.record
        instr, ctx, resp = r["instruction"], r["context"], r["response"]
        issues = []
        if _CONTEXT_WORDS.search(instr) and not ctx:
            issues.append("needs_missing_context")
        if len(resp.split()) > 12 and resp.rstrip()[-1:] not in ".!?)\"'":
            issues.append("truncated_or_incomplete")
        iw = set(_WORD.findall((instr + " " + ctx).lower()))
        rw = set(_WORD.findall(resp.lower()))
        overlap = len(iw & rw) / max(1, min(len(iw), len(rw)))
        off_topic_cut = 0.08 if job.framing == "A" else 0.12
        if len(rw) >= 8 and len(iw) >= 3 and overlap < off_topic_cut:
            issues.append("response_off_topic")
        if len(resp.split()) < 2 and not ctx:
            issues.append("low_effort")
        quality = max(1, 5 - 2 * len(issues))
        lowered = instr.lower()
        if ctx:
            category = ("summarization" if "summar" in lowered else
                        "information_extraction" if re.search(r"\b(list|extract)\b", lowered)
                        else "closed_qa")
        elif re.search(r"\b(classify|which of|categor)", lowered):
            category = "classification"
        elif re.search(r"\b(ideas|brainstorm|list of|suggest)", lowered):
            category = "brainstorming"
        elif re.search(r"\b(write|poem|story|letter)\b", lowered):
            category = "creative_writing"
        elif re.search(r"^(what|who|when|where|which)\b", lowered):
            category = "open_qa"
        else:
            category = "general_qa"
        return {"label": validate({
            "category": category, "quality": quality, "issues": issues,
            "keep": not issues, "note": "mock heuristic",
        })}

    def label(self, jobs, workers=1, on_result=None):
        return _parallel(jobs, self.label_one, 1, on_result)


# --------------------------------------------------------------------------- api

class ApiBackend:
    name = "api"

    def __init__(self, model: str = DEFAULT_MODEL, effort: str = "low",
                 max_tokens: int = 2048, batch: bool = False):
        import anthropic  # imported lazily so mock runs need no SDK

        self.anthropic = anthropic
        self.client = anthropic.Anthropic(max_retries=8)
        self.model, self.effort, self.max_tokens, self.batch = model, effort, max_tokens, batch

    def _params(self, job: Job) -> dict:
        return {
            "model": self.model,
            "max_tokens": self.max_tokens,
            "system": SYSTEMS[job.framing],
            "messages": [{"role": "user", "content": user_message(job.record, job.framing)}],
            "output_config": {
                "effort": self.effort,
                "format": {"type": "json_schema", "schema": LABEL_SCHEMA},
            },
        }

    @staticmethod
    def _parse(message) -> Result:
        if message.stop_reason == "refusal":
            return {"error": "refusal"}
        if message.stop_reason == "max_tokens":
            return {"error": "max_tokens"}
        text = next((b.text for b in message.content if b.type == "text"), None)
        if text is None:
            return {"error": "no text block"}
        usage = {"input_tokens": message.usage.input_tokens,
                 "output_tokens": message.usage.output_tokens}
        try:
            return {"label": validate(json.loads(text)), "usage": usage}
        except (json.JSONDecodeError, ValueError) as exc:
            return {"error": f"invalid label: {exc}", "usage": usage}

    def label_one(self, job: Job) -> Result:
        a = self.anthropic
        try:
            return self._parse(self.client.messages.create(**self._params(job)))
        except a.BadRequestError as exc:
            return {"error": f"bad request: {exc.message}"}
        except (a.AuthenticationError, a.PermissionDeniedError, a.NotFoundError):
            raise  # configuration problems: stop instead of failing every job
        except a.RateLimitError as exc:
            return {"error": f"rate limited after retries: {exc.message}"}
        except a.APIStatusError as exc:
            return {"error": f"api error {exc.status_code}: {exc.message}"}
        except a.APIConnectionError as exc:
            return {"error": f"connection error: {exc}"}

    def label(self, jobs, workers=32, on_result=None):
        if self.batch:
            return self._label_batch(jobs, on_result)
        return _parallel(jobs, self.label_one, workers, on_result)

    def _label_batch(self, jobs, on_result, chunk: int = 10000, poll_s: int = 30):
        from anthropic.types.message_create_params import MessageCreateParamsNonStreaming
        from anthropic.types.messages.batch_create_params import Request

        by_id = {j.id: j for j in jobs}
        results: dict[str, Result] = {}
        for start in range(0, len(jobs), chunk):
            part = jobs[start : start + chunk]
            batch = self.client.messages.batches.create(requests=[
                Request(custom_id=j.id, params=MessageCreateParamsNonStreaming(**self._params(j)))
                for j in part
            ])
            print(f"  batch {batch.id}: {len(part)} requests submitted", flush=True)
            while self.client.messages.batches.retrieve(batch.id).processing_status != "ended":
                time.sleep(poll_s)
            for item in self.client.messages.batches.results(batch.id):
                res = (self._parse(item.result.message) if item.result.type == "succeeded"
                       else {"error": f"batch {item.result.type}"})
                results[item.custom_id] = res
                if on_result and item.custom_id in by_id:
                    on_result(by_id[item.custom_id], res)
        return results


# --------------------------------------------------------------------------- claude-cli

class ClaudeCliBackend:
    """Runs each label as `claude -p` with tools off and a JSON schema."""

    name = "claude-cli"

    def __init__(self, model: str = "haiku", timeout_s: int = 180):
        self.model, self.timeout_s = model, timeout_s
        # An empty working directory so no project CLAUDE.md leaks into prompts.
        self.cwd = tempfile.mkdtemp(prefix="swarm-clean-")

    def label_one(self, job: Job) -> Result:
        cmd = [
            "claude", "-p", "--model", self.model, "--output-format", "json",
            "--json-schema", json.dumps(LABEL_SCHEMA), "--system-prompt", SYSTEMS[job.framing],
            "--tools", "", "--no-session-persistence",
        ]
        proc = subprocess.run(
            cmd, input=user_message(job.record, job.framing), capture_output=True, text=True,
            timeout=self.timeout_s, cwd=self.cwd, env={**os.environ},
        )
        if proc.returncode != 0:
            return {"error": f"claude exited {proc.returncode}: {proc.stderr.strip()[:300]}"}
        try:
            envelope = json.loads(proc.stdout)
            payload = envelope.get("structured_output")
            if payload is None:
                payload = json.loads(envelope.get("result", ""))
            u = envelope.get("usage") or {}
            # The CLI adds its own system scaffolding, mostly served from the
            # prompt cache, so count cache reads and writes as input too and
            # keep the CLI's own dollar figure, which prices them correctly.
            usage = {"input_tokens": u.get("input_tokens", 0)
                     + u.get("cache_creation_input_tokens", 0)
                     + u.get("cache_read_input_tokens", 0),
                     "output_tokens": u.get("output_tokens", 0),
                     "cost_usd": envelope.get("total_cost_usd", 0.0) or 0.0}
        except (json.JSONDecodeError, ValueError, AttributeError) as exc:
            return {"error": f"invalid output: {exc}"}
        try:
            return {"label": validate(payload), "usage": usage}
        except (ValueError, AttributeError) as exc:
            return {"error": f"invalid output: {exc}", "usage": usage}

    def label(self, jobs, workers=8, on_result=None):
        return _parallel(jobs, self.label_one, workers, on_result)


def make_backend(name: str, model: str | None, effort: str, batch: bool):
    if name == "mock":
        return MockBackend()
    if name == "api":
        return ApiBackend(model=model or DEFAULT_MODEL, effort=effort, batch=batch)
    if name == "claude-cli":
        return ClaudeCliBackend(model=model or "haiku")
    raise ValueError(f"unknown backend {name!r}")
