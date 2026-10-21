package org.runnerup.workout.feedback;

/** Receives formatted countdown values from the workout engine. */
public interface CountdownDisplay {
  void show(String text);

  void hide();

  void remove();
}
