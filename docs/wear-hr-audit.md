# Wear heart-rate integration audit

Upstream [PR #1378](https://github.com/jonasoreland/runnerup/pull/1378) was open when fetched on 19 October 2026. The reviewed head was `f154de85121e956de7fe94cbe4fd47216e50d156`, against PR base `b8a2f8ebe40e4ec3f258b50e31b0dd1771d8fa2d`. The patch was applied onto this fork's `ac4f8baa` baseline and adapted for the current `HRProvider` interface. This is initial, unverified integration, not a completed Wear HR release.

## Implemented in this branch

- Phone discovers reachable watches advertising the HR capability, selects a node, sends start/stop commands and accepts HR messages from that node only.
- Watch reads actual `Sensor.TYPE_HEART_RATE` values and sends plausible measurements to the paired phone through the Wearable Data Layer. Phone reports a connected HR source only after a valid reading, not merely after start-message delivery.
- Phone rejects malformed/out-of-range samples and battery values. Its last HR sample expires after 15 seconds; `Tracker` then reports no current HR value. A 30-second first-reading timeout enters the existing provider retry flow.
- Tracker's Bluetooth permission gate no longer blocks the Wear provider. Disconnecting HR does not stop the phone's GPS/workout service.
- The phone now shows localized Wear HR connection states in settings, pre-run status and the active workout HR header. When an established stream is stale, the phone sends one bounded restart request; after 30 seconds without fresh data it stops the watch service, removes its listener and reports unavailable. Settings permits a manual retry.
- Repeated start commands re-register the watch's direct sensor listener without duplicate listeners. This recovery path has provider/state tests, but remains unverified on physical hardware.
- The watch now has a visible heart-rate readiness page before and during a workout. It checks for a sensor and the API-appropriate permission; the user starts the permission flow with an explicit button. The sensor service no longer launches permission UI from the background.
- The watch service now declares the `health` foreground-service type, gates capture on sensor plus version-appropriate foreground/background heart-rate access, and displays an ongoing low-priority notification while sampling. The listener catches expected service-start and permission failures instead of crashing.
- The Compose workout glance reads the latest validated sample from the existing watch sensor service. It shows a live BPM for up to 15 seconds, indicates waiting before the first sample and unavailable after a 15-second initial wait, then removes the old BPM and labels an expired sample as stale. The status row is hidden when the capture service is inactive. This is local presentation state; it does not start another sensor listener or change phone transfer.

## Release blockers and limitations

- **No physical watch test.** A Samsung Galaxy Watch6 Classic, paired phone, permission grant/revocation, screen-off operation, watch process termination, reconnection, battery behavior and interval controls remain unverified.
- The foreground-service and background-permission implementation has local build/unit/lint coverage but has not been exercised on a watch. Screen-off/background HR remains **unverified and not release-ready** until a Galaxy Watch6 Classic confirms FGS start from Data Layer commands, ongoing notification behavior, screen-off sensor samples, permission denial/revocation and reconnection. [Android background sensor guidance](https://developer.android.com/health-and-fitness/health-services/background-body-sensors), [health foreground-service requirements](https://developer.android.com/develop/background-work/services/fgs/service-types) and [background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) inform this assessment.
- The permission page and API 32/33/35/36 mapping have unit/build coverage but have not been exercised on a watch. The API 33–35 app-settings path and API 36 background permission prompt remain unverified. [Android health permission guidance](https://developer.android.com/health-and-fitness/health-services/permissions) covers the API 36 transition.
- Message delivery and sample timeout do not establish continuous sensor operation. Automatic restart and phone UI state are implemented, but do not prove that the watch restarts its sensor in background or with its screen off.
- Local sample presentation does not verify screen-off/background collection, foreground-service startup, or Data Layer delivery on physical hardware.
- The new freshness presentation has unit coverage for waiting, live, stale, unavailable, future timestamps, capture-off hiding, plausible BPM validation and sample clearing. Compose rendering and update timing remain unverified on a real watch.

Next: run the on-device screen-off/reconnect/permission test matrix on a Galaxy Watch6 Classic once all phases are ready for manual testing. Consider Health Services after measuring direct-sensor behavior against that baseline.
