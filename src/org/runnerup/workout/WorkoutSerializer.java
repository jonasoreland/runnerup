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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.export.FormCrawler;
import org.runnerup.util.SafeParse;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.Pair;

@TargetApi(Build.VERSION_CODES.FROYO)
public class WorkoutSerializer {

    public static final String WORKOUTS_DIR = "workouts";

    private static String getString(JSONObject obj, String key) {
        try {
            return obj.getString(key);
        } catch (JSONException e) {
        }
        return null;
    }

    private static Integer getInt(JSONObject obj, String key) {
        try {
            return obj.getInt(key);
        } catch (JSONException e) {
        }
        return null;
    }

    private static class jsonstep
    {
        int order;
        Integer group;
        Integer parentGroup;
        RepeatStep parentStep;
        Step step;
    };

    public static Workout readJSON(Reader in) throws JSONException {
        JSONObject obj = FormCrawler.parse(in);
        obj = obj.getJSONObject("com.garmin.connect.workout.json.UserWorkoutJson");
        Workout w = new Workout();
        JSONArray steps = obj.getJSONArray("workoutSteps");
        int stepNo = 0;
        JSONObject step = null;
        ArrayList<jsonstep> list = new ArrayList<jsonstep>(4);
        while ((step = steps.optJSONObject(stepNo)) != null)
        {
            jsonstep js = parseStep(step);
            list.add(js);
            stepNo++;
        }

        for (jsonstep s : list) {
            if (s.parentGroup != null) {
                s.parentStep = findRepeatStep(list, s.parentGroup);
            } else {
            }
        }

        Collections.sort(list, new Comparator<jsonstep>() {
            @Override
            public int compare(jsonstep lhs, jsonstep rhs) {
                return lhs.order - rhs.order;
            }
        });

        for (jsonstep s : list) {
            if (s.parentStep != null) {
                s.parentStep.steps.add(s.step);
            }
            else {
                w.steps.add(s.step);
            }
        }

        return w;
    }

    private static RepeatStep findRepeatStep(ArrayList<jsonstep> list, int groupId) {
        for (jsonstep s : list) {
            if (s.group != null && s.group.intValue() == groupId &&
                    s.step instanceof RepeatStep) {
                return (RepeatStep) s.step;
            }
        }
        return null;
    }

    private static Intensity getIntensity(JSONObject obj) throws JSONException {
        String stepTypeKey = obj.getString("stepTypeKey");
        if (stepTypeKey.equalsIgnoreCase("warmup"))
            return Intensity.WARMUP;
        else if (stepTypeKey.equalsIgnoreCase("repeat"))
            return Intensity.REPEAT;
        else if (stepTypeKey.equalsIgnoreCase("rest"))
            return Intensity.RESTING;
        else if (stepTypeKey.equalsIgnoreCase("recovery"))
            return Intensity.RECOVERY;
        else if (stepTypeKey.equalsIgnoreCase("cooldown"))
            return Intensity.COOLDOWN;

        // @TODO look at intensityTypeKey too??
        else if (stepTypeKey.equalsIgnoreCase("interval"))
            return Intensity.ACTIVE;
        else if (stepTypeKey.equalsIgnoreCase("other"))
            return Intensity.ACTIVE;
        return null;
    }

    private static Pair<Dimension, Double> NullDimensionPair = new Pair<Dimension, Double>(null,
            0.0);

    private static Pair<Dimension, Double> getDuration(JSONObject obj, Intensity intensity)
            throws JSONException {
        Dimension dim = null;
        double val = 0;
        String endConditionTypeKey = obj.getString("endConditionTypeKey");
        if (endConditionTypeKey.equalsIgnoreCase("lap.button")) {
            return NullDimensionPair;
        } else if (endConditionTypeKey.equalsIgnoreCase("iterations")) {
            val = SafeParse.parseDouble(getString(obj, "endConditionValue"), 1);
        } else if (endConditionTypeKey.equalsIgnoreCase("distance")) {
            dim = Dimension.DISTANCE;
            val = SafeParse.parseDouble(getString(obj, "endConditionValue"), 0);
            val = scale(val, obj, "endConditionUnitKey");
        } else if (endConditionTypeKey.equalsIgnoreCase("time")) {
            dim = Dimension.TIME;
            val = SafeParse.parseDouble(getString(obj, "endConditionValue"), 0);
            val = scale(val, obj, "endConditionUnitKey");
        } else if (endConditionTypeKey.equalsIgnoreCase("calories")) {
            // not implemented
            return NullDimensionPair;
        } else if (endConditionTypeKey.equalsIgnoreCase("heart.rate")) {
            // not implemented
            return NullDimensionPair;
        }

        return new Pair<Dimension, Double>(dim, val);
    }

    private static double scale(double val, JSONObject obj, String key) {
        String unit = getString(obj, key);
        if (unit == null)
            return val;

        if (unit.equalsIgnoreCase("dimensionless"))
            return val;

        /**
         * The below are one I found in the json objects...
         */
        if (unit.equalsIgnoreCase("centimeter"))
            return val / 100.0;
        if (unit.equalsIgnoreCase("ms"))
            return val / 1000.0;
        if (unit.equalsIgnoreCase("kilojoule"))
            return val;
        if (unit.equalsIgnoreCase("bpm"))
            return val;

        /**
         * The below I just added for "completeness"
         */
        if (unit.equalsIgnoreCase("millimeter"))
            return val / 1000.0;
        if (unit.equalsIgnoreCase("kilometer"))
            return val * 1000.0;
        if (unit.equalsIgnoreCase("miles"))
            return val * 1609.34;

        return val;
    }

    private static Pair<Dimension, Range> NullTargetPair = new Pair<Dimension, Range>(null, null);

    private static Pair<Dimension, Range> getTarget(JSONObject obj) {
        String targetTypeKey = getString(obj, "targetTypeKey");
        if (targetTypeKey == null)
            return NullTargetPair;
        if (targetTypeKey.equalsIgnoreCase("no.target"))
            return NullTargetPair;

        Dimension dim = null;
        Range range = null;
        if (targetTypeKey.equalsIgnoreCase("pace.zone")) {
            dim = Dimension.PACE;
            range = new Range(SafeParse.parseDouble(getString(obj, "targetValueOne"), 0),
                    SafeParse.parseDouble(getString(obj, "targetValueTwo"), 0));
            scale(range, dim, obj, "targetValueUnitKey");
        } else if (targetTypeKey.equalsIgnoreCase("speed.zone")) {
            dim = Dimension.SPEED;
            range = new Range(SafeParse.parseDouble(getString(obj, "targetValueOne"), 0),
                    SafeParse.parseDouble(getString(obj, "targetValueTwo"), 0));
            scale(range, dim, obj, "targetValueUnitKey");
        } else if (targetTypeKey.equalsIgnoreCase("heart.rate.zone")) {
            dim = Dimension.HR;
            range = new Range(SafeParse.parseDouble(getString(obj, "targetValueOne"), 0),
                    SafeParse.parseDouble(getString(obj, "targetValueTwo"), 0));
            scale(range, dim, obj, "targetValueUnitKey");
        } else if (targetTypeKey.equalsIgnoreCase("cadence")) {
            // Not implemented
            return NullTargetPair;
        }

        return new Pair<Dimension, Range>(dim, range);
    }

    private static void scale(Range range, Dimension dim, JSONObject obj, String key) {
        String unit = getString(obj, key);
        if (unit == null)
            return;

        if (unit.equalsIgnoreCase("dimensionless"))
            return;

        double factor = 1.0;
        Dimension unitDim = dim;
        /**
         * The below are one I found in the json objects...
         */
        if (unit.equalsIgnoreCase("centimetersPerMillisecond")) {
            factor = 1000.0 / 100.0;
            unitDim = Dimension.SPEED;
        } else if (unit.equalsIgnoreCase("stepsPerMinute")) {
            // not implemented
        } else if (unit.equalsIgnoreCase("bpm")) {
            // not implemented
        }

        /**
         * The below I just added for "completeness"
         */
        else if (unit.equalsIgnoreCase("metersPerMillisecond")) {
            factor = 1000.0 / 1.0;
            unitDim = Dimension.SPEED;
        } else if (unit.equalsIgnoreCase("metersPerSecond")) {
            factor = 1.0 / 1.0;
            unitDim = Dimension.SPEED;
        } else if (unit.equalsIgnoreCase("centimetersPerSecond")) {
            factor = 1.0 / 100.0;
            unitDim = Dimension.SPEED;
        }

        if (unit.equalsIgnoreCase("millisecondsPerCentimeter")) {
            factor = 100.0 / 1000.0;
            unitDim = Dimension.PACE;
        } else if (unit.equalsIgnoreCase("millisecondsPerMeter")) {
            factor = 1.0 / 1000.0;
            unitDim = Dimension.PACE;
        } else if (unit.equalsIgnoreCase("secondsPerMeter")) {
            factor = 1.0 / 1.0;
            unitDim = Dimension.PACE;
        } else if (unit.equalsIgnoreCase("secondsPerCentimeters")) {
            factor = 100.0 / 1.0;
            unitDim = Dimension.PACE;
        }

        range.minValue *= factor;
        range.maxValue *= factor;
        if (dim == Dimension.SPEED && unitDim == Dimension.PACE ||
                dim == Dimension.PACE && unitDim == Dimension.SPEED)
        {
            range.minValue = range.minValue == 0 ? 0 : 1.0 / range.minValue;
            range.maxValue = range.maxValue == 0 ? 0 : 1.0 / range.maxValue;
            if (range.minValue > range.maxValue) {
                double tmp = range.minValue;
                range.minValue = range.maxValue;
                range.maxValue = tmp;
            }
        }
    }

    private static jsonstep parseStep(JSONObject obj) throws JSONException {
        jsonstep js = new jsonstep();
        js.order = obj.getInt("stepOrder");
        js.group = getInt(obj, "groupId");
        js.parentGroup = getInt(obj, "parentGroupId");
        Intensity intensity = getIntensity(obj);
        Pair<Dimension, Double> duration = getDuration(obj, intensity);
        Pair<Dimension, Range> target = getTarget(obj);
        switch (intensity) {
            case REPEAT: {
                RepeatStep rs = new RepeatStep();
                rs.repeatCount = duration.second.intValue();
                js.step = rs;
                break;
            }
            case RESTING:
                js.step = Step.createPauseStep(duration.first, duration.second);
                break;
            case ACTIVE:
            case WARMUP:
            case COOLDOWN:
            case RECOVERY:
                js.step = new Step();
                js.step.intensity = intensity;
                js.step.durationType = duration.first;
                js.step.durationValue = duration.second;
                js.step.targetType = target.first;
                js.step.targetValue = target.second;
                break;
        }

        return js;
    }

    public static File getFile(Context ctx, String name) {
        return new File(ctx.getDir(WORKOUTS_DIR, 0).getPath() + File.separator + name);
    }

    public static Workout readFile(Context ctx, String name) throws FileNotFoundException,
            JSONException {
        File fin = getFile(ctx, name);
        System.err.println("reading " + fin.getPath());
        return readJSON(new FileReader(fin));
    }
}
