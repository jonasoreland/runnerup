# Google Play Internal Publishing

The manual workflow at **Actions → Publish Relven to Play Internal Testing → Run workflow** builds and uploads both the phone and Wear app bundles. It only runs on `master`, requires typing `PUBLISH_INTERNAL`, and requires an even phone version code that exceeds the highest version code already uploaded for this app. Wear uses the next code. The current local phone build is `24000374`, so the default suggested first code is `24000376`; check Play Console before running and raise it if any track already contains a higher code.

The action signs with the original Relven upload keystore. Google Play uses the package ID `se.hampusandersson.relven`. Do not run the workflow until all five repository secrets below have been added.

## Add the five repository secrets

In `HampusAndersson01/Relven-2.0`, open **Settings → Secrets and variables → Actions → New repository secret**. Add these exact names:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of the existing Relven upload `.jks` or `.keystore` file |
| `ANDROID_KEYSTORE_PASSWORD` | The keystore password |
| `ANDROID_KEY_ALIAS` | The alias of the upload key inside that keystore |
| `ANDROID_KEY_PASSWORD` | The password for that alias/key |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64` | Base64 encoding of the Google Play service-account JSON key file |

GitHub cannot show or copy secret values from the old `Relven` repository; it only exposes their names. The old repo currently has all five names, while `Relven-2.0` has none, so add the values again from your local credential files/password manager. Do not paste credentials into a chat, commit them, or store the generated `release.properties` file.

For the binary keystore and JSON key, PowerShell can pipe base64 directly into `gh secret set` without printing the encoded data:

```powershell
$repo = "HampusAndersson01/Relven-2.0"
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\secure\relven-upload.jks")) | gh secret set ANDROID_KEYSTORE_BASE64 --repo $repo
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\secure\google-play-service-account.json")) | gh secret set GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64 --repo $repo
```

Replace the example paths with your actual local files. Enter the passwords and alias through the GitHub secret form so they are not written into shell history. Do not pass a secret using a literal command-line argument.

## Find or create the Play service account key

If the old JSON key file is available locally, keep using it; there is no need to create another account. If it is missing, open [Google Cloud Console → Service Accounts](https://console.cloud.google.com/iam-admin/serviceaccounts), select or create a Cloud project, then go to **APIs & Services → Library**, enable **Google Play Android Developer API**, and create a service account. Open its **Keys → Add key → Create new key → JSON**. Google only offers the downloaded private key once, so store it securely.

In Google Play Console, open **Users and permissions**, invite the service account's `client_email`, and grant app-level **Release apps to testing tracks** access for Relven. The Google Play API describes this as `CAN_MANAGE_TRACK_APKS`; it permits testing-track releases without granting production-release permission. The API setup steps are in Google's [Getting Started guide](https://developers.google.com/android-publisher/getting_started), and the JSON-key creation steps are in [Google Cloud's service-account key guide](https://docs.cloud.google.com/iam/docs/keys-create-delete).

## Run the workflow

Before dispatching, check the highest uploaded phone/Wear version code in Play Console. Choose an even phone code greater than every code already uploaded, leave room for Wear's `+1`, type `PUBLISH_INTERNAL`, and start the workflow on `master`. GitHub Actions secrets are managed as described in [GitHub's Actions secrets guide](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).

The action validates both bundle signatures, package IDs, version codes, watch form-factor metadata, and matching upload certificates before upload. It uploads Wear to `wear:internal` and phone to `internal`. Google Play processes the two bundles in separate API calls, so if one upload succeeds and the other fails, check the two tracks in Play Console before retrying.
