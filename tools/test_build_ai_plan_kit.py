"""Tests for the reproducible Android AI Plan Kit archive."""

import json
from pathlib import Path
import subprocess
import sys
import unittest
import zipfile
import jsonschema
from referencing import Registry, Resource


ROOT = Path(__file__).resolve().parents[1]
BUILDER = ROOT / "tools" / "build_ai_plan_kit.py"
OUTPUT = ROOT / "app" / "assets" / "relven" / "relven-ai-plan-kit-v2.zip"
ARCHIVE_SOURCES = {
    "README.md": ROOT / "docs" / "ai-plan-kit" / "README.md",
    "INSTRUCTIONS.md": ROOT / "docs" / "ai-plan-kit" / "INSTRUCTIONS.md",
    "capabilities.json": ROOT / "docs" / "ai-plan-kit" / "capabilities.json",
    "schemas/plan-v1.schema.json": ROOT / "schemas" / "plan-v1.schema.json",
    "schemas/plan-v2.schema.json": ROOT / "schemas" / "plan-v2.schema.json",
    "schemas/workout-v3.schema.json": ROOT / "schemas" / "workout-v3.schema.json",
    "schemas/strength-session-v1.schema.json": ROOT / "schemas" / "strength-session-v1.schema.json",
    "schemas/activity-v1.schema.json": ROOT / "schemas" / "activity-v1.schema.json",
    "schemas/exercise-library-v1.schema.json": ROOT / "schemas" / "exercise-library-v1.schema.json",
    "schemas/exercise-collection-v1.schema.json": ROOT / "schemas" / "exercise-collection-v1.schema.json",
    "catalog/exercise-library-v1.json": ROOT / "app" / "assets" / "relven" / "exercise-library-v1.json",
    "JSON-FORMAT.md": ROOT / "docs" / "ai-plan-kit" / "JSON-FORMAT.md",
    "examples/plan-running.json": ROOT / "schemas" / "examples" / "plan-running.json",
    "examples/plan-strength.json": ROOT / "schemas" / "examples" / "plan-strength.json",
    "examples/activity.json": ROOT / "schemas" / "examples" / "activity.json",
}
GENERATED_ENTRIES = {"catalog/exercise-inventory.md"}


class AiPlanKitBuildTest(unittest.TestCase):
    def build(self, *arguments):
        return subprocess.run(
            [sys.executable, str(BUILDER), *arguments],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )

    def setUp(self):
        generated = self.build()
        self.assertEqual(generated.returncode, 0, generated.stderr)
        self.assertTrue(OUTPUT.is_file())

    def test_generator_builds_the_committed_archive_and_check_mode_passes(self):
        generated = self.build()
        self.assertEqual(generated.returncode, 0, generated.stderr)
        self.assertTrue(OUTPUT.is_file())
        first = OUTPUT.read_bytes()

        regenerated = self.build()
        self.assertEqual(regenerated.returncode, 0, regenerated.stderr)
        self.assertEqual(first, OUTPUT.read_bytes())

        checked = self.build("--check")
        self.assertEqual(checked.returncode, 0, checked.stderr)

    def test_archive_contains_exact_canonical_sources_with_stable_paths(self):
        self.assertTrue(OUTPUT.is_file(), "Run the generator to create the bundled asset")
        with zipfile.ZipFile(OUTPUT) as archive:
            expected_names = {
                "relven-ai-plan-kit/" + name for name in ARCHIVE_SOURCES
            } | {"relven-ai-plan-kit/" + name for name in GENERATED_ENTRIES}
            self.assertEqual(expected_names, set(archive.namelist()))
            for archive_name, source_path in ARCHIVE_SOURCES.items():
                expected = source_path.read_bytes().replace(b"\r\n", b"\n")
                self.assertEqual(
                    expected,
                    archive.read("relven-ai-plan-kit/" + archive_name),
                    archive_name,
                )

    def test_capabilities_mark_v2_as_app_managed_and_limit_generation_to_v1(self):
        self.assertTrue(OUTPUT.is_file(), "Run the generator to create the bundled asset")
        with zipfile.ZipFile(OUTPUT) as archive:
            capabilities = json.loads(
                archive.read("relven-ai-plan-kit/capabilities.json")
            )
        self.assertEqual(2, capabilities["kitVersion"])
        self.assertEqual([1], capabilities["generatedPlanSchemaVersions"])
        self.assertEqual(2, capabilities["appManagedRevisionSchemaVersion"])
        self.assertTrue(capabilities["strengthPrehab"]["planImport"])
        self.assertTrue(capabilities["strengthPrehab"]["guidedPhoneSession"])
        self.assertFalse(capabilities["planZipImport"])
        self.assertEqual("relven.strength-session", capabilities["strengthPrehab"]["portablePerformanceFormat"])
        self.assertFalse(capabilities["strengthPrehab"]["planImportAcceptsPerformanceExports"])

    def test_performance_export_schema_accepts_prescription_plus_explicit_partial_results(self):
        schema = json.loads((ROOT / "schemas" / "strength-session-v1.schema.json").read_text(encoding="utf-8"))
        plan = json.loads((ROOT / "schemas" / "examples" / "plan-strength.json").read_text(encoding="utf-8"))
        workout = plan["workouts"][0]
        workout["steps"] = workout["steps"][:1]
        export = {
            "format": "relven.strength-session",
            "schemaVersion": 1,
            "prescription": workout,
            "performance": {
                "sessionId": "run-1",
                "planId": plan["id"],
                "scheduledEntryId": "day-1",
                "sessionStatus": "COMPLETED",
                "completion": "partial",
                "finishedAtMillis": 1792830030000,
                "sets": [
                    {"exerciseIndex": 0, "exerciseStepId": "squat-step", "exerciseName": "Bodyweight squat", "setNumber": 1, "status": "COMPLETED", "repetitions": 10},
                    {"exerciseIndex": 0, "exerciseStepId": "squat-step", "exerciseName": "Bodyweight squat", "setNumber": 2, "status": "SKIPPED"},
                    {"exerciseIndex": 0, "exerciseStepId": "squat-step", "exerciseName": "Bodyweight squat", "setNumber": 3, "status": "INCOMPLETE"},
                ],
            },
        }
        workout_schema = json.loads((ROOT / "schemas" / "workout-v3.schema.json").read_text(encoding="utf-8"))
        registry = Registry().with_resource(
            "https://relven.app/schemas/workout-v3.schema.json",
            Resource.from_contents(workout_schema),
        )
        jsonschema.Draft202012Validator(schema, registry=registry).validate(export)

    def test_activity_export_example_validates_as_compact_without_route_coordinates(self):
        schema = json.loads((ROOT / "schemas" / "activity-v1.schema.json").read_text(encoding="utf-8"))
        example = json.loads((ROOT / "schemas" / "examples" / "activity.json").read_text(encoding="utf-8"))
        jsonschema.Draft202012Validator(schema).validate(example)
        self.assertFalse(example["dataQuality"]["rawRouteIncluded"])
        self.assertNotIn("route", example["activity"])
        with zipfile.ZipFile(OUTPUT) as archive:
            self.assertIn("relven-ai-plan-kit/schemas/activity-v1.schema.json", archive.namelist())
            self.assertIn("relven-ai-plan-kit/examples/activity.json", archive.namelist())

    def test_complete_kit_contains_the_exercise_database_and_interchange_guide(self):
        with zipfile.ZipFile(OUTPUT) as archive:
            names = set(archive.namelist())
            expected_entries = {
                "relven-ai-plan-kit/catalog/exercise-library-v1.json",
                "relven-ai-plan-kit/catalog/exercise-inventory.md",
                "relven-ai-plan-kit/schemas/exercise-collection-v1.schema.json",
                "relven-ai-plan-kit/JSON-FORMAT.md",
            }
            self.assertTrue(expected_entries.issubset(names))
            catalog = json.loads(
                archive.read("relven-ai-plan-kit/catalog/exercise-library-v1.json")
            )
            self.assertEqual("relven.exercise-library", catalog["format"])
            self.assertEqual(116, len(catalog["exercises"]))
            self.assertEqual(2, catalog["libraryRevision"])
            capabilities = json.loads(
                archive.read("relven-ai-plan-kit/capabilities.json")
            )
            self.assertEqual(116, capabilities["exerciseCatalog"]["bundledExerciseCount"])
            self.assertEqual(
                "catalog/exercise-inventory.md",
                capabilities["exerciseCatalog"]["bundledInventoryPath"],
            )

    def test_exercise_inventory_index_lists_every_bundled_exercise_by_bilingual_name_and_id(self):
        with zipfile.ZipFile(OUTPUT) as archive:
            inventory = archive.read(
                "relven-ai-plan-kit/catalog/exercise-inventory.md"
            ).decode("utf-8")
            catalog = json.loads(
                archive.read("relven-ai-plan-kit/catalog/exercise-library-v1.json")
            )
        self.assertIn("116 bundled exercises", inventory)
        for exercise in catalog["exercises"]:
            self.assertIn(exercise["exerciseId"], inventory)
            self.assertIn(exercise["name"]["sv"], inventory)
            self.assertIn(exercise["name"]["en"], inventory)

    def test_check_mode_detects_a_stale_archive(self):
        original = OUTPUT.read_bytes()
        try:
            OUTPUT.write_bytes(original + b"stale")
            checked = self.build("--check")
            self.assertNotEqual(0, checked.returncode)
            self.assertIn("stale", checked.stderr.lower())
        finally:
            OUTPUT.write_bytes(original)


if __name__ == "__main__":
    unittest.main()
