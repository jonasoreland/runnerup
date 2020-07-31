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

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class RepeatStep extends Step {

    int repeatCount = 0;

    public ArrayList<Step> getSteps() {
        return steps;
    }

    final ArrayList<Step> steps = new ArrayList<>();

    private int currentStep = 0;
    private int currentRepeat = 0;

    public RepeatStep() {
        this.intensity = Intensity.REPEAT;
        this.durationType = null;
        this.targetType = null;
    }

    @Override
    public Dimension getDurationType() {
        return null;
    }

    @Override
    public void onInit(Workout w) {
        currentStep = 0;
        currentRepeat = 0;
        for (Step s : steps) {
            s.onInit(w);
        }
    }

    @Override
    public void onBind(Workout w, HashMap<String, Object> bindValues) {
        for (Step s : steps) {
            s.onBind(w, bindValues);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "currentStep: " + currentStep + "(" + steps.size() + ") currentRepeat: "
                + currentRepeat + "(" + repeatCount + ")";
    }

    @Override
    public void onEnd(Workout w) {
        for (Step s : steps) {
            s.onEnd(w);
        }
    }

    @Override
    public void onRepeat(int current, int count) {
        super.onRepeat(current, count);
        currentStep = 0;
        currentRepeat = 0;
        for (Step s : steps) {
            s.onRepeat(0, repeatCount);
        }
    }

    @Override
    public void onStart(Scope what, Workout s) {
        if (steps.size() > currentStep) {
            steps.get(currentStep).onStart(what, s);
        }
    }

    @Override
    public void onStop(Workout s) {
        steps.get(currentStep).onStop(s);
    }

    @Override
    public void onPause(Workout s) {
        steps.get(currentStep).onPause(s);
    }

    @Override
    public boolean onTick(Workout w) {
        return currentStep >= steps.size() || currentRepeat >= repeatCount || steps.get(currentStep).onTick(w);
    }

    /**
     * Return true when the step cannot be increased within this repeat step
     * @param w
     * @return
     */
    @Override
    public boolean onNextStep(Workout w) {
        if (steps.size() <= currentStep) {
            // Incorrect handling or repeat 0, move to next
            return true;
        }
        if (!steps.get(currentStep).onNextStep(w)) {
            // current step is another repeat step
            return false;
        }

        currentStep++;
        if (currentStep >= steps.size()) {
            currentStep = 0;
            currentRepeat++;
            if (currentRepeat >= repeatCount) {
                return true;
            }
            for (Step s : steps) {
                s.onRepeat(currentRepeat, repeatCount);
            }
        }
        return false;
    }

    @Override
    public void onResume(Workout s) {
        steps.get(currentStep).onResume(s);
    }

    @Override
    public void onComplete(Scope scope, Workout s) {
        if (steps.size() > currentStep) {
            steps.get(currentStep).onComplete(scope, s);
        }
    }

    @Override
    public double getDistance(Workout w, Scope s) {
        return steps.get(currentStep).getDistance(w, s);
    }

    @Override
    public double getTime(Workout w, Scope s) {
        return steps.get(currentStep).getTime(w, s);
    }

    @Override
    public double getSpeed(Workout w, Scope s) {
        return steps.get(currentStep).getSpeed(w, s);
    }

    @Override
    public double getHeartbeats(Workout w, Scope s) {
        return steps.get(currentStep).getHeartbeats(w, s);
    }

    @Override
    public double getDuration(Dimension dimension) {
        return steps.get(currentStep).getDuration(dimension);
    }

    @Override
    public int getRepeatCount() {
        return repeatCount;
    }

    @Override
    public void setRepeatCount(int val) {
        repeatCount = val;
    }

    @Override
    public int getCurrentRepeat() {
        return currentRepeat;
    }

    @Override
    public Step getCurrentStep() {
        if (currentStep < steps.size())
            return steps.get(currentStep).getCurrentStep();
        return null;
    }

    @Override
    public boolean isLastStep() {
        if (currentRepeat + 1 < repeatCount) {
            return false;
        }
        if (currentStep >= steps.size()) {
            return true;
        }

        return steps.get(currentStep).isLastStep();
    }

    @Override
    public void getSteps(Step parent, int level, List<Workout.StepListEntry> list) {
        list.add(new Workout.StepListEntry(list.size(), this, level, parent));
        for (Step s2 : steps) {
            s2.getSteps(this, level + 1, list);
        }
    }
}
