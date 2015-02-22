package org.runnerup.content.db.provider.feed;

import java.util.Date;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import org.runnerup.content.db.provider.base.AbstractSelection;

/**
 * Selection for the {@code feed} table.
 */
public class FeedSelection extends AbstractSelection<FeedSelection> {
    @Override
    protected Uri baseUri() {
        return FeedColumns.CONTENT_URI;
    }

    /**
     * Query the given content resolver using this selection.
     *
     * @param contentResolver The content resolver to query.
     * @param projection A list of which columns to return. Passing null will return all columns, which is inefficient.
     * @param sortOrder How to order the rows, formatted as an SQL ORDER BY clause (excluding the ORDER BY itself). Passing null will use the default sort
     *            order, which may be unordered.
     * @return A {@code FeedCursor} object, which is positioned before the first entry, or null.
     */
    public FeedCursor query(ContentResolver contentResolver, String[] projection, String sortOrder) {
        Cursor cursor = contentResolver.query(uri(), projection, sel(), args(), sortOrder);
        if (cursor == null) return null;
        return new FeedCursor(cursor);
    }

    /**
     * Equivalent of calling {@code query(contentResolver, projection, null)}.
     */
    public FeedCursor query(ContentResolver contentResolver, String[] projection) {
        return query(contentResolver, projection, null);
    }

    /**
     * Equivalent of calling {@code query(contentResolver, projection, null, null)}.
     */
    public FeedCursor query(ContentResolver contentResolver) {
        return query(contentResolver, null, null);
    }


    public FeedSelection id(long... value) {
        addEquals("feed." + FeedColumns._ID, toObjectArray(value));
        return this;
    }

    public FeedSelection accountId(int... value) {
        addEquals(FeedColumns.ACCOUNT_ID, toObjectArray(value));
        return this;
    }

    public FeedSelection accountIdNot(int... value) {
        addNotEquals(FeedColumns.ACCOUNT_ID, toObjectArray(value));
        return this;
    }

    public FeedSelection accountIdGt(int value) {
        addGreaterThan(FeedColumns.ACCOUNT_ID, value);
        return this;
    }

    public FeedSelection accountIdGtEq(int value) {
        addGreaterThanOrEquals(FeedColumns.ACCOUNT_ID, value);
        return this;
    }

    public FeedSelection accountIdLt(int value) {
        addLessThan(FeedColumns.ACCOUNT_ID, value);
        return this;
    }

    public FeedSelection accountIdLtEq(int value) {
        addLessThanOrEquals(FeedColumns.ACCOUNT_ID, value);
        return this;
    }

    public FeedSelection extId(String... value) {
        addEquals(FeedColumns.EXT_ID, value);
        return this;
    }

    public FeedSelection extIdNot(String... value) {
        addNotEquals(FeedColumns.EXT_ID, value);
        return this;
    }

    public FeedSelection extIdLike(String... value) {
        addLike(FeedColumns.EXT_ID, value);
        return this;
    }

    public FeedSelection extIdContains(String... value) {
        addContains(FeedColumns.EXT_ID, value);
        return this;
    }

    public FeedSelection extIdStartsWith(String... value) {
        addStartsWith(FeedColumns.EXT_ID, value);
        return this;
    }

    public FeedSelection extIdEndsWith(String... value) {
        addEndsWith(FeedColumns.EXT_ID, value);
        return this;
    }

    public FeedSelection entryType(int... value) {
        addEquals(FeedColumns.ENTRY_TYPE, toObjectArray(value));
        return this;
    }

    public FeedSelection entryTypeNot(int... value) {
        addNotEquals(FeedColumns.ENTRY_TYPE, toObjectArray(value));
        return this;
    }

    public FeedSelection entryTypeGt(int value) {
        addGreaterThan(FeedColumns.ENTRY_TYPE, value);
        return this;
    }

    public FeedSelection entryTypeGtEq(int value) {
        addGreaterThanOrEquals(FeedColumns.ENTRY_TYPE, value);
        return this;
    }

    public FeedSelection entryTypeLt(int value) {
        addLessThan(FeedColumns.ENTRY_TYPE, value);
        return this;
    }

    public FeedSelection entryTypeLtEq(int value) {
        addLessThanOrEquals(FeedColumns.ENTRY_TYPE, value);
        return this;
    }

    public FeedSelection type(Integer... value) {
        addEquals(FeedColumns.TYPE, value);
        return this;
    }

    public FeedSelection typeNot(Integer... value) {
        addNotEquals(FeedColumns.TYPE, value);
        return this;
    }

    public FeedSelection typeGt(int value) {
        addGreaterThan(FeedColumns.TYPE, value);
        return this;
    }

    public FeedSelection typeGtEq(int value) {
        addGreaterThanOrEquals(FeedColumns.TYPE, value);
        return this;
    }

    public FeedSelection typeLt(int value) {
        addLessThan(FeedColumns.TYPE, value);
        return this;
    }

    public FeedSelection typeLtEq(int value) {
        addLessThanOrEquals(FeedColumns.TYPE, value);
        return this;
    }

    public FeedSelection startTime(long... value) {
        addEquals(FeedColumns.START_TIME, toObjectArray(value));
        return this;
    }

    public FeedSelection startTimeNot(long... value) {
        addNotEquals(FeedColumns.START_TIME, toObjectArray(value));
        return this;
    }

    public FeedSelection startTimeGt(long value) {
        addGreaterThan(FeedColumns.START_TIME, value);
        return this;
    }

    public FeedSelection startTimeGtEq(long value) {
        addGreaterThanOrEquals(FeedColumns.START_TIME, value);
        return this;
    }

    public FeedSelection startTimeLt(long value) {
        addLessThan(FeedColumns.START_TIME, value);
        return this;
    }

    public FeedSelection startTimeLtEq(long value) {
        addLessThanOrEquals(FeedColumns.START_TIME, value);
        return this;
    }

    public FeedSelection duration(Integer... value) {
        addEquals(FeedColumns.DURATION, value);
        return this;
    }

    public FeedSelection durationNot(Integer... value) {
        addNotEquals(FeedColumns.DURATION, value);
        return this;
    }

    public FeedSelection durationGt(int value) {
        addGreaterThan(FeedColumns.DURATION, value);
        return this;
    }

    public FeedSelection durationGtEq(int value) {
        addGreaterThanOrEquals(FeedColumns.DURATION, value);
        return this;
    }

    public FeedSelection durationLt(int value) {
        addLessThan(FeedColumns.DURATION, value);
        return this;
    }

    public FeedSelection durationLtEq(int value) {
        addLessThanOrEquals(FeedColumns.DURATION, value);
        return this;
    }

    public FeedSelection distance(Float... value) {
        addEquals(FeedColumns.DISTANCE, value);
        return this;
    }

    public FeedSelection distanceNot(Float... value) {
        addNotEquals(FeedColumns.DISTANCE, value);
        return this;
    }

    public FeedSelection distanceGt(float value) {
        addGreaterThan(FeedColumns.DISTANCE, value);
        return this;
    }

    public FeedSelection distanceGtEq(float value) {
        addGreaterThanOrEquals(FeedColumns.DISTANCE, value);
        return this;
    }

    public FeedSelection distanceLt(float value) {
        addLessThan(FeedColumns.DISTANCE, value);
        return this;
    }

    public FeedSelection distanceLtEq(float value) {
        addLessThanOrEquals(FeedColumns.DISTANCE, value);
        return this;
    }

    public FeedSelection userId(String... value) {
        addEquals(FeedColumns.USER_ID, value);
        return this;
    }

    public FeedSelection userIdNot(String... value) {
        addNotEquals(FeedColumns.USER_ID, value);
        return this;
    }

    public FeedSelection userIdLike(String... value) {
        addLike(FeedColumns.USER_ID, value);
        return this;
    }

    public FeedSelection userIdContains(String... value) {
        addContains(FeedColumns.USER_ID, value);
        return this;
    }

    public FeedSelection userIdStartsWith(String... value) {
        addStartsWith(FeedColumns.USER_ID, value);
        return this;
    }

    public FeedSelection userIdEndsWith(String... value) {
        addEndsWith(FeedColumns.USER_ID, value);
        return this;
    }

    public FeedSelection userFirstName(String... value) {
        addEquals(FeedColumns.USER_FIRST_NAME, value);
        return this;
    }

    public FeedSelection userFirstNameNot(String... value) {
        addNotEquals(FeedColumns.USER_FIRST_NAME, value);
        return this;
    }

    public FeedSelection userFirstNameLike(String... value) {
        addLike(FeedColumns.USER_FIRST_NAME, value);
        return this;
    }

    public FeedSelection userFirstNameContains(String... value) {
        addContains(FeedColumns.USER_FIRST_NAME, value);
        return this;
    }

    public FeedSelection userFirstNameStartsWith(String... value) {
        addStartsWith(FeedColumns.USER_FIRST_NAME, value);
        return this;
    }

    public FeedSelection userFirstNameEndsWith(String... value) {
        addEndsWith(FeedColumns.USER_FIRST_NAME, value);
        return this;
    }

    public FeedSelection userLastName(String... value) {
        addEquals(FeedColumns.USER_LAST_NAME, value);
        return this;
    }

    public FeedSelection userLastNameNot(String... value) {
        addNotEquals(FeedColumns.USER_LAST_NAME, value);
        return this;
    }

    public FeedSelection userLastNameLike(String... value) {
        addLike(FeedColumns.USER_LAST_NAME, value);
        return this;
    }

    public FeedSelection userLastNameContains(String... value) {
        addContains(FeedColumns.USER_LAST_NAME, value);
        return this;
    }

    public FeedSelection userLastNameStartsWith(String... value) {
        addStartsWith(FeedColumns.USER_LAST_NAME, value);
        return this;
    }

    public FeedSelection userLastNameEndsWith(String... value) {
        addEndsWith(FeedColumns.USER_LAST_NAME, value);
        return this;
    }

    public FeedSelection userImageUrl(String... value) {
        addEquals(FeedColumns.USER_IMAGE_URL, value);
        return this;
    }

    public FeedSelection userImageUrlNot(String... value) {
        addNotEquals(FeedColumns.USER_IMAGE_URL, value);
        return this;
    }

    public FeedSelection userImageUrlLike(String... value) {
        addLike(FeedColumns.USER_IMAGE_URL, value);
        return this;
    }

    public FeedSelection userImageUrlContains(String... value) {
        addContains(FeedColumns.USER_IMAGE_URL, value);
        return this;
    }

    public FeedSelection userImageUrlStartsWith(String... value) {
        addStartsWith(FeedColumns.USER_IMAGE_URL, value);
        return this;
    }

    public FeedSelection userImageUrlEndsWith(String... value) {
        addEndsWith(FeedColumns.USER_IMAGE_URL, value);
        return this;
    }

    public FeedSelection notes(String... value) {
        addEquals(FeedColumns.NOTES, value);
        return this;
    }

    public FeedSelection notesNot(String... value) {
        addNotEquals(FeedColumns.NOTES, value);
        return this;
    }

    public FeedSelection notesLike(String... value) {
        addLike(FeedColumns.NOTES, value);
        return this;
    }

    public FeedSelection notesContains(String... value) {
        addContains(FeedColumns.NOTES, value);
        return this;
    }

    public FeedSelection notesStartsWith(String... value) {
        addStartsWith(FeedColumns.NOTES, value);
        return this;
    }

    public FeedSelection notesEndsWith(String... value) {
        addEndsWith(FeedColumns.NOTES, value);
        return this;
    }

    public FeedSelection comments(String... value) {
        addEquals(FeedColumns.COMMENTS, value);
        return this;
    }

    public FeedSelection commentsNot(String... value) {
        addNotEquals(FeedColumns.COMMENTS, value);
        return this;
    }

    public FeedSelection commentsLike(String... value) {
        addLike(FeedColumns.COMMENTS, value);
        return this;
    }

    public FeedSelection commentsContains(String... value) {
        addContains(FeedColumns.COMMENTS, value);
        return this;
    }

    public FeedSelection commentsStartsWith(String... value) {
        addStartsWith(FeedColumns.COMMENTS, value);
        return this;
    }

    public FeedSelection commentsEndsWith(String... value) {
        addEndsWith(FeedColumns.COMMENTS, value);
        return this;
    }

    public FeedSelection url(String... value) {
        addEquals(FeedColumns.URL, value);
        return this;
    }

    public FeedSelection urlNot(String... value) {
        addNotEquals(FeedColumns.URL, value);
        return this;
    }

    public FeedSelection urlLike(String... value) {
        addLike(FeedColumns.URL, value);
        return this;
    }

    public FeedSelection urlContains(String... value) {
        addContains(FeedColumns.URL, value);
        return this;
    }

    public FeedSelection urlStartsWith(String... value) {
        addStartsWith(FeedColumns.URL, value);
        return this;
    }

    public FeedSelection urlEndsWith(String... value) {
        addEndsWith(FeedColumns.URL, value);
        return this;
    }

    public FeedSelection flags(String... value) {
        addEquals(FeedColumns.FLAGS, value);
        return this;
    }

    public FeedSelection flagsNot(String... value) {
        addNotEquals(FeedColumns.FLAGS, value);
        return this;
    }

    public FeedSelection flagsLike(String... value) {
        addLike(FeedColumns.FLAGS, value);
        return this;
    }

    public FeedSelection flagsContains(String... value) {
        addContains(FeedColumns.FLAGS, value);
        return this;
    }

    public FeedSelection flagsStartsWith(String... value) {
        addStartsWith(FeedColumns.FLAGS, value);
        return this;
    }

    public FeedSelection flagsEndsWith(String... value) {
        addEndsWith(FeedColumns.FLAGS, value);
        return this;
    }
}
