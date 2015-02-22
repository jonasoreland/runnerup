package org.runnerup.content.db.provider.location;

import java.util.Date;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import org.runnerup.content.db.provider.base.AbstractSelection;

/**
 * Selection for the {@code location} table.
 */
public class LocationSelection extends AbstractSelection<LocationSelection> {
    @Override
    protected Uri baseUri() {
        return LocationColumns.CONTENT_URI;
    }

    /**
     * Query the given content resolver using this selection.
     *
     * @param contentResolver The content resolver to query.
     * @param projection A list of which columns to return. Passing null will return all columns, which is inefficient.
     * @param sortOrder How to order the rows, formatted as an SQL ORDER BY clause (excluding the ORDER BY itself). Passing null will use the default sort
     *            order, which may be unordered.
     * @return A {@code LocationCursor} object, which is positioned before the first entry, or null.
     */
    public LocationCursor query(ContentResolver contentResolver, String[] projection, String sortOrder) {
        Cursor cursor = contentResolver.query(uri(), projection, sel(), args(), sortOrder);
        if (cursor == null) return null;
        return new LocationCursor(cursor);
    }

    /**
     * Equivalent of calling {@code query(contentResolver, projection, null)}.
     */
    public LocationCursor query(ContentResolver contentResolver, String[] projection) {
        return query(contentResolver, projection, null);
    }

    /**
     * Equivalent of calling {@code query(contentResolver, projection, null, null)}.
     */
    public LocationCursor query(ContentResolver contentResolver) {
        return query(contentResolver, null, null);
    }


    public LocationSelection id(long... value) {
        addEquals("location." + LocationColumns._ID, toObjectArray(value));
        return this;
    }

    public LocationSelection activityId(int... value) {
        addEquals(LocationColumns.ACTIVITY_ID, toObjectArray(value));
        return this;
    }

    public LocationSelection activityIdNot(int... value) {
        addNotEquals(LocationColumns.ACTIVITY_ID, toObjectArray(value));
        return this;
    }

    public LocationSelection activityIdGt(int value) {
        addGreaterThan(LocationColumns.ACTIVITY_ID, value);
        return this;
    }

    public LocationSelection activityIdGtEq(int value) {
        addGreaterThanOrEquals(LocationColumns.ACTIVITY_ID, value);
        return this;
    }

    public LocationSelection activityIdLt(int value) {
        addLessThan(LocationColumns.ACTIVITY_ID, value);
        return this;
    }

    public LocationSelection activityIdLtEq(int value) {
        addLessThanOrEquals(LocationColumns.ACTIVITY_ID, value);
        return this;
    }

    public LocationSelection lap(int... value) {
        addEquals(LocationColumns.LAP, toObjectArray(value));
        return this;
    }

    public LocationSelection lapNot(int... value) {
        addNotEquals(LocationColumns.LAP, toObjectArray(value));
        return this;
    }

    public LocationSelection lapGt(int value) {
        addGreaterThan(LocationColumns.LAP, value);
        return this;
    }

    public LocationSelection lapGtEq(int value) {
        addGreaterThanOrEquals(LocationColumns.LAP, value);
        return this;
    }

    public LocationSelection lapLt(int value) {
        addLessThan(LocationColumns.LAP, value);
        return this;
    }

    public LocationSelection lapLtEq(int value) {
        addLessThanOrEquals(LocationColumns.LAP, value);
        return this;
    }

    public LocationSelection type(int... value) {
        addEquals(LocationColumns.TYPE, toObjectArray(value));
        return this;
    }

    public LocationSelection typeNot(int... value) {
        addNotEquals(LocationColumns.TYPE, toObjectArray(value));
        return this;
    }

    public LocationSelection typeGt(int value) {
        addGreaterThan(LocationColumns.TYPE, value);
        return this;
    }

    public LocationSelection typeGtEq(int value) {
        addGreaterThanOrEquals(LocationColumns.TYPE, value);
        return this;
    }

    public LocationSelection typeLt(int value) {
        addLessThan(LocationColumns.TYPE, value);
        return this;
    }

    public LocationSelection typeLtEq(int value) {
        addLessThanOrEquals(LocationColumns.TYPE, value);
        return this;
    }

    public LocationSelection time(long... value) {
        addEquals(LocationColumns.TIME, toObjectArray(value));
        return this;
    }

    public LocationSelection timeNot(long... value) {
        addNotEquals(LocationColumns.TIME, toObjectArray(value));
        return this;
    }

    public LocationSelection timeGt(long value) {
        addGreaterThan(LocationColumns.TIME, value);
        return this;
    }

    public LocationSelection timeGtEq(long value) {
        addGreaterThanOrEquals(LocationColumns.TIME, value);
        return this;
    }

    public LocationSelection timeLt(long value) {
        addLessThan(LocationColumns.TIME, value);
        return this;
    }

    public LocationSelection timeLtEq(long value) {
        addLessThanOrEquals(LocationColumns.TIME, value);
        return this;
    }

    public LocationSelection longitude(float... value) {
        addEquals(LocationColumns.LONGITUDE, toObjectArray(value));
        return this;
    }

    public LocationSelection longitudeNot(float... value) {
        addNotEquals(LocationColumns.LONGITUDE, toObjectArray(value));
        return this;
    }

    public LocationSelection longitudeGt(float value) {
        addGreaterThan(LocationColumns.LONGITUDE, value);
        return this;
    }

    public LocationSelection longitudeGtEq(float value) {
        addGreaterThanOrEquals(LocationColumns.LONGITUDE, value);
        return this;
    }

    public LocationSelection longitudeLt(float value) {
        addLessThan(LocationColumns.LONGITUDE, value);
        return this;
    }

    public LocationSelection longitudeLtEq(float value) {
        addLessThanOrEquals(LocationColumns.LONGITUDE, value);
        return this;
    }

    public LocationSelection latitude(float... value) {
        addEquals(LocationColumns.LATITUDE, toObjectArray(value));
        return this;
    }

    public LocationSelection latitudeNot(float... value) {
        addNotEquals(LocationColumns.LATITUDE, toObjectArray(value));
        return this;
    }

    public LocationSelection latitudeGt(float value) {
        addGreaterThan(LocationColumns.LATITUDE, value);
        return this;
    }

    public LocationSelection latitudeGtEq(float value) {
        addGreaterThanOrEquals(LocationColumns.LATITUDE, value);
        return this;
    }

    public LocationSelection latitudeLt(float value) {
        addLessThan(LocationColumns.LATITUDE, value);
        return this;
    }

    public LocationSelection latitudeLtEq(float value) {
        addLessThanOrEquals(LocationColumns.LATITUDE, value);
        return this;
    }

    public LocationSelection accurancy(Float... value) {
        addEquals(LocationColumns.ACCURANCY, value);
        return this;
    }

    public LocationSelection accurancyNot(Float... value) {
        addNotEquals(LocationColumns.ACCURANCY, value);
        return this;
    }

    public LocationSelection accurancyGt(float value) {
        addGreaterThan(LocationColumns.ACCURANCY, value);
        return this;
    }

    public LocationSelection accurancyGtEq(float value) {
        addGreaterThanOrEquals(LocationColumns.ACCURANCY, value);
        return this;
    }

    public LocationSelection accurancyLt(float value) {
        addLessThan(LocationColumns.ACCURANCY, value);
        return this;
    }

    public LocationSelection accurancyLtEq(float value) {
        addLessThanOrEquals(LocationColumns.ACCURANCY, value);
        return this;
    }

    public LocationSelection altitude(Float... value) {
        addEquals(LocationColumns.ALTITUDE, value);
        return this;
    }

    public LocationSelection altitudeNot(Float... value) {
        addNotEquals(LocationColumns.ALTITUDE, value);
        return this;
    }

    public LocationSelection altitudeGt(float value) {
        addGreaterThan(LocationColumns.ALTITUDE, value);
        return this;
    }

    public LocationSelection altitudeGtEq(float value) {
        addGreaterThanOrEquals(LocationColumns.ALTITUDE, value);
        return this;
    }

    public LocationSelection altitudeLt(float value) {
        addLessThan(LocationColumns.ALTITUDE, value);
        return this;
    }

    public LocationSelection altitudeLtEq(float value) {
        addLessThanOrEquals(LocationColumns.ALTITUDE, value);
        return this;
    }

    public LocationSelection speed(Float... value) {
        addEquals(LocationColumns.SPEED, value);
        return this;
    }

    public LocationSelection speedNot(Float... value) {
        addNotEquals(LocationColumns.SPEED, value);
        return this;
    }

    public LocationSelection speedGt(float value) {
        addGreaterThan(LocationColumns.SPEED, value);
        return this;
    }

    public LocationSelection speedGtEq(float value) {
        addGreaterThanOrEquals(LocationColumns.SPEED, value);
        return this;
    }

    public LocationSelection speedLt(float value) {
        addLessThan(LocationColumns.SPEED, value);
        return this;
    }

    public LocationSelection speedLtEq(float value) {
        addLessThanOrEquals(LocationColumns.SPEED, value);
        return this;
    }

    public LocationSelection bearing(Float... value) {
        addEquals(LocationColumns.BEARING, value);
        return this;
    }

    public LocationSelection bearingNot(Float... value) {
        addNotEquals(LocationColumns.BEARING, value);
        return this;
    }

    public LocationSelection bearingGt(float value) {
        addGreaterThan(LocationColumns.BEARING, value);
        return this;
    }

    public LocationSelection bearingGtEq(float value) {
        addGreaterThanOrEquals(LocationColumns.BEARING, value);
        return this;
    }

    public LocationSelection bearingLt(float value) {
        addLessThan(LocationColumns.BEARING, value);
        return this;
    }

    public LocationSelection bearingLtEq(float value) {
        addLessThanOrEquals(LocationColumns.BEARING, value);
        return this;
    }

    public LocationSelection hr(Integer... value) {
        addEquals(LocationColumns.HR, value);
        return this;
    }

    public LocationSelection hrNot(Integer... value) {
        addNotEquals(LocationColumns.HR, value);
        return this;
    }

    public LocationSelection hrGt(int value) {
        addGreaterThan(LocationColumns.HR, value);
        return this;
    }

    public LocationSelection hrGtEq(int value) {
        addGreaterThanOrEquals(LocationColumns.HR, value);
        return this;
    }

    public LocationSelection hrLt(int value) {
        addLessThan(LocationColumns.HR, value);
        return this;
    }

    public LocationSelection hrLtEq(int value) {
        addLessThanOrEquals(LocationColumns.HR, value);
        return this;
    }

    public LocationSelection cadence(Integer... value) {
        addEquals(LocationColumns.CADENCE, value);
        return this;
    }

    public LocationSelection cadenceNot(Integer... value) {
        addNotEquals(LocationColumns.CADENCE, value);
        return this;
    }

    public LocationSelection cadenceGt(int value) {
        addGreaterThan(LocationColumns.CADENCE, value);
        return this;
    }

    public LocationSelection cadenceGtEq(int value) {
        addGreaterThanOrEquals(LocationColumns.CADENCE, value);
        return this;
    }

    public LocationSelection cadenceLt(int value) {
        addLessThan(LocationColumns.CADENCE, value);
        return this;
    }

    public LocationSelection cadenceLtEq(int value) {
        addLessThanOrEquals(LocationColumns.CADENCE, value);
        return this;
    }
}
