package org.runnerup.workout;

public interface WorkoutComponent {
	/**
	 * Called before workout begins
	 */
	public void onInit(Workout s);

	/**
	 * Called when *what* starts
	 */
	public void onStart(Scope what, Workout s);

	/**
	 * Called when user press PauseButton after this either onResume or
	 * onComplete will be called
	 */
	public void onPause(Workout s);

	/**
	 * Called when user press StopButton after this either onResume or
	 * onComplete will be called
	 */
	public void onStop(Workout s);

	/**
	 * Called when user press ResumeButton
	 */
	public void onResume(Workout s);

	/**
	 * Called when *what* is completed
	 */
	public void onComplete(Scope what, Workout s);

	/**
	 * Called after workout has ended
	 */
	public void onEnd(Workout s);
}
