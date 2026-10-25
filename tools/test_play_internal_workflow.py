"""Guard the manual Play workflow's signing-only validation mode."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github" / "workflows" / "play-internal.yml"


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
