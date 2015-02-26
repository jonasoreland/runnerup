package org.runnerup.db.entities;

import org.runnerup.common.util.Constants;

/**
 * Content values wrapper for the {@code activity} table.
 */
public class ActivityValues extends AbstractBaseValues {

    /**
     * Start time of the activity
     */
    public void putStartTime(long value) {
        mContentValues.put(Constants.DB.ACTIVITY.START_TIME, value);
    }

    /**
     * Distance of the activity
     */
    public void putDistance(Float value) {
        mContentValues.put(Constants.DB.ACTIVITY.DISTANCE, value);
    }

    /**
     * Duration of the activity
     */
    public void putTime(Long value) {
        mContentValues.put(Constants.DB.ACTIVITY.TIME, value);
    }

    /**
     * Name of the activity
     */
    public void putName(String value) {
        mContentValues.put(Constants.DB.ACTIVITY.NAME, value);
    }

    /**
     * Comment for the activity
     */
    public void putComment(String value) {
        mContentValues.put(Constants.DB.ACTIVITY.COMMENT, value);
    }

    /**
     * Sport type of the activity
     */
    public void putType(Integer value) {
        mContentValues.put(Constants.DB.ACTIVITY.SPORT, value);
    }

    /**
     * Maximum HR of the activity
     */
    public void putMaxHr(Integer value) {
        mContentValues.put(Constants.DB.ACTIVITY.MAX_HR, value);
    }

    /**
     * Avarage HR of the activity
     */
    public void putAvgHr(Integer value) {
        mContentValues.put(Constants.DB.ACTIVITY.AVG_HR, value);
    }

    /**
     * Avarage cadence of the activity
     */
    public void putAvgCadence(Integer value) {
        mContentValues.put(Constants.DB.ACTIVITY.AVG_CADENCE, value);
    }

    /**
     * Status of the activity
     */
    public void putDeleted(boolean value) {
        mContentValues.put(Constants.DB.ACTIVITY.DELETED, value);
    }

    /**
     * Workaround column
     */
    public void putNullcolumnhack(String value) {
        mContentValues.put(Constants.DB.ACTIVITY.NULLCOLUMNHACK, value);

    }
}
