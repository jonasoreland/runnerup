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

import org.runnerup.R;

import android.content.Context;
import android.content.res.Resources;

/**
 * This is just constant
 */
public enum Event {

	STARTED(1), PAUSED(2), STOPPED(3), RESUMED(4), COMPLETED(5);

	int value = 0;

	private Event(int val) {
		this.value = val;
	}

	/**
	 * @return the eventValue
	 */
	public int getValue() {
		return value;
	}

	public boolean equal(Event what) {
		if (what == null || what.value != this.value)
			return false;
		return true;
	}

	public String getCue(Context ctx) {
		Resources res = ctx.getResources();
		switch (this) {
		case STARTED:
			return res.getString(R.string.started);
		case PAUSED:
			return res.getString(R.string.paused);
		case RESUMED:
			return res.getString(R.string.resumed);
		case STOPPED:
			return res.getString(R.string.stopped);
		case COMPLETED:
			return res.getString(R.string.completed);
		}
		// TODO Auto-generated method stub
		return null;
	}
}
