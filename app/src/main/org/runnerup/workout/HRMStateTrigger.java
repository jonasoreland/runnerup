package org.runnerup.workout;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.hr.HRProvider;

public class HRMStateTrigger extends Trigger {
  private boolean isConnected;

  @Override
  JSONObject toRuntimeCheckpoint() throws JSONException {
    return super.toRuntimeCheckpoint().put("connected", isConnected);
  }

  @Override
  boolean restoreRuntimeCheckpoint(JSONObject state) throws JSONException {
    if (!super.restoreRuntimeCheckpoint(state)) return false;
    isConnected = state.getBoolean("connected");
    return true;
  }

  @Override
  public boolean onTick(Workout w) {
    boolean newState = getState(w);
    if (newState != isConnected) {
      isConnected = newState;
      fire(w);
    }
    return false;
  }

  @Override
  public void onRepeat(int current, int limit) {}

  private boolean getState(Workout s) {
    if (s.tracker == null) {
      // Taskkiller?
      return false;
    }
    HRProvider hrProvider = s.tracker.getHRProvider();
    if (hrProvider != null) return (hrProvider.isConnected());
    return (false);
  }

  @Override
  public void onStart(Scope what, Workout s) {
    isConnected = getState(s);
  }

  @Override
  public void onPause(Workout s) {}

  @Override
  public void onStop(Workout s) {}

  @Override
  public void onResume(Workout s) {}

  @Override
  public void onComplete(Scope what, Workout s) {}
}
