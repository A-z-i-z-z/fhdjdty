"""Labeler prompts and the output schema.

Two independent framings (A and B) label every record. They differ in wording,
field order and category order so that their errors are less correlated; a
record only passes on agreement. Neither prompt shows the dataset's own human
category, which keeps the gold-label accuracy check honest.

The schema only allows judgements (enums, numbers, a short note). The labelers
are told never to rewrite or produce training text, and nothing they write is
copied into the cleaned dataset except the labels themselves.
"""

from __future__ import annotations

from . import DOLLY_CATEGORIES

PROMPT_VERSION = "v1"

ISSUES = [
    "response_incorrect",
    "response_off_topic",
    "instruction_unclear",
    "needs_missing_context",
    "truncated_or_incomplete",
    "contains_personal_data",
    "harmful_or_toxic",
    "low_effort",
    "formatting_noise",
    "not_english",
]

# Issues that make a record unusable no matter what the quality score says.
HARD_ISSUES = {
    "response_off_topic",
    "needs_missing_context",
    "truncated_or_incomplete",
    "contains_personal_data",
    "harmful_or_toxic",
}

LABEL_SCHEMA = {
    "type": "object",
    "properties": {
        "category": {"type": "string", "enum": DOLLY_CATEGORIES},
        "quality": {"type": "integer", "enum": [1, 2, 3, 4, 5]},
        "issues": {"type": "array", "items": {"type": "string", "enum": ISSUES}},
        "keep": {"type": "boolean"},
        "note": {"type": "string"},
    },
    "required": ["category", "quality", "issues", "keep", "note"],
    "additionalProperties": False,
}

CATEGORY_GUIDE = {
    "open_qa": "a factual question answered from general knowledge, no passage given",
    "general_qa": "an open-ended or advice-style question with no single right answer",
    "classification": "sort or label given items into classes",
    "closed_qa": "answer a question using only the given passage",
    "brainstorming": "generate a list of ideas or options",
    "information_extraction": "pull specific facts out of the given passage",
    "summarization": "summarise the given passage",
    "creative_writing": "write a story, poem, letter or other creative text",
}

RULES = """Rules:
- You are a data reviewer. Only judge the record. Never rewrite, improve or
  continue the instruction or the response, and never write an answer yourself.
- quality: 5 = correct, complete, helpful; 3 = usable with minor flaws;
  1 = wrong, empty or useless.
- issues: list every issue that applies, or an empty list.
- needs_missing_context: the instruction refers to a passage, text, article or
  reference that is not provided.
- keep: true only if this pair would be a good, safe training example as is.
- note: at most 15 words saying why. Plain words, no quotes from the record."""


def _categories(order: list[str]) -> str:
    return "\n".join(f"- {c}: {CATEGORY_GUIDE[c]}" for c in order)


SYSTEM_A = (
    "You review instruction/response pairs from an open dataset and label them.\n\n"
    "Categories:\n" + _categories(DOLLY_CATEGORIES) + "\n\n" + RULES
)

SYSTEM_B = (
    "You are a strict quality auditor for a training-data cleaning project. "
    "Assume a record may be broken: the response may belong to a different "
    "question, be cut off, or rely on a missing passage. Check before you trust.\n\n"
    "Task types:\n" + _categories(list(reversed(DOLLY_CATEGORIES))) + "\n\n" + RULES
)

SYSTEM_TIEBREAK = (
    "Two reviewers disagreed about this instruction/response pair. Give your own "
    "independent judgement.\n\nCategories:\n" + _categories(sorted(DOLLY_CATEGORIES))
    + "\n\n" + RULES
)


def _block(tag: str, text: str) -> str:
    return f"<{tag}>\n{text if text else '(none)'}\n</{tag}>"


def user_message(record: dict, framing: str) -> str:
    instr = _block("instruction", record["instruction"])
    ctx = _block("context", record["context"])
    resp = _block("response", record["response"])
    if framing == "B":
        body = "\n\n".join([resp, instr, ctx])
        return "Audit this record (response shown first).\n\n" + body
    return "Label this record.\n\n" + "\n\n".join([instr, ctx, resp])


SYSTEMS = {"A": SYSTEM_A, "B": SYSTEM_B, "C": SYSTEM_TIEBREAK}
