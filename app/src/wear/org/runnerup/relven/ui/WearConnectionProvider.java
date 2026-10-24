package org.runnerup.relven.ui;

import android.content.Context;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import java.util.ArrayList;
import java.util.List;

/** Reads only currently connected Wear OS nodes through the existing Wear Data Layer. */
public final class WearConnectionProvider {
  public interface Callback {
    void onResult(boolean supported, boolean succeeded, List<String> connectedNodes);
  }

  private WearConnectionProvider() {}

  public static void query(Context context, Callback callback) {
    try {
      Wearable.getNodeClient(context.getApplicationContext())
          .getConnectedNodes()
          .addOnCompleteListener(
              task -> {
                if (!task.isSuccessful() || task.getResult() == null) {
                  callback.onResult(true, false, null);
                  return;
                }
                List<String> names = new ArrayList<>();
                for (Node node : task.getResult()) {
                  String name = node.getDisplayName();
                  names.add(name == null || name.trim().isEmpty() ? "Wear OS-klocka" : name);
                }
                callback.onResult(true, true, names);
              });
    } catch (RuntimeException exception) {
      callback.onResult(true, false, null);
    }
  }
}
