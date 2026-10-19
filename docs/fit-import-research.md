# FIT Workout import research

Garmin's [Workout file description](https://developer.garmin.com/fit/file-types/workout) identifies File ID, Workout and Workout Step messages as the required structure. A FIT Activity file represents a completed recording and must not enter the workout importer.

The official [FIT Java SDK](https://github.com/garmin/fit-java-sdk) is maintained and available on Maven Central, but its [FIT Protocol License](https://github.com/garmin/fit-java-sdk/blob/main/LICENSE.txt) restricts redistribution and placing the SDK under a license that requires source distribution or modification rights. Relven is GPLv3. **Do not add the Garmin SDK dependency without a verified compatible grant.** Its public GitHub availability alone is not a compatible license.

Next implementation step: select and verify an independently licensed FIT decoder suitable for Android, or implement the binary reader in this GPL project using interoperable protocol facts without copying SDK code. Verify FIT header, CRC, message order and file type, then map only executable running steps into the existing `Workout` model. Include real FIT Workout fixtures and explicit unsupported-feature errors. No FIT decoder is currently included.
