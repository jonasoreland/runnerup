package org.runnerup.export;

import org.runnerup.gpstracker.GpsTracker;

/**
 * Created by niklas.weidemann on 2014-10-13.
 */
public interface LiveLogger {
    public void liveLog(GpsTracker gpsTracker, int type);
}
