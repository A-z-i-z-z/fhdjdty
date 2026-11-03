"""Automatic verifier. A claim survives only if the machine can reproduce it.

Pipeline for one claim:
  1. Static gate   test parses, has a test_ function, imports the target package, mentions the
                   claimed symbol, and does not use banned modules or fail on its own.
  2. Reproduce     the test runs against a fresh copy of the target and must FAIL, `repeats`
                   times in a row, with the same signature each time. The failure must be an
                   AssertionError raised in the test, or an exception raised from inside the
                   target package. Errors in the test itself (bad import, NameError, wrong call
                   signature, collection error, timeout) reject the claim.
  3. Fix check     (only when the claim has a fix_patch) the patch must apply to source files
                   only, the claim's test must then pass, and no test that passed in the
                   baseline suite may start failing.

Statuses: confirmed_fixed > confirmed > rejected.

The verifier executes model-written code. Run it in a throwaway container or VM; it strips
API keys from the environment and can cut network access with --isolate-network, but it is
not a security sandbox.
"""

from __future__ import annotations

import ast
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass, field
from pathlib import Path

BANNED_MODULES = {
    "subprocess", "socket", "requests", "urllib", "http", "httpx", "ftplib", "smtplib",
    "multiprocessing", "ctypes", "shutil", "signal", "asyncio",
}
BANNED_CALLS = {"system", "popen", "exec", "eval", "__import__", "fork", "rmtree"}
CLAIM_TEST_NAME = "test_swarm_claim.py"
FRAME_RE = re.compile(r'File "([^"]+)", line (\d+), in (\S+)')
EXC_RE = re.compile(r"^(?:E\s+)?([A-Za-z_][\w.]*(?:Error|Exception|Warning|Exit|Interrupt))\b")


@dataclass
class Verdict:
    claim_id: str
    status: str  # confirmed_fixed | confirmed | rejected
    reason: str
    signature: str = ""
    fix: str = "none"  # none | ok | patch_failed | patch_touches_tests | still_fails | breaks_suite
    details: dict = field(default_factory=dict)

    def to_dict(self) -> dict:
        return asdict(self)


def claim_id(claim: dict) -> str:
    h = hashlib.sha256((claim["file"] + claim["symbol"] + claim["test_code"]).encode())
    return h.hexdigest()[:12]


# ---------------------------------------------------------------- 1. static gate


def static_gate(claim: dict, package: str) -> str | None:
    """Return a rejection reason, or None if the test may be executed."""
    code = claim.get("test_code", "")
    try:
        tree = ast.parse(code)
    except SyntaxError as e:
        return f"test does not parse: {e.msg}"
    tests = [
        n for n in ast.walk(tree)
        if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)) and n.name.startswith("test")
    ]
    if not tests:
        return "no test_ function"
    imports_target = False
    imported_modules: list[str] = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            mods = [a.name for a in node.names]
        elif isinstance(node, ast.ImportFrom):
            mods = [node.module or ""]
        else:
            mods = []
        imported_modules.extend(mods)
        for m in mods:
            root = m.split(".")[0]
            if root == package:
                imports_target = True
            if root in BANNED_MODULES or m.startswith("os.") and m != "os.path":
                return f"banned import: {m}"
        if isinstance(node, ast.Call):
            fn = node.func
            name = fn.attr if isinstance(fn, ast.Attribute) else getattr(fn, "id", "")
            if name in BANNED_CALLS:
                return f"banned call: {name}()"
            if name in {"fail", "xfail", "skip"}:
                return f"test calls pytest.{name}()"
        if isinstance(node, ast.Assert) and isinstance(node.test, ast.Constant) and not node.test.value:
            return "test contains an unconditional failing assert"
        if isinstance(node, ast.Raise) and node.exc is not None:
            exc = node.exc.func if isinstance(node.exc, ast.Call) else node.exc
            if getattr(exc, "id", "") == "AssertionError":
                return "test raises AssertionError itself"
    if not imports_target:
        return f"test never imports {package}"
    # The test must be about the claimed code. Dunder methods (`a == b` for __eq__) and private
    # helpers are usually reached through the owning class or a public caller in the same module,
    # so naming any part of the dotted symbol, or importing the claimed module or the package root, is enough.
    parts = [p for p in claim["symbol"].split(".") if p]
    module = claim.get("file", "").removesuffix(".py").replace("/", ".")
    imported = {m for m in imported_modules if m}
    # Packages that re-export from __init__ (`from more_itertools import sample`) import the root.
    if not any(p in code for p in parts) and not ({module, package} & imported):
        return f"test never mentions {claim['symbol']} or imports {module}"
    return None


# ---------------------------------------------------------------- running pytest


@dataclass
class RunResult:
    outcome: str  # passed | failed | error | timeout | no_tests
    cases: list[dict]
    raw: str = ""


class Runner:
    def __init__(self, target_root: Path, package: str, timeout_s: int = 60,
                 isolate_network: bool = False):
        self.target_root = Path(target_root)
        self.package = package
        self.timeout_s = timeout_s
        self.prefix: list[str] = []
        if isolate_network:
            if not shutil.which("unshare"):
                raise RuntimeError("--isolate-network needs `unshare` (util-linux)")
            self.prefix = ["unshare", "-rn"]

    def workspace(self) -> Path:
        ws = Path(tempfile.mkdtemp(prefix="swarm-verify-"))
        shutil.copytree(
            self.target_root, ws / "src",
            ignore=shutil.ignore_patterns(".git", "__pycache__", "*.pyc", ".pytest_cache", ".tox"),
        )
        return ws / "src"

    def env(self, ws: Path) -> dict:
        keep = {"PATH", "HOME", "LANG", "LC_ALL", "TMPDIR", "SYSTEMROOT"}
        env = {k: v for k, v in os.environ.items() if k in keep}
        env.update(PYTHONPATH=str(ws), PYTHONHASHSEED="0", PYTHONDONTWRITEBYTECODE="1")
        return env

    def pytest(self, ws: Path, targets: list[str], timeout_s: int | None = None) -> RunResult:
        xml = ws.parent / f"junit-{os.urandom(4).hex()}.xml"
        cmd = self.prefix + [
            sys.executable, "-m", "pytest", "-q", "-p", "no:cacheprovider", "-p", "no:randomly",
            "--tb=native", f"--junitxml={xml}", "-o", "junit_family=xunit2", *targets,
        ]
        try:
            proc = subprocess.run(cmd, cwd=ws, env=self.env(ws), capture_output=True, text=True,
                                  timeout=timeout_s or self.timeout_s)
        except subprocess.TimeoutExpired:
            return RunResult("timeout", [])
        raw = proc.stdout[-4000:] + proc.stderr[-2000:]
        if not xml.exists():
            return RunResult("error", [], raw)
        cases = parse_junit(xml)
        if not cases:
            return RunResult("no_tests", [], raw)
        if any(c["state"] == "error" for c in cases):
            return RunResult("error", cases, raw)
        if any(c["state"] == "failed" for c in cases):
            return RunResult("failed", cases, raw)
        if all(c["state"] == "skipped" for c in cases):
            return RunResult("no_tests", cases, raw)
        return RunResult("passed", cases, raw)


def parse_junit(path: Path) -> list[dict]:
    cases = []
    for tc in ET.parse(path).getroot().iter("testcase"):
        state, text, msg = "passed", "", ""
        for child in tc:
            if child.tag in ("failure", "error", "skipped"):
                state = {"failure": "failed", "error": "error", "skipped": "skipped"}[child.tag]
                text = child.text or ""
                msg = child.get("message", "")
        cases.append({
            "id": f"{tc.get('classname', '')}::{tc.get('name', '')}",
            "state": state, "message": msg, "text": text,
        })
    return cases


def classify_failure(text: str, ws: Path, package: str) -> tuple[bool, str, str]:
    """Decide whether a failure traceback proves something about the target.

    Returns (accepted, signature, reason).
    """
    frames = [(Path(f), int(l), fn) for f, l, fn in FRAME_RE.findall(text)]
    exc_type = ""
    for line in reversed(text.strip().splitlines()):
        m = EXC_RE.match(line.strip())
        if m:
            exc_type = m.group(1).split(".")[-1]
            break
    if not frames:
        return False, "", "no traceback in failure output"
    pkg_dir = (ws / package).resolve()

    def in_test(p: Path) -> bool:
        return p.name == CLAIM_TEST_NAME

    def in_pkg(p: Path) -> bool:
        try:
            p.resolve().relative_to(pkg_dir)
            return True
        except ValueError:
            return False

    last_test = max((i for i, (p, _, _) in enumerate(frames) if in_test(p)), default=-1)
    if last_test < 0:
        return False, "", "failure did not come from the claim's test"
    after = [(p, l, fn) for p, l, fn in frames[last_test + 1:] if in_pkg(p)]
    if after:
        p, l, fn = after[-1]
        rel = p.resolve().relative_to(pkg_dir.parent).as_posix()
        return True, f"crash:{exc_type or '?'}@{rel}:{fn}", "exception raised inside target"
    if exc_type == "AssertionError" and last_test == len(frames) - 1:
        return True, "assert", "assertion failed in test"
    return False, "", f"{exc_type or 'exception'} raised by the test itself, not by the target"


# ---------------------------------------------------------------- verifier


class Verifier:
    def __init__(self, target_root: Path, package: str, test_paths: list[str],
                 repeats: int = 2, timeout_s: int = 60, isolate_network: bool = False,
                 check_fixes: bool = True, suite_timeout_s: int = 900):
        self.runner = Runner(target_root, package, timeout_s, isolate_network)
        self.package = package
        self.test_paths = test_paths
        self.repeats = repeats
        self.check_fixes = check_fixes
        self.suite_timeout_s = suite_timeout_s
        self.baseline_passing: set[str] | None = None

    def compute_baseline(self) -> dict:
        ws = self.runner.workspace()
        try:
            res = self.runner.pytest(ws, self.test_paths, self.suite_timeout_s)
            self.baseline_passing = {c["id"] for c in res.cases if c["state"] == "passed"}
            return {"outcome": res.outcome, "passing": len(self.baseline_passing),
                    "total": len(res.cases)}
        finally:
            shutil.rmtree(ws.parent, ignore_errors=True)

    def verify(self, claim: dict) -> Verdict:
        cid = claim.get("id") or claim_id(claim)
        reason = static_gate(claim, self.package)
        if reason:
            return Verdict(cid, "rejected", f"static: {reason}")

        ws = self.runner.workspace()
        try:
            (ws / CLAIM_TEST_NAME).write_text(claim["test_code"])
            signatures = []
            for i in range(self.repeats):
                res = self.runner.pytest(ws, [CLAIM_TEST_NAME])
                if res.outcome == "passed":
                    return Verdict(cid, "rejected", "test passes on current code")
                if res.outcome != "failed":
                    detail = res.cases[0]["text"][-600:] if res.cases else res.raw[-600:]
                    return Verdict(cid, "rejected", f"test {res.outcome}", details={"output": detail})
                failing = [c for c in res.cases if c["state"] == "failed"]
                sigs = []
                for case in failing:
                    ok, sig, why = classify_failure(case["text"], ws, self.package)
                    if not ok:
                        return Verdict(cid, "rejected", why, details={"output": case["text"][-600:]})
                    sigs.append(sig)
                signatures.append(tuple(sorted(sigs)))
            if len(set(signatures)) != 1:
                return Verdict(cid, "rejected", "flaky: failure differs between runs")
            signature = "|".join(signatures[0])
        finally:
            shutil.rmtree(ws.parent, ignore_errors=True)

        verdict = Verdict(cid, "confirmed", "reproduced", signature=signature)
        if self.check_fixes and claim.get("fix_patch", "").strip():
            verdict.fix, verdict.details["fix"] = self.check_fix(claim)
            if verdict.fix == "ok":
                verdict.status = "confirmed_fixed"
        return verdict

    def check_fix(self, claim: dict) -> tuple[str, str]:
        patch = claim["fix_patch"]
        touched = re.findall(r"^\+\+\+ (?:b/)?(\S+)", patch, re.M)
        if not touched or any(not t.startswith(self.package + "/") for t in touched):
            return "patch_touches_tests", f"patch touches {touched}; only {self.package}/ allowed"
        ws = self.runner.workspace()
        try:
            patch_file = ws.parent / "fix.patch"
            patch_file.write_text(patch if patch.endswith("\n") else patch + "\n")
            applied = subprocess.run(["git", "apply", "--recount", str(patch_file)], cwd=ws,
                                     capture_output=True, text=True)
            if applied.returncode != 0:
                return "patch_failed", applied.stderr[-400:]
            (ws / CLAIM_TEST_NAME).write_text(claim["test_code"])
            res = self.runner.pytest(ws, [CLAIM_TEST_NAME])
            if res.outcome != "passed":
                return "still_fails", f"claim test is {res.outcome} after the fix"
            if self.baseline_passing is None:
                self.compute_baseline()
            if not self.baseline_passing:
                # A suite that did not run proves nothing about the patch.
                return "no_baseline", "the target's own test suite did not run before patching"
            suite = self.runner.pytest(ws, self.test_paths, self.suite_timeout_s)
            broken = sorted(
                c["id"] for c in suite.cases
                if c["state"] != "passed" and c["id"] in (self.baseline_passing or set())
            )
            if broken or suite.outcome == "timeout":
                return "breaks_suite", ", ".join(broken[:10]) or "suite timed out"
            return "ok", ""
        finally:
            shutil.rmtree(ws.parent, ignore_errors=True)
