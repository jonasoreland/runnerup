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

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.os.Build;

import org.runnerup.BuildConfig;
import org.runnerup.common.util.Constants.DB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@TargetApi(Build.VERSION_CODES.FROYO)
public class Step implements TickComponent {

    String name = null;

    /**
     * Intensity
     */
    Intensity intensity = Intensity.ACTIVE;

    /**
     * Duration
     */
    Dimension durationType = null;
    double durationValue = 0;

    /**
     * Target
     */
    Dimension targetType = null;
    Range targetValue = null;

    /**
     * Autolap (m)
     */
    double autolap = 0;

    /**
     * Triggers
     */
    final ArrayList<Trigger> triggers = new ArrayList<Trigger>();

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the durationType
     */
    public Dimension getDurationType() {
        return durationType;
    }

    /**
     * @param durationType the durationType to set
     */
    public void setDurationType(Dimension durationType) {
        this.durationType = durationType;
    }

    /**
     * @return the durationValue
     */
    public double getDurationValue() {
        return durationValue;
    }

    /**
     * @param durationValue the durationValue to set
     */
    public void setDurationValue(double durationValue) {
        this.durationValue = durationValue;
    }

    /**
     * @return the targetType
     */
    public Dimension getTargetType() {
        return targetType;
    }

    /**
     * @param targetType the targetType to set
     */
    public void setTargetType(Dimension targetType) {
        this.targetType = targetType;
    }

    /**
     * @return the targetValue
     */
    public Range getTargetValue() {
        return targetValue;
    }

    /**
     * @param targetValue the targetValue to set
     */
    public void setTargetValue(double targetValue) {
        this.targetValue = new Range(targetValue, targetValue);
    }

    public void setTargetValue(double min, double max) {
        this.targetValue = new Range(min, max);
    }

    public Intensity getIntensity() {
        return intensity;
    }
    public void setIntensity(Intensity intensity) {
        this.intensity = intensity;
    }

    /**
     * @return the autolap
     */
    public double getAutolap() {
        return autolap;
    }

    /**
     * @param val the autolap to set
     */
    public void setAutolap(double val) {
        this.autolap = val;
    }

    @Override
    public void onInit(Workout s) {
        for (Trigger t : triggers) {
            t.onInit(s);
        }
    }

    @Override
    public void onBind(Workout s, HashMap<String, Object> bindValues) {
        for (Trigger t : triggers) {
            t.onBind(s, bindValues);
        }
    }

    @Override
    public void onEnd(Workout s) {
        for (Trigger t : triggers) {
            t.onEnd(s);
        }
    }

    public void onRepeat(int current, int count) {
        for (Trigger t : triggers) {
            t.onRepeat(current, count);
        }
    }

    double stepStartTime = 0;
    double stepStartDistance = 0;
    double stepStartHeartbeats = 0;
    double lapStartTime = 0;
    double lapStartDistance = 0;
    double lapStartHeartbeats = 0;

    @Override
    public void onStart(Scope what, Workout s) {
        double time = s.getTime(Scope.ACTIVITY);
        double dist = s.getDistance(Scope.ACTIVITY);
        double beats = s.getHeartbeats(Scope.ACTIVITY);

        if (what == Scope.STEP) {
            stepStartTime = time;
            stepStartDistance = dist;
            stepStartHeartbeats = beats;
            if (s.isPaused())
                s.tracker.pause();
            else
                s.tracker.resume();
        } else if (what == Scope.LAP) {
            lapStartTime = time;
            lapStartDistance = dist;
            lapStartHeartbeats = beats;
            ContentValues tmp = new ContentValues();
            tmp.put(DB.LAP.INTENSITY, intensity.getValue());
            if (durationType != null) {
                switch (durationType) {
                    case TIME:
                        tmp.put(DB.LAP.PLANNED_TIME, (long) durationValue);
                        break;
                    case DISTANCE:
                        tmp.put(DB.LAP.PLANNED_DISTANCE, (long) durationValue);
                        break;
                    case PACE:
                    case SPEED:
                    case HR:
                    case HRZ:
                    case CAD:
                    case TEMPERATURE:
                    case PRESSURE:
                        break;
                }
            }
            if (targetType != null) {
                switch (targetType) {
                    case PACE:
                        tmp.put(DB.LAP.PLANNED_PACE, targetValue.maxValue);
                        break;
                    case SPEED:
                        if (targetValue.maxValue != 0) {
                            tmp.put(DB.LAP.PLANNED_PACE, 1.0d / targetValue.maxValue);
                        }
                        break;
                    case DISTANCE:
                    case TIME:
                    case HR:
                    case HRZ:
                    case CAD:
                    case TEMPERATURE:
                    case PRESSURE:
                        break;
                }
            }
            s.newLap(tmp);
        }

        for (Trigger t : triggers) {
            t.onStart(what, s);
        }
    }

    @Override
    public void onStop(Workout s) {
        s.tracker.stop();
        for (Trigger t : triggers) {
            t.onStop(s);
        }

        /**
         * Save current lap so that it shows in DetailActivity
         */
        long distance = Math.round(s.getDistance(Scope.LAP));
        long time = Math.round(s.getTime(Scope.LAP));
        long hr = Math.round(s.getHeartRate(Scope.LAP));
        if (distance > 0 || time > 0) {
            ContentValues tmp = new ContentValues();
            tmp.put(DB.LAP.DISTANCE, distance);
            tmp.put(DB.LAP.TIME, time);
            tmp.put(DB.LAP.AVG_HR, Math.round(hr));
            s.saveLap(tmp, /** next lap */
            false);
        }
    }

    @Override
    public void onPause(Workout s) {
        s.tracker.pause();
        for (Trigger t : triggers) {
            t.onPause(s);
        }
    }

    /**
     * @return true if finished
     */
    public boolean onTick(Workout s) {
        if (checkFinished(s)) {
            return true;
        }

        for (Trigger t : triggers) {
            t.onTick(s);
        }

        if (this.autolap > 0 && s.getDistance(Scope.LAP) >= this.autolap) {
            s.onNewLap();
        }
        return false;
    }

    public boolean onNextStep(Workout w) {
        return true; // move to next step
    }

    private boolean checkFinished(Workout s) {
        if (durationType == null)
            return false;

        return s.get(Scope.STEP, durationType) >= this.durationValue;
    }

    @Override
    public void onResume(Workout s) {
        for (Trigger t : triggers) {
            t.onResume(s);
        }
        s.tracker.resume();
    }

    @Override
    public void onComplete(Scope scope, Workout s) {
        if (scope == Scope.LAP) {
            long distance = Math.round(s.getDistance(scope));
            long time = Math.round(s.getTime(scope));
            double hr = s.getHeartRate(scope);
            if (distance > 0 || time > 0) {
                ContentValues tmp = new ContentValues();
                tmp.put(DB.LAP.DISTANCE, distance);
                tmp.put(DB.LAP.TIME, time);
                tmp.put(DB.LAP.AVG_HR, Math.round(hr));
                s.saveLap(tmp, /** next lap */
                true);
            }
        }
        for (Trigger t : triggers) {
            t.onComplete(scope, s);
        }

        if (scope == Scope.STEP) {
            for (Trigger t : triggers) {
                t.onEnd(s);
            }
        }
    }

    public double getDistance(Workout w, Scope s) {
        double d = w.getDistance(Scope.ACTIVITY);
        if (s == Scope.STEP) {
            return d - stepStartDistance;
        } else if (s == Scope.LAP) {
            return d - lapStartDistance;
        }
        if (BuildConfig.DEBUG) { throw new AssertionError(); }
        return 0;
    }

    public double getTime(Workout w, Scope s) {
        double t = w.getTime(Scope.ACTIVITY);
        if (s == Scope.STEP) {
            return t - stepStartTime;
        } else if (s == Scope.LAP) {
            return t - lapStartTime;
        }
        if (org.runnerup.BuildConfig.DEBUG) { throw new AssertionError(); }
        return 0;
    }

    public double getSpeed(Workout w, Scope s) {
        double t = getTime(w, s);
        double d = getDistance(w, s);
        if (t != 0) {
            return d / t;
        }
        return 0;
    }

    public double getHeartbeats(Workout w, Scope s) {
        double t = w.getHeartbeats(Scope.ACTIVITY);
        if (s == Scope.STEP) {
            return t - stepStartHeartbeats;
        } else if (s == Scope.LAP) {
            return t - lapStartHeartbeats;
        }
        return 0;
    }

    public double getDuration(Dimension dimension) {
        if (durationType == dimension)
            return durationValue;
        return 0;
    }

    public static Step createPauseStep(Dimension dim, double duration) {
        Step step = null;
        if (dim == null || dim == Dimension.TIME)
            step = new PauseStep();
        else
            step = new Step();

        step.intensity = Intensity.RESTING;
        step.durationType = dim;
        step.durationValue = duration;
        return step;
    }

    public void getSteps(Step parent, int i, List<Workout.StepListEntry> list) {
        list.add(new Workout.StepListEntry(list.size(), this, i, parent));
    }

    public Step getCurrentStep() {
        return this;
    }

    public int getRepeatCount() {
        return 0;
    }
    public void setRepeatCount(int val) { }

    public int getCurrentRepeat() {
        return 0;
    }

    public boolean isLastStep() {
        return true;
    }

    public boolean isPauseStep() { return false; }
}
