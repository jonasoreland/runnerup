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

import android.content.ContentValues;
import android.location.Location;
import android.util.Log;

import org.runnerup.BuildConfig;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.tracker.Tracker;
import org.runnerup.tracker.component.TrackerCadence;
import org.runnerup.tracker.component.TrackerHRM;
import org.runnerup.tracker.component.TrackerPressure;
import org.runnerup.tracker.component.TrackerTemperature;
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


public class Workout implements WorkoutComponent, WorkoutInfo {

    private long lap = 0;
    private int currentStepNo = -1;
    private int workoutType = Constants.WORKOUT_TYPE.BASIC;
    private Step currentStep = null;
    private boolean paused = false;
    final ArrayList<Step> steps = new ArrayList<>();
    private final ArrayList<WorkoutStepListener> stepListeners = new ArrayList<>();
    public int sport = DB.ACTIVITY.SPORT_RUNNING;
    private boolean mute;

    class PendingFeedback {
        int depth = 0;
        final HashSet<Feedback> set = new HashSet<>(); // For uniquing

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
                // make sure that no small mistake crashes a workout...
                Log.w(getClass().getName(), "PendingFeedback:add: " + ex.toString());
            }
        }

        @SuppressWarnings("UnusedReturnValue")
        boolean end() {
            --depth;
            if (depth == 0 && Workout.this.textToSpeech != null) {
                set.clear();
                try {
                    Workout.this.textToSpeech.emit();
                } catch (Exception ex) {
                    // make sure that no small mistake crashes a workout...
                    Log.w(getClass().getName(), "PendingFeedback:end: " + ex.toString());
                }
                return true;
            }
            return false;
        }
    }

    private final PendingFeedback pendingFeedback = new PendingFeedback();

    Tracker tracker = null;
    private HRZones hrZones = null;
    private RUTextToSpeech textToSpeech = null;

    public static final String KEY_TTS = "tts";
    public static final String KEY_COUNTER_VIEW = "CountdownView";
    public static final String KEY_FORMATTER = "Formatter";
    public static final String KEY_HRZONES = "HrZones";
    public static final String KEY_MUTE = "mute";
    public static final String KEY_WORKOUT_TYPE = "workout_type";
    public static final String KEY_SPORT_TYPE = "sport";

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
            if (!finished)
                break;

            onNextStep();
        }
        emitFeedback();
    }

    public void onNextStep() {
        if (currentStep == null) {
            return;
        }
        currentStep.onComplete(Scope.LAP, this);
        currentStep.onComplete(Scope.STEP, this);

        // Increase the step counter unless this is a repeat step not yet finished
        if (currentStep.onNextStep(this))
            currentStepNo++;

        if (currentStepNo < steps.size()) {
            setCurrentStep(steps.get(currentStepNo));
            currentStep.onStart(Scope.STEP, this);
            currentStep.onStart(Scope.LAP, this);
        } else {
            // End the workout
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
        tracker.displayNotificationState();
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
        if (currentStep == null ||
                isLastStep() && (
                        // Basic workout but not warmup
                        this.workoutType == Constants.WORKOUT_TYPE.BASIC ||
                                // Last step where not keypress
                                currentStep.getDurationType() != null)) {
            onNewLap();
        } else {
            onNextStep();
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
        if (tracker == null) {
            // Taskkiller?
            return;
        }
        tracker.displayNotificationState();
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
        if (tracker == null) {
            // Taskkiller?
            return;
        }
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
        if (d == null) {
            return 0;
        }
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
                if (tracker != null) {
                    return tracker.getDistance();
                }
                break;
            case STEP:
            case LAP:
                if (currentStep != null)
                    return currentStep.getDistance(this, scope);
                //if (BuildConfig.DEBUG) { throw new AssertionError(); }
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
                if (tracker != null) {
                    return tracker.getTimeMs() / 1000.0d;
                }
                break;
            case STEP:
            case LAP:
                if (currentStep != null)
                    return currentStep.getTime(this, scope);
                //if (BuildConfig.DEBUG) { throw new AssertionError(); }
                break;
            case CURRENT:
                if (BuildConfig.DEBUG) { throw new AssertionError(); }
                return System.currentTimeMillis() / 1000.0d; // now, not to be used
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
                    return 0;
                return d / t;
            case STEP:
            case LAP:
                if (currentStep != null)
                    return currentStep.getSpeed(this, scope);
                break;
            case CURRENT:
                if (tracker != null) {
                    Double s = tracker.getCurrentSpeed();
                    if (s != null)
                        return s;
                }
                break;
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
                if (tracker != null) {
                    return tracker.getHeartbeats();
                }
                break;
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
            case CURRENT:
                if (tracker != null) {
                    Integer val = tracker.getCurrentHRValue();
                    if (val != null)
                        return val;
                }
                return 0;
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
            case CURRENT:
                if (tracker != null) {
                    Float val = tracker.getCurrentCadence();
                    if (val != null)
                        return val;
                return 0;
            }
            case LAP:
            case STEP:
            case ACTIVITY:
                break;
        }

        double t = getTime(scope); // in seconds
        double b = -1; //TODO get steps for scope

        //TODO
        if (BuildConfig.DEBUG) { throw new AssertionError(); }
        if (t != 0) {
            return (60 * b)/ 2 / t; // bpm
        }
        return 0.0;
    }

    @Override
    public double getTemperature(Scope scope) {
        switch (scope) {
            case CURRENT:
                if (tracker != null) {
                    Float val = tracker.getCurrentTemperature();
                    if (val != null)
                        return val;
                }
                return -1;
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
            case CURRENT:
                if (tracker != null) {
                    Float val = tracker.getCurrentPressure();
                    if (val != null)
                        return val;
                }
                return -1;
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
        if (currentStep == null || currentStep.getCurrentStep() == null)
            return Intensity.ACTIVE; //No next step, assertion

        return currentStep.getCurrentStep().getIntensity();
    }

    @Override
    public boolean isEnabled(Dimension dim, Scope scope) {
        if (tracker == null) {
            return false;
        }
        if (dim == Dimension.HR) {
            return tracker.isComponentConnected(TrackerHRM.NAME);
        } else if (dim == Dimension.HRZ) {
            return hrZones != null &&
                    hrZones.isConfigured() &&
                    tracker.isComponentConnected(TrackerHRM.NAME);
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

    public boolean isLastStep() {
        if (currentStepNo >= steps.size())
            // Incorrect workout
            return true;

        if (currentStepNo + 1 < steps.size())
            return false;

        return steps.get(currentStepNo).isLastStep();
    }

    /**
     * flattened list of all steps in workout
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
        ArrayList<StepListEntry> list = new ArrayList<>();
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
                    return (/* 1* */ 60 + 5 * 60 * Math.random());
                case LAP:
                    return (/* 1* */ 60 + 5 * 60 * Math.random());
                case CURRENT:
                    return System.currentTimeMillis() / 1000.0;
            }
            return 0;
        }

        public double getSpeed(Scope scope) {
            if (scope == Scope.CURRENT) {
                scope = Scope.STEP;
            }
            double d = getDistance(scope);
            double t = getTime(scope);
            if (t == 0)
                return 0;
            return d / t;
        }

        public double getHeartRate(Scope scope) {
            return 150 + 25 * Math.random();
        }

        public double getCadence(Scope scope) {
            return 50 + 25 * Math.random();
        }
    }

    @Override
    public Location getLastKnownLocation() {
        return tracker.getLastKnownLocation();
    }

    public static Workout fakeWorkoutForTestingAudioCue() {
        return new FakeWorkout();
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
