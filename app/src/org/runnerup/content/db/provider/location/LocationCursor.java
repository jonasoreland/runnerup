package org.runnerup.content.db.provider.location;

import java.util.Date;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.runnerup.content.db.provider.base.AbstractCursor;

/**
 * Cursor wrapper for the {@code location} table.
 */
public class LocationCursor extends AbstractCursor implements LocationModel {
    public LocationCursor(Cursor cursor) {
        super(cursor);
    }

    /**
     * Primary key.
     */
    public long getId() {
        return getLongOrNull(LocationColumns._ID);
    }

    /**
     * Id of the activity the location point belongs to
     */
    public int getActivityId() {
        return getIntegerOrNull(LocationColumns.ACTIVITY_ID);
    }

    /**
     * Lap number of the activity the location point belongs to
     */
    public int getLap() {
        return getIntegerOrNull(LocationColumns.LAP);
    }

    /**
     * Type of the location point
     */
    public int getType() {
        return getIntegerOrNull(LocationColumns.TYPE);
    }

    /**
     * The moment in time when the location point was recorded
     */
    public long getTime() {
        return getLongOrNull(LocationColumns.TIME);
    }

    /**
     * Longitude of the location
     */
    public float getLongitude() {
        return getFloatOrNull(LocationColumns.LONGITUDE);
    }

    /**
     * Latitude of the location
     */
    public float getLatitude() {
        return getFloatOrNull(LocationColumns.LATITUDE);
    }

    /**
     * Accuracy of the location
     * Can be {@code null}.
     */
    @Nullable
    public Float getAccurancy() {
        return getFloatOrNull(LocationColumns.ACCURANCY);
    }

    /**
     * Altitude of the location
     * Can be {@code null}.
     */
    @Nullable
    public Float getAltitude() {
        return getFloatOrNull(LocationColumns.ALTITUDE);
    }

    /**
     * Speed of the location
     * Can be {@code null}.
     */
    @Nullable
    public Float getSpeed() {
        return getFloatOrNull(LocationColumns.SPEED);
    }

    /**
     * Bearing of the location
     * Can be {@code null}.
     */
    @Nullable
    public Float getBearing() {
        return getFloatOrNull(LocationColumns.BEARING);
    }

    /**
     * HR at the location
     * Can be {@code null}.
     */
    @Nullable
    public Integer getHr() {
        return getIntegerOrNull(LocationColumns.HR);
    }

    /**
     * Cadence at the location
     * Can be {@code null}.
     */
    @Nullable
    public Integer getCadence() {
        return getIntegerOrNull(LocationColumns.CADENCE);
    }
}
