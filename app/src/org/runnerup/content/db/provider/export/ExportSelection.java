package org.runnerup.content.db.provider.export;

import java.util.Date;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import org.runnerup.content.db.provider.base.AbstractSelection;

/**
 * Selection for the {@code export} table.
 */
public class ExportSelection extends AbstractSelection<ExportSelection> {
    @Override
    protected Uri baseUri() {
        return ExportColumns.CONTENT_URI;
    }

    /**
     * Query the given content resolver using this selection.
     *
     * @param contentResolver The content resolver to query.
     * @param projection A list of which columns to return. Passing null will return all columns, which is inefficient.
     * @param sortOrder How to order the rows, formatted as an SQL ORDER BY clause (excluding the ORDER BY itself). Passing null will use the default sort
     *            order, which may be unordered.
     * @return A {@code ExportCursor} object, which is positioned before the first entry, or null.
     */
    public ExportCursor query(ContentResolver contentResolver, String[] projection, String sortOrder) {
        Cursor cursor = contentResolver.query(uri(), projection, sel(), args(), sortOrder);
        if (cursor == null) return null;
        return new ExportCursor(cursor);
    }

    /**
     * Equivalent of calling {@code query(contentResolver, projection, null)}.
     */
    public ExportCursor query(ContentResolver contentResolver, String[] projection) {
        return query(contentResolver, projection, null);
    }

    /**
     * Equivalent of calling {@code query(contentResolver, projection, null, null)}.
     */
    public ExportCursor query(ContentResolver contentResolver) {
        return query(contentResolver, null, null);
    }


    public ExportSelection id(long... value) {
        addEquals("export." + ExportColumns._ID, toObjectArray(value));
        return this;
    }

    public ExportSelection activityId(int... value) {
        addEquals(ExportColumns.ACTIVITY_ID, toObjectArray(value));
        return this;
    }

    public ExportSelection activityIdNot(int... value) {
        addNotEquals(ExportColumns.ACTIVITY_ID, toObjectArray(value));
        return this;
    }

    public ExportSelection activityIdGt(int value) {
        addGreaterThan(ExportColumns.ACTIVITY_ID, value);
        return this;
    }

    public ExportSelection activityIdGtEq(int value) {
        addGreaterThanOrEquals(ExportColumns.ACTIVITY_ID, value);
        return this;
    }

    public ExportSelection activityIdLt(int value) {
        addLessThan(ExportColumns.ACTIVITY_ID, value);
        return this;
    }

    public ExportSelection activityIdLtEq(int value) {
        addLessThanOrEquals(ExportColumns.ACTIVITY_ID, value);
        return this;
    }

    public ExportSelection accountId(String... value) {
        addEquals(ExportColumns.ACCOUNT_ID, value);
        return this;
    }

    public ExportSelection accountIdNot(String... value) {
        addNotEquals(ExportColumns.ACCOUNT_ID, value);
        return this;
    }

    public ExportSelection accountIdLike(String... value) {
        addLike(ExportColumns.ACCOUNT_ID, value);
        return this;
    }

    public ExportSelection accountIdContains(String... value) {
        addContains(ExportColumns.ACCOUNT_ID, value);
        return this;
    }

    public ExportSelection accountIdStartsWith(String... value) {
        addStartsWith(ExportColumns.ACCOUNT_ID, value);
        return this;
    }

    public ExportSelection accountIdEndsWith(String... value) {
        addEndsWith(ExportColumns.ACCOUNT_ID, value);
        return this;
    }

    public ExportSelection status(String... value) {
        addEquals(ExportColumns.STATUS, value);
        return this;
    }

    public ExportSelection statusNot(String... value) {
        addNotEquals(ExportColumns.STATUS, value);
        return this;
    }

    public ExportSelection statusLike(String... value) {
        addLike(ExportColumns.STATUS, value);
        return this;
    }

    public ExportSelection statusContains(String... value) {
        addContains(ExportColumns.STATUS, value);
        return this;
    }

    public ExportSelection statusStartsWith(String... value) {
        addStartsWith(ExportColumns.STATUS, value);
        return this;
    }

    public ExportSelection statusEndsWith(String... value) {
        addEndsWith(ExportColumns.STATUS, value);
        return this;
    }

    public ExportSelection extId(String... value) {
        addEquals(ExportColumns.EXT_ID, value);
        return this;
    }

    public ExportSelection extIdNot(String... value) {
        addNotEquals(ExportColumns.EXT_ID, value);
        return this;
    }

    public ExportSelection extIdLike(String... value) {
        addLike(ExportColumns.EXT_ID, value);
        return this;
    }

    public ExportSelection extIdContains(String... value) {
        addContains(ExportColumns.EXT_ID, value);
        return this;
    }

    public ExportSelection extIdStartsWith(String... value) {
        addStartsWith(ExportColumns.EXT_ID, value);
        return this;
    }

    public ExportSelection extIdEndsWith(String... value) {
        addEndsWith(ExportColumns.EXT_ID, value);
        return this;
    }

    public ExportSelection extra(int... value) {
        addEquals(ExportColumns.EXTRA, toObjectArray(value));
        return this;
    }

    public ExportSelection extraNot(int... value) {
        addNotEquals(ExportColumns.EXTRA, toObjectArray(value));
        return this;
    }

    public ExportSelection extraGt(int value) {
        addGreaterThan(ExportColumns.EXTRA, value);
        return this;
    }

    public ExportSelection extraGtEq(int value) {
        addGreaterThanOrEquals(ExportColumns.EXTRA, value);
        return this;
    }

    public ExportSelection extraLt(int value) {
        addLessThan(ExportColumns.EXTRA, value);
        return this;
    }

    public ExportSelection extraLtEq(int value) {
        addLessThanOrEquals(ExportColumns.EXTRA, value);
        return this;
    }
}
