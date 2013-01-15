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
package org.runnerup.workout;

public enum Intensity {

	/**
	 * Running
	 */
	ACTIVE(0),
	
	/**
	 *
	 */
	RESTING(1),
	
	/**
	 * Warm up
	 */
	WARMUP(2),
	
	/**
	 * Cool down
	 */
	COOLDOWN(3);
	
	int value;
	Intensity(int val) {
		this.value = val;
	}

	public int getValue() {
		return value;
	}
}
