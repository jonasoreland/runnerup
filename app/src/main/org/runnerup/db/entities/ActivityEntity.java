/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.db.entities;

import android.database.Cursor;
import android.util.Log;

import org.runnerup.common.util.Constants;
import org.runnerup.workout.Sport;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Content values wrapper for the {@code activity} table.
 */

public class ActivityEntity extends AbstractEntity {

    private final List<LapEntity> laps;

    private final List<LocationEntity> locationPoints;

    public ActivityEntity() {
        super();
        laps = new ArrayList<>();
        locationPoints = new ArrayList<>();
    }

    public ActivityEntity(Cursor c) {
        this();
        try {
            toContentValues(c);
        } catch (Exception e) {
            Log.e(Constants.LOG, e.getMessage());
        }
    }

    /**
     * Start time of the activity (in seconds since epoch)
     */
    private void setStartTime(Long value) {
        values().put(Constants.DB.ACTIVITY.START_TIME, value);
    }

    public void setStartTime(Date date) {
        setStartTime(TimeUnit.MILLISECONDS.toSeconds(date.getTime()));
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
    public void setDistance(Double value) {
        values().put(Constants.DB.ACTIVITY.DISTANCE, value);
    }

    public Double getDistance() {
        if (values().containsKey(Constants.DB.ACTIVITY.DISTANCE)) {
            return values().getAsDouble(Constants.DB.ACTIVITY.DISTANCE);
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
            return Double.valueOf(values().getAsString(Constants.DB.ACTIVITY.TIME)).longValue();
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
        if (value == null) {
            values().put(Constants.DB.ACTIVITY.SPORT, Sport.OTHER.getDbValue());
        } else {
            values().put(Constants.DB.ACTIVITY.SPORT, value);
        }
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
    public void setAvgCadence(Double value) {
        values().put(Constants.DB.ACTIVITY.AVG_CADENCE, value);
    }

    public Double getAvgCadence() {
        if (values().containsKey(Constants.DB.ACTIVITY.AVG_CADENCE)) {
            return values().getAsDouble(Constants.DB.ACTIVITY.AVG_CADENCE);
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
        ArrayList<String> columns = new ArrayList<>();
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

    private void addLap(LapEntity lap) {
        if (lap.getActivityId() != null && (this.getId() == null || !lap.getActivityId().equals(this.getId()))) {
            throw new IllegalArgumentException("Foreign key of lap (" + lap.getActivityId() +
                    ") doesn't match the activity primary key (" + this.getId() + ")");
        }

        if (lap.getActivityId() == null && this.getId() != null) {
            lap.setActivityId(this.getId());
        }

        getLaps().add(lap);
    }

    private void addLaps(List<LapEntity> laps) {
        for (LapEntity lap : laps) {
            this.addLap(lap);
        }
    }

    public void putLaps(List<LapEntity> laps) {
        this.getLaps().clear();
        this.addLaps(laps);
    }

    public List<LapEntity> getLaps() {
        return laps;
    }

    private void addPoint(LocationEntity point) {
        if (point.getActivityId() != null && (this.getId() == null || !point.getActivityId().equals(this.getId()))) {
            throw new IllegalArgumentException("Foreign key of point (" + point.getActivityId() +
                    ") doesn't match the activity primary key (" + this.getId() + ")");
        }

        if (point.getActivityId() == null && this.getId() != null) {
            point.setActivityId(this.getId());
        }

        getLocationPoints().add(point);
    }

    private void addPoints(List<LocationEntity> points) {
        for (LocationEntity point : points) {
            this.addPoint(point);
        }
    }

    public void putPoints(List<LocationEntity> points) {
        this.getLocationPoints().clear();
        this.addPoints(points);
    }

    public List<LocationEntity> getLocationPoints() {
        return locationPoints;
    }
}
