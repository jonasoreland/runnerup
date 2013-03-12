/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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
import android.text.format.DateUtils;

/**
 * This is just constant
 */
public enum Dimension {

	TIME(1, R.string.txt_dimension_time), 
	DISTANCE(2, R.string.txt_dimension_distance),
	SPEED(3, R.string.txt_dimension_speed);

	int value = 0;
	int textId = 0;
	
	private Dimension(int val, int textId) {
		this.value = val;
		this.textId = textId;
	}

	/**
	 * @return the value
	 */
	public int getValue() {
		return value;
	}

	public int getTextId() {
		return textId;
	}

	public boolean equal(Dimension what) {
		if (what == null || what.value != this.value)
			return false;
		return true;
	}

	private static String elapsedCue(Resources res, long seconds, boolean emitDimension) {
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
			emitDimension = true;
			s.append(hours).append(" ").append(res.getString(R.string.hours));
		}
		if (minutes > 0) {
			if (hours > 0)
				s.append(" ");
			emitDimension = true;
			s.append(minutes).append(" ").append(res.getString(R.string.minutes));
		}
		if (seconds > 0) {
			if (hours > 0 || minutes > 0)
				s.append(" ");
			
			if (emitDimension) {
				s.append(seconds).append(" ").append(res.getString(R.string.seconds));
			} else {
				s.append(seconds);
			}
		}
		return s.toString();
	}

	public static String distanceCue(Resources res, long meters, boolean emitDimension, boolean txt) {
		double base_val = 1000; // 1km
		int res_base = R.string.txt_distance_kilometer;
		int res_base_multi = R.string.txt_distance_kilometers;
		int res_meter = R.string.txt_distance_meter;
		int res_meters = R.string.txt_distance_meters;

		if (txt) {
			res_base = R.string.txt_distance_km;
			res_base_multi = R.string.txt_distance_km;
			res_meter = R.string.txt_distance_m;
			res_meters = R.string.txt_distance_m;
		}
		
		StringBuffer s = new StringBuffer();
		if (meters >= base_val) {
			emitDimension = true;
			double base = ((double)meters) / base_val;
			double val = Math.round(base * 10d) / 10.d;
			s.append(val).append(" ").append(res.getString(base > 1 ? res_base_multi : res_base));
		} else {
			s.append(meters);
			if (emitDimension) {
				s.append(" ").append(res.getString(meters > 1 ? res_meters : res_meter));
			}
		}
		return s.toString();
	}
	
	public String getCue(Context ctx, double val, boolean emitDimension) {
		Resources res = ctx.getResources();
		switch (this) {
		case TIME:
			return elapsedCue(res, (long) val, emitDimension);
		case DISTANCE:
			return distanceCue(res, (long)val, emitDimension, false);
		case SPEED:
			return elapsedCue(res, (long) Speed.convert(val, Speed.PACE_SPK), emitDimension)
					+ (emitDimension ? " " + res.getString(R.string.perkilometer) : "");
		}
		return "";
	}

	public String getRemainingText(Context ctx, double d) {
		Resources res = ctx.getResources();
		switch (this) {
		case TIME:
			return DateUtils.formatElapsedTime((long)d);
		case DISTANCE:
			return distanceCue(res, (long)d, true, true);
		}
		return "";
	}
}
