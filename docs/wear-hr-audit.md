# Wear heart-rate integration audit

Upstream [PR #1378](https://github.com/jonasoreland/runnerup/pull/1378) was open when fetched on 19 October 2026. The reviewed head was `f154de85121e956de7fe94cbe4fd47216e50d156`, against PR base `b8a2f8ebe40e4ec3f258b50e31b0dd1771d8fa2d`. The patch was applied onto this fork's `ac4f8baa` baseline and adapted for the current `HRProvider` interface. This is initial, unverified integration, not a completed Wear HR release.

## Implemented in this branch

- Phone discovers reachable watches advertising the HR capability, selects a node, sends start/stop commands and accepts HR messages from that node only.
- Watch reads actual `Sensor.TYPE_HEART_RATE` values and sends plausible measurements to the paired phone through the Wearable Data Layer. Phone reports a connected HR source only after a valid reading, not merely after start-message delivery.
- Phone rejects malformed/out-of-range samples and battery values. Its last HR sample expires after 15 seconds; `Tracker` then reports no current HR value. A 30-second first-reading timeout enters the existing provider retry flow.
- Tracker's Bluetooth permission gate no longer blocks the Wear provider. Disconnecting HR does not stop the phone's GPS/workout service.
- The watch service does not restart a stale sensor command after process death. The sample freshness rule has unit tests.
- The watch now has a visible heart-rate readiness page before and during a workout. It checks for a sensor and the API-appropriate permission; the user starts the permission flow with an explicit button. The sensor service no longer launches permission UI from the background.

## Release blockers and limitations

- **No physical watch test.** A Samsung Galaxy Watch6 Classic, paired phone, permission grant/revocation, screen-off operation, watch process termination, reconnection, battery behavior and interval controls remain unverified.
- The imported watch implementation uses a plain started service and direct `SensorManager`. It does not hold a health foreground service or background body-sensor permission. Modern Wear OS can restrict collection when the UI is not foregrounded, so screen-off/background HR is **unsupported** until implemented and tested. [Android background sensor guidance](https://developer.android.com/health-and-fitness/health-services/background-body-sensors) and [foreground service guidance](https://developer.android.com/develop/background-work/services/fgs/service-types) inform this assessment.
- The permission page and API 35/36 mapping have unit/build coverage but have not been exercised on a watch. Permission revocation, denial/rationale behavior and the page's fit on supported Wear OS versions remain unverified. [Android health permission guidance](https://developer.android.com/health-and-fitness/health-services/permissions) covers the API 36 transition.
- Message delivery and sample timeout do not establish continuous sensor operation. The phone expires stale readings, but an automatic restart after a mid-session sensor stop is not proven.
- This patch does not yet show live heart-rate readings on a dedicated watch status screen or replace the inherited workout UI.

Next: add the health foreground-service lifecycle and required background permission policy, then expose live sensor status on the phone and run the on-device screen-off/reconnect/permission test matrix. Consider Health Services after measuring direct-sensor behavior against the Galaxy Watch6 Classic baseline.
