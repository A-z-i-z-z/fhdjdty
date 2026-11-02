"""Stage 0: pick open-access papers from arXiv and turn each PDF into plain text.

Needs outbound access to export.arxiv.org and arxiv.org.
"""
from __future__ import annotations

import json
import re
import shutil
import subprocess
import time
import urllib.parse
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

from .common import Workdir

ARXIV_API = "https://export.arxiv.org/api/query"
ATOM = {"a": "http://www.w3.org/2005/Atom", "arxiv": "http://arxiv.org/schemas/atom"}
UA = {"User-Agent": "paper-extraction-pilot/0.1 (research dataset; contact via GitHub)"}

# Experimental solid-electrolyte papers that report conductivity numbers in the text.
DEFAULT_QUERY = (
    'abs:"solid electrolyte" AND abs:"ionic conductivity" AND cat:cond-mat.mtrl-sci'
)


def _get(url: str, timeout: int = 60, retries: int = 5) -> bytes:
    """GET with backoff: arXiv answers bursts with 429/503 (seen at ~30 PDFs in a row)."""
    req = urllib.request.Request(url, headers=UA)
    for attempt in range(retries + 1):
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code not in (429, 500, 502, 503) or attempt == retries:
                raise
            wait = int(e.headers.get("Retry-After") or 0) or 15 * 2 ** attempt
            print(f"  HTTP {e.code}, retrying in {wait}s")
            time.sleep(wait)


def arxiv_metadata(ids: list[str] | None = None, query: str | None = None, max_results: int = 20) -> list[dict]:
    params = {"max_results": str(max_results)}
    if ids:
        params["id_list"] = ",".join(ids)
    if query:
        params["search_query"] = query
        params["sortBy"] = "relevance"
    root = ET.fromstring(_get(ARXIV_API + "?" + urllib.parse.urlencode(params)))
    out = []
    for e in root.findall("a:entry", ATOM):
        abs_url = e.findtext("a:id", default="", namespaces=ATOM)
        m = re.search(r"abs/([^v]+)(v\d+)?$", abs_url)
        if not m:
            continue
        doi = e.findtext("arxiv:doi", default=None, namespaces=ATOM)
        lic = e.find("arxiv:license", ATOM)
        out.append({
            "paper_id": f"arXiv:{m.group(1)}",
            "arxiv_id": m.group(1),
            "title": " ".join((e.findtext("a:title", default="", namespaces=ATOM)).split()),
            "authors": [a.findtext("a:name", default="", namespaces=ATOM) for a in e.findall("a:author", ATOM)],
            "published": e.findtext("a:published", default="", namespaces=ATOM)[:10],
            "doi": doi,
            "license": lic.get("href") if lic is not None else None,
            "abs_url": f"https://arxiv.org/abs/{m.group(1)}",
            "pdf_url": f"https://arxiv.org/pdf/{m.group(1)}",
        })
    return out


def strip_references(text: str) -> str:
    """Drop the bibliography: it costs tokens and only adds literature-cited noise."""
    heads = list(re.finditer(r"(?im)^\s*(references|bibliography|references and notes)\s*$", text))
    if heads and heads[-1].start() > len(text) * 0.5:
        return text[: heads[-1].start()]
    return text


def pdf_to_text(pdf_path, txt_path) -> None:
    if shutil.which("pdftotext"):
        subprocess.run(["pdftotext", "-enc", "UTF-8", str(pdf_path), str(txt_path)], check=True)
        return
    from pypdf import PdfReader  # fallback: pip install pypdf

    reader = PdfReader(str(pdf_path))
    txt_path.write_text("\n".join(p.extract_text() or "" for p in reader.pages))


def fetch(wd: Workdir, seeds: list[str], query: str | None, n: int) -> list[str]:
    wd.ensure()
    metas = arxiv_metadata(ids=seeds, max_results=len(seeds)) if seeds else []
    have = {m["arxiv_id"] for m in metas}
    if query and len(metas) < n:
        time.sleep(3)  # arXiv asks for 3 s between API calls
        for m in arxiv_metadata(query=query, max_results=n * 2):
            if len(metas) >= n:
                break
            if m["arxiv_id"] not in have:
                metas.append(m)
                have.add(m["arxiv_id"])
    fetched = []
    for m in metas[:n]:
        d = wd.paper_dir(m["paper_id"])
        d.mkdir(parents=True, exist_ok=True)
        pdf, txt = d / "paper.pdf", d / "text.txt"
        if not txt.exists():
            print(f"fetch {m['paper_id']}  {m['title'][:70]}")
            try:
                pdf.write_bytes(_get(m["pdf_url"], timeout=120))
                raw = d / "raw.txt"
                pdf_to_text(pdf, raw)
            except Exception as e:  # one bad download should not stop the batch
                print(f"  skip {m['paper_id']}: {e}")
                continue
            txt.write_text(strip_references(raw.read_text(errors="replace")))
            time.sleep(3)
        m["text_chars"] = len(txt.read_text())
        (d / "meta.json").write_text(json.dumps(m, indent=2))
        fetched.append(m["paper_id"])
    return fetched


def add_local(wd: Workdir, paper_id: str, title: str, path, source_url: str | None = None) -> str:
    """Register a paper you already have as a PDF or .txt (e.g. from PubMed Central)."""
    from pathlib import Path

    path = Path(path)
    wd.ensure()
    d = wd.paper_dir(paper_id)
    d.mkdir(parents=True, exist_ok=True)
    if path.suffix.lower() == ".pdf":
        shutil.copy(path, d / "paper.pdf")
        pdf_to_text(d / "paper.pdf", d / "raw.txt")
        raw = (d / "raw.txt").read_text(errors="replace")
    else:
        raw = path.read_text(errors="replace")
    (d / "text.txt").write_text(strip_references(raw))
    meta = {"paper_id": paper_id, "title": title, "abs_url": source_url, "doi": None,
            "published": None, "license": None, "text_chars": len((d / "text.txt").read_text())}
    (d / "meta.json").write_text(json.dumps(meta, indent=2))
    return paper_id
