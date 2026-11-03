# Open-data cleaning swarm (pilot)

Many cheap Claude Haiku 5.5 labelers clean, filter, label and deduplicate an
existing open dataset so it can be used to train a small model. Every label is
checked automatically, so only records that survive verification end up in the
output.

**The labelers never write training text.** They only return judgements
(a category, a 1 to 5 quality score, a list of issues, keep or drop, and a short
note). The cleaned dataset contains the original human-written text plus those
labels. This keeps the project in line with Anthropic's terms, which restrict
using Claude outputs to develop competing models.

## Dataset

[databricks-dolly-15k](https://huggingface.co/datasets/databricks/databricks-dolly-15k):
about 15,000 instruction/response pairs written by Databricks employees, in 8
task categories. License: **CC BY-SA 3.0**, so the cleaned output is shared
under the same license (the pipeline writes a `LICENSE.txt` next to it).

Why this one: it is small (13 MB), openly licensed, human-written, known to
contain noise (wrong answers, responses that rely on a missing passage,
near-duplicates), and every record already carries a human category label,
which gives a free gold standard to measure the swarm against.

## Pipeline

```
download -> rules -> hide canaries -> labeler A + labeler B -> tie-break C -> verdict -> clean.jsonl
```

1. **Rules (no model, no cost).** Unicode and whitespace normalisation,
   Wikipedia citation markers removed, empty or oversized records dropped,
   records with email addresses or phone numbers dropped, exact duplicates and
   near-duplicates (3-word shingles, Jaccard >= 0.7) removed.
2. **Two independent labelers.** Every record is labeled twice with different
   framings (A is a neutral labeler, B a skeptical auditor that sees the
   response first and the categories in reverse order), so their mistakes are
   less correlated. Neither sees the dataset's own category.
3. **Tie-break.** When A and B disagree on keep/drop or category, or one call
   failed, a third labeler (C) votes and the majority wins.
4. **Verdict.** A record is kept only if most labelers vote keep, its mean
   quality is at least 3, and no blocking issue (off-topic response, missing
   context, truncated, personal data, harmful) is flagged by a majority.

## How labels are verified

| Check | What it measures | Effect |
|---|---|---|
| **Canaries** | About 5% extra records built from real ones but broken on purpose: a response swapped in from another question, the passage removed from a passage-based task, or a response cut off. The right verdict (drop) is known. | If the swarm rejects fewer than 80% of them, the run is marked **FAIL** and exits with code 2. Canaries never reach the output. |
| **Agreement** | Cohen's kappa between labelers A and B on keep/drop and category. | Disagreements trigger a tie-break; low kappa means the prompts need work. |
| **Gold labels** | Accuracy of the swarm's category against Dolly's human category. | Reported. Records where every labeler agrees on a different category are flagged `possible_mislabel` for review. |

## Running it

Python 3.10+. From this folder:

```bash
pip install -r requirements.txt

# Free dry run with a heuristic stand-in for the model
python -m swarm_clean.pipeline --backend mock --limit 300

# See how many calls a run would make and a rough cost, without calling anything
python -m swarm_clean.pipeline --limit 0 --plan

# Real run on the Anthropic API (needs ANTHROPIC_API_KEY): 64 labelers at once
python -m swarm_clean.pipeline --backend api --limit 500 --workers 64

# Whole dataset through the Message Batches API (half price, results within 24 h)
python -m swarm_clean.pipeline --backend api --limit 0 --batch

# Use a Claude subscription's usage instead of API credits (needs `claude` logged in)
python -m swarm_clean.pipeline --backend claude-cli --limit 200 --workers 8
```

`--workers` is how many labelers run at the same time. Labels are cached in
`out/cache/labels.jsonl`, so an interrupted run picks up where it stopped and
a rerun with the same prompts costs nothing. Changing a prompt or a cleaning
rule only re-labels the records it affects.

Rough cost on the API: about 1,200 input and 650 output tokens per call
(output includes Haiku's own reasoning at `--effort low`), so the whole dataset
(two passes plus tie-breaks, about 34,000 calls) is roughly **$14 at Haiku 5.5
list price, about $7 with `--batch`**. Run `--plan` for an estimate on your
sample; the report gives the real token count afterwards.

With `--batch`, each batch id is saved in `out/cache/batches.json` as soon as
it is submitted. If the run is stopped while a batch is processing, running the
same command again picks that batch up instead of submitting (and paying for)
it twice. Labels already in the cache are never sent again.

On the `claude-cli` backend: it runs `claude -p --model haiku` with tools off
and a JSON schema for each record, from an empty folder, and uses the Claude
plan's usage instead of API credits. Each call carries the CLI's own scaffolding,
so it costs about $0.0008 at list price (roughly 3x a direct API call) and is
CPU-bound at 1 to 2 calls a second on a 4-CPU machine, whatever `--workers` is.

## Output

Everything goes to `out/` (ignored by git):

| File | Contents |
|---|---|
| `clean.jsonl` | Kept records with mean quality >= `--min-quality` (default 3.5): original fields plus `labels` (`swarm_category`, `quality`, `issues`, `unanimous`, `possible_mislabel`) |
| `clean_lenient.jsonl` | Same, at the lower `--lenient-quality` bar (default 3.0); a superset of `clean.jsonl` |
| `rejected.jsonl` | Dropped records with the stage (`rules` or `swarm`) and reasons |
| `canaries.jsonl` | The hidden canaries and how the swarm judged each one |
| `report.md`, `report.json` | Counts, canary recall, kappa, gold accuracy, reject reasons, tokens and cost |
| `cache/labels.jsonl` | Every raw label, for auditing and resuming |

## Tests

```bash
python -m unittest discover -s tests -v
```

The tests run fully offline on a small synthetic fixture
(`tests/fixtures/sample.jsonl`, written for the tests, not taken from Dolly).
They cover the rules, canaries, voting, schema validation, cache resume, the
tie-break path, and a run that must fail when the labelers miss the canaries.

## Status and next steps

Live runs so far, all on the `claude-cli` backend (Haiku 5.5, subscription usage):

| Run | Kept | Canary recall | Kappa keep / category | Category vs Dolly | Cost at API list price |
|---|---|---|---|---|---|
| 200 records, 8 workers, prompts v1 | 128 (64%) | 10/10 | 0.65 / 0.75 | 54.5% | about $0.40 |
| 2,000 records, 100 workers, prompts v1 | 1,311 (66%) | 99/100 | 0.72 / 0.83 | 60.4% | about $3.95 |
| 2,000 records, 100 workers, prompts v2 | 1,666 (83%) | 99/100 | 0.73 / 0.81 | 62.8% | about $4.30 |
| Full dataset (15,011), 64 workers, prompts v2 | 9,365 at q>=3.5, 12,233 at q>=3.0 | 737/747 | 0.71 / 0.80 | 63.0% | about $30 |

With v1 the labelers dropped about a third of Dolly, mostly for reasons that
should not disqualify a training example: missing extra detail, typos, facts
they could not verify, and "most recent" judged against today rather than
early 2023. Prompts v2 fix that. In a hand check of records v2 keeps and v1
dropped, most were fine, but about a third of those with a mean quality of
exactly 3.0 were doubtful, so `clean.jsonl` now uses 3.5 by default (1,248 of
the 2,000 records) and `clean_lenient.jsonl` keeps the 3.0 set (1,666).

The full run took about 4.5 hours of labeling in three resumed slices (34,800
calls, no errors). Of its 10 missed canaries, most are canaries that stay
answerable without the removed passage (for example a summary of a well-known
children's book), so the true recall is higher than the 98.7% reported.

The one missed canary in the 2,000 runs was itself a bad canary (a passage
removed from a question that does not need one); the maker's passage check was
tightened afterwards.

100 workers on a 4-CPU container: about 11 GB of RAM, but throughput stays at
about 1 to 2 calls a second because each `claude -p` start is CPU-bound. 200
calls timed out in the first 100-worker run; the backend now retries a timeout
once. For thousands of agents, the API backend is the better route.

Suggested next steps:

1. Decide whether 3.5 (default) or 3.0 suits the target model; both sets are written.
2. Decide whether the closed_qa / information_extraction / summarization split
   matters; most category "errors" sit on that boundary.
3. Spot-check a sample of `clean.jsonl` and `rejected.jsonl` from the full run.
