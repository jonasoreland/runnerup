"""Build the offline AI Plan Kit from Relven's canonical schemas."""

import argparse
import json
from pathlib import Path
import sys
import zipfile


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app" / "assets" / "relven" / "relven-ai-plan-kit-v2.zip"
PREFIX = "relven-ai-plan-kit/"
FILES = {
    "README.md": "docs/ai-plan-kit/README.md",
    "INSTRUCTIONS.md": "docs/ai-plan-kit/INSTRUCTIONS.md",
    "capabilities.json": "docs/ai-plan-kit/capabilities.json",
    "JSON-FORMAT.md": "docs/ai-plan-kit/JSON-FORMAT.md",
    "schemas/plan-v1.schema.json": "schemas/plan-v1.schema.json",
    "schemas/plan-v2.schema.json": "schemas/plan-v2.schema.json",
    "schemas/workout-v3.schema.json": "schemas/workout-v3.schema.json",
    "schemas/exercise-library-v1.schema.json": "schemas/exercise-library-v1.schema.json",
    "schemas/exercise-collection-v1.schema.json": "schemas/exercise-collection-v1.schema.json",
    "catalog/exercise-library-v1.json": "app/assets/relven/exercise-library-v1.json",
    "catalog/exercise-inventory.md": "app/assets/relven/exercise-library-v1.json",
    "examples/plan-running.json": "schemas/examples/plan-running.json",
    "examples/plan-strength.json": "schemas/examples/plan-strength.json",
}


def build_exercise_inventory() -> bytes:
    """Build a readable index without replacing canonical exercise JSON."""
    catalog = json.loads((ROOT / FILES["catalog/exercise-library-v1.json"]).read_text(encoding="utf-8"))
    exercises = catalog["exercises"]
    lines = [
        "# Bundled exercise inventory",
        "",
        f"{len(exercises)} bundled exercises · library revision {catalog['libraryRevision']}",
        "",
        "This is a quick index. `exercise-library-v1.json` is canonical for IDs, revisions, instructions, equipment, and all other fields.",
        "Use only active exercises whose canonical `equipment` array is empty when creating no-equipment workouts.",
        "",
        "| Svenska | English | Exercise ID | Category | Movement pattern | Muscle groups | Equipment |",
        "|---|---|---|---|---|---|---|",
    ]
    for exercise in sorted(exercises, key=lambda item: (item["name"]["sv"].casefold(), item["exerciseId"])):
        name_sv = exercise["name"]["sv"].replace("|", "\\|")
        name_en = exercise["name"]["en"].replace("|", "\\|")
        muscle_groups = ", ".join(exercise["muscleGroups"])
        equipment = ", ".join(exercise["equipment"]) or "None"
        lines.append(
            f"| {name_sv} | {name_en} | `{exercise['exerciseId']}` | {exercise['category']} | "
            f"{exercise['movementPattern']} | {muscle_groups} | {equipment} |"
        )
    return ("\n".join(lines) + "\n").encode("utf-8")


def build_archive() -> bytes:
    """Return stable ZIP bytes for the current source files."""
    from io import BytesIO

    output = BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_STORED) as archive:
        for archive_name, relative_source in sorted(FILES.items()):
            entry = zipfile.ZipInfo(PREFIX + archive_name)
            entry.date_time = (2026, 1, 1, 0, 0, 0)
            entry.compress_type = zipfile.ZIP_STORED
            entry.create_system = 3
            entry.external_attr = 0o100644 << 16
            source = (
                build_exercise_inventory()
                if archive_name == "catalog/exercise-inventory.md"
                else (ROOT / relative_source).read_bytes().replace(b"\r\n", b"\n")
            )
            archive.writestr(entry, source, compress_type=zipfile.ZIP_STORED)
    return output.getvalue()


def main(arguments=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail when the asset is stale")
    options = parser.parse_args(arguments)
    expected = build_archive()
    if options.check:
        if not OUTPUT.is_file() or OUTPUT.read_bytes() != expected:
            print("AI Plan Kit asset is stale; run python tools/build_ai_plan_kit.py", file=sys.stderr)
            return 1
        return 0

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(expected)
    print(OUTPUT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
