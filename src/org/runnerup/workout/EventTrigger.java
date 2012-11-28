/*
 * Copyright (C) 2012 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
