# Google Play Internal Publishing Workflow Design

## Goal

Migrate the existing Relven manual Play Internal publishing action to `HampusAndersson01/Relven-2.0`, producing compatible signed phone and Wear bundles under the original application ID.

## Requirements

- Trigger only by manual `workflow_dispatch` on `master`, with separate exact confirmations for `VALIDATE_SIGNING_ONLY` and `PUBLISH_INTERNAL`.
- Use a GitHub hosted Linux runner and this repository's root Gradle tasks (`:app:bundleLatestRelease` and `:wear:bundleRelease`).
- Use the existing upload keystore and Play service-account credential names from the original Relven repository; never store secret values in source control.
- Restore Git LFS files during checkout before Gradle setup validates the wrapper JAR.
- Generate ignored `release.properties` from Actions secrets, sign both release bundles with the original upload key, and remove temporary credential files even after failure.
- Preserve package ID `se.hampusandersson.relven`. Accept the phone version code as a required dispatch input, require an even code greater than the known local baseline `24000374`, and use the following odd code for Wear. This avoids assuming the version currently uploaded to Play Console.
- Verify bundle existence, signature validity, package ID, requested version codes, phone/watch form-factor metadata, and matching signing certificates before upload.
- In validation-only mode, do not restore the Play service-account key and never invoke either upload action; report the public shared signing certificate SHA-256 fingerprint for comparison in Play Console.
- Publish Wear to `wear:internal` and phone to `internal`, then clean up credentials.
- Document the five repository secret names, where to obtain each value, and the local-only method for base64 encoding binary/JSON files without printing their contents.

## Explicitly out of scope

- Running either workflow mode or uploading an AAB during implementation of this change.
- Changing the existing upload key, app-signing enrollment, package ID, or Android app data.
- Automatically choosing a version code from an unverified Play Console state.

## Verification

- Validate the Gradle version-code override through app and Wear release bundle metadata.
- Run the repository's full app/Wear test, build, lint, and Spotless matrix.
- Review the GitHub workflow for expression injection, secret exposure, missing cleanup, and track/package mistakes.
