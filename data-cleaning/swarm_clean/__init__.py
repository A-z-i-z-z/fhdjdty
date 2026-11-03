"""Swarm data-cleaning pilot: cheap parallel labelers clean, filter and label an
existing open dataset. The labelers only judge records; they never write
training text."""

DOLLY_CATEGORIES = [
    "open_qa",
    "general_qa",
    "classification",
    "closed_qa",
    "brainstorming",
    "information_extraction",
    "summarization",
    "creative_writing",
]
