package org.runnerup.relven.ui;

import android.content.Context;
import java.util.List;

/** Keeps the settings UI honest in builds without the Wear OS Data Layer dependency. */
public final class WearConnectionProvider {
  public interface Callback {
    void onResult(boolean supported, boolean succeeded, List<String> connectedNodes);
  }

  private WearConnectionProvider() {}

  public static void query(Context context, Callback callback) {
    callback.onResult(false, false, null);
  }
}
