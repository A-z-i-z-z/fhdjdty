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

Rough cost on the API: about 900 input and 250 output tokens per call, so the
whole dataset (two passes plus tie-breaks, about 33,000 calls) is roughly
**$7 at Haiku 5.5 list price, about $3.50 with `--batch`**. Run `--plan` for an
estimate on your sample; the report gives the real token count afterwards.

On the `claude-cli` backend: it runs `claude -p --model haiku` with tools off
and a JSON schema for each record, from an empty folder. It has not yet been
run against a live subscription, so start with a small `--limit` and check
`out/report.md` for errors. Subscription usage limits apply, so keep
`--workers` modest.

## Output

Everything goes to `out/` (ignored by git):

| File | Contents |
|---|---|
| `clean.jsonl` | Kept records: original fields plus `labels` (`swarm_category`, `quality`, `issues`, `unanimous`, `possible_mislabel`) |
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

The pipeline has only been run with the mock backend so far; the container it
was built in could not reach Hugging Face. Suggested next steps:

1. Run `--backend api --limit 500` and read `out/report.md`. Canary recall and
   gold accuracy show whether the prompts are good enough.
2. Spot-check 50 kept and 50 rejected records by hand before scaling up.
3. Run the full dataset with `--batch`.
