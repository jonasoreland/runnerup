package org.runnerup.db.entities;

import org.runnerup.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * Content values wrapper for the {@code location} table.
 */
public class LocationValues extends AbstractBaseValues {
    /**
     * Id of the activity the location point belongs to
     */
    public void setActivityId(int value) {
        values().put(Constants.DB.LOCATION.ACTIVITY, value);
    }

    public Integer getActivityId() {
        if (values().containsKey(Constants.DB.LOCATION.ACTIVITY)) {
            return values().getAsInteger(Constants.DB.LOCATION.ACTIVITY);
        }
        return null;
    }

    /**
     * Lap number of the activity the location point belongs to
     */
    public void setLap(Integer value) {
        values().put(Constants.DB.LOCATION.LAP, value);
    }

    public Integer getLap() {
        if (values().containsKey(Constants.DB.LOCATION.LAP)) {
            return values().getAsInteger(Constants.DB.LOCATION.LAP);
        }
        return null;
    }

    /**
     * Type of the location point
     */
    public void setType(Integer value) {
        values().put(Constants.DB.LOCATION.TYPE, value);
    }

    public Integer getType() {
        if (values().containsKey(Constants.DB.LOCATION.TYPE)) {
            return values().getAsInteger(Constants.DB.LOCATION.TYPE);
        }
        return null;
    }

    /**
     * The moment in time when the location point was recorded
     */
    public void setTime(Long value) {
        values().put(Constants.DB.LOCATION.TIME, value);
    }

    public Long getTime() {
        if (values().containsKey(Constants.DB.LOCATION.TIME)) {
            return values().getAsLong(Constants.DB.LOCATION.TIME);
        }
        return null;
    }

    /**
     * Longitude of the location
     */
    public void setLongitude(Float value) {
        values().put(Constants.DB.LOCATION.LONGITUDE, value);
    }

    public Float getLongitude() {
        if (values().containsKey(Constants.DB.LOCATION.LONGITUDE)) {
            return values().getAsFloat(Constants.DB.LOCATION.LONGITUDE);
        }
        return null;
    }

    /**
     * Latitude of the location
     */
    public void setLatitude(Float value) {
        values().put(Constants.DB.LOCATION.LATITUDE, value);
    }

    public Float getLatitude() {
        if (values().containsKey(Constants.DB.LOCATION.LATITUDE)) {
            return values().getAsFloat(Constants.DB.LOCATION.LATITUDE);
        }
        return null;
    }

    /**
     * Accuracy of the location
     */
    public void setAccuracy(Float value) {
        values().put(Constants.DB.LOCATION.ACCURANCY, value);
    }

    public Float getAccuracy() {
        if (values().containsKey(Constants.DB.LOCATION.ACCURANCY)) {
            return values().getAsFloat(Constants.DB.LOCATION.ACCURANCY);
        }
        return null;
    }

    /**
     * Altitude of the location
     */
    public void setAltitude(Float value) {
        values().put(Constants.DB.LOCATION.ALTITUDE, value);
    }

    public Float getAltitude() {
        if (values().containsKey(Constants.DB.LOCATION.ALTITUDE)) {
            return values().getAsFloat(Constants.DB.LOCATION.ALTITUDE);
        }
        return null;
    }

    /**
     * Speed of the location
     */
    public void setSpeed(Float value) {
        values().put(Constants.DB.LOCATION.SPEED, value);
    }

    public Float getSpeed() {
        if (values().containsKey(Constants.DB.LOCATION.SPEED)) {
            return values().getAsFloat(Constants.DB.LOCATION.SPEED);
        }
        return null;
    }

    /**
     * Bearing of the location
     */
    public void setBearing(Float value) {
        values().put(Constants.DB.LOCATION.BEARING, value);
    }

    public Float getBearing() {
        if (values().containsKey(Constants.DB.LOCATION.BEARING)) {
            return values().getAsFloat(Constants.DB.LOCATION.BEARING);
        }
        return null;
    }

    /**
     * HR at the location
     */
    public void setHr(Integer value) {
        values().put(Constants.DB.LOCATION.HR, value);
    }

    public Integer getHr() {
        if (values().containsKey(Constants.DB.LOCATION.HR)) {
            return values().getAsInteger(Constants.DB.LOCATION.HR);
        }
        return null;
    }

    /**
     * Cadence at the location
     */
    public void setCadence(Integer value) {
        values().put(Constants.DB.LOCATION.CADENCE, value);
    }

    public Integer getCadence() {
        if (values().containsKey(Constants.DB.LOCATION.CADENCE)) {
            return values().getAsInteger(Constants.DB.LOCATION.CADENCE);
        }
        return null;
    }

    @Override
    protected List<String> getValidColumns() {
        List<String> columns = new ArrayList<String>();
        columns.add(Constants.DB.PRIMARY_KEY);
        columns.add(Constants.DB.LOCATION.ACTIVITY);
        columns.add(Constants.DB.LOCATION.LAP);
        columns.add(Constants.DB.LOCATION.TYPE);
        columns.add(Constants.DB.LOCATION.TIME);
        columns.add(Constants.DB.LOCATION.LATITUDE);
        columns.add(Constants.DB.LOCATION.LONGITUDE);
        columns.add(Constants.DB.LOCATION.ACCURANCY);
        columns.add(Constants.DB.LOCATION.ALTITUDE);
        columns.add(Constants.DB.LOCATION.SPEED);
        columns.add(Constants.DB.LOCATION.BEARING);
        columns.add(Constants.DB.LOCATION.HR);
        columns.add(Constants.DB.LOCATION.CADENCE);
        return columns;
    }

    @Override
    protected String getTableName() {
        return Constants.DB.LOCATION.TABLE;
    }

    @Override
    protected String getNullColumnHack() {
        return null;
    }
}
