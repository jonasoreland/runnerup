# Keep workout setup inside the Relven shell

## Goal
Opening the Start tab from Relven Home must keep the user in the Relven navigation shell instead of launching RunnerUp's standalone `MainLayout` tab activity.

## Design
Add a persisted Start destination to `RelvenHomeActivity`. Host the existing `StartFragment` in a stable fragment container within that destination so RunnerUp retains its tested GPS, permission, workout setup, and tracker lifecycle behavior. Keep Relven's Compose bottom navigation visible and selected while setup is shown. Remove the hosted fragment when navigating away so its GPS and service lifecycle cleanup runs. Route both the Home start card and Start tab to this destination.

This increment removes the top-level app-shell handoff. Workout manager and plan library actions remain subsequent UI migration work.
