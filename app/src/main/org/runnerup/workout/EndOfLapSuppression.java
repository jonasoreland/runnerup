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

import android.util.Log;

/**
 * This suppression is for suppressing interval (distance) triggers really close
 * too end of lap (currently 5 meters)
 * 
 * @author jonas
 */

public class EndOfLapSuppression extends TriggerSuppression {

    enum t_type {
        t_Interval,
        t_EndOfLap,
        t_Empty
    }

    public static final TriggerSuppression EmptyLapSuppression = new EndOfLapSuppression(
            t_type.t_Empty);
    public static final TriggerSuppression EndOfLapSuppression = new EndOfLapSuppression(
            t_type.t_EndOfLap);

    private t_type suppressionType = t_type.t_Interval;
    private double lapDuration = 0;

    private static final double lapTimeLimit = 10; // seconds
    private static final double lapDistanceLimit = 5; // meters

    private static final double minTimeLimit = 3; // suppress Event.COMPLETED if lap/step is
                                    // shorter than this
    private static final double minDistanceLimit = 3;// suppress Event.COMPLETED if lap/step
                                       // is shorter than this

    private EndOfLapSuppression(t_type type) {
        suppressionType = type;
    }

    public EndOfLapSuppression(double lap) {
        this.lapDuration = lap;
    }

    public boolean suppress(Trigger trigger, Workout w) {
        switch (suppressionType) {
            case t_Interval:
                return suppressInterval(trigger, w);
            case t_EndOfLap:
                return suppressEndOfLap(trigger, w);
            case t_Empty:
                return suppressEmpty(trigger, w);
        }

        return false;
    }

    private boolean suppressEmpty(Trigger trigger, Workout w) {
        if (!(trigger instanceof EventTrigger)) {
            return false;
        }

        EventTrigger et = (EventTrigger) trigger;
        if (et.event != Event.COMPLETED) {
            return false;
        }

        Scope s = et.scope;
        if (w.getDistance(s) > minDistanceLimit) {
            return false;
        }

        return w.getTime(s) <= minTimeLimit;
    }

    private boolean suppressInterval(Trigger trigger, Workout w) {

        if (!(trigger instanceof IntervalTrigger)) {
            return false;
        }

        IntervalTrigger it = (IntervalTrigger) trigger;
        if (it.dimension != Dimension.DISTANCE) {
            return false;
        }

        double distance = w.getDistance(Scope.LAP);
        if ((distance - lapDuration) == lapDistanceLimit) {
            Log.e(getClass().getName(), "suppressing trigger! distance: " + distance + ", lapDistance: "
                    + lapDuration);
            return true;
        }
        
        return false;
    }

    private boolean suppressEndOfLap(Trigger trigger, Workout w) {
        /* suppress end of lap, if it's really end of step */
        if (!(trigger instanceof EventTrigger)) {
            return false;
        }

        EventTrigger et = (EventTrigger) trigger;
        if (et.scope != Scope.LAP || et.event != Event.COMPLETED)
            return false;

        Step s = w.getCurrentStep();
        if (s == null || s.getDurationType() == null)
            return false;

        switch (s.getDurationType()) {
            case HR:
            case HRZ:
            case CAD:
            case TEMPERATURE:
            case PRESSURE:
            case PACE:
            case SPEED:
                return false;
            case DISTANCE:
                return Math.abs(w.getDistance(Scope.STEP) - s.getDurationValue()) <= lapDistanceLimit;
            case TIME:
                return Math.abs(w.getTime(Scope.STEP) - s.getDurationValue()) <= lapTimeLimit;
            default:
                break;

        }
        return false;
    }
}
