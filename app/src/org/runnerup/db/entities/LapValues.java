package org.runnerup.db.entities;

import android.database.Cursor;
import android.util.Log;

import org.runnerup.common.util.Constants;

import java.util.ArrayList;

/**
 * Content values wrapper for the {@code lap} table.
 */
public class LapValues extends AbstractBaseValues {

    public LapValues() {
        super();
    }

    public LapValues(Cursor c) {
        super();
        try {
            toContentValues(c);
        } catch (Exception e) {
            Log.e(Constants.LOG, e.getMessage());
        }
    }

    /**
     * Id of the activity the lap belongs to
     */
    public void setActivityId(Long value) {
        values().put(Constants.DB.LAP.ACTIVITY, value);
    }

    public Long getActivityId() {
        if (values().containsKey(Constants.DB.LAP.ACTIVITY)) {
            return values().getAsLong(Constants.DB.LAP.ACTIVITY);
        }
        return null;
    }

    /**
     * Number of the lap
     */
    public void setLap(Integer value) {
        values().put(Constants.DB.LAP.LAP, value);
    }

    public Integer getLap() {
        if (values().containsKey(Constants.DB.LAP.LAP)) {
            return values().getAsInteger(Constants.DB.LAP.LAP);
        }
        return null;
    }

    /**
     * Type (intensity) of the lap
     */
    public void setType(Integer value) {
        values().put(Constants.DB.LAP.INTENSITY, value);
    }

    public Integer getType() {
        if (values().containsKey(Constants.DB.LAP.INTENSITY)) {
            return values().getAsInteger(Constants.DB.LAP.INTENSITY);
        }
        return null;
    }

    /**
     * Duration of the lap
     */
    public void setTime(Integer value) {
        values().put(Constants.DB.LAP.TIME, value);
    }

    public Integer getTime() {
        if (values().containsKey(Constants.DB.LAP.TIME)) {
            return values().getAsInteger(Constants.DB.LAP.TIME);
        }
        return null;
    }

    /**
     * Distance of the lap
     */
    public void setDistance(Float value) {
        values().put(Constants.DB.LAP.DISTANCE, value);
    }

    public Float getDistance() {
        if (values().containsKey(Constants.DB.LAP.DISTANCE)) {
            return values().getAsFloat(Constants.DB.LAP.DISTANCE);
        }
        return null;
    }

    /**
     * Planned duration of the lap
     */
    public void setPlannedTime(Integer value) {
        values().put(Constants.DB.LAP.PLANNED_TIME, value);
    }

    public Integer getPlannedTime() {
        if (values().containsKey(Constants.DB.LAP.PLANNED_TIME)) {
            return values().getAsInteger(Constants.DB.LAP.PLANNED_TIME);
        }
        return null;
    }

    /**
     * Planned distance of the lap
     */
    public void setPlannedDistance(Float value) {
        values().put(Constants.DB.LAP.PLANNED_DISTANCE, value);
    }

    public Float getPlannedDistance() {
        if (values().containsKey(Constants.DB.LAP.PLANNED_DISTANCE)) {
            return values().getAsFloat(Constants.DB.LAP.PLANNED_DISTANCE);
        }
        return null;
    }

    /**
     * Planned pace of the lap
     */
    public void setPlannedPace(Float value) {
        values().put(Constants.DB.LAP.PLANNED_PACE, value);
    }

    public Float getPlannedPace() {
        if (values().containsKey(Constants.DB.LAP.PLANNED_PACE)) {
            return values().getAsFloat(Constants.DB.LAP.PLANNED_PACE);
        }
        return null;
    }

    /**
     * Average HR of the lap
     */
    public void setAvgHr(Integer value) {
        values().put(Constants.DB.LAP.AVG_HR, value);
    }

    public Integer getAvgHr() {
        if (values().containsKey(Constants.DB.LAP.AVG_HR)) {
            return values().getAsInteger(Constants.DB.LAP.AVG_HR);
        }
        return null;
    }

    /**
     * Maximum HR of the lap
     */
    public void setMaxHr(Integer value) {
        values().put(Constants.DB.LAP.MAX_HR, value);
    }

    public Integer getMaxHr() {
        if (values().containsKey(Constants.DB.LAP.MAX_HR)) {
            return values().getAsInteger(Constants.DB.LAP.MAX_HR);
        }
        return null;
    }

    /**
     * Avarage cadence of the lap
     */
    public void setAvgCadence(Integer value) {
        values().put(Constants.DB.LAP.AVG_CADENCE, value);
    }

    public Integer getAvgCadence() {
        if (values().containsKey(Constants.DB.LAP.AVG_CADENCE)) {
            return values().getAsInteger(Constants.DB.LAP.AVG_CADENCE);
        }
        return null;
    }

    @Override
    public ArrayList<String> getValidColumns() {
        ArrayList<String> columns = new ArrayList<String>();
        columns.add(Constants.DB.PRIMARY_KEY);
        columns.add(Constants.DB.LAP.ACTIVITY);
        columns.add(Constants.DB.LAP.LAP);
        columns.add(Constants.DB.LAP.INTENSITY);
        columns.add(Constants.DB.LAP.TIME);
        columns.add(Constants.DB.LAP.DISTANCE);
        columns.add(Constants.DB.LAP.PLANNED_TIME);
        columns.add(Constants.DB.LAP.PLANNED_DISTANCE);
        columns.add(Constants.DB.LAP.PLANNED_PACE);
        columns.add(Constants.DB.LAP.AVG_HR);
        columns.add(Constants.DB.LAP.MAX_HR);
        columns.add(Constants.DB.LAP.AVG_CADENCE);
        return columns;
    }

    @Override
    public String getTableName() {
        return Constants.DB.LAP.TABLE;
    }

    @Override
    protected String getNullColumnHack() {
        return null;
    }
}
