package org.runnerup.db.entities;

import android.database.Cursor;
import android.util.Log;

import org.runnerup.common.util.Constants;

import java.util.ArrayList;

/**
 * Content values wrapper for the {@code activity} table.
 */
public class ActivityValues extends AbstractBaseValues {

    public ActivityValues(Cursor c) {
        try {
            toContentValues(c);
        } catch (Exception e) {
            Log.e("RunnerUp", e.getMessage());
        }
    }

    /**
     * Start time of the activity
     */
    public void setStartTime(Long value) {
        values().put(Constants.DB.ACTIVITY.START_TIME, value);
    }

    public Long getStartTime() {
        if (values().containsKey(Constants.DB.ACTIVITY.START_TIME)) {
            return values().getAsLong(Constants.DB.ACTIVITY.START_TIME);
        }
        return null;
    }

    /**
     * Distance of the activity
     */
    public void setDistance(Float value) {
        values().put(Constants.DB.ACTIVITY.DISTANCE, value);
    }

    public Float getDistance() {
        if (values().containsKey(Constants.DB.ACTIVITY.DISTANCE)) {
            return values().getAsFloat(Constants.DB.ACTIVITY.DISTANCE);
        }
        return null;
    }

    /**
     * Duration of the activity
     */
    public void setTime(Long value) {
        values().put(Constants.DB.ACTIVITY.TIME, value);
    }

    public Long getTime() {
        if (values().containsKey(Constants.DB.ACTIVITY.TIME)) {
            return values().getAsLong(Constants.DB.ACTIVITY.TIME);
        }
        return null;
    }

    /**
     * Name of the activity
     */
    public void setName(String value) {
        values().put(Constants.DB.ACTIVITY.NAME, value);
    }

    public String getName() {
        if (values().containsKey(Constants.DB.ACTIVITY.NAME)) {
            return values().getAsString(Constants.DB.ACTIVITY.NAME);
        }
        return null;
    }

    /**
     * Comment for the activity
     */
    public void setComment(String value) {
        values().put(Constants.DB.ACTIVITY.COMMENT, value);
    }

    public String getComment() {
        if (values().containsKey(Constants.DB.ACTIVITY.COMMENT)) {
            return values().getAsString(Constants.DB.ACTIVITY.COMMENT);
        }
        return null;
    }

    /**
     * Sport type of the activity
     */
    public void setSport(Integer value) {
        values().put(Constants.DB.ACTIVITY.SPORT, value);
    }

    public Integer getSport() {
        if (values().containsKey(Constants.DB.ACTIVITY.SPORT)) {
            return values().getAsInteger(Constants.DB.ACTIVITY.SPORT);
        }
        return null;
    }

    /**
     * Maximum HR of the activity
     */
    public void setMaxHr(Integer value) {
        values().put(Constants.DB.ACTIVITY.MAX_HR, value);
    }

    public Integer getMaxHr() {
        if (values().containsKey(Constants.DB.ACTIVITY.MAX_HR)) {
            return values().getAsInteger(Constants.DB.ACTIVITY.MAX_HR);
        }
        return null;
    }

    /**
     * Avarage HR of the activity
     */
    public void setAvgHr(Integer value) {
        values().put(Constants.DB.ACTIVITY.AVG_HR, value);
    }

    public Integer getAvgHr() {
        if (values().containsKey(Constants.DB.ACTIVITY.AVG_HR)) {
            return values().getAsInteger(Constants.DB.ACTIVITY.AVG_HR);
        }
        return null;
    }

    /**
     * Avarage cadence of the activity
     */
    public void setAvgCadence(Integer value) {
        values().put(Constants.DB.ACTIVITY.AVG_CADENCE, value);
    }

    public Integer getAvgCadence() {
        if (values().containsKey(Constants.DB.ACTIVITY.AVG_CADENCE)) {
            return values().getAsInteger(Constants.DB.ACTIVITY.AVG_CADENCE);
        }
        return null;
    }

    /**
     * Status of the activity
     */
    public void setDeleted(Boolean value) {
        values().put(Constants.DB.ACTIVITY.DELETED, value);
    }

    public Boolean getDeleted() {
        if (values().containsKey(Constants.DB.ACTIVITY.DELETED)) {
            return values().getAsBoolean(Constants.DB.ACTIVITY.DELETED);
        }
        return Boolean.FALSE;
    }

    /**
     * Workaround column
     */
    public void putNullcolumnhack(String value) {
        values().put(Constants.DB.ACTIVITY.NULLCOLUMNHACK, value);

    }

    @Override
    protected ArrayList<String> getValidColumns() {
        ArrayList<String> columns = new ArrayList<String>();
        columns.add(Constants.DB.PRIMARY_KEY);
        columns.add(Constants.DB.ACTIVITY.START_TIME);
        columns.add(Constants.DB.ACTIVITY.DISTANCE);
        columns.add(Constants.DB.ACTIVITY.TIME);
        columns.add(Constants.DB.ACTIVITY.NAME);
        columns.add(Constants.DB.ACTIVITY.COMMENT);
        columns.add(Constants.DB.ACTIVITY.SPORT);
        columns.add(Constants.DB.ACTIVITY.MAX_HR);
        columns.add(Constants.DB.ACTIVITY.AVG_HR);
        columns.add(Constants.DB.ACTIVITY.AVG_CADENCE);
        columns.add(Constants.DB.ACTIVITY.DELETED);
        columns.add(Constants.DB.ACTIVITY.NULLCOLUMNHACK);
        return columns;
    }

    @Override
    protected String getTableName() {
        return Constants.DB.ACTIVITY.TABLE;
    }


    @Override
    protected String getNullColumnHack() {
        return Constants.DB.ACTIVITY.NULLCOLUMNHACK;
    }
}
