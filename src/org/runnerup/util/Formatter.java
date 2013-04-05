/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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
package org.runnerup.util;

import org.runnerup.R;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Speed;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateUtils;

public class Formatter {

	Context context = null;
	Resources resources = null;
	java.text.DateFormat dateFormat = null;
	java.text.DateFormat timeFormat = null;

	public static final int CUE = 1;           // for text to speech
	public static final int CUE_SHORT = 2;     // brief for tts
	public static final int CUE_LONG = 3;      // long for tts
	public static final int TXT = 4;           // same as TXT_SHORT
	public static final int TXT_SHORT = 5;     // brief for printing
	public static final int TXT_LONG = 6;     // long for printing
	
	public Formatter(Context ctx) {
		context = ctx;
		resources = ctx.getResources();

		dateFormat = android.text.format.DateFormat.getDateFormat(ctx);
		timeFormat = android.text.format.DateFormat.getTimeFormat(ctx);
	}

	public String format(int target, Dimension dimension, double value) {
		switch(dimension) {
		case DISTANCE:
			return formatDistance(target, (long)value);
		case TIME:
			return formatElapsedTime(target, (long)value);
		case PACE:
			return formatPace(target, value);
		}
		return "";
	}
		
	public String formatElapsedTime(int target, long seconds) {
		switch(target) {
		case CUE:
		case CUE_SHORT:
			return cueElapsedTime(seconds, false);
		case CUE_LONG:
			return cueElapsedTime(seconds, true);
		case TXT:
		case TXT_SHORT:
			return DateUtils.formatElapsedTime(seconds);
		case TXT_LONG:
			return txtElapsedTime(seconds);
		}
		return "";
	}

	private String cueElapsedTime(long seconds, boolean includeDimension) {
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
			includeDimension = true;
			s.append(hours).append(" ").append(resources.getString(hours > 1 ? R.string.hours : R.string.hour));
		}
		if (minutes > 0) {
			if (hours > 0)
				s.append(" ");
			includeDimension = true;
			s.append(minutes).append(" ").append(resources.getString(minutes > 1 ? R.string.minutes : R.string.minute));
		}
		if (seconds > 0) {
			if (hours > 0 || minutes > 0)
				s.append(" ");
			
			if (includeDimension) {
				s.append(seconds).append(" ").append(resources.getString(seconds > 1 ? R.string.seconds : R.string.second));
			} else {
				s.append(seconds);
			}
		}
		return s.toString();
	}

	private String txtElapsedTime(long seconds) {
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
			s.append(hours).append(" ").append(resources.getString(R.string.txt_elapsed_h));
		}
		if (minutes > 0) {
			if (hours > 0)
				s.append(" ");
			s.append(minutes).append(" ").append(resources.getString(R.string.txt_elapsed_m));
		}
		if (seconds > 0) {
			if (hours > 0 || minutes > 0)
				s.append(" ");
			s.append(seconds).append(" ").append(resources.getString(R.string.txt_elapsed_s));
		}
		return s.toString();
	}

	
	/**
	 * Format pace
	 * 
	 * @param target
	 * @param meter_per_seconds
	 * @return
	 */
	public String formatPace(int target, double meter_per_seconds) {
		switch(target) {
		case CUE:
		case CUE_SHORT:
		case CUE_LONG:
			return cuePace(meter_per_seconds);
		case TXT:
		case TXT_SHORT:
			return txtPace(meter_per_seconds, false);
		case TXT_LONG:
			return txtPace(meter_per_seconds, true);
		}
		return "";
	}
	
	/**
	 * 
	 * @param speed_meter_per_second
	 * @return string suitable for printing according to settings
	 */
	private String txtPace(double meter_per_second, boolean includeUnit) {
		//TODO read preferences for preferred unit
		long val = (long) Speed.convert(meter_per_second, Speed.PACE_SPK);
		String str = DateUtils.formatElapsedTime(val);
		if (includeUnit == false)
			return str;
		else
			return str + "/km";
	}

	private String cuePace(double meter_per_seconds) {
		long seconds_per_unit = (long) Speed.convert(meter_per_seconds, Speed.PACE_SPK);
		long hours_per_unit = 0;
		long minutes_per_unit = 0;
		if (seconds_per_unit >= 3600) {
			hours_per_unit = seconds_per_unit / 3600;
			seconds_per_unit -= hours_per_unit * 3600;
		}
		if (seconds_per_unit >= 60) {
			minutes_per_unit = seconds_per_unit / 60;
			seconds_per_unit -= minutes_per_unit * 60;
		}
		StringBuilder s = new StringBuilder();
		if (hours_per_unit > 0) {
			s.append(hours_per_unit).append(" ").append(resources.getString(hours_per_unit > 1 ? R.string.hours : R.string.hour));
		}
		if (minutes_per_unit > 0) {
			if (hours_per_unit > 0)
				s.append(" ");
			s.append(minutes_per_unit).append(" ").append(resources.getString(minutes_per_unit > 1 ? R.string.minutes : R.string.minute));
		}
		if (seconds_per_unit > 0) {
			if (hours_per_unit > 0 || minutes_per_unit > 0)
				s.append(" ");
			s.append(seconds_per_unit).append(" ").append(resources.getString(seconds_per_unit > 1 ? R.string.seconds : R.string.second));
		}
		s.append(" " + resources.getString(R.string.perkilometer));
		return s.toString();
	}

	/**
	 * 
	 * @param target
	 * @param seconds_since_epoch
	 * @return
	 */
	public String formatDateTime(int target, long seconds_since_epoch) {
		// ignore target
		StringBuffer s = new StringBuffer();
		s.append(dateFormat.format(seconds_since_epoch * 1000)); // takes milliseconds as argument
		s.append(" ");
		s.append(timeFormat.format(seconds_since_epoch * 1000));
		return s.toString();
	}

	/**
	 * 
	 * @param target
	 * @param meters
	 * @return
	 */
	public String formatDistance(int target, long meters) {
		switch(target) {
		case CUE:
		case CUE_LONG:
		case CUE_SHORT:
			return cueDistance(meters, false);
		case TXT:
		case TXT_SHORT:
			return cueDistance(meters, true);
		case TXT_LONG:
			return Long.toString(meters) + " m";
		}
		return null;
	}

	private String cueDistance(long meters, boolean txt) {
		double base_val = 1000; // 1km
		double decimals = 1;
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
			double base = ((double)meters) / base_val;
			double exp = Math.pow(10, decimals);
			double val = Math.round(base * exp) / exp;
			s.append(val).append(" ").append(resources.getString(base > 1 ? res_base_multi : res_base));
		} else {
			s.append(meters);
			s.append(" ").append(resources.getString(meters > 1 ? res_meters : res_meter));
		}
		return s.toString();
	}
}
