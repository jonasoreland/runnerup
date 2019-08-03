package org.runnerup.workout.feedback;

import android.content.Context;

import org.runnerup.R;
import org.runnerup.workout.HRMStateTrigger;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Workout;

public class HRMStateChangeFeedback extends AudioFeedback {
    public HRMStateChangeFeedback(HRMStateTrigger trigger) {
        // Set temporary id, overridden in getCue()
        super(R.string.cue_hrm_connection_lost);
    }

    String getCue(Workout w, Context ctx) {
        return (formatter.getCueString((w.getHeartRate(Scope.CURRENT) == 0)
                ? R.string.cue_hrm_connection_lost
                : R.string.cue_hrm_connection_restored));
    }
}
