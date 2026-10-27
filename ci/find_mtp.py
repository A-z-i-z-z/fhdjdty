#!/usr/bin/env python3
"""Find and download a small GGUF that carries MTP (nextn) layers: find_mtp.py <out.gguf>

Searches Hugging Face for small Qwen3.5 GGUF uploads and reads only the header of each candidate
(HTTP range request) to check for blk.N.nextn.* tensors before downloading it."""
import json
import re
import struct
import sys
import time
import urllib.request

out = sys.argv[1]
SEARCHES = ["Qwen3.5-0.8B", "Qwen3.5-2B"]
QUANTS = ["Q8_0", "Q4_0", "Q4_K_M"]
HEADER_BYTES = 24 * 1024 * 1024


def get(url, headers=None):
    hdrs = {"User-Agent": "yerel-ci"}
    hdrs.update(headers or {})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=hdrs), timeout=90) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001
            print(f"retry {attempt + 1}: {url}: {e}", file=sys.stderr)
            time.sleep(4 * (attempt + 1))
    return None


class Reader:
    def __init__(self, data):
        self.d = data
        self.p = 0

    def take(self, n):
        if self.p + n > len(self.d):
            raise EOFError
        b = self.d[self.p:self.p + n]
        self.p += n
        return b

    def u32(self):
        return struct.unpack("<I", self.take(4))[0]

    def u64(self):
        return struct.unpack("<Q", self.take(8))[0]

    def string(self):
        return self.take(self.u64()).decode("utf-8", "replace")


SCALAR = {0: 1, 1: 1, 2: 2, 3: 2, 4: 4, 5: 4, 6: 4, 7: 1, 10: 8, 11: 8, 12: 8}


def skip_value(r, t):
    if t in SCALAR:
        r.take(SCALAR[t])
    elif t == 8:
        r.string()
    elif t == 9:
        et = r.u32()
        n = r.u64()
        if et in SCALAR:
            r.take(SCALAR[et] * n)
        else:
            for _ in range(n):
                skip_value(r, et)
    else:
        raise ValueError(f"bad gguf type {t}")


def tensor_names(data):
    r = Reader(data)
    if r.take(4) != b"GGUF":
        return None
    r.u32()
    n_tensors = r.u64()
    n_kv = r.u64()
    for _ in range(n_kv):
        r.string()
        skip_value(r, r.u32())
    names = []
    for _ in range(n_tensors):
        names.append(r.string())
        n_dims = r.u32()
        r.take(8 * n_dims + 4 + 8)
    return names


def main():
    seen = set()
    for q in SEARCHES:
        raw = get(f"https://huggingface.co/api/models?search={q}&filter=gguf&limit=40")
        if not raw:
            continue
        for m in json.loads(raw):
            repo = m.get("id") or m.get("modelId")
            if not repo or repo in seen:
                continue
            seen.add(repo)
            tree = get(f"https://huggingface.co/api/models/{repo}/tree/main?recursive=true")
            if not tree:
                continue
            files = [f["path"] for f in json.loads(tree) if f.get("type") == "file" and f["path"].lower().endswith(".gguf")]
            files = [f for f in files if "mmproj" not in f.lower() and not re.search(r"-\d{5}-of-\d{5}\.gguf$", f)]
            for quant in QUANTS:
                pick = [f for f in files if re.search(rf"(?<![A-Za-z0-9]){re.escape(quant)}(?![A-Za-z0-9])", f, re.I)]
                if not pick:
                    continue
                path = sorted(pick, key=len)[0]
                url = f"https://huggingface.co/{repo}/resolve/main/{path}"
                head = get(url, {"Range": f"bytes=0-{HEADER_BYTES - 1}"})
                try:
                    names = tensor_names(head) if head else None
                except (EOFError, ValueError):
                    names = None
                has = bool(names) and any(".nextn." in n for n in names)
                print(f"{repo}/{path}: {'MTP' if has else 'no MTP' if names is not None else 'unreadable'}")
                if has:
                    for attempt in range(3):
                        try:
                            urllib.request.urlretrieve(url + "?download=true", out)
                            print(f"downloaded {repo}/{path}")
                            return 0
                        except Exception as e:  # noqa: BLE001
                            print(f"download retry {attempt + 1}: {e}", file=sys.stderr)
                            time.sleep(10)
                break
    print("no GGUF with MTP layers found")
    return 1


sys.exit(main())
