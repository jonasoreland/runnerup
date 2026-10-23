package org.runnerup.relven.ui

import android.net.Uri

internal enum class ActivityShareFormat(val extension: String, val mimeType: String) {
  Gpx("gpx", "application/gpx+xml"),
  Tcx("tcx", "application/vnd.garmin.tcx+xml")
}

internal data class ActivityShareTarget(val uri: Uri, val mimeType: String)

internal fun activityShareTarget(
    authority: String,
    activityId: Long,
    fileName: String,
    format: ActivityShareFormat): ActivityShareTarget =
    ActivityShareTarget(
        uri =
            Uri.Builder()
                .scheme("content")
                .authority(authority)
                .appendPath(format.extension)
                .appendPath(activityId.toString())
                .appendPath(fileName)
                .build(),
        mimeType = format.mimeType)
