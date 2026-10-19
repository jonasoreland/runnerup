# Verified Stitch references

Project `15389433782836702529` (Relven Fitness App UI Design) was verified on 19 October 2026. [manifest.json](manifest.json) maps each screen ID to its downloaded screenshot and, where available, HTML export. These are design references, not application code.

The original Daily Activity Dashboard export establishes the Android visual base: Plus Jakarta Sans, background `#0A0F18`, orange `#FF5A1F`, muted text `#8E9BAE`, dark translucent surfaces, a compact profile/notification header and a three-destination floating navigation pill. The Running Mode export uses a small circular back button and dark navy map surface. The uploaded three-screen Android image confirms their shared direction; the Wear reference uses a high-contrast, glanceable circular display.

The current Training Plan, Running Details and Strength Details exports are **not approved implementation references**. They introduce Inter/Space Grotesk, cyan `#38BDF8`, a different bottom navigation count and denser card layouts. A Stitch edit was requested for exactly those three existing screens, but the service returned HTTP 502 after 95 seconds. A fresh read showed unchanged file entries. Per the integration's guidance, the edit was not retried. Their current exports are retained for content inventory, while the original generated screens govern visual choices until corrected versions can be fetched.

The source SVG from the previous Relven repository is copied as `relven-app-icon-source.svg`; the original repository was not modified. `relven-mark-transparent.svg` contains the same logo paths for launcher use.
