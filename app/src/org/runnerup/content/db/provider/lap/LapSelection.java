package org.runnerup.content.db.provider.lap;

import java.util.Date;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import org.runnerup.content.db.provider.base.AbstractSelection;

/**
 * Selection for the {@code lap} table.
 */
public class LapSelection extends AbstractSelection<LapSelection> {
    @Override
    protected Uri baseUri() {
        return LapColumns.CONTENT_URI;
    }

    /**
     * Query the given content resolver using this selection.
     *
     * @param contentResolver The content resolver to query.
     * @param projection A list of which columns to return. Passing null will return all columns, which is inefficient.
     * @param sortOrder How to order the rows, formatted as an SQL ORDER BY clause (excluding the ORDER BY itself). Passing null will use the default sort
     *            order, which may be unordered.
     * @return A {@code LapCursor} object, which is positioned before the first entry, or null.
     */
    public LapCursor query(ContentResolver contentResolver, String[] projection, String sortOrder) {
        Cursor cursor = contentResolver.query(uri(), projection, sel(), args(), sortOrder);
        if (cursor == null) return null;
        return new LapCursor(cursor);
    }

    /**
     * Equivalent of calling {@code query(contentResolver, projection, null)}.
     */
    public LapCursor query(ContentResolver contentResolver, String[] projection) {
        return query(contentResolver, projection, null);
    }

    /**
     * Equivalent of calling {@code query(contentResolver, projection, null, null)}.
     */
    public LapCursor query(ContentResolver contentResolver) {
        return query(contentResolver, null, null);
    }


    public LapSelection id(long... value) {
        addEquals("lap." + LapColumns._ID, toObjectArray(value));
        return this;
    }

    public LapSelection activityId(int... value) {
        addEquals(LapColumns.ACTIVITY_ID, toObjectArray(value));
        return this;
    }

    public LapSelection activityIdNot(int... value) {
        addNotEquals(LapColumns.ACTIVITY_ID, toObjectArray(value));
        return this;
    }

    public LapSelection activityIdGt(int value) {
        addGreaterThan(LapColumns.ACTIVITY_ID, value);
        return this;
    }

    public LapSelection activityIdGtEq(int value) {
        addGreaterThanOrEquals(LapColumns.ACTIVITY_ID, value);
        return this;
    }

    public LapSelection activityIdLt(int value) {
        addLessThan(LapColumns.ACTIVITY_ID, value);
        return this;
    }

    public LapSelection activityIdLtEq(int value) {
        addLessThanOrEquals(LapColumns.ACTIVITY_ID, value);
        return this;
    }

    public LapSelection lap(int... value) {
        addEquals(LapColumns.LAP, toObjectArray(value));
        return this;
    }

    public LapSelection lapNot(int... value) {
        addNotEquals(LapColumns.LAP, toObjectArray(value));
        return this;
    }

    public LapSelection lapGt(int value) {
        addGreaterThan(LapColumns.LAP, value);
        return this;
    }

    public LapSelection lapGtEq(int value) {
        addGreaterThanOrEquals(LapColumns.LAP, value);
        return this;
    }

    public LapSelection lapLt(int value) {
        addLessThan(LapColumns.LAP, value);
        return this;
    }

    public LapSelection lapLtEq(int value) {
        addLessThanOrEquals(LapColumns.LAP, value);
        return this;
    }

    public LapSelection type(int... value) {
        addEquals(LapColumns.TYPE, toObjectArray(value));
        return this;
    }

    public LapSelection typeNot(int... value) {
        addNotEquals(LapColumns.TYPE, toObjectArray(value));
        return this;
    }

    public LapSelection typeGt(int value) {
        addGreaterThan(LapColumns.TYPE, value);
        return this;
    }

    public LapSelection typeGtEq(int value) {
        addGreaterThanOrEquals(LapColumns.TYPE, value);
        return this;
    }

    public LapSelection typeLt(int value) {
        addLessThan(LapColumns.TYPE, value);
        return this;
    }

    public LapSelection typeLtEq(int value) {
        addLessThanOrEquals(LapColumns.TYPE, value);
        return this;
    }

    public LapSelection time(Integer... value) {
        addEquals(LapColumns.TIME, value);
        return this;
    }

    public LapSelection timeNot(Integer... value) {
        addNotEquals(LapColumns.TIME, value);
        return this;
    }

    public LapSelection timeGt(int value) {
        addGreaterThan(LapColumns.TIME, value);
        return this;
    }

    public LapSelection timeGtEq(int value) {
        addGreaterThanOrEquals(LapColumns.TIME, value);
        return this;
    }

    public LapSelection timeLt(int value) {
        addLessThan(LapColumns.TIME, value);
        return this;
    }

    public LapSelection timeLtEq(int value) {
        addLessThanOrEquals(LapColumns.TIME, value);
        return this;
    }

    public LapSelection distance(Float... value) {
        addEquals(LapColumns.DISTANCE, value);
        return this;
    }

    public LapSelection distanceNot(Float... value) {
        addNotEquals(LapColumns.DISTANCE, value);
        return this;
    }

    public LapSelection distanceGt(float value) {
        addGreaterThan(LapColumns.DISTANCE, value);
        return this;
    }

    public LapSelection distanceGtEq(float value) {
        addGreaterThanOrEquals(LapColumns.DISTANCE, value);
        return this;
    }

    public LapSelection distanceLt(float value) {
        addLessThan(LapColumns.DISTANCE, value);
        return this;
    }

    public LapSelection distanceLtEq(float value) {
        addLessThanOrEquals(LapColumns.DISTANCE, value);
        return this;
    }

    public LapSelection plannedTime(Integer... value) {
        addEquals(LapColumns.PLANNED_TIME, value);
        return this;
    }

    public LapSelection plannedTimeNot(Integer... value) {
        addNotEquals(LapColumns.PLANNED_TIME, value);
        return this;
    }

    public LapSelection plannedTimeGt(int value) {
        addGreaterThan(LapColumns.PLANNED_TIME, value);
        return this;
    }

    public LapSelection plannedTimeGtEq(int value) {
        addGreaterThanOrEquals(LapColumns.PLANNED_TIME, value);
        return this;
    }

    public LapSelection plannedTimeLt(int value) {
        addLessThan(LapColumns.PLANNED_TIME, value);
        return this;
    }

    public LapSelection plannedTimeLtEq(int value) {
        addLessThanOrEquals(LapColumns.PLANNED_TIME, value);
        return this;
    }

    public LapSelection plannedDistance(Float... value) {
        addEquals(LapColumns.PLANNED_DISTANCE, value);
        return this;
    }

    public LapSelection plannedDistanceNot(Float... value) {
        addNotEquals(LapColumns.PLANNED_DISTANCE, value);
        return this;
    }

    public LapSelection plannedDistanceGt(float value) {
        addGreaterThan(LapColumns.PLANNED_DISTANCE, value);
        return this;
    }

    public LapSelection plannedDistanceGtEq(float value) {
        addGreaterThanOrEquals(LapColumns.PLANNED_DISTANCE, value);
        return this;
    }

    public LapSelection plannedDistanceLt(float value) {
        addLessThan(LapColumns.PLANNED_DISTANCE, value);
        return this;
    }

    public LapSelection plannedDistanceLtEq(float value) {
        addLessThanOrEquals(LapColumns.PLANNED_DISTANCE, value);
        return this;
    }

    public LapSelection plannedPace(Float... value) {
        addEquals(LapColumns.PLANNED_PACE, value);
        return this;
    }

    public LapSelection plannedPaceNot(Float... value) {
        addNotEquals(LapColumns.PLANNED_PACE, value);
        return this;
    }

    public LapSelection plannedPaceGt(float value) {
        addGreaterThan(LapColumns.PLANNED_PACE, value);
        return this;
    }

    public LapSelection plannedPaceGtEq(float value) {
        addGreaterThanOrEquals(LapColumns.PLANNED_PACE, value);
        return this;
    }

    public LapSelection plannedPaceLt(float value) {
        addLessThan(LapColumns.PLANNED_PACE, value);
        return this;
    }

    public LapSelection plannedPaceLtEq(float value) {
        addLessThanOrEquals(LapColumns.PLANNED_PACE, value);
        return this;
    }

    public LapSelection avgHr(Integer... value) {
        addEquals(LapColumns.AVG_HR, value);
        return this;
    }

    public LapSelection avgHrNot(Integer... value) {
        addNotEquals(LapColumns.AVG_HR, value);
        return this;
    }

    public LapSelection avgHrGt(int value) {
        addGreaterThan(LapColumns.AVG_HR, value);
        return this;
    }

    public LapSelection avgHrGtEq(int value) {
        addGreaterThanOrEquals(LapColumns.AVG_HR, value);
        return this;
    }

    public LapSelection avgHrLt(int value) {
        addLessThan(LapColumns.AVG_HR, value);
        return this;
    }

    public LapSelection avgHrLtEq(int value) {
        addLessThanOrEquals(LapColumns.AVG_HR, value);
        return this;
    }

    public LapSelection maxHr(Integer... value) {
        addEquals(LapColumns.MAX_HR, value);
        return this;
    }

    public LapSelection maxHrNot(Integer... value) {
        addNotEquals(LapColumns.MAX_HR, value);
        return this;
    }

    public LapSelection maxHrGt(int value) {
        addGreaterThan(LapColumns.MAX_HR, value);
        return this;
    }

    public LapSelection maxHrGtEq(int value) {
        addGreaterThanOrEquals(LapColumns.MAX_HR, value);
        return this;
    }

    public LapSelection maxHrLt(int value) {
        addLessThan(LapColumns.MAX_HR, value);
        return this;
    }

    public LapSelection maxHrLtEq(int value) {
        addLessThanOrEquals(LapColumns.MAX_HR, value);
        return this;
    }

    public LapSelection avgCadence(Integer... value) {
        addEquals(LapColumns.AVG_CADENCE, value);
        return this;
    }

    public LapSelection avgCadenceNot(Integer... value) {
        addNotEquals(LapColumns.AVG_CADENCE, value);
        return this;
    }

    public LapSelection avgCadenceGt(int value) {
        addGreaterThan(LapColumns.AVG_CADENCE, value);
        return this;
    }

    public LapSelection avgCadenceGtEq(int value) {
        addGreaterThanOrEquals(LapColumns.AVG_CADENCE, value);
        return this;
    }

    public LapSelection avgCadenceLt(int value) {
        addLessThan(LapColumns.AVG_CADENCE, value);
        return this;
    }

    public LapSelection avgCadenceLtEq(int value) {
        addLessThanOrEquals(LapColumns.AVG_CADENCE, value);
        return this;
    }
}
