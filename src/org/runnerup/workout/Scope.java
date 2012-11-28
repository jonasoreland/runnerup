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
public enum Scope {

	WORKOUT(1), ACTIVITY(2), LAP(3);

	int value = 0;

	private Scope(int val) {
		this.value = val;
	}

	/**
	 * @return the scopeValue
	 */
	public int getValue() {
		return value;
	}

	public boolean equal(Scope what) {
		if (what == null || what.value != this.value)
			return false;
		return true;
	}

	public String getCue(Context ctx) {
		Resources res = ctx.getResources();
		switch (this) {
		case WORKOUT:
			return res.getString(R.string.workout);
		case ACTIVITY:
			return res.getString(R.string.activity);
		case LAP:
			return res.getString(R.string.lap);
		}
		return "";
	}
}
