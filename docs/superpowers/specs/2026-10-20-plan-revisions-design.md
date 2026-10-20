# Plan revision and rescheduling design

## Intent and constraints

Add a useful first editing action to the active plan calendar while keeping imported plan packages immutable. The user can move one scheduled workout or rest item to a different local date. Every accepted move creates a complete new plan revision and makes it active; the imported package and every previous revision remain available. RunnerUp continues to own workout execution and activity history. This work is offline and does not infer completion.

Preserve application ID `se.hampusandersson.relven`. Do not use the physical Android device until all product phases are complete and the app is ready for manual testing. Keep one PR open at a time and merge it before starting another.

## User behavior

- Each workout/rest entry shown for the active plan has a localized Reschedule action.
- The user enters a target date in a calendar selection mode and explicitly saves the change.
- Cancel exits the mode without writing data.
- Moving a workout onto a rest day, or a rest item onto a date with another item, is rejected with a localized explanation. Multiple workouts may share a date, matching the existing schema. Existing workout reference, notes, plan timezone, and all other entries are preserved.
- A successful move creates the next immutable revision and activates it. The calendar then shows the new revision at the target date.
- Failures leave the previous active revision intact and are shown to the user.

## Revision data and storage

- Continue to parse and preserve plan schema v1 packages unchanged.
- Add schema v2 for app-created revisions. It carries revision metadata: the original imported plan ID (`rootPlanId`), a positive sequence number, and the immediately preceding revision ID. The revision also has a fresh unique plan ID and a full snapshot of its schedule.
- Revision numbers increase within a root lineage. Creation requires that the exact previous plan be saved. Reject duplicate or broken lineage rather than replacing a file.
- Write the complete revision atomically, then switch the active-plan preference to its new ID. If activation fails, the prior active plan remains selected and the saved revision is harmlessly retained for recovery.
- Never modify imported or preceding JSON files. Never modify workouts or completed activity records.

## Scope limits

This increment reschedules one existing entry. It does not add/delete entries, edit workout definitions or notes, support strength plans, infer workout completion, or redesign the plan library. Dates remain one-time local calendar dates in the package timezone.

## Verification

- Unit tests cover v1 compatibility, v2 metadata validation, date transformation, field preservation, invalid date collisions, revision sequencing, immutability, activation, and failed-operation recovery.
- Compose changes preserve localized date selection, cancel, saving, and error feedback with accessible controls.
- Run debug/release builds, app unit tests, lint, formatting, and `git diff --check`; perform source-level accessibility/responsive inspection. Do not use the protected physical device.
- Obtain an independent code review, fix actionable findings test-first, then open one PR and wait for hosted CI before merging.
