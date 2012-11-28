package org.runnerup.workout;

public class IntervalTrigger extends Trigger {

	Scope scope = Scope.WORKOUT;
	Dimension dimension = Dimension.TIME;

	double first = 120;
	double interval = 120;
	int count = 0; // endless

	double next = 0;

	@Override
	public boolean onTick(Workout w) {
		if (next != 0) {
			double now = w.get(scope, dimension);
			if (now >= next) {
				w.log("fire interval: " + now);
				fire(w);
				scheduleNext(now);
			}
		}
		return false;
	}

	private void scheduleNext(double now) {
		if (interval == 0) {
			// last occurrence (maybe first)
			next = 0;
		} else {
			while (next <= now) {
				next += interval;
			}
			if (count != 0 && (next > (first + interval * (count - 1)))) {
				// no more occurrences
				next = 0;
			}
		}
	}

	@Override
	public void onStart(Scope what, Workout s) {
		if (this.scope == what) {
			next = first;
		}
	}

	@Override
	public void onPause(Workout s) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStop(Workout s) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onResume(Workout s) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onComplete(Scope what, Workout s) {
		// TODO Auto-generated method stub

	}
}
