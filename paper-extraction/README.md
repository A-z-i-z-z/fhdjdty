# Paper extraction swarm (pilot)

One Haiku agent reads one paper and pulls every reported ionic conductivity into a fixed
schema. A program then checks each record mechanically against the paper text. A second wave
of Haiku agents re-reads each paper and judges whether the surviving records mean what they
claim. Only records that pass both end up in the table, and each one carries a verbatim quote
so a person can check it in seconds.

## The field: solid-state electrolytes, ionic conductivity

The pilot targets **solid lithium (and sodium) ion conductors**: garnets (LLZO), NASICONs
(LATP, LAGP), sulfide argyrodites, halides and so on. The headline number in almost every
paper is the ionic conductivity at room temperature, with the composition, the dopants, how
it was made and how dense the pellet was.

Why this field:

- **The key number is easy to check by machine.** Conductivity is always a value and a unit
  (`4.8 × 10−4 S cm−1`, `1.2 mS/cm`), so a parser can turn it into S/cm and reject anything
  outside the physically possible range. Activation energy, sintering temperature and density
  are plain numbers the checker can look for in the text.
- **It is a real gap.** Solid-state batteries are a hot topic and ML screening of electrolytes
  is limited by small, hand-curated datasets (a few hundred to a few thousand entries).
  Thousands of papers report these numbers in prose and tables that nobody has pooled.
- **The open-access supply is large.** arXiv `cond-mat.mtrl-sci` alone has thousands of
  solid-electrolyte papers, and many more are open access in PubMed Central and MDPI.
- **The traps are well known**, which makes a good test of the verification wave: values
  quoted from other papers in the introduction, bulk versus total conductivity, simulated
  versus measured values, and table rows that shift during PDF extraction.

Perovskite solar cells would have been the obvious alternative, but a large community
database for them already exists, so a new one would add less.

## Schema

One row per reported conductivity of one material. Full definitions are in
[`schema/extraction.schema.json`](schema/extraction.schema.json).

| Group | Fields |
|---|---|
| Material | `material_as_written`, `material_class` (garnet, NASICON, argyrodite, halide, …), `mobile_ion`, `dopants` |
| Origin | `data_origin`: `experimental`, `computational`, or `literature_cited` (a value the paper quotes from someone else) |
| Processing | `synthesis_method`, `sintering_temperature_C`, `sintering_time_h`, `relative_density_pct` |
| Result | `conductivity_value_as_reported`, `conductivity_unit_as_reported`, `conductivity_S_per_cm` (computed by code, never by the model), `log10_conductivity`, `conductivity_kind` (total, bulk, grain boundary) |
| Conditions | `measurement_temperature_as_reported`, `measurement_temperature_C` (null for "room temperature"), `activation_energy_eV`, `measurement_method` |
| Evidence | `evidence_quote` (verbatim), `unit_evidence_quote` (for table cells whose unit is in the header), `evidence_location` |
| Provenance | `record_id`, `paper_id`, `title`, `year`, `doi`, `source_url`, `status`, `corrections`, `warnings` |

Design choices worth knowing:

- **The model copies, the code converts.** The model returns the value and unit exactly as
  printed. The code parses them into S/cm. This keeps unit mistakes out of the data and makes
  the "is it really in the paper" check possible.
- **Nulls over guesses.** "Room temperature" is kept as text with a null number, rather than
  silently becoming 25 °C.
- **Cited values are kept but labelled**, so they can be filtered out of the dataset or used
  to cross-check other papers.

## Pipeline

```
fetch ──► extract (Haiku, 1 per paper) ──► check (code) ──► verify (Haiku, 1 per paper) ──► table
```

1. **fetch** (`pipeline/fetch.py`): takes arXiv ids from [`seeds.txt`](seeds.txt), tops up to
   `--n` papers with an arXiv search, downloads the PDFs, runs `pdftotext`, and drops the
   reference list. `add` registers a PDF or text file you already have.
2. **extract** (`prompts/extract.md`): one headless `claude -p --model haiku` call per paper,
   with tools switched off and the JSON schema enforced.
3. **check** (`pipeline/check.py`): no model. A record is rejected unless its quote is a
   verbatim span of the paper (ignoring whitespace and PDF hyphenation), the value is inside
   the quote, the unit is shown verbatim, the value parses, and it lies between 1e-14 and
   1e-1 S/cm. Secondary numbers that do not appear anywhere in the paper become warnings for
   the verifier.
4. **verify** (`prompts/verify.md`): a fresh Haiku call per paper sees the paper, the
   surviving records and the checker's warnings, and returns `supported`,
   `supported_with_corrections` or `unsupported` for each. Wrong material, value, temperature
   or origin means rejection. A correction to a secondary field is applied only if its own
   evidence quote is found verbatim in the paper.
5. **table** (`pipeline/table.py`): writes `records.csv`, `rejected.csv` (with the reason)
   and `report.md` (counts and token use) in the work directory.

Every stage is resumable: a paper with an existing output file is skipped, so a crashed or
rate-limited swarm can just be run again.

### Why `claude -p` and not the API

The swarm runs through the Claude Code CLI, so it uses the signed-in Claude plan's usage
rather than API credits. Each call has tools disabled (`--tools ""`), a short custom system
prompt, no MCP servers, and runs in an empty temporary directory, so a paper cannot make the
agent do anything other than return JSON.

## Running it

Needs Python 3.10+, `pdftotext` (poppler-utils) or `pip install pypdf`, a signed-in `claude`
CLI, and network access to `arxiv.org` and `export.arxiv.org`.

```bash
cd paper-extraction
python3 -m pipeline.cli fetch --n 15          # 4 seed papers + 11 from the arXiv search
python3 -m pipeline.cli run --concurrency 8   # extract, check, verify, table
cat data/pilot/report.md
```

Output goes to `data/pilot/` (override with `--workdir`). PDFs and raw text are git-ignored;
the extractions, verdicts and CSVs are small enough to commit.

Tests for the checker (no network, no model):

```bash
python3 -m unittest discover -s tests -t .
```

## What has been tested so far

### 100-paper run (2026-11-02)

`fetch --n 100` then `run --concurrency 16`, outputs in [`data/run100/`](data/run100/). The
arXiv search for the default query ran out at 82 results; one PDF download broke, so 81
papers were processed (54 in scope). arXiv started answering HTTP 429 after ~30 PDFs in a
row, so `fetch` now retries with backoff and skips a paper whose download fails.

- 163 records extracted, **141 kept (87%)**: 51 experimental, 33 computational, 57
  `literature_cited`. The checker rejected 12, the verifier rejected 10 and corrected 6.
- The first pass kept only 126: the unit parser did not accept `S • cm-1`, `S.cm-1` or
  `mS.cm−1`, which one review paper uses throughout. Fixed in `check.py` (with tests) and the
  three affected papers re-verified.
- What the verifier rejected: an unsupported or missing measurement temperature (6), a value
  tied to the wrong or an unnamed composition (2), a range collapsed into a single value, and
  a bulk/total mix-up. At least two of the temperature cases had the right value and
  material and could have been corrected rather than rejected.
- About 6.2M input and 0.31M output tokens (≈ 80k per paper), notional API price $1.01.

**Hand spot check** of 30 random kept records against the paper text (seed 42): material,
value, unit and conductivity kind were right in **30/30**. Data origin was wrong in **2/30**:
both from one paper (arXiv:2503.11941) that recalls computed values from the authors' own
earlier paper and were labelled `computational` instead of `literature_cited`. Secondary
fields checked (activation energy, density, sintering temperature) were right. One
systematic gap: 7 kept records report "300 K" but leave `measurement_temperature_C` null;
converting Kelvin should move into code, like the conductivity units.

So on this sample about 93% of kept records are fully right and 100% have the right number
for the right material. The verifier is not just rubber-stamping (it rejected 10 and
corrected 6), but it misses "cited from our own earlier work".

### Real 15-paper pilot (2026-11-02)

Run with `fetch --n 15` and `run --concurrency 8` on Haiku. Full outputs are in
[`data/pilot/`](data/pilot/) ([`report.md`](data/pilot/report.md),
[`records.csv`](data/pilot/records.csv), [`rejected.csv`](data/pilot/rejected.csv)); the
paper PDFs and text are git-ignored because they are not ours to redistribute.

- 15 papers fetched (4 seeds + 11 from the search); the extractor judged 12 in scope. The
  three out of scope were theory or simulation papers with no conductivity value.
- 31 records extracted, **30 kept** (29 verified, 1 verified with a correction), 27
  experimental and 3 labelled `literature_cited`.
- The checker rejected 1: a cited value of `10−12 mS/cm` (an upper bound from another
  dataset) that converts to 1e-15 S/cm, below the plausible range.
- The verifier corrected 1 (dropped an unsupported sintering temperature) and rejected none.
  It did handle a PDF artefact correctly: `250C` in one paper is "25 °C" with the degree sign
  lost, and the record's 25 °C was kept.
- About 0.9M input and 58k output tokens in total, roughly 60k tokens per paper across both
  waves. The CLI's notional API-price figure was $0.16; on a Claude plan it is plan usage.

Caveat: a verifier that rejects nothing on real papers is either facing a good extractor or
being too lenient. The hand check of 30 kept and 30 rejected records is still needed to tell
which; there are too few rejections here for the second half of that check.

### Before the real run


What was tested on the synthetic paper and unit tests:

- **Checker unit tests** (8 tests): unit parsing across ten notations, verbatim matching
  across PDF line-break hyphens, rejection of paraphrased quotes, values missing from the
  quote, units not shown, and out-of-range values from lost superscript minus signs.
- **End to end with real Haiku calls on a synthetic paper**
  ([`tests/fixtures/synthetic_garnet.txt`](tests/fixtures/synthetic_garnet.txt), invented
  numbers, see its README). The extractor produced 5 records, all correct, including the
  three table rows, the bulk value, and the cited value labelled `literature_cited`. It
  skipped the electronic conductivity and the figure-only claim.
- **Verifier on planted errors.** Three records were corrupted by hand before the verify
  stage: a shifted table row, a cited value relabelled `experimental`, and a wrong sintering
  temperature. The verifier rejected the first two and corrected the third (1250 → 1150 °C).
- One extract plus verify pass on that short paper used about 21k input and 7k output tokens.

One synthetic paper is a smoke test, not a precision estimate. The real pilot should end with
a person hand-checking a random 30 kept records and 30 rejected ones to measure precision and
how much the verifier throws away.

## Scaling notes

- A typical arXiv paper is 30–80k characters, roughly 10–25k tokens. Expect about 40–60k
  tokens per paper across both waves, so 1,000 papers is on the order of 50M tokens of Haiku.
- `--concurrency` controls parallel `claude` processes. Start at 8 and raise it until the plan's
  rate limit starts returning errors; failed papers are retried on the next run.
- Beyond arXiv, the same `add` command takes PDFs from PubMed Central's open-access subset.
- Next steps after the pilot: deduplicate the same material reported by several papers,
  normalise compositions into element fractions, and add a third "disagreement" wave only for
  records where extractor and verifier disagree.
