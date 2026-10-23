package org.runnerup.relven.ui;

public interface WorkoutSetupListener {
  void onPreferenceChanged(String key, String value);

  void onSelectWorkout(String name);

  void onEditStep(int index);

  void onManageWorkouts();

  void onManageAudio(int tab);

  void onGymNameChanged(String name);

  void onGymQueryChanged(String query);

  void onAddGymExercise(String exerciseId);

  void onRemoveGymExercise(String exerciseId);

  void onUpdateGymExercise(String exerciseId, String sets, String repetitions, String restSeconds);

  void onRetryGymCatalog();

  void onStartGymWorkout();
}
