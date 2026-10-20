package org.runnerup.view;

import androidx.annotation.StringRes;
import org.runnerup.common.R;
import org.runnerup.hr.WearHRConnectionState;

/** Maps Wear heart-rate states to localized phone UI labels. */
final class WearHRStatusText {
  private WearHRStatusText() {}

  @StringRes
  static int resourceFor(WearHRConnectionState.Status status) {
    switch (status) {
      case CONNECTING:
        return R.string.wear_hr_status_connecting;
      case CONNECTED:
        return R.string.wear_hr_status_connected;
      case RECONNECTING:
        return R.string.wear_hr_status_reconnecting;
      case UNAVAILABLE:
        return R.string.wear_hr_status_unavailable;
      case DISCONNECTED:
      default:
        return R.string.wear_hr_status_disconnected;
    }
  }
}
