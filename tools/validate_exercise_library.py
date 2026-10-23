#!/usr/bin/env python3
"""Validate the bundled exercise catalog, its stable baseline, and focus audit."""

import json
import re
import uuid
from collections import Counter
from pathlib import Path

from jsonschema import Draft202012Validator


ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "app/assets/relven/exercise-library-v1.json"
SCHEMA = ROOT / "schemas/exercise-library-v1.schema.json"
BASELINE = ROOT / "app/test/resources/exercise-library-v1-baseline.json"
AUDIT = ROOT / "app/test/resources/exercise-library-v2-focus-audit.json"
EXPECTED = {
    "Hamstrings": 12,
    "Groin": 12,
    "Lower leg": 14,
    "Functional lower body": 16,
    "Core": 14,
    "Shoulder": 14,
    "Plyometrics": 10,
    "Mobility": 8,
}


def read(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


catalog = read(ASSET)
schema = read(SCHEMA)
Draft202012Validator.check_schema(schema)
errors = sorted(Draft202012Validator(schema).iter_errors(catalog), key=lambda e: list(map(str, e.path)))
if errors:
    raise SystemExit("Schema validation failed:\n" + "\n".join(f"- {e.json_path}: {e.message}" for e in errors))

original = read(BASELINE)
original_by_id = {entry["exerciseId"]: entry for entry in original["exercises"]}
catalog_by_id = {entry["exerciseId"]: entry for entry in catalog["exercises"]}
assert catalog["schemaVersion"] == 1
assert catalog["libraryRevision"] == 2
assert len(catalog["exercises"]) == 116
assert len(catalog["exercises"]) <= 256
assert len(catalog_by_id) == len(catalog["exercises"])
assert all(catalog_by_id[eid] == entry for eid, entry in original_by_id.items())

added = [entry for entry in catalog["exercises"] if entry["exerciseId"] not in original_by_id]
assert len(added) == 100
for entry in catalog["exercises"]:
    parsed = uuid.UUID(entry["exerciseId"])
    assert str(parsed) == entry["exerciseId"]
for entry in added:
    assert uuid.UUID(entry["exerciseId"]).version == 4
    assert entry["revision"] == 1
    assert entry["equipment"] == []
    assert set(entry["name"]) == {"sv", "en"}
    assert set(entry["instructions"]) == {"sv", "en"}
    for text in [*entry["name"].values(), *(step for steps in entry["instructions"].values() for step in steps)]:
        assert text and text == text.strip() and not re.search(r"\s{2,}", text)

names_en = [e["name"]["en"].casefold() for e in catalog["exercises"]]
names_sv = [e["name"]["sv"].casefold() for e in catalog["exercises"]]
assert len(names_en) == len(set(names_en))
assert len(names_sv) == len(set(names_sv))

audit = read(AUDIT)
assert set(audit) == set(EXPECTED)
assert {focus: len(ids) for focus, ids in audit.items()} == EXPECTED
assert len({eid for ids in audit.values() for eid in ids}) == 100
assert {eid for ids in audit.values() for eid in ids} == {e["exerciseId"] for e in added}
print("Exercise catalog valid: schema v1, library revision 2, 116 total, 100 new, all additions equipment-free.")
print("Focus counts:", dict(Counter({focus: len(ids) for focus, ids in audit.items()})))
