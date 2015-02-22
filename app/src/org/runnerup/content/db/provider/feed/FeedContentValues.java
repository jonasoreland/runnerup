package org.runnerup.content.db.provider.feed;

import java.util.Date;

import android.content.ContentResolver;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.runnerup.content.db.provider.base.AbstractContentValues;

/**
 * Content values wrapper for the {@code feed} table.
 */
public class FeedContentValues extends AbstractContentValues {
    @Override
    public Uri uri() {
        return FeedColumns.CONTENT_URI;
    }

    /**
     * Update row(s) using the values stored by this object and the given selection.
     *
     * @param contentResolver The content resolver to use.
     * @param where The selection to use (can be {@code null}).
     */
    public int update(ContentResolver contentResolver, @Nullable FeedSelection where) {
        return contentResolver.update(uri(), values(), where == null ? null : where.sel(), where == null ? null : where.args());
    }

    /**
     * Id of the account that the feed entry belongs to
     */
    public FeedContentValues putAccountId(int value) {
        mContentValues.put(FeedColumns.ACCOUNT_ID, value);
        return this;
    }


    /**
     * External Id of the feed entry
     */
    public FeedContentValues putExtId(@Nullable String value) {
        mContentValues.put(FeedColumns.EXT_ID, value);
        return this;
    }

    public FeedContentValues putExtIdNull() {
        mContentValues.putNull(FeedColumns.EXT_ID);
        return this;
    }

    /**
     * Type of the feed entry
     */
    public FeedContentValues putEntryType(int value) {
        mContentValues.put(FeedColumns.ENTRY_TYPE, value);
        return this;
    }


    /**
     * Sub-Type of the feed entry
     */
    public FeedContentValues putType(@Nullable Integer value) {
        mContentValues.put(FeedColumns.TYPE, value);
        return this;
    }

    public FeedContentValues putTypeNull() {
        mContentValues.putNull(FeedColumns.TYPE);
        return this;
    }

    /**
     * Time of the feed entry
     */
    public FeedContentValues putStartTime(long value) {
        mContentValues.put(FeedColumns.START_TIME, value);
        return this;
    }


    /**
     * Duration of the feed entry activity
     */
    public FeedContentValues putDuration(@Nullable Integer value) {
        mContentValues.put(FeedColumns.DURATION, value);
        return this;
    }

    public FeedContentValues putDurationNull() {
        mContentValues.putNull(FeedColumns.DURATION);
        return this;
    }

    /**
     * Distance of the feed entry activity
     */
    public FeedContentValues putDistance(@Nullable Float value) {
        mContentValues.put(FeedColumns.DISTANCE, value);
        return this;
    }

    public FeedContentValues putDistanceNull() {
        mContentValues.putNull(FeedColumns.DISTANCE);
        return this;
    }

    /**
     * User Id of the feed entry activity
     */
    public FeedContentValues putUserId(@Nullable String value) {
        mContentValues.put(FeedColumns.USER_ID, value);
        return this;
    }

    public FeedContentValues putUserIdNull() {
        mContentValues.putNull(FeedColumns.USER_ID);
        return this;
    }

    /**
     * First name of the user of the feed entry activity
     */
    public FeedContentValues putUserFirstName(@Nullable String value) {
        mContentValues.put(FeedColumns.USER_FIRST_NAME, value);
        return this;
    }

    public FeedContentValues putUserFirstNameNull() {
        mContentValues.putNull(FeedColumns.USER_FIRST_NAME);
        return this;
    }

    /**
     * Last name of the user of the feed entry activity
     */
    public FeedContentValues putUserLastName(@Nullable String value) {
        mContentValues.put(FeedColumns.USER_LAST_NAME, value);
        return this;
    }

    public FeedContentValues putUserLastNameNull() {
        mContentValues.putNull(FeedColumns.USER_LAST_NAME);
        return this;
    }

    /**
     * Image URL of the user of the feed entry activity
     */
    public FeedContentValues putUserImageUrl(@Nullable String value) {
        mContentValues.put(FeedColumns.USER_IMAGE_URL, value);
        return this;
    }

    public FeedContentValues putUserImageUrlNull() {
        mContentValues.putNull(FeedColumns.USER_IMAGE_URL);
        return this;
    }

    /**
     * Notes of the feed entry activity
     */
    public FeedContentValues putNotes(@Nullable String value) {
        mContentValues.put(FeedColumns.NOTES, value);
        return this;
    }

    public FeedContentValues putNotesNull() {
        mContentValues.putNull(FeedColumns.NOTES);
        return this;
    }

    /**
     * Comments of the feed entry activity
     */
    public FeedContentValues putComments(@Nullable String value) {
        mContentValues.put(FeedColumns.COMMENTS, value);
        return this;
    }

    public FeedContentValues putCommentsNull() {
        mContentValues.putNull(FeedColumns.COMMENTS);
        return this;
    }

    /**
     * URL the feed entry activity
     */
    public FeedContentValues putUrl(@Nullable String value) {
        mContentValues.put(FeedColumns.URL, value);
        return this;
    }

    public FeedContentValues putUrlNull() {
        mContentValues.putNull(FeedColumns.URL);
        return this;
    }

    /**
     * Flags of the feed entry activity
     */
    public FeedContentValues putFlags(@Nullable String value) {
        mContentValues.put(FeedColumns.FLAGS, value);
        return this;
    }

    public FeedContentValues putFlagsNull() {
        mContentValues.putNull(FeedColumns.FLAGS);
        return this;
    }
}
