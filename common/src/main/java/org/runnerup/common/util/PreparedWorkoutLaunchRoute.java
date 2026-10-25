package org.runnerup.common.util;

import java.net.URI;
import java.net.URISyntaxException;

/** Shared identity and validation for the browsable URI that opens a prepared Wear workout. */
public final class PreparedWorkoutLaunchRoute {
  public static final String URI_STRING = "relven://workout/prepared";

  private PreparedWorkoutLaunchRoute() {}

  public static boolean matches(String action, boolean browsable, String uriString) {
    if (!"android.intent.action.VIEW".equals(action) || !browsable || uriString == null) {
      return false;
    }
    try {
      URI uri = new URI(uriString);
      return "relven".equals(uri.getScheme())
          && "workout".equals(uri.getHost())
          && "/prepared".equals(uri.getPath())
          && uri.getPort() == -1
          && uri.getRawUserInfo() == null
          && uri.getRawQuery() == null
          && uri.getRawFragment() == null;
    } catch (URISyntaxException malformed) {
      return false;
    }
  }
}
