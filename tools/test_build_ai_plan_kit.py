"""Tests for the reproducible Android AI Plan Kit archive."""

import json
from pathlib import Path
import subprocess
import sys
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
BUILDER = ROOT / "tools" / "build_ai_plan_kit.py"
OUTPUT = ROOT / "app" / "assets" / "relven" / "relven-ai-plan-kit-v1.zip"
ARCHIVE_SOURCES = {
    "README.md": ROOT / "docs" / "ai-plan-kit" / "README.md",
    "INSTRUCTIONS.md": ROOT / "docs" / "ai-plan-kit" / "INSTRUCTIONS.md",
    "capabilities.json": ROOT / "docs" / "ai-plan-kit" / "capabilities.json",
    "schemas/plan-v1.schema.json": ROOT / "schemas" / "plan-v1.schema.json",
    "schemas/plan-v2.schema.json": ROOT / "schemas" / "plan-v2.schema.json",
    "schemas/workout-v3.schema.json": ROOT / "schemas" / "workout-v3.schema.json",
    "schemas/exercise-library-v1.schema.json": ROOT / "schemas" / "exercise-library-v1.schema.json",
    "examples/plan-running.json": ROOT / "schemas" / "examples" / "plan-running.json",
    "examples/plan-strength.json": ROOT / "schemas" / "examples" / "plan-strength.json",
}


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
            }
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
        self.assertEqual(1, capabilities["kitVersion"])
        self.assertEqual([1], capabilities["generatedPlanSchemaVersions"])
        self.assertEqual(2, capabilities["appManagedRevisionSchemaVersion"])
        self.assertTrue(capabilities["strengthPlanImport"])
        self.assertTrue(capabilities["strengthWorkoutDetails"])
        self.assertFalse(capabilities["planZipImport"])

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
