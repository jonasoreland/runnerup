package org.runnerup.content.db.provider.location;

import java.util.Date;

import android.content.ContentResolver;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.runnerup.content.db.provider.base.AbstractContentValues;

/**
 * Content values wrapper for the {@code location} table.
 */
public class LocationContentValues extends AbstractContentValues {
    @Override
    public Uri uri() {
        return LocationColumns.CONTENT_URI;
    }

    /**
     * Update row(s) using the values stored by this object and the given selection.
     *
     * @param contentResolver The content resolver to use.
     * @param where The selection to use (can be {@code null}).
     */
    public int update(ContentResolver contentResolver, @Nullable LocationSelection where) {
        return contentResolver.update(uri(), values(), where == null ? null : where.sel(), where == null ? null : where.args());
    }

    /**
     * Id of the activity the location point belongs to
     */
    public LocationContentValues putActivityId(int value) {
        mContentValues.put(LocationColumns.ACTIVITY_ID, value);
        return this;
    }


    /**
     * Lap number of the activity the location point belongs to
     */
    public LocationContentValues putLap(int value) {
        mContentValues.put(LocationColumns.LAP, value);
        return this;
    }


    /**
     * Type of the location point
     */
    public LocationContentValues putType(int value) {
        mContentValues.put(LocationColumns.TYPE, value);
        return this;
    }


    /**
     * The moment in time when the location point was recorded
     */
    public LocationContentValues putTime(long value) {
        mContentValues.put(LocationColumns.TIME, value);
        return this;
    }


    /**
     * Longitude of the location
     */
    public LocationContentValues putLongitude(float value) {
        mContentValues.put(LocationColumns.LONGITUDE, value);
        return this;
    }


    /**
     * Latitude of the location
     */
    public LocationContentValues putLatitude(float value) {
        mContentValues.put(LocationColumns.LATITUDE, value);
        return this;
    }


    /**
     * Accuracy of the location
     */
    public LocationContentValues putAccurancy(@Nullable Float value) {
        mContentValues.put(LocationColumns.ACCURANCY, value);
        return this;
    }

    public LocationContentValues putAccurancyNull() {
        mContentValues.putNull(LocationColumns.ACCURANCY);
        return this;
    }

    /**
     * Altitude of the location
     */
    public LocationContentValues putAltitude(@Nullable Float value) {
        mContentValues.put(LocationColumns.ALTITUDE, value);
        return this;
    }

    public LocationContentValues putAltitudeNull() {
        mContentValues.putNull(LocationColumns.ALTITUDE);
        return this;
    }

    /**
     * Speed of the location
     */
    public LocationContentValues putSpeed(@Nullable Float value) {
        mContentValues.put(LocationColumns.SPEED, value);
        return this;
    }

    public LocationContentValues putSpeedNull() {
        mContentValues.putNull(LocationColumns.SPEED);
        return this;
    }

    /**
     * Bearing of the location
     */
    public LocationContentValues putBearing(@Nullable Float value) {
        mContentValues.put(LocationColumns.BEARING, value);
        return this;
    }

    public LocationContentValues putBearingNull() {
        mContentValues.putNull(LocationColumns.BEARING);
        return this;
    }

    /**
     * HR at the location
     */
    public LocationContentValues putHr(@Nullable Integer value) {
        mContentValues.put(LocationColumns.HR, value);
        return this;
    }

    public LocationContentValues putHrNull() {
        mContentValues.putNull(LocationColumns.HR);
        return this;
    }

    /**
     * Cadence at the location
     */
    public LocationContentValues putCadence(@Nullable Integer value) {
        mContentValues.put(LocationColumns.CADENCE, value);
        return this;
    }

    public LocationContentValues putCadenceNull() {
        mContentValues.putNull(LocationColumns.CADENCE);
        return this;
    }
}
