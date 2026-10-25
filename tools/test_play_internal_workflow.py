"""Guard the manual Play workflow's signing-only validation mode."""

import hashlib
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github" / "workflows" / "play-internal.yml"
WRAPPER_JAR = ROOT / "gradle" / "wrapper" / "gradle-wrapper.jar"
WRAPPER_PROPERTIES = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"


def step_block(workflow: str, step_name: str) -> str:
    lines = workflow.splitlines()
    start = next(
        index
        for index, line in enumerate(lines)
        if line.strip() == f"- name: {step_name}"
    )
    end = next(
        (
            index
            for index in range(start + 1, len(lines))
            if lines[index].startswith("      - name:")
        ),
        len(lines),
    )
    return "\n".join(lines[start:end])


class PlayInternalWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_dispatch_accepts_validation_and_publish_modes_on_master(self):
        self.assertIn("VALIDATE_SIGNING_ONLY", self.workflow)
        self.assertIn(
            "inputs.confirm == 'PUBLISH_INTERNAL' || inputs.confirm == 'VALIDATE_SIGNING_ONLY'",
            self.workflow,
        )
        self.assertIn("github.ref == 'refs/heads/master'", self.workflow)

    def test_play_service_account_is_restored_only_for_publish_mode(self):
        service_account = step_block(self.workflow, "Restore Play service account")
        self.assertIn("if: inputs.confirm == 'PUBLISH_INTERNAL'", service_account)
        self.assertIn("secrets.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64", service_account)

    def test_lfs_restoration_precedes_gradle_wrapper_validation(self):
        self.assertIn("lfs: true", self.workflow)
        self.assertLess(
            self.workflow.index("lfs: true"),
            self.workflow.index("gradle/actions/setup-gradle"),
        )

    def test_play_workflow_uses_pinned_open_source_gradle_action_with_validation(self):
        self.assertIn(
            "gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0",
            self.workflow,
        )
        self.assertIn("cache-provider: basic", self.workflow)
        self.assertIn("validate-wrappers: true", self.workflow)
        self.assertNotIn("validate-wrappers: false", self.workflow)

    def test_gradle_wrapper_jar_matches_published_961_checksum(self):
        actual = hashlib.sha256(WRAPPER_JAR.read_bytes()).hexdigest()
        self.assertEqual(
            "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7",
            actual,
        )

    def test_gradle_distribution_checksum_is_pinned(self):
        properties = WRAPPER_PROPERTIES.read_text(encoding="utf-8")
        self.assertIn(
            "distributionSha256Sum=9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14",
            properties,
        )

    def test_both_play_upload_actions_require_publish_mode(self):
        for step_name in (
            "Publish Wear OS bundle to Play Internal Testing",
            "Publish phone bundle to Play Internal Testing",
        ):
            with self.subTest(step=step_name):
                step = step_block(self.workflow, step_name)
                self.assertIn("if: inputs.confirm == 'PUBLISH_INTERNAL'", step)

    def test_signing_validation_reports_public_certificate_fingerprint(self):
        verification = step_block(self.workflow, "Verify Play bundles before upload")
        self.assertIn("GITHUB_STEP_SUMMARY", verification)
        self.assertIn("phone_cert", verification)
        self.assertIn("wear_cert", verification)


if __name__ == "__main__":
    unittest.main()
