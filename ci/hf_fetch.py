#!/usr/bin/env python3
"""Download one GGUF from a Hugging Face repo: hf_fetch.py <owner/repo> <QUANT> <out.gguf>"""
import json
import re
import sys
import time
import urllib.request

repo, quant, out = sys.argv[1], sys.argv[2].upper(), sys.argv[3]


def get(url):
    for attempt in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "yerel-ci"}), timeout=60) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001
            print(f"retry {attempt + 1}: {e}", file=sys.stderr)
            time.sleep(5 * (attempt + 1))
    raise SystemExit(f"cannot fetch {url}")


tree = json.loads(get(f"https://huggingface.co/api/models/{repo}/tree/main?recursive=true"))
files = [f["path"] for f in tree if f.get("type") == "file" and f["path"].lower().endswith(".gguf")]
files = [f for f in files if "mmproj" not in f.lower() and not re.search(r"-\d{5}-of-\d{5}\.gguf$", f)]
pick = [f for f in files if re.search(rf"(?<![A-Za-z0-9]){re.escape(quant)}(?![A-Za-z0-9])", f, re.I)]
if not pick:
    raise SystemExit(f"no {quant} file in {repo}: {files}")
path = sorted(pick, key=len)[0]
print(f"{repo}/{path}")
url = f"https://huggingface.co/{repo}/resolve/main/{path}?download=true"
for attempt in range(4):
    try:
        urllib.request.urlretrieve(url, out)
        break
    except Exception as e:  # noqa: BLE001
        print(f"download retry {attempt + 1}: {e}", file=sys.stderr)
        time.sleep(10)
else:
    raise SystemExit("download failed")
