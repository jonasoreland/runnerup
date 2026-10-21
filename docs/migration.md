# Migration milestone

1. Fetched the existing GitHub fork `HampusAndersson01/Relven-2.0` into the empty local checkout and added RunnerUp's `upstream` remote. Both pointed to `ac4f8baa` initially.
2. Built untouched phone/watch debug APKs and selected tests before changing application code.
3. Reused the original Relven phone/watch Play ID `se.hampusandersson.relven` per the owner's instruction, retained internal namespaces and wearable protocol paths, changed launcher/permission labels, and applied the uploaded icon from verified current Stitch project `15389433782836702529`.
4. Replaced README/store copy and removed stale upstream store screenshots/listings. License, credits, copyright headers and Git history remain.

The square icon reference came from the verified current Stitch project. The original vector supplied by the owner was copied read-only from the original Relven worktree, and a transparent cutout of its paths was rendered for phone and watch launchers. The production launcher now uses the Relven foreground mark and dark brand background through AndroidX SplashScreen compat, transitioning into the existing Relven Home theme. The splash has not been rendered on a real device; the physical device remains reserved for final manual testing.

The same-ID app upgrade retains the old private database, but the RunnerUp-derived app writes history to `runnerup.db`. The Maintenance importer now copies supported v3 workouts and eligible completed running activities from the read-only archive; it never activates plans, replays pending operations, or writes to or deletes `relven-workouts.db`. Unsupported workouts and activities, plans, active sessions, schedule changes, pending operations, and source JSON remain in the complete private archive, which stays exportable. Activity routes retain mapped location points and technical pause markers; extra recovery intervals and workout-step detail remain archived. This conversion is covered by synthetic v18 archive tests; a production-era v18 backup has not yet been exercised. Release metadata still uses `se.hampusandersson.relven`; actual Play version-code floor and existing signing identity must be confirmed before upload.

The Maintenance review/import UI and synthetic archive conversion tests are complete. The fork's hosted Android/F-Droid workflow passed for the migration PR. A copied production-era v18 backup and a signed same-ID upgrade still need validation after all product phases are complete; no Play upload is part of that check.

## Read-only legacy-data vault (20 October 2026)

On phone startup, Relven checks for `relven-workouts.db` and writes one integrity-checked JSON table-row archive to private app storage. SQLite is opened read-only, rows and SQLite value types are preserved, and the old database is never changed or removed. Existing archives are retained instead of overwritten. The user can export the archive from **Settings → Maintenance → Export previous Relven data** using Android's document picker. This JSON archive is not a restorable SQLite database.

This protects old data from accidental loss and provides the source archive for the local importer. The Maintenance screen reports import eligibility and keeps the archive export available after import. Synthetic tests cover conversion, retries, duplicate handling, unsupported records, transaction rollback, and source-vault retention. Validation against a copied real v18 database and a same-ID signed upgrade remain pending.
