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
public enum Dimension {

	TIME(1), DISTANCE(2), SPEED(3);

	int value = 0;

	private Dimension(int val) {
		this.value = val;
	}

	/**
	 * @return the value
	 */
	public int getValue() {
		return value;
	}

	public boolean equal(Dimension what) {
		if (what == null || what.value != this.value)
			return false;
		return true;
	}

	private String elapsedCue(Resources res, long seconds) {
		long hours = 0;
		long minutes = 0;
		if (seconds >= 3600) {
			hours = seconds / 3600;
			seconds -= hours * 3600;
		}
		if (seconds >= 60) {
			minutes = seconds / 60;
			seconds -= minutes * 60;
		}
		StringBuilder s = new StringBuilder();
		if (hours > 0) {
			s.append(hours).append(" ").append(res.getString(R.string.hours));
		}
		if (minutes > 0) {
			s.append(minutes).append(" ")
					.append(res.getString(R.string.minutes));
		}
		if (seconds > 0) {
			s.append(seconds).append(" ")
					.append(res.getString(R.string.seconds));
		}
		return s.toString();
	}

	public String getCue(Context ctx, double val) {
		Resources res = ctx.getResources();
		switch (this) {
		case TIME:
			return elapsedCue(res, (long) val);
		case DISTANCE:
			return "" + (((double) ((long) (val * 10))) / 10) + " "
					+ res.getString(R.string.kilometers);
		case SPEED:
			return elapsedCue(res, (long) Speed.convert(val, Speed.PACE_SPK))
					+ " " + res.getString(R.string.perkilometer);
		}
		// TODO Auto-generated method stub
		return "";
	}
}
