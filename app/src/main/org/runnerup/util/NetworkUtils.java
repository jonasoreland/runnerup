package org.runnerup.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import androidx.core.content.ContextCompat;

public class NetworkUtils {
  public static boolean isNetworkAvailable(Context context) {
    ConnectivityManager cm = ContextCompat.getSystemService(context, ConnectivityManager.class);
    if (cm == null) {
      return false;
    }
    android.net.Network network = cm.getActiveNetwork();
    NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
    return capabilities != null
        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
  }
}
