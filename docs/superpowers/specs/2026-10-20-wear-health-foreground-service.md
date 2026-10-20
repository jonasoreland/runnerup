# Wear heart-rate foreground service

## Goal

Keep direct `SensorManager` heart-rate capture alive during a phone-directed workout, including when the watch display turns off, using the Android health foreground-service type and version-appropriate background sensor permission.

## Design

- The existing phone HR provider remains authoritative for workout start/stop. The watch does not create a separate workout session.
- The watch service must validate sensor, foreground heart-rate, and background heart-rate permissions before capture. A missing permission means HR capture stays off; the workout continues without watch HR.
- API 25–32 use the existing `BODY_SENSORS` permission. API 33–35 also require `BODY_SENSORS_BACKGROUND` for background capture. API 36+ use `READ_HEART_RATE` and `READ_HEALTH_DATA_IN_BACKGROUND`.
- `HeartRateService` becomes a `health` foreground service with a low-priority ongoing notification while capturing. Stop commands unregister the sensor, remove the notification, and stop the service.
- The Wear permission page explains the background requirement, shows readiness from both permissions, and exposes user-initiated permission/settings actions. It does not start capture itself.
- The listener service catches expected foreground-service start and permission failures, records a useful log, and does not crash or launch UI.
- English and Swedish copy describe continuous heart-rate access during a workout and the persistent notification.

## Out of scope

- Replacing `SensorManager` with Health Services.
- Phone-visible sensor status, automatic recovery after sensor loss, and any changes to phone workout execution.
- Claiming screen-off reliability before testing on a physical Galaxy Watch6 Classic. No ADB device is used during this phase.

## Verification

Unit tests cover SDK permission mapping and readiness combinations. Local gates include Wear debug/release builds and tests, app tests, Wear/app lint, Spotless, and release package identity. Physical screen-off, permission grant/revoke, notification, and reconnection tests remain a later manual gate.

## Platform references

- [Wear OS health permissions](https://developer.android.com/health-and-fitness/health-services/permissions)
- [Health foreground service type](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Background body-sensor permission](https://developer.android.com/health-and-fitness/health-services/background-body-sensors)
- [Foreground-service background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
