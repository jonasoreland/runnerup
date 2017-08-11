/*
 * Copyright (C) 2014 niklas.weidemann
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

import android.location.Location;

public interface WorkoutInfo {
    double get(Scope scope, Dimension d);

    double getDistance(Scope scope);

    double getTime(Scope scope);

    double getSpeed(Scope scope);

    double getPace(Scope scope);

    double getDuration(Scope scope, Dimension dimension);

    double getRemaining(Scope scope, Dimension dimension);

    double getHeartRate(Scope scope);

    double getHeartRateZone(Scope scope);

    double getCadence(Scope scope);
    double getTemperature(Scope scope);
    double getPressure(Scope scope);

    int getSport();
    Intensity getIntensity();

    /* TODO make better/more elaborate state visible... */
    boolean isPaused();

    boolean isEnabled(Dimension dim, Scope scope);

    Location getLastKnownLocation();
}
