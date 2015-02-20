package org.runnerup.content.db.provider.feed;

import java.util.Date;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.runnerup.content.db.provider.base.AbstractCursor;

/**
 * Cursor wrapper for the {@code feed} table.
 */
public class FeedCursor extends AbstractCursor implements FeedModel {
    public FeedCursor(Cursor cursor) {
        super(cursor);
    }

    /**
     * Primary key.
     */
    public long getId() {
        return getLongOrNull(FeedColumns._ID);
    }

    /**
     * Id of the account that the feed entry belongs to
     */
    public int getAccountId() {
        return getIntegerOrNull(FeedColumns.ACCOUNT_ID);
    }

    /**
     * External Id of the feed entry
     * Can be {@code null}.
     */
    @Nullable
    public String getExtId() {
        return getStringOrNull(FeedColumns.EXT_ID);
    }

    /**
     * Type of the feed entry
     */
    public int getEntryType() {
        return getIntegerOrNull(FeedColumns.ENTRY_TYPE);
    }

    /**
     * Sub-Type of the feed entry
     * Can be {@code null}.
     */
    @Nullable
    public Integer getType() {
        return getIntegerOrNull(FeedColumns.TYPE);
    }

    /**
     * Time of the feed entry
     */
    public long getStartTime() {
        return getLongOrNull(FeedColumns.START_TIME);
    }

    /**
     * Duration of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    public Integer getDuration() {
        return getIntegerOrNull(FeedColumns.DURATION);
    }

    /**
     * Distance of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    public Float getDistance() {
        return getFloatOrNull(FeedColumns.DISTANCE);
    }

    /**
     * User Id of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    public String getUserId() {
        return getStringOrNull(FeedColumns.USER_ID);
    }

    /**
     * First name of the user of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    public String getUserFirstName() {
        return getStringOrNull(FeedColumns.USER_FIRST_NAME);
    }

    /**
     * Last name of the user of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    public String getUserLastName() {
        return getStringOrNull(FeedColumns.USER_LAST_NAME);
    }

    /**
     * Image URL of the user of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    public String getUserImageUrl() {
        return getStringOrNull(FeedColumns.USER_IMAGE_URL);
    }

    /**
     * Notes of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    public String getNotes() {
        return getStringOrNull(FeedColumns.NOTES);
    }

    /**
     * Comments of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    public String getComments() {
        return getStringOrNull(FeedColumns.COMMENTS);
    }

    /**
     * URL the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    public String getUrl() {
        return getStringOrNull(FeedColumns.URL);
    }

    /**
     * Flags of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    public String getFlags() {
        return getStringOrNull(FeedColumns.FLAGS);
    }
}
