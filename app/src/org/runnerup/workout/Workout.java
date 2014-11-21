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
import android.os.Build;

import org.runnerup.gpstracker.GpsTracker;
import org.runnerup.util.Constants.DB;
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
    Step currentStep = null;
    boolean paused = false;
    final ArrayList<Step> steps = new ArrayList<Step>();
    int sport = DB.ACTIVITY.SPORT_RUNNING;

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
                f.emit(Workout.this, gpsTracker.getApplicationContext());
            } catch (Exception ex) {
                // make sure that no small misstake crashes a workout...
                ex.printStackTrace();
            }
        }

        boolean end() {
            --depth;
            if (depth == 0) {
                set.clear();
                Workout.this.textToSpeech.emit();
            }
            return depth == 0;
        }
    }

    final PendingFeedback pendingFeedback = new PendingFeedback();

    GpsTracker gpsTracker = null;
    SharedPreferences audioCuePrefs;
    HRZones hrZones = null;
    RUTextToSpeech textToSpeech = null;

    public static final String KEY_TTS = "tts";
    public static final String KEY_COUNTER_VIEW = "CountdownView";
    public static final String KEY_FORMATTER = "Formatter";
    public static final String KEY_HRZONES = "HrZones";

    public Workout() {
    }

    public void setGpsTracker(GpsTracker gpsTracker) {
        this.gpsTracker = gpsTracker;
    }

    public boolean isEnabled(Dimension dim, Scope scope) {
        if (dim == Dimension.HR) {
            return gpsTracker.isHRConnected();
        } else if (dim == Dimension.HRZ) {
            if (hrZones == null || !hrZones.isConfigured() || !gpsTracker.isHRConnected())
                return false;
        } else if ((dim == Dimension.SPEED || dim == Dimension.PACE) &&
                scope == Scope.CURRENT) {
            return gpsTracker.getCurrentSpeed() != null;
        }
        return true;
    }

    public void onInit(Workout w) {
        assert (w == this);
        for (Step a : steps) {
            a.onInit(this);
        }
    }

    public void onBind(Workout w, HashMap<String, Object> bindValues) {
        hrZones = (HRZones) bindValues.get(Workout.KEY_HRZONES);
        textToSpeech = (RUTextToSpeech) bindValues.get(Workout.KEY_TTS);
        for (Step a : steps) {
            a.onBind(w, bindValues);
        }
    }

    public void onEnd(Workout w) {
        assert (w == this);
        for (Step a : steps) {
            a.onEnd(this);
        }
    }

    @Override
    public void onRepeat(int current, int limit) {
    }

    public void onStart(Scope s, Workout w) {
        assert (w == this);

        initFeedback();

        for (Step st : steps) {
            st.onRepeat(0, 1);
        }

        currentStepNo = 0;
        if (steps.size() > 0) {
            currentStep = steps.get(currentStepNo);
        }

        if (currentStep != null) {
            currentStep.onStart(Scope.WORKOUT, this);
            currentStep.onStart(Scope.STEP, this);
            currentStep.onStart(Scope.LAP, this);
        }

        emitFeedback();
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
            currentStep = steps.get(currentStepNo);
            currentStep.onStart(Scope.STEP, this);
            currentStep.onStart(Scope.LAP, this);
        } else {
            currentStep.onComplete(Scope.WORKOUT, this);
            currentStep = null;
            gpsTracker.stop();
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

    public boolean isPaused() {
        return paused;
    }

    public void onNewLap() {
        initFeedback();
        if (currentStep != null) {
            currentStep.onComplete(Scope.LAP, this);
            currentStep.onStart(Scope.LAP, this);
        }
        emitFeedback();
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
            currentStep.onComplete(Scope.WORKOUT, this);
        }
        currentStep = null;
        currentStepNo = -1;
    }

    public void onSave() {
        gpsTracker.completeActivity(true);
    }

    public void onDiscard() {
        gpsTracker.completeActivity(false);
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
        }
        return 0;
    }

    @Override
    public double getDistance(Scope scope) {
        switch (scope) {
            case WORKOUT:
                return gpsTracker.getDistance();
            case STEP:
            case LAP:
                if (currentStep != null)
                    return currentStep.getDistance(this, scope);
                assert (false);
                break;
            case CURRENT:
                break;
        }
        return 0;
    }

    @Override
    public double getTime(Scope scope) {
        switch (scope) {
            case WORKOUT:
                return gpsTracker.getTime();
            case STEP:
            case LAP:
                if (currentStep != null)
                    return currentStep.getTime(this, scope);
                assert (false);
                break;
            case CURRENT:
                return System.currentTimeMillis() / 1000; // now
        }
        return 0;
    }

    @Override
    public double getSpeed(Scope scope) {
        switch (scope) {
            case WORKOUT:
                double d = getDistance(scope);
                double t = getTime(scope);
                if (t == 0)
                    return (double) 0;
                return d / t;
            case STEP:
            case LAP:
                if (currentStep != null)
                    return currentStep.getSpeed(this, scope);
                assert (false);
                break;
            case CURRENT:
                Double s = gpsTracker.getCurrentSpeed();
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
            case WORKOUT:
                return gpsTracker.getHeartbeats();
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
                Integer val = gpsTracker.getCurrentHRValue();
                if (val == null)
                    return 0;
                return val;
            }
            case LAP:
            case STEP:
            case WORKOUT:
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

    private double getHeartRateZone(Scope scope) {
        return hrZones.getZone(getHeartRate(scope));
    }

    @Override
    public int getSport() {
        return sport;
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
        gpsTracker.newLap(tmp);
    }

    void saveLap(ContentValues tmp, boolean next) {
        gpsTracker.saveLap(tmp);
        if (next) {
            lap++;
        }
    }

    @Override
    public int getStepCount() {
        return steps.size();
    }

    @Override
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
        public StepListEntry(Step step, int level, Step parent) {
            this.level = level;
            this.step = step;
            this.parent = parent;
        }

        public final int level;
        public final Step parent;
        public final Step step;
    }

    @Override
    public List<StepListEntry> getSteps() {
        ArrayList<StepListEntry> list = new ArrayList<StepListEntry>();
        for (Step s : steps) {
            s.getSteps(null, 0, list);
        }
        return list;
    }

    @Override
    public Step getCurrentStep() {
        if (currentStepNo >= 0 && currentStepNo < steps.size())
            return steps.get(currentStepNo).getCurrentStep();
        return null;
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
                case WORKOUT:
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
                case WORKOUT:
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

    public static Workout fakeWorkoutForTestingAudioCue() {
        FakeWorkout w = new FakeWorkout();
        return w;
    }

    public void setAudioCues(SharedPreferences prefs) {
        audioCuePrefs = prefs;
    }

    public SharedPreferences getAudioCues() {
        return audioCuePrefs;
    }
}
