package org.runnerup.export;

import org.runnerup.workout.ActivityInfo;

/**
 * Created by niklas.weidemann on 2014-10-13.
 */
public interface LiveLogger {
    public void liveLog(ActivityInfo activityInfo, int type);
}
