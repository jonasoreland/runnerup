/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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

/**
 * This suppression is for suppressing interval (distance) triggers really close
 * too end of lap (currently 5 meters)
 * 
 * @author jonas
 *
 */
public class EndOfLapSuppression extends TriggerSuppression {

	double lapDistance = 0;
	static double lapDistanceLimit = 5; // meters
	
	public EndOfLapSuppression(double lap) {
		this.lapDistance = lap;
	}

	public boolean suppress(Trigger trigger, Workout w) {
		if (! (trigger instanceof IntervalTrigger))
			return false;

		IntervalTrigger it = (IntervalTrigger) trigger;
		if (it.dimension != Dimension.DISTANCE)
			return false;
		
		double distance = w.getDistance(Scope.LAP);
		if (Math.abs(distance - lapDistance) > lapDistanceLimit)
			return false;

		System.err.println("suppressing trigger! distance: " + distance + ", lapDistance: " + lapDistance);
		
		return true;
	}
}
