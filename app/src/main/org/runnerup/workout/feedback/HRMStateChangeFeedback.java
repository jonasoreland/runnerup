package org.runnerup.workout.feedback;

import android.content.Context;

import org.runnerup.R;
import org.runnerup.workout.HRMStateTrigger;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Workout;

public class HRMStateChangeFeedback extends AudioFeedback {
    public HRMStateChangeFeedback(HRMStateTrigger trigger) {
        super(0);
    }

    String getCue(Workout w, Context ctx) {
        return (ctx.getResources().getString((w.getHeartRate(Scope.CURRENT) == 0)
                ? R.string.cue_hrm_connection_lost
                : R.string.cue_hrm_connection_restored));
    }
}
