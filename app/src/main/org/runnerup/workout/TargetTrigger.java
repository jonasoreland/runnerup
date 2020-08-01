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

import android.util.Log;

import org.runnerup.BuildConfig;

import java.util.Arrays;


public class TargetTrigger extends Trigger {

    private boolean inited = false;
    private boolean paused = false;

    private int graceCount = 30; //
    @SuppressWarnings("FieldCanBeLocal")
    private final int initialGrace = 20;
    private final int minGraceCount; //

    Scope scope = Scope.STEP;
    private Dimension dimension;

    Range range = null;

    private int cntMeasures = 0;
    private final double[] measure;
    @SuppressWarnings("FieldCanBeLocal")
    private final int skip_values;
    private final double[] sort_measure;
    private double lastTimestamp = 0;

    private final double[] measure_time;
    private final double[] measure_distance;

    /**
     * cache computing of median
     */
    private double lastVal = 0;
    private int lastValCnt = 0;

    public TargetTrigger(Dimension dim, int movingAverageSeconds, int graceSeconds) {
        dimension = dim;
        measure = new double[movingAverageSeconds];
        sort_measure = new double[movingAverageSeconds];

        if (dimension == Dimension.HRZ)
            dimension = Dimension.HR;

        measure_time = new double[movingAverageSeconds];
        measure_distance = new double[movingAverageSeconds];

        minGraceCount = graceSeconds;
        skip_values = (5 * movingAverageSeconds) / 100; // ignore 5% lowest and
                                                        // 5% higest values

        reset();
    }

    @Override
    public boolean onTick(Workout w) {
        if (paused) {
            return false;
        }

        if (!w.isEnabled(dimension, Scope.STEP)) {
            inited = false;
            return false;
        }

        double time_now = w.get(Scope.STEP, Dimension.TIME);

        if (time_now < lastTimestamp) {
            Log.i(getClass().getName(), "time_now < lastTimestamp");
            reset();
            return false;
        }

        if (!inited) {
            Log.i(getClass().getName(), "inited == false");
            lastTimestamp = time_now;
            initMeasurement(w, time_now);
            inited = true;
            return false;
        }

        if ((time_now - lastTimestamp) < 1.0) {
            return false;
        }

        final int elapsed_seconds = (int) (time_now - lastTimestamp);
        lastTimestamp = time_now;

        try {
            double val_now = getMeasurement(w, time_now);
            for (int i = 0; i < elapsed_seconds; i++) {
                addObservation(val_now);
            }
            // Log.e(getName(), "val_now: " + val_now + " elapsed: " +
            // elapsed_seconds);

            if (graceCount > 0) { // only emit coaching ever so often
            // Log.e(getName(), "graceCount: " + graceCount);
                graceCount -= elapsed_seconds;
            } else {
                double avg = getValue();
                double cmp = range.compare(avg);
                // Log.e(getName(), " => avg: " + avg + " => cmp: " + cmp);
                if (cmp == 0) {
                    graceCount = minGraceCount;
                    return false;
                }
                fire(w);
                graceCount = minGraceCount;
            }
        } catch (ArithmeticException ex) {
            return false;
        }
        return false;
    }

    private void addObservation(double val_now) {
        int pos = cntMeasures % measure.length;
        measure[pos] = val_now;
        cntMeasures++;
    }

    public Dimension getDimension() { return dimension; }

    public Scope getScope() { return scope; }

    public Range getRange() { return range; }

    public double getValue() {
        if (cntMeasures == lastValCnt)
            return lastVal;

        //not all values in the measure array are meaningful when
        //cntMeasures is small so we adjust for it.
        int meaningful_length = Math.min(cntMeasures,measure.length);
        //should the percentage of values skipped be a variable of the class?
        int meaningful_skip_values = (5*meaningful_length)/100;
        System.arraycopy(measure, 0, sort_measure, 0, meaningful_length);
        java.util.Arrays.sort(sort_measure,0,meaningful_length);
        double cnt = 0;
        double val = 0;
        for (int i = meaningful_skip_values; i < meaningful_length - meaningful_skip_values; i++) {
            val += sort_measure[i];
            cnt++;
        }
        lastVal = val / cnt;
        lastValCnt = cntMeasures;
        return lastVal;
    }

    private void reset() {
        Arrays.fill(measure, 0);
        inited = false;
        cntMeasures = 0;
        graceCount = initialGrace;
        lastTimestamp = 0;

        lastVal = 0;
        lastValCnt = 0;
    }

    private void initMeasurement(Workout w, double time_now) {
        switch (dimension) {
            case PACE:
            case SPEED:
                double distance_now = w.get(scope, Dimension.DISTANCE);
                if (lastTimestamp == 0) {
                    measure_time[0] = time_now;
                    measure_distance[0] = distance_now;
                }
            case DISTANCE:
            case HR:
            case HRZ:
            case CAD:
            case TEMPERATURE:
            case PRESSURE:
            case TIME:
            default:
                break;
        }
    }

    private double getMeasurement(Workout w, double time_now) {
        switch (dimension) {
            case PACE:
            case SPEED:
                double distance_now = w.get(scope, Dimension.DISTANCE);

                int oldpos = 0;
                int newpos = (cntMeasures + 1) % measure_time.length;
                if (cntMeasures >= measure_time.length) {
                    oldpos = newpos;
                }
                double delta_distance = distance_now - measure_distance[oldpos];
                double delta_time = time_now - measure_time[oldpos];

                measure_time[newpos] = time_now;
                measure_distance[newpos] = distance_now;

                if (dimension == Dimension.PACE) {
                    return delta_time / delta_distance;
                } else {
                    if (BuildConfig.DEBUG && dimension != Dimension.SPEED) { throw new AssertionError(); }
                    return delta_distance / delta_time;
                }
            case DISTANCE:
            case HR:
            case HRZ:
            case CAD:
            case TEMPERATURE:
            case PRESSURE:
            case TIME:
            default:
                break;
        }
        return w.get(Scope.CURRENT, dimension);
    }

    @Override
    public void onRepeat(int current, int limit) {
    }

    @Override
    public void onStart(Scope what, Workout s) {
        if (this.scope == what) {
            reset();
            for (Feedback f : triggerAction) {
                f.onStart(s);
            }
        }
    }

    @Override
    public void onPause(Workout s) {
        paused = true;
    }

    @Override
    public void onStop(Workout s) {
        paused = true;
    }

    @Override
    public void onResume(Workout s) {
        paused = false;
        reset();
    }

    @Override
    public void onComplete(Scope what, Workout s) {
    }
}
