"""Target codebase handling: load config, fetch a pinned checkout, split it into work units."""

from __future__ import annotations

import ast
import fnmatch
import json
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

BUG_HUNTING_DIR = Path(__file__).resolve().parent.parent


@dataclass
class TargetConfig:
    name: str
    package: str
    source_globs: list[str]
    repo: str | None = None
    ref: str | None = None
    commit: str | None = None
    local_path: str | None = None
    exclude_globs: list[str] = field(default_factory=list)
    test_paths: list[str] = field(default_factory=lambda: ["tests"])
    max_unit_lines: int = 300

    @classmethod
    def load(cls, path: str | Path) -> "TargetConfig":
        data = json.loads(Path(path).read_text())
        data.pop("_comment", None)
        cfg = cls(**data)
        if cfg.local_path and not Path(cfg.local_path).is_absolute():
            cfg.local_path = str((Path(path).resolve().parent / cfg.local_path).resolve())
        return cfg


@dataclass
class WorkUnit:
    unit_id: str
    file: str  # path relative to the checkout root
    symbols: list[str]
    start_line: int
    end_line: int


def prepare_checkout(cfg: TargetConfig, cache_dir: Path) -> Path:
    """Return a directory holding the target source at the pinned commit."""
    if cfg.local_path:
        return Path(cfg.local_path)
    if not (cfg.repo and cfg.ref):
        raise ValueError("config needs either local_path or repo + ref")
    dest = cache_dir / f"{cfg.name}-{cfg.ref}"
    if not dest.exists():
        dest.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(
            ["git", "clone", "--quiet", "--depth", "1", "--branch", cfg.ref, cfg.repo, str(dest)],
            check=True,
        )
    if cfg.commit:
        head = subprocess.run(
            ["git", "-C", str(dest), "rev-parse", "HEAD"], capture_output=True, text=True, check=True
        ).stdout.strip()
        if head != cfg.commit:
            raise RuntimeError(f"{dest} is at {head}, config pins {cfg.commit}")
    return dest


def source_files(cfg: TargetConfig, root: Path) -> list[str]:
    files: set[str] = set()
    for pattern in cfg.source_globs:
        for p in root.glob(pattern):
            rel = p.relative_to(root).as_posix()
            if p.is_file() and not any(fnmatch.fnmatch(rel, ex) for ex in cfg.exclude_globs):
                files.add(rel)
    return sorted(files)


def split_file(root: Path, rel: str, max_lines: int) -> list[WorkUnit]:
    """Group a module's top-level functions and classes into chunks of at most max_lines.

    A single definition longer than max_lines becomes its own unit. Module-level code that is
    not a def or class is visible to every agent through the full-file context.
    """
    src = (root / rel).read_text()
    try:
        tree = ast.parse(src)
    except SyntaxError:
        return []
    defs = [
        n
        for n in tree.body
        if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef))
    ]
    units: list[WorkUnit] = []
    group: list[ast.stmt] = []

    def flush() -> None:
        if not group:
            return
        names = [g.name for g in group]  # type: ignore[attr-defined]
        start = min(g.lineno for g in group)
        end = max(g.end_lineno or g.lineno for g in group)
        units.append(WorkUnit(f"{rel}::{names[0]}", rel, names, start, end))
        group.clear()

    for node in defs:
        size = (node.end_lineno or node.lineno) - node.lineno + 1
        current = sum((g.end_lineno or g.lineno) - g.lineno + 1 for g in group)
        if group and current + size > max_lines:
            flush()
        group.append(node)
    flush()
    return units


def make_units(cfg: TargetConfig, root: Path) -> list[WorkUnit]:
    units: list[WorkUnit] = []
    for rel in source_files(cfg, root):
        units.extend(split_file(root, rel, cfg.max_unit_lines))
    return units
