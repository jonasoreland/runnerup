package org.runnerup.workout;

public abstract class Trigger implements TickComponent {

	Feedback triggerAction[] = new Feedback[0];

	Scope scope = Scope.WORKOUT;

	@Override
	public void onInit(Workout s) {
	}

	@Override
	public void onEnd(Workout s) {
	}

	public void fire(Workout s) {
		for (Feedback f : triggerAction) {
			s.addFeedback(f);
		}
	}
}
