"""Hunter agents. One call = one agent working on one work unit.

Backends:
  api     Anthropic API (needs ANTHROPIC_API_KEY or an `ant auth login` profile). Use this for
          large swarms: it is billed per token and is not limited by a subscription's usage caps.
  cli     `claude -p` (Claude Code, headless). Runs on the logged-in subscription. Fine for a
          pilot of 10-20 agents; not meant for hundreds in parallel.
  replay  Reads canned claims from a JSONL file. No model calls; used to test the pipeline.
"""

from __future__ import annotations

import asyncio
import json
import re
from dataclasses import dataclass, field
from pathlib import Path

from pydantic import BaseModel, Field

from .target import BUG_HUNTING_DIR, TargetConfig, WorkUnit

# USD per million tokens, from the Anthropic pricing table (prompts up to 100K tokens).
PRICES = {"claude-haiku-5-5": (0.10, 0.50)}


class Claim(BaseModel):
    title: str = Field(description="One line naming the bug.")
    file: str = Field(description="Path of the buggy file, as given in the slice.")
    symbol: str = Field(description="Function or class (or Class.method) that has the bug.")
    explanation: str = Field(description="Why this is a bug, in 1-4 sentences.")
    expected_behavior_source: str = Field(
        description="Quote of the docstring, doc or protocol that defines correct behaviour."
    )
    test_code: str = Field(description="Complete pytest file that fails on the current code.")
    fix_patch: str = Field(default="", description="Optional unified diff fixing the bug.")


class HuntResult(BaseModel):
    claims: list[Claim]


@dataclass
class AgentResult:
    unit_id: str
    claims: list[dict]
    input_tokens: int = 0
    output_tokens: int = 0
    cost_usd: float = 0.0
    error: str | None = None
    extra: dict = field(default_factory=dict)


def build_prompt(cfg: TargetConfig, root: Path, unit: WorkUnit, max_claims: int) -> str:
    template = (BUG_HUNTING_DIR / "prompts" / "hunter.md").read_text()
    module = unit.file[:-3].replace("/", ".") if unit.file.endswith(".py") else unit.file
    module = module.split(".", 1)[1] if module.startswith(cfg.package + ".") else module
    values = {
        "{project}": cfg.name,
        "{package}": cfg.package,
        "{module}": module,
        "{file}": unit.file,
        "{symbols}": ", ".join(unit.symbols),
        "{max_claims}": str(max_claims),
        # last, so braces inside the source are never treated as placeholders
        "{source}": (root / unit.file).read_text(),
    }
    for key, val in values.items():
        template = template.replace(key, val)
    return template


class ApiBackend:
    def __init__(self, model: str, effort: str, max_tokens: int = 16000):
        import anthropic  # imported lazily so replay/cli runs don't need the SDK

        self.anthropic = anthropic
        self.client = anthropic.AsyncAnthropic(max_retries=4)
        self.model = model
        self.effort = effort
        self.max_tokens = max_tokens

    async def hunt(self, unit: WorkUnit, prompt: str) -> AgentResult:
        try:
            resp = await self.client.messages.parse(
                model=self.model,
                max_tokens=self.max_tokens,
                output_config={"effort": self.effort},
                output_format=HuntResult,
                messages=[{"role": "user", "content": prompt}],
            )
        except self.anthropic.APIStatusError as e:  # 4xx/5xx after SDK retries
            return AgentResult(unit.unit_id, [], error=f"api status {e.status_code}: {e.message}")
        except self.anthropic.APIConnectionError as e:
            return AgentResult(unit.unit_id, [], error=f"api connection: {e}")
        usage = resp.usage
        pin, pout = PRICES.get(self.model, (0.0, 0.0))
        cost = (usage.input_tokens * pin + usage.output_tokens * pout) / 1e6
        result = AgentResult(unit.unit_id, [], usage.input_tokens, usage.output_tokens, cost)
        if resp.stop_reason == "refusal":
            result.error = "refusal"
        elif resp.stop_reason == "max_tokens":
            result.error = "hit max_tokens"
        elif resp.parsed_output is None:
            result.error = "no parsed output"
        else:
            result.claims = [c.model_dump() for c in resp.parsed_output.claims]
        return result


class CliBackend:
    SUFFIX = (
        "\n\n## Output format\n\nReply with only a JSON object, no prose and no code fence, "
        "matching this JSON schema:\n" + json.dumps(HuntResult.model_json_schema())
    )

    def __init__(self, model: str, timeout_s: int = 900):
        self.model = model
        self.timeout_s = timeout_s

    async def hunt(self, unit: WorkUnit, prompt: str) -> AgentResult:
        proc = await asyncio.create_subprocess_exec(
            "claude", "-p", "--model", self.model, "--output-format", "json",
            "--tools", "",  # the hunter only reads the prompt; no tools needed
            "--json-schema", json.dumps(HuntResult.model_json_schema()),
            stdin=asyncio.subprocess.PIPE,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        try:
            out, err = await asyncio.wait_for(
                proc.communicate((prompt + self.SUFFIX).encode()), self.timeout_s
            )
        except asyncio.TimeoutError:
            proc.kill()
            return AgentResult(unit.unit_id, [], error="cli timeout")
        if proc.returncode != 0:
            return AgentResult(unit.unit_id, [], error=f"cli exit {proc.returncode}: {err.decode()[-500:]}")
        try:
            envelope = json.loads(out)
            if envelope.get("structured_output") is not None:
                parsed = HuntResult.model_validate(envelope["structured_output"])
            else:
                text = envelope.get("result", "")
                match = re.search(r"\{.*\}", text, re.S)
                parsed = HuntResult.model_validate_json(match.group(0) if match else text)
        except Exception as e:  # malformed output is a failed agent, not a crash
            return AgentResult(unit.unit_id, [], error=f"unparseable cli output: {e}")
        usage = envelope.get("usage", {})
        return AgentResult(
            unit.unit_id,
            [c.model_dump() for c in parsed.claims],
            usage.get("input_tokens", 0),
            usage.get("output_tokens", 0),
            float(envelope.get("total_cost_usd") or 0.0),
        )


class ReplayBackend:
    """Hands each unit the canned claims whose file and symbol fall inside it."""

    def __init__(self, path: str | Path):
        self.claims = [json.loads(l) for l in Path(path).read_text().splitlines() if l.strip()]

    async def hunt(self, unit: WorkUnit, prompt: str) -> AgentResult:
        mine = [
            {k: v for k, v in c.items() if not k.startswith("_")}
            for c in self.claims
            if c["file"] == unit.file and c["symbol"].split(".")[0] in unit.symbols
        ]
        return AgentResult(unit.unit_id, mine)


def make_backend(name: str, model: str, effort: str, replay_file: str | None):
    if name == "api":
        return ApiBackend(model, effort)
    if name == "cli":
        return CliBackend(model)
    if name == "replay":
        if not replay_file:
            raise ValueError("--replay-file is required with --backend replay")
        return ReplayBackend(replay_file)
    raise ValueError(f"unknown backend {name}")
