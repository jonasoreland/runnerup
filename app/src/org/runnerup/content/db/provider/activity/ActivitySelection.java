package org.runnerup.content.db.provider.activity;

import java.util.Date;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import org.runnerup.content.db.provider.base.AbstractSelection;

/**
 * Selection for the {@code activity} table.
 */
public class ActivitySelection extends AbstractSelection<ActivitySelection> {
    @Override
    protected Uri baseUri() {
        return ActivityColumns.CONTENT_URI;
    }

    /**
     * Query the given content resolver using this selection.
     *
     * @param contentResolver The content resolver to query.
     * @param projection A list of which columns to return. Passing null will return all columns, which is inefficient.
     * @param sortOrder How to order the rows, formatted as an SQL ORDER BY clause (excluding the ORDER BY itself). Passing null will use the default sort
     *            order, which may be unordered.
     * @return A {@code ActivityCursor} object, which is positioned before the first entry, or null.
     */
    public ActivityCursor query(ContentResolver contentResolver, String[] projection, String sortOrder) {
        Cursor cursor = contentResolver.query(uri(), projection, sel(), args(), sortOrder);
        if (cursor == null) return null;
        return new ActivityCursor(cursor);
    }

    /**
     * Equivalent of calling {@code query(contentResolver, projection, null)}.
     */
    public ActivityCursor query(ContentResolver contentResolver, String[] projection) {
        return query(contentResolver, projection, null);
    }

    /**
     * Equivalent of calling {@code query(contentResolver, projection, null, null)}.
     */
    public ActivityCursor query(ContentResolver contentResolver) {
        return query(contentResolver, null, null);
    }


    public ActivitySelection id(long... value) {
        addEquals("activity." + ActivityColumns._ID, toObjectArray(value));
        return this;
    }

    public ActivitySelection startTime(long... value) {
        addEquals(ActivityColumns.START_TIME, toObjectArray(value));
        return this;
    }

    public ActivitySelection startTimeNot(long... value) {
        addNotEquals(ActivityColumns.START_TIME, toObjectArray(value));
        return this;
    }

    public ActivitySelection startTimeGt(long value) {
        addGreaterThan(ActivityColumns.START_TIME, value);
        return this;
    }

    public ActivitySelection startTimeGtEq(long value) {
        addGreaterThanOrEquals(ActivityColumns.START_TIME, value);
        return this;
    }

    public ActivitySelection startTimeLt(long value) {
        addLessThan(ActivityColumns.START_TIME, value);
        return this;
    }

    public ActivitySelection startTimeLtEq(long value) {
        addLessThanOrEquals(ActivityColumns.START_TIME, value);
        return this;
    }

    public ActivitySelection distance(Float... value) {
        addEquals(ActivityColumns.DISTANCE, value);
        return this;
    }

    public ActivitySelection distanceNot(Float... value) {
        addNotEquals(ActivityColumns.DISTANCE, value);
        return this;
    }

    public ActivitySelection distanceGt(float value) {
        addGreaterThan(ActivityColumns.DISTANCE, value);
        return this;
    }

    public ActivitySelection distanceGtEq(float value) {
        addGreaterThanOrEquals(ActivityColumns.DISTANCE, value);
        return this;
    }

    public ActivitySelection distanceLt(float value) {
        addLessThan(ActivityColumns.DISTANCE, value);
        return this;
    }

    public ActivitySelection distanceLtEq(float value) {
        addLessThanOrEquals(ActivityColumns.DISTANCE, value);
        return this;
    }

    public ActivitySelection time(Long... value) {
        addEquals(ActivityColumns.TIME, value);
        return this;
    }

    public ActivitySelection timeNot(Long... value) {
        addNotEquals(ActivityColumns.TIME, value);
        return this;
    }

    public ActivitySelection timeGt(long value) {
        addGreaterThan(ActivityColumns.TIME, value);
        return this;
    }

    public ActivitySelection timeGtEq(long value) {
        addGreaterThanOrEquals(ActivityColumns.TIME, value);
        return this;
    }

    public ActivitySelection timeLt(long value) {
        addLessThan(ActivityColumns.TIME, value);
        return this;
    }

    public ActivitySelection timeLtEq(long value) {
        addLessThanOrEquals(ActivityColumns.TIME, value);
        return this;
    }

    public ActivitySelection name(String... value) {
        addEquals(ActivityColumns.NAME, value);
        return this;
    }

    public ActivitySelection nameNot(String... value) {
        addNotEquals(ActivityColumns.NAME, value);
        return this;
    }

    public ActivitySelection nameLike(String... value) {
        addLike(ActivityColumns.NAME, value);
        return this;
    }

    public ActivitySelection nameContains(String... value) {
        addContains(ActivityColumns.NAME, value);
        return this;
    }

    public ActivitySelection nameStartsWith(String... value) {
        addStartsWith(ActivityColumns.NAME, value);
        return this;
    }

    public ActivitySelection nameEndsWith(String... value) {
        addEndsWith(ActivityColumns.NAME, value);
        return this;
    }

    public ActivitySelection comment(String... value) {
        addEquals(ActivityColumns.COMMENT, value);
        return this;
    }

    public ActivitySelection commentNot(String... value) {
        addNotEquals(ActivityColumns.COMMENT, value);
        return this;
    }

    public ActivitySelection commentLike(String... value) {
        addLike(ActivityColumns.COMMENT, value);
        return this;
    }

    public ActivitySelection commentContains(String... value) {
        addContains(ActivityColumns.COMMENT, value);
        return this;
    }

    public ActivitySelection commentStartsWith(String... value) {
        addStartsWith(ActivityColumns.COMMENT, value);
        return this;
    }

    public ActivitySelection commentEndsWith(String... value) {
        addEndsWith(ActivityColumns.COMMENT, value);
        return this;
    }

    public ActivitySelection type(Integer... value) {
        addEquals(ActivityColumns.TYPE, value);
        return this;
    }

    public ActivitySelection typeNot(Integer... value) {
        addNotEquals(ActivityColumns.TYPE, value);
        return this;
    }

    public ActivitySelection typeGt(int value) {
        addGreaterThan(ActivityColumns.TYPE, value);
        return this;
    }

    public ActivitySelection typeGtEq(int value) {
        addGreaterThanOrEquals(ActivityColumns.TYPE, value);
        return this;
    }

    public ActivitySelection typeLt(int value) {
        addLessThan(ActivityColumns.TYPE, value);
        return this;
    }

    public ActivitySelection typeLtEq(int value) {
        addLessThanOrEquals(ActivityColumns.TYPE, value);
        return this;
    }

    public ActivitySelection maxHr(Integer... value) {
        addEquals(ActivityColumns.MAX_HR, value);
        return this;
    }

    public ActivitySelection maxHrNot(Integer... value) {
        addNotEquals(ActivityColumns.MAX_HR, value);
        return this;
    }

    public ActivitySelection maxHrGt(int value) {
        addGreaterThan(ActivityColumns.MAX_HR, value);
        return this;
    }

    public ActivitySelection maxHrGtEq(int value) {
        addGreaterThanOrEquals(ActivityColumns.MAX_HR, value);
        return this;
    }

    public ActivitySelection maxHrLt(int value) {
        addLessThan(ActivityColumns.MAX_HR, value);
        return this;
    }

    public ActivitySelection maxHrLtEq(int value) {
        addLessThanOrEquals(ActivityColumns.MAX_HR, value);
        return this;
    }

    public ActivitySelection avgHr(Integer... value) {
        addEquals(ActivityColumns.AVG_HR, value);
        return this;
    }

    public ActivitySelection avgHrNot(Integer... value) {
        addNotEquals(ActivityColumns.AVG_HR, value);
        return this;
    }

    public ActivitySelection avgHrGt(int value) {
        addGreaterThan(ActivityColumns.AVG_HR, value);
        return this;
    }

    public ActivitySelection avgHrGtEq(int value) {
        addGreaterThanOrEquals(ActivityColumns.AVG_HR, value);
        return this;
    }

    public ActivitySelection avgHrLt(int value) {
        addLessThan(ActivityColumns.AVG_HR, value);
        return this;
    }

    public ActivitySelection avgHrLtEq(int value) {
        addLessThanOrEquals(ActivityColumns.AVG_HR, value);
        return this;
    }

    public ActivitySelection avgCadence(Integer... value) {
        addEquals(ActivityColumns.AVG_CADENCE, value);
        return this;
    }

    public ActivitySelection avgCadenceNot(Integer... value) {
        addNotEquals(ActivityColumns.AVG_CADENCE, value);
        return this;
    }

    public ActivitySelection avgCadenceGt(int value) {
        addGreaterThan(ActivityColumns.AVG_CADENCE, value);
        return this;
    }

    public ActivitySelection avgCadenceGtEq(int value) {
        addGreaterThanOrEquals(ActivityColumns.AVG_CADENCE, value);
        return this;
    }

    public ActivitySelection avgCadenceLt(int value) {
        addLessThan(ActivityColumns.AVG_CADENCE, value);
        return this;
    }

    public ActivitySelection avgCadenceLtEq(int value) {
        addLessThanOrEquals(ActivityColumns.AVG_CADENCE, value);
        return this;
    }

    public ActivitySelection deleted(boolean value) {
        addEquals(ActivityColumns.DELETED, toObjectArray(value));
        return this;
    }

    public ActivitySelection nullcolumnhack(String... value) {
        addEquals(ActivityColumns.NULLCOLUMNHACK, value);
        return this;
    }

    public ActivitySelection nullcolumnhackNot(String... value) {
        addNotEquals(ActivityColumns.NULLCOLUMNHACK, value);
        return this;
    }

    public ActivitySelection nullcolumnhackLike(String... value) {
        addLike(ActivityColumns.NULLCOLUMNHACK, value);
        return this;
    }

    public ActivitySelection nullcolumnhackContains(String... value) {
        addContains(ActivityColumns.NULLCOLUMNHACK, value);
        return this;
    }

    public ActivitySelection nullcolumnhackStartsWith(String... value) {
        addStartsWith(ActivityColumns.NULLCOLUMNHACK, value);
        return this;
    }

    public ActivitySelection nullcolumnhackEndsWith(String... value) {
        addEndsWith(ActivityColumns.NULLCOLUMNHACK, value);
        return this;
    }
}
