package org.runnerup.db.entities;

import org.runnerup.common.util.Constants;

/**
 * Content values wrapper for the {@code lap} table.
 */
public class LapValues extends AbstractBaseValues {
    /**
     * Id of the activity the lap belongs to
     */
    public void putActivityId(int value) {
        mContentValues.put(Constants.DB.LAP.ACTIVITY, value);
    }

    /**
     * Number of the lap
     */
    public void putLap(int value) {
        mContentValues.put(Constants.DB.LAP.LAP, value);
    }


    /**
     * Type (intensity) of the lap
     */
    public void putType(int value) {
        mContentValues.put(Constants.DB.LAP.INTENSITY, value);
    }


    /**
     * Duration of the lap
     */
    public void putTime(Integer value) {
        mContentValues.put(Constants.DB.LAP.TIME, value);
    }

    /**
     * Distance of the lap
     */
    public void putDistance(Float value) {
        mContentValues.put(Constants.DB.LAP.DISTANCE, value);
    }

    /**
     * Planned duration of the lap
     */
    public void putPlannedTime(Integer value) {
        mContentValues.put(Constants.DB.LAP.PLANNED_TIME, value);
    }

    /**
     * Planned distance of the lap
     */
    public void putPlannedDistance(Float value) {
        mContentValues.put(Constants.DB.LAP.PLANNED_DISTANCE, value);
    }

    /**
     * Planned pace of the lap
     */
    public void putPlannedPace(Float value) {
        mContentValues.put(Constants.DB.LAP.PLANNED_PACE, value);
    }

    /**
     * Average HR of the lap
     */
    public void putAvgHr(Integer value) {
        mContentValues.put(Constants.DB.LAP.AVG_HR, value);
    }

    /**
     * Maximum HR of the lap
     */
    public void putMaxHr(Integer value) {
        mContentValues.put(Constants.DB.LAP.MAX_HR, value);
    }

    /**
     * Avarage cadence of the lap
     */
    public void putAvgCadence(Integer value) {
        mContentValues.put(Constants.DB.LAP.AVG_CADENCE, value);
    }
}
