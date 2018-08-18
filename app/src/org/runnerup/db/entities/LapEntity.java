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

import java.util.ArrayList;

/**
 * Content values wrapper for the {@code lap} table.
 */

public class LapEntity extends AbstractEntity {

    public LapEntity() {
        super();
    }

    public LapEntity(Cursor c) {
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
     * Duration of the lap (in seconds)
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
    public void setDistance(Double value) {
        values().put(Constants.DB.LAP.DISTANCE, value);
    }

    public Double getDistance() {
        if (values().containsKey(Constants.DB.LAP.DISTANCE)) {
            return values().getAsDouble(Constants.DB.LAP.DISTANCE);
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
    public void setPlannedDistance(Double value) {
        values().put(Constants.DB.LAP.PLANNED_DISTANCE, value);
    }

    public Double getPlannedDistance() {
        if (values().containsKey(Constants.DB.LAP.PLANNED_DISTANCE)) {
            return values().getAsDouble(Constants.DB.LAP.PLANNED_DISTANCE);
        }
        return null;
    }

    /**
     * Planned pace of the lap
     */
    public void setPlannedPace(Double value) {
        values().put(Constants.DB.LAP.PLANNED_PACE, value);
    }

    public Double getPlannedPace() {
        if (values().containsKey(Constants.DB.LAP.PLANNED_PACE)) {
            return values().getAsDouble(Constants.DB.LAP.PLANNED_PACE);
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
    public void setAvgCadence(Double value) {
        values().put(Constants.DB.LAP.AVG_CADENCE, value);
    }

    public Double getAvgCadence() {
        if (values().containsKey(Constants.DB.LAP.AVG_CADENCE)) {
            return values().getAsDouble(Constants.DB.LAP.AVG_CADENCE);
        }
        return null;
    }

    @Override
    public ArrayList<String> getValidColumns() {
        ArrayList<String> columns = new ArrayList<>();
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
