package org.runnerup.workout.feedback;

import android.content.Context;

import org.runnerup.workout.HRMStateTrigger;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Workout;

public class HRMStateChangeFeedback extends AudioFeedback {
    public HRMStateChangeFeedback(HRMStateTrigger trigger) {
        // Set temporary id, overridden in getCue()
        super(org.runnerup.common.R.string.cue_hrm_connection_lost);
    }

    String getCue(Workout w, Context ctx) {
        return (formatter.getCueString((w.getHeartRate(Scope.CURRENT) == 0)
                ? org.runnerup.common.R.string.cue_hrm_connection_lost
                : org.runnerup.common.R.string.cue_hrm_connection_restored));
    }
}
