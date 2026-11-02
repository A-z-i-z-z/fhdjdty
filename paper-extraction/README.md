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

The cloud environment this was built in cannot reach arXiv, PubMed Central or Crossref (its
network policy blocks them), so **the real 15-paper run has not happened yet**. What was
tested:

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
