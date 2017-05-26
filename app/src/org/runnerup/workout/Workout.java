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
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;

import org.runnerup.BuildConfig;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.tracker.Tracker;
import org.runnerup.tracker.component.TrackerHRM;
import org.runnerup.tracker.component.TrackerCadence;
import org.runnerup.tracker.component.TrackerTemperature;
import org.runnerup.tracker.component.TrackerPressure;
import org.runnerup.util.HRZones;
import org.runnerup.workout.feedback.RUTextToSpeech;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * This class is the top level object for a workout, it is being called by
 * RunActivity, and by the Workout components
 */

@TargetApi(Build.VERSION_CODES.FROYO)
public class Workout implements WorkoutComponent, WorkoutInfo {

    long lap = 0;
    int currentStepNo = -1;
    int workoutType = Constants.WORKOUT_TYPE.BASIC;
    Step currentStep = null;
    boolean paused = false;
    final ArrayList<Step> steps = new ArrayList<Step>();
    final ArrayList<WorkoutStepListener> stepListeners = new ArrayList<WorkoutStepListener>();
    int sport = DB.ACTIVITY.SPORT_RUNNING;
    private boolean mute;

    class PendingFeedback {
        int depth = 0;
        final HashSet<Feedback> set = new HashSet<Feedback>(); // For uniquing

        void init() {
            depth++;
        }

        void add(Feedback f) {
            if (set.contains(f))
                return;
            set.add(f);

            try {
                f.emit(Workout.this, tracker.getApplicationContext());
            } catch (Exception ex) {
                // make sure that no small misstake crashes a workout...
                ex.printStackTrace();
            }
        }

        boolean end() {
            --depth;
            if (depth == 0) {
                set.clear();
                try {
                    Workout.this.textToSpeech.emit();
                } catch (Exception ex) {
                    // make sure that no small misstake crashes a workout...
                    ex.printStackTrace();
                }
                return true;
            }
            return false;
        }
    }

    final PendingFeedback pendingFeedback = new PendingFeedback();

    Tracker tracker = null;
    SharedPreferences audioCuePrefs;
    HRZones hrZones = null;
    RUTextToSpeech textToSpeech = null;

    public static final String KEY_TTS = "tts";
    public static final String KEY_COUNTER_VIEW = "CountdownView";
    public static final String KEY_FORMATTER = "Formatter";
    public static final String KEY_HRZONES = "HrZones";
    public static final String KEY_MUTE = "mute";
    public static final String KEY_WORKOUT_TYPE = "type";

    public Workout() {
    }

    public void setTracker(Tracker tracker) {
        this.tracker = tracker;
    }

    public void onInit(Workout w) {
        if (BuildConfig.DEBUG && w != this) { throw new AssertionError(); }
        for (Step a : steps) {
            a.onInit(this);
        }
    }

    public void onBind(Workout w, HashMap<String, Object> bindValues) {
        if (bindValues.containsKey(Workout.KEY_HRZONES))
            hrZones = (HRZones) bindValues.get(Workout.KEY_HRZONES);
        if (bindValues.containsKey(Workout.KEY_TTS))
            textToSpeech = (RUTextToSpeech) bindValues.get(Workout.KEY_TTS);
        for (Step a : steps) {
            a.onBind(w, bindValues);
        }
    }

    public void onEnd(Workout w) {
        if (BuildConfig.DEBUG && w != this) { throw new AssertionError(); }
        for (Step a : steps) {
            a.onEnd(this);
        }
    }

    @Override
    public void onRepeat(int current, int limit) {
    }

    public void onStart(Scope s, Workout w) {
        if (BuildConfig.DEBUG && w != this) { throw new AssertionError(); }

        initFeedback();

        for (Step st : steps) {
            st.onRepeat(0, 1);
        }

        currentStepNo = 0;
        if (steps.size() > 0) {
            setCurrentStep(steps.get(currentStepNo));
        }

        if (currentStep != null) {
            currentStep.onStart(Scope.ACTIVITY, this);
            currentStep.onStart(Scope.STEP, this);
            currentStep.onStart(Scope.LAP, this);
        }

        emitFeedback();
    }

    private void setCurrentStep(Step step) {
        Step oldStep = currentStep;
        currentStep = step;

        Step newStep = (step == null) ? null : step.getCurrentStep();
        for (WorkoutStepListener l : stepListeners) {
            l.onStepChanged(oldStep, newStep);
        }
    }

    public void onTick() {
        initFeedback();

        while (currentStep != null) {
            boolean finished = currentStep.onTick(this);
            if (finished == false)
                break;

            onNextStep();
        }
        emitFeedback();
    }

    public void onNextStep() {
        currentStep.onComplete(Scope.LAP, this);
        currentStep.onComplete(Scope.STEP, this);

        if (currentStep.onNextStep(this))
            currentStepNo++;

        if (currentStepNo < steps.size()) {
            setCurrentStep(steps.get(currentStepNo));
            currentStep.onStart(Scope.STEP, this);
            currentStep.onStart(Scope.LAP, this);
        } else {
            currentStep.onComplete(Scope.ACTIVITY, this);
            setCurrentStep(null);
            tracker.stop();
        }
    }

    public void onPause(Workout w) {

        initFeedback();
        if (currentStep != null) {
            currentStep.onPause(this);
        }
        emitFeedback();
        paused = true;
    }

    public void onNewLap() {
        initFeedback();
        if (currentStep != null) {
            currentStep.onComplete(Scope.LAP, this);
            currentStep.onStart(Scope.LAP, this);
        }
        emitFeedback();
    }

    public void onNewLapOrNextStep() {
        if (!isLastStep()) {
            onNextStep();
        } else {
            onNewLap();
        }
    }

    public void onStop(Workout w) {

        initFeedback();
        if (currentStep != null) {
            currentStep.onStop(this);
        }
        emitFeedback();
    }

    public void onResume(Workout w) {
        initFeedback();
        if (currentStep != null) {
            currentStep.onResume(this);
        }
        emitFeedback();
        paused = false;
    }

    public void onComplete(Scope s, Workout w) {
        if (currentStep != null) {
            currentStep.onComplete(Scope.LAP, this);
            currentStep.onComplete(Scope.STEP, this);
            currentStep.onComplete(Scope.ACTIVITY, this);
        }
        setCurrentStep(null);
        currentStepNo = -1;
    }

    public void onSave() {
        tracker.completeActivity(true);
    }

    public void onDiscard() {
        tracker.completeActivity(false);
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public double get(Scope scope, Dimension d) {
        switch (d) {
            case DISTANCE:
                return getDistance(scope);
            case TIME:
                return getTime(scope);
            case SPEED:
                return getSpeed(scope);
            case PACE:
                return getPace(scope);
            case HR:
                return getHeartRate(scope);
            case HRZ:
                return getHeartRateZone(scope);
            case CAD:
                return getCadence(scope);
            case TEMPERATURE:
                return getTemperature(scope);
            case PRESSURE:
                return getPressure(scope);
        }
        return 0;
    }

    @Override
    public double getDistance(Scope scope) {
        switch (scope) {
            case ACTIVITY:
                return tracker.getDistance();
            case STEP:
            case LAP:
                if (currentStep != null)
                    return currentStep.getDistance(this, scope);
                if (BuildConfig.DEBUG) { throw new AssertionError(); }
                break;
            case CURRENT:
                break;
        }
        return 0;
    }

    @Override
    public double getTime(Scope scope) {
        switch (scope) {
            case ACTIVITY:
                return tracker.getTime();
            case STEP:
            case LAP:
                if (currentStep != null)
                    return currentStep.getTime(this, scope);
                if (BuildConfig.DEBUG) { throw new AssertionError(); }
                break;
            case CURRENT:
                return System.currentTimeMillis() / 1000; // now
        }
        return 0;
    }

    @Override
    public double getSpeed(Scope scope) {
        switch (scope) {
            case ACTIVITY:
                double d = getDistance(scope);
                double t = getTime(scope);
                if (t == 0)
                    return (double) 0;
                return d / t;
            case STEP:
            case LAP:
                if (currentStep != null)
                    return currentStep.getSpeed(this, scope);
                break;
            case CURRENT:
                Double s = tracker.getCurrentSpeed();
                if (s != null)
                    return s;
                return 0;
        }
        return 0;
    }

    @Override
    public double getPace(Scope scope) {
        double s = getSpeed(scope);
        if (s != 0)
            return 1.0d / s;
        return 0;
    }

    @Override
    public double getDuration(Scope scope, Dimension dimension) {
        if (scope == Scope.STEP && currentStep != null) {
            return currentStep.getDuration(dimension);
        }
        return 0;
    }

    @Override
    public double getRemaining(Scope scope, Dimension dimension) {
        double curr = this.get(scope, dimension);
        double duration = this.getDuration(scope, dimension);
        if (duration > curr) {
            return duration - curr;
        } else {
            return 0;
        }
    }

    double getHeartbeats(Scope scope) {
        switch (scope) {
            case ACTIVITY:
                return tracker.getHeartbeats();
            case STEP:
            case LAP:
                if (currentStep != null)
                    return currentStep.getHeartbeats(this, scope);
                return 0;
            case CURRENT:
                return 0;
        }
        return 0;
    }

    @Override
    public double getHeartRate(Scope scope) {
        switch (scope) {
            case CURRENT: {
                Integer val = tracker.getCurrentHRValue();
                if (val == null)
                    return 0;
                return val;
            }
            case LAP:
            case STEP:
            case ACTIVITY:
                break;
        }

        double t = getTime(scope); // in seconds
        double b = getHeartbeats(scope); // total (estimated) beats during
        // workout

        if (t != 0) {
            return (60 * b) / t; // bpm
        }
        return 0.0;
    }

    @Override
    public double getCadence(Scope scope) {
        switch (scope) {
            case CURRENT: {
                Float val = tracker.getCurrentCadence();
                if (val == null)
                    return -1; //TODO should not be used
                return val;
            }
            case LAP:
            case STEP:
            case ACTIVITY:
                break;
        }

        double t = getTime(scope); // in seconds
        double b = -1; //TODO get steps for scope

        if (BuildConfig.DEBUG) { throw new AssertionError(); }
        if (t != 0) {
            return (60 * b)/ 2 / t; // bpm
        }
        return 0.0;
    }

    @Override
    public double getTemperature(Scope scope) {
        switch (scope) {
            case CURRENT: {
                Float val = tracker.getCurrentTemperature();
                if (val == null)
                    return -1;  //TODO should not be used
                return val;
            }
            case LAP:
            case STEP:
            case ACTIVITY:
                break;
        }

        //TODO
        if (BuildConfig.DEBUG) { throw new AssertionError(); }
        return 0.0;
    }

    @Override
    public double getPressure(Scope scope) {
        switch (scope) {
            case CURRENT: {
                Float val = tracker.getCurrentPressure();
                if (val == null)
                    return -1;  //TODO should not be used
                return val;
            }
            case LAP:
            case STEP:
            case ACTIVITY:
                break;
        }

        //TODO
        if (BuildConfig.DEBUG) { throw new AssertionError(); }
        return 0.0;
    }

    @Override
    public double getHeartRateZone(Scope scope) {
        return hrZones.getZone(getHeartRate(scope));
    }

    @Override
    public int getSport() {
        return sport;
    }

    @Override
    public Intensity getIntensity() {
        if (currentStep == null)
            return Intensity.ACTIVE; // needed ??

        return currentStep.getCurrentStep().getIntensity();
    }

    @Override
    public boolean isEnabled(Dimension dim, Scope scope) {
        if (dim == Dimension.HR) {
            return tracker.isComponentConnected(TrackerHRM.NAME);
        } else if (dim == Dimension.HRZ) {
            if (hrZones == null ||
                    !hrZones.isConfigured() ||
                    !tracker.isComponentConnected(TrackerHRM.NAME))
                return false;
        } else if (dim == Dimension.CAD) {
            return tracker.isComponentConnected(TrackerCadence.NAME);
        } else if (dim == Dimension.TEMPERATURE) {
            return tracker.isComponentConnected(TrackerTemperature.NAME);
        } else if (dim == Dimension.PRESSURE) {
            return tracker.isComponentConnected(TrackerPressure.NAME);
        } else if ((dim == Dimension.SPEED || dim == Dimension.PACE) &&
                scope == Scope.CURRENT) {
            return tracker.getCurrentSpeed() != null;
        }
        return true;
    }

    private void initFeedback() {
        pendingFeedback.init();
    }

    public void addFeedback(Feedback f) {
        pendingFeedback.add(f);
    }

    private void emitFeedback() {
        pendingFeedback.end();
    }

    void newLap(ContentValues tmp) {
        tmp.put(DB.LAP.LAP, lap);
        tracker.newLap(tmp);
    }

    void saveLap(ContentValues tmp, boolean next) {
        tracker.saveLap(tmp);
        if (next) {
            lap++;
        }
    }

    public int getStepCount() {
        return steps.size();
    }

    public boolean isLastStep() {
        if (currentStepNo + 1 < steps.size())
            return false;
        if (currentStepNo < steps.size())
            return steps.get(currentStepNo).isLastStep();
        return true;
    }

    /**
     * @return flattened list of all steps in workout
     */
    static public class StepListEntry {
        public StepListEntry(int index, Step step, int level, Step parent) {
            this.index = index;
            this.level = level;
            this.step = step;
            this.parent = parent;
        }

        public final int index;
        public final int level;
        public final Step parent;
        public final Step step;
    }

    public void addStep(Step s) {
        steps.add(s);
    }

    public List<Step> getSteps() {
        return steps;
    }

    public List<StepListEntry> getStepList() {
        ArrayList<StepListEntry> list = new ArrayList<StepListEntry>();
        for (Step s : steps) {
            s.getSteps(null, 0, list);
        }
        return list;
    }

    public Step getCurrentStep() {
        if (currentStepNo >= 0 && currentStepNo < steps.size())
            return steps.get(currentStepNo).getCurrentStep();
        return null;
    }

    public void registerWorkoutStepListener(WorkoutStepListener listener) {
        stepListeners.add(listener);
    }

    public void unregisterWorkoutStepListener(WorkoutStepListener listener) {
        stepListeners.remove(listener);
    }

    private static class FakeWorkout extends Workout {

        FakeWorkout() {
            super();
        }

        @Override
        public boolean isEnabled(Dimension dim, Scope scope) {
            return true;
        }

        public double getDistance(Scope scope) {
            switch (scope) {
                case ACTIVITY:
                    return (3000 + 7000 * Math.random());
                case STEP:
                    return (300 + 700 * Math.random());
                case LAP:
                    return (300 + 700 * Math.random());
                case CURRENT:
                    return 0;
            }
            return 0;
        }

        public double getTime(Scope scope) {
            switch (scope) {
                case ACTIVITY:
                    return (10 * 60 + 50 * 60 * Math.random());
                case STEP:
                    return (1 * 60 + 5 * 60 * Math.random());
                case LAP:
                    return (1 * 60 + 5 * 60 * Math.random());
                case CURRENT:
                    return System.currentTimeMillis() / 1000;
            }
            return 0;
        }

        public double getSpeed(Scope scope) {
            double d = getDistance(scope);
            double t = getTime(scope);
            if (t == 0)
                return 0;
            return d / t;
        }

        public double getHeartRate(Scope scope) {
            return 150 + 25 * Math.random();
        }
    }

    @Override
    public Location getLastKnownLocation() {
        return tracker.getLastKnownLocation();
    }

    public static Workout fakeWorkoutForTestingAudioCue() {
        FakeWorkout w = new FakeWorkout();
        return w;
    }

    public void setMute(boolean mute) {
        this.mute = mute;
    }

    public boolean getMute() {
        return mute;
    }

    public void setWorkoutType(int workoutType) {
        this.workoutType = workoutType;
    }

    public int getWorkoutType() {
        return this.workoutType;
    }

}
