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
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;

import androidx.annotation.NonNull;

import org.runnerup.common.util.Constants;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Content values wrapper for the {@code location} table.
 */

public class LocationEntity extends AbstractEntity {

    private Double mDistance;
    private Long mElapsed;

    public LocationEntity() {
        super();
    }

    private LocationEntity(Cursor c, LocationEntity lastLocation) {
        super();
        toContentValues(c);

        // Compute distance and elapsed
        Double distance = 0.0;
        Long elapsed = 0L;
        if (lastLocation != null) {
            //First point is zero
            int type = this.getType();
            distance = lastLocation.getDistance();
            elapsed = lastLocation.getElapsed();
            switch (type) {
                case Constants.DB.LOCATION.TYPE_START:
                case Constants.DB.LOCATION.TYPE_END:
                case Constants.DB.LOCATION.TYPE_RESUME:
                    break;
                case Constants.DB.LOCATION.TYPE_PAUSE:
                case Constants.DB.LOCATION.TYPE_GPS:
                    float[] res = {
                            0
                    };
                    Location.distanceBetween(lastLocation.getLatitude(),
                            lastLocation.getLongitude(), this.getLatitude(), this.getLongitude(),
                            res);
                    distance += res[0];
                    elapsed += this.getTime() - lastLocation.getTime();
                    break;
            }
        }
        mDistance = distance;
        mElapsed = elapsed;
    }

    public static class LocationList<E> implements Iterable<E> {
        LocationIterator iter;
        final long mID;
        final SQLiteDatabase mDB;

        public LocationList(SQLiteDatabase mDB, long mID) {
            this.mID = mID;
            this.mDB = mDB;
        }

        @NonNull
        @Override
        public Iterator<E> iterator() {
            iter = new LocationIterator(this.mID, this.mDB);
            return iter;
        }

        public int getCount() {
            return iter == null ? 0 : iter.getCount();
        }

        public void close() {
            if (iter != null) {iter.close();}
        }

        private class LocationIterator implements Iterator<E> {
            private LocationIterator(long mID, SQLiteDatabase mDB) {
                c = mDB.query(Constants.DB.LOCATION.TABLE, from, "activity_id == " + mID,
                        null, null, null, "_id", null);
                if (!c.moveToFirst()) {
                    c.close();
                }
            }

            final String[] from = new String[]{
                    Constants.DB.LOCATION.LATITUDE,
                    Constants.DB.LOCATION.LONGITUDE,
                    Constants.DB.LOCATION.ALTITUDE,
                    Constants.DB.LOCATION.TYPE,
                    Constants.DB.LOCATION.TIME,
                    Constants.DB.LOCATION.LAP,
                    Constants.DB.LOCATION.HR
            };
            final Cursor c;
            E prev = null;

            public int getCount() {
                return c.getCount();
            }

            public void close() {
                if (!c.isClosed()) {
                    c.close();
                }
            }

            @Override
            public boolean hasNext() {
                return !c.isClosed() && !c.isLast();
            }

            @Override
            @SuppressWarnings("unchecked")
            public E next() {
                c.moveToNext();
                prev = (E)new LocationEntity(c, (LocationEntity)prev);
                if (c.isLast()) {
                    c.close();
                }
                return prev;
            }

            @Override
            public void remove() {
                next();
            }
        }
    }

    /**
     * Id of the activity the location point belongs to
     */
    public void setActivityId(Long value) {
        values().put(Constants.DB.LOCATION.ACTIVITY, value);
    }

    public Long getActivityId() {
        if (values().containsKey(Constants.DB.LOCATION.ACTIVITY)) {
            return values().getAsLong(Constants.DB.LOCATION.ACTIVITY);
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
     * The time in ms (since epoch) for the location point
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
    public void setLongitude(Double value) {
        values().put(Constants.DB.LOCATION.LONGITUDE, value);
    }

    public Double getLongitude() {
        if (values().containsKey(Constants.DB.LOCATION.LONGITUDE)) {
            return values().getAsDouble(Constants.DB.LOCATION.LONGITUDE);
        }
        return null;
    }

    /**
     * Latitude of the location
     */
    public void setLatitude(Double value) {
        values().put(Constants.DB.LOCATION.LATITUDE, value);
    }

    public Double getLatitude() {
        if (values().containsKey(Constants.DB.LOCATION.LATITUDE)) {
            return values().getAsDouble(Constants.DB.LOCATION.LATITUDE);
        }
        return null;
    }

    /**
     * Distance of the location
     */

    public Double getDistance() {
        return mDistance;
    }

    /**
     * Elapsed time in ms, excluding pauses
     */
    public Long getElapsed() {
        return mElapsed;
    }


    /**
     * Accuracy of the location
     */
    private void setAccuracy(Double value) {
        values().put(Constants.DB.LOCATION.ACCURANCY, value);
    }

    public Double getAccuracy() {
        if (values().containsKey(Constants.DB.LOCATION.ACCURANCY)) {
            return values().getAsDouble(Constants.DB.LOCATION.ACCURANCY);
        }
        return null;
    }

    /**
     * Satellites for the location
     */
    private void setSatelites(int value) {
        values().put(Constants.DB.LOCATION.SATELLITES, value);
    }

    public int getSatellites() {
        if (values().containsKey(Constants.DB.LOCATION.SATELLITES)) {
            return values().getAsInteger(Constants.DB.LOCATION.SATELLITES);
        }
        return -1;
    }

    /**
     * Altitude of the location
     */
    public void setAltitude(Double value) {
        values().put(Constants.DB.LOCATION.ALTITUDE, value);
    }

    public Double getAltitude() {
        if (values().containsKey(Constants.DB.LOCATION.ALTITUDE)) {
            return values().getAsDouble(Constants.DB.LOCATION.ALTITUDE);
        }
        return null;
    }

    /**
     * Altitude of the location, raw GPS format (not baro or geoid adjusted)
     */
    public void setGPSAltitude(Double value) {
        values().put(Constants.DB.LOCATION.GPS_ALTITUDE, value);
    }

    public Double getGPSAltitude() {
        if (values().containsKey(Constants.DB.LOCATION.GPS_ALTITUDE)) {
            return values().getAsDouble(Constants.DB.LOCATION.GPS_ALTITUDE);
        }
        return null;
    }

    /**
     * Speed of the location
     */
    public void setSpeed(Double value) {
        values().put(Constants.DB.LOCATION.SPEED, value);
    }

    public Double getSpeed() {
        if (values().containsKey(Constants.DB.LOCATION.SPEED)) {
            return values().getAsDouble(Constants.DB.LOCATION.SPEED);
        }
        return null;
    }

    /**
     * Bearing of the location
     */
    private void setBearing(Double value) {
        values().put(Constants.DB.LOCATION.BEARING, value);
    }

    public Double getBearing() {
        if (values().containsKey(Constants.DB.LOCATION.BEARING)) {
            return values().getAsDouble(Constants.DB.LOCATION.BEARING);
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
    private void setCadence(Double value) {
        values().put(Constants.DB.LOCATION.CADENCE, value);
    }

    public Double getCadence() {
        if (values().containsKey(Constants.DB.LOCATION.CADENCE)) {
            return values().getAsDouble(Constants.DB.LOCATION.CADENCE);
        }
        return null;
    }

    /**
     * Temperature at the location
     */
    public void setTemperature(Double value) {
        values().put(Constants.DB.LOCATION.TEMPERATURE, value);
    }

    public Double getTemperature() {
        if (values().containsKey(Constants.DB.LOCATION.TEMPERATURE)) {
            return values().getAsDouble(Constants.DB.LOCATION.TEMPERATURE);
        }
        return null;
    }

    /**
     * Pressure at the location
     */
    public void setPressure(Double value) {
        values().put(Constants.DB.LOCATION.PRESSURE, value);
    }

    public Double getPressure() {
        if (values().containsKey(Constants.DB.LOCATION.PRESSURE)) {
            return values().getAsDouble(Constants.DB.LOCATION.PRESSURE);
        }
        return null;
    }

    @Override
    protected List<String> getValidColumns() {
        List<String> columns = new ArrayList<>();
        columns.add(Constants.DB.PRIMARY_KEY);
        columns.add(Constants.DB.LOCATION.ACTIVITY);
        columns.add(Constants.DB.LOCATION.LAP);
        columns.add(Constants.DB.LOCATION.TYPE);
        columns.add(Constants.DB.LOCATION.TIME);

        columns.add(Constants.DB.LOCATION.ELAPSED);
        columns.add(Constants.DB.LOCATION.DISTANCE);

        columns.add(Constants.DB.LOCATION.LATITUDE);
        columns.add(Constants.DB.LOCATION.LONGITUDE);
        columns.add(Constants.DB.LOCATION.ALTITUDE);

        columns.add(Constants.DB.LOCATION.HR);
        columns.add(Constants.DB.LOCATION.CADENCE);
        columns.add(Constants.DB.LOCATION.TEMPERATURE);
        columns.add(Constants.DB.LOCATION.PRESSURE);

        columns.add(Constants.DB.LOCATION.GPS_ALTITUDE);
        columns.add(Constants.DB.LOCATION.ACCURANCY);
        columns.add(Constants.DB.LOCATION.SPEED);
        columns.add(Constants.DB.LOCATION.BEARING);
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
