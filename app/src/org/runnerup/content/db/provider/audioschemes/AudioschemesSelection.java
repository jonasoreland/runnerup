package org.runnerup.content.db.provider.audioschemes;

import java.util.Date;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import org.runnerup.content.db.provider.base.AbstractSelection;

/**
 * Selection for the {@code audioschemes} table.
 */
public class AudioschemesSelection extends AbstractSelection<AudioschemesSelection> {
    @Override
    protected Uri baseUri() {
        return AudioschemesColumns.CONTENT_URI;
    }

    /**
     * Query the given content resolver using this selection.
     *
     * @param contentResolver The content resolver to query.
     * @param projection A list of which columns to return. Passing null will return all columns, which is inefficient.
     * @param sortOrder How to order the rows, formatted as an SQL ORDER BY clause (excluding the ORDER BY itself). Passing null will use the default sort
     *            order, which may be unordered.
     * @return A {@code AudioschemesCursor} object, which is positioned before the first entry, or null.
     */
    public AudioschemesCursor query(ContentResolver contentResolver, String[] projection, String sortOrder) {
        Cursor cursor = contentResolver.query(uri(), projection, sel(), args(), sortOrder);
        if (cursor == null) return null;
        return new AudioschemesCursor(cursor);
    }

    /**
     * Equivalent of calling {@code query(contentResolver, projection, null)}.
     */
    public AudioschemesCursor query(ContentResolver contentResolver, String[] projection) {
        return query(contentResolver, projection, null);
    }

    /**
     * Equivalent of calling {@code query(contentResolver, projection, null, null)}.
     */
    public AudioschemesCursor query(ContentResolver contentResolver) {
        return query(contentResolver, null, null);
    }


    public AudioschemesSelection id(long... value) {
        addEquals("audioschemes." + AudioschemesColumns._ID, toObjectArray(value));
        return this;
    }

    public AudioschemesSelection name(String... value) {
        addEquals(AudioschemesColumns.NAME, value);
        return this;
    }

    public AudioschemesSelection nameNot(String... value) {
        addNotEquals(AudioschemesColumns.NAME, value);
        return this;
    }

    public AudioschemesSelection nameLike(String... value) {
        addLike(AudioschemesColumns.NAME, value);
        return this;
    }

    public AudioschemesSelection nameContains(String... value) {
        addContains(AudioschemesColumns.NAME, value);
        return this;
    }

    public AudioschemesSelection nameStartsWith(String... value) {
        addStartsWith(AudioschemesColumns.NAME, value);
        return this;
    }

    public AudioschemesSelection nameEndsWith(String... value) {
        addEndsWith(AudioschemesColumns.NAME, value);
        return this;
    }

    public AudioschemesSelection sortOrder(int... value) {
        addEquals(AudioschemesColumns.SORT_ORDER, toObjectArray(value));
        return this;
    }

    public AudioschemesSelection sortOrderNot(int... value) {
        addNotEquals(AudioschemesColumns.SORT_ORDER, toObjectArray(value));
        return this;
    }

    public AudioschemesSelection sortOrderGt(int value) {
        addGreaterThan(AudioschemesColumns.SORT_ORDER, value);
        return this;
    }

    public AudioschemesSelection sortOrderGtEq(int value) {
        addGreaterThanOrEquals(AudioschemesColumns.SORT_ORDER, value);
        return this;
    }

    public AudioschemesSelection sortOrderLt(int value) {
        addLessThan(AudioschemesColumns.SORT_ORDER, value);
        return this;
    }

    public AudioschemesSelection sortOrderLtEq(int value) {
        addLessThanOrEquals(AudioschemesColumns.SORT_ORDER, value);
        return this;
    }
}
