package org.runnerup.workout;

public class EventTrigger extends Trigger {

	Scope scope = Scope.ACTIVITY;
	Event event = Event.STARTED;

	@Override
	public boolean onTick(Workout w) {
		return false;
	}

	@Override
	public void onStart(Scope what, Workout s) {
		if (this.scope == what && this.event == Event.STARTED) {
			s.log("fire onStart");
			fire(s);
		}
	}

	@Override
	public void onPause(Workout s) {
		if (this.event == Event.PAUSED) {
			s.log("fire onPause");
			fire(s);
		}
	}

	@Override
	public void onStop(Workout s) {
		if (this.event == Event.STOPPED) {
			s.log("fire onStop");
			fire(s);
		}
	}

	@Override
	public void onResume(Workout s) {
		if (this.event == Event.RESUMED) {
			s.log("fire onResume");
			fire(s);
		}
	}

	@Override
	public void onComplete(Scope what, Workout s) {
		if (this.scope == what && this.event == Event.COMPLETED) {
			s.log("fire onComplete");
			fire(s);
		}
	}

}
