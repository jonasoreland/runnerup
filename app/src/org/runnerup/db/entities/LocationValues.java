package org.runnerup.db.entities;

import org.runnerup.common.util.Constants;

/**
 * Content values wrapper for the {@code location} table.
 */
public class LocationValues extends AbstractBaseValues {
    /**
     * Id of the activity the location point belongs to
     */
    public void putActivityId(int value) {
        mContentValues.put(Constants.DB.LOCATION.ACTIVITY, value);
    }

    /**
     * Lap number of the activity the location point belongs to
     */
    public void putLap(int value) {
        mContentValues.put(Constants.DB.LOCATION.LAP, value);
    }

    /**
     * Type of the location point
     */
    public void putType(int value) {
        mContentValues.put(Constants.DB.LOCATION.TYPE, value);
    }

    /**
     * The moment in time when the location point was recorded
     */
    public void putTime(long value) {
        mContentValues.put(Constants.DB.LOCATION.TIME, value);
    }

    /**
     * Longitude of the location
     */
    public void putLongitude(float value) {
        mContentValues.put(Constants.DB.LOCATION.LONGITUDE, value);
    }

    /**
     * Latitude of the location
     */
    public void putLatitude(float value) {
        mContentValues.put(Constants.DB.LOCATION.LATITUDE, value);
    }

    /**
     * Accuracy of the location
     */
    public void putAccurancy(Float value) {
        mContentValues.put(Constants.DB.LOCATION.ACCURANCY, value);
    }

    /**
     * Altitude of the location
     */
    public void putAltitude(Float value) {
        mContentValues.put(Constants.DB.LOCATION.ALTITUDE, value);
    }

    /**
     * Speed of the location
     */
    public void putSpeed(Float value) {
        mContentValues.put(Constants.DB.LOCATION.SPEED, value);
    }

    /**
     * Bearing of the location
     */
    public void putBearing(Float value) {
        mContentValues.put(Constants.DB.LOCATION.BEARING, value);
    }

    /**
     * HR at the location
     */
    public void putHr(Integer value) {
        mContentValues.put(Constants.DB.LOCATION.HR, value);
    }

    /**
     * Cadence at the location
     */
    public void putCadence(Integer value) {
        mContentValues.put(Constants.DB.LOCATION.CADENCE, value);
    }ń
}
