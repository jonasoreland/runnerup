# Guided strength session execution

## Goal

Let a user execute an imported bodyweight strength or prehab workout from the plan calendar, record actual repetitions or timed work, move through the prescribed sets and rest periods, and save completion locally against the scheduled plan entry.

## Behavior

- Start execution only from validated strength/prehab details in the active plan calendar. Running remains in RunnerUp.
- Show one exercise at a time with its pinned name, instructions, prescription, set progress, and next-exercise context.
- Repetition sets accept an actual count; timed sets count down the prescribed duration. Between sets/exercises, count down the prescribed rest, with a clear skip-rest action.
- Allow pausing and resuming the active strength session. Do not mark the plan entry complete until the user finishes the final set and confirms completion.
- Save actual set results locally and offline, linked to plan ID, scheduled entry ID, workout ID, and original workout name. Preserve plan documents and old completions when revisions are activated.
- Show completion on the calendar for that scheduled entry and prevent accidental duplicate completion; allow opening the saved summary.
- Use localized accessible controls and the existing Relven Compose theme. No images or external network services are required.

## Validation and recovery

- Reject malformed set values and impossible session references without modifying the active plan.
- Persist progress before transitions so process recreation can resume the session without losing completed sets.
- Store completed sessions atomically and keep execution data separate from RunnerUp running activities.

## Out of scope

Watch execution, exercise authoring/media, equipment exercises, progression analytics, plan mutation on completion, web synchronization, and physical device testing.

## Verification

Test session progression, rep/timed sets, rest skipping/countdown boundaries, pause/resume persistence codec, completion identity and duplicate handling. Run local unit tests, debug/release builds, lint, Spotless, then hosted CI. No ADB device use.
