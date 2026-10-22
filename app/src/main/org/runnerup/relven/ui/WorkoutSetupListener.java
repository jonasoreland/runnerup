package org.runnerup.relven.ui;

public interface WorkoutSetupListener {
  void onPreferenceChanged(String key, String value);

  void onSelectWorkout(String name);

  void onManageWorkouts();

  void onManageAudio(int tab);
}
