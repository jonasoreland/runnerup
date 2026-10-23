# GPS drift quality correction

## Goal

Prevent a stationary phone's noisy location estimates from accumulating into route distance and pace while preserving credible walking/running movement. Keep `Tracker` as the one tracking authority and retain the existing no-GPS, pause/resume, gap, persistence, and map behavior.

## Root cause

`GpsFixFilter` compares each sample with a fixed last-counted anchor. Samples inside the accuracy deadband are marked stationary without advancing that anchor. `Tracker` pins their persisted coordinates to the preceding accepted route coordinate. A sequence of similarly biased fixes can therefore walk past the deadband; the next sample is counted as a movement segment from the old anchor. The current tests exercise bounded jitter around the anchor, not correlated drift over a minute.

## Implementation steps

1. Add a failing synthetic sequence reproducing correlated low-speed drift with realistic position accuracy and Android speed/speed-accuracy evidence; retain tests for walking and running.
2. Implement a conservative stationary veto only when fresh platform speed and its reported accuracy satisfy `speed + 2 × speedAccuracy <= 0.25 m/s`. Treat the doubled accuracy as a guard margin, not a formal two-sigma guarantee. Advance the filter's reference on vetoed fixes so drift cannot accumulate. Do not use speed-accuracy data when the platform does not provide it or when confidence overlaps plausible movement.
3. Verify tracker distance/current pace/persistence consume the filter result consistently; keep elevations unchanged for stationary samples and ensure rejected fixes do not enter route persistence.
4. Add tests for unavailable/uncertain provider speed, slow walking with corroborating speed, normal running, speed spikes, and stationary drift recovery.
5. Run focused and full tests, Spotless, lint and debug/no-map builds. Update GPS audit/status docs with the measured behavior. Review and merge this as one PR, then build from updated `master` before moving to the next phase.

## Acceptance checks

- A 9 m-accuracy stationary drift sequence lasting about 79 seconds does not create a route line, distance, or derived pace when provider speed confidence indicates stationary.
- Provider speed and speed accuracy corresponding to 0.3 m/s or faster are not classified as stationary, preserving very slow walking.
- Credible slow walking is accumulated; missing/uncertain provider speed does not create a new hard movement floor.
- Existing accuracy, monotonic timestamp, stale/future, pause/resume, gap, and implausible speed protections still pass.
- No schema change, second tracking engine, paid service, or physical-device ADB use.
