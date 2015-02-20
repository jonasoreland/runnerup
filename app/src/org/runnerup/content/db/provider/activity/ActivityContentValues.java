package org.runnerup.content.db.provider.activity;

import java.util.Date;

import android.content.ContentResolver;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.runnerup.content.db.provider.base.AbstractContentValues;

/**
 * Content values wrapper for the {@code activity} table.
 */
public class ActivityContentValues extends AbstractContentValues {
    @Override
    public Uri uri() {
        return ActivityColumns.CONTENT_URI;
    }

    /**
     * Update row(s) using the values stored by this object and the given selection.
     *
     * @param contentResolver The content resolver to use.
     * @param where The selection to use (can be {@code null}).
     */
    public int update(ContentResolver contentResolver, @Nullable ActivitySelection where) {
        return contentResolver.update(uri(), values(), where == null ? null : where.sel(), where == null ? null : where.args());
    }

    /**
     * Start time of the activity
     */
    public ActivityContentValues putStartTime(long value) {
        mContentValues.put(ActivityColumns.START_TIME, value);
        return this;
    }


    /**
     * Distance of the activity
     */
    public ActivityContentValues putDistance(@Nullable Float value) {
        mContentValues.put(ActivityColumns.DISTANCE, value);
        return this;
    }

    public ActivityContentValues putDistanceNull() {
        mContentValues.putNull(ActivityColumns.DISTANCE);
        return this;
    }

    /**
     * Duration of the activity
     */
    public ActivityContentValues putTime(@Nullable Long value) {
        mContentValues.put(ActivityColumns.TIME, value);
        return this;
    }

    public ActivityContentValues putTimeNull() {
        mContentValues.putNull(ActivityColumns.TIME);
        return this;
    }

    /**
     * Name of the activity
     */
    public ActivityContentValues putName(@Nullable String value) {
        mContentValues.put(ActivityColumns.NAME, value);
        return this;
    }

    public ActivityContentValues putNameNull() {
        mContentValues.putNull(ActivityColumns.NAME);
        return this;
    }

    /**
     * Comment for the activity
     */
    public ActivityContentValues putComment(@Nullable String value) {
        mContentValues.put(ActivityColumns.COMMENT, value);
        return this;
    }

    public ActivityContentValues putCommentNull() {
        mContentValues.putNull(ActivityColumns.COMMENT);
        return this;
    }

    /**
     * Sport type of the activity
     */
    public ActivityContentValues putType(@Nullable Integer value) {
        mContentValues.put(ActivityColumns.TYPE, value);
        return this;
    }

    public ActivityContentValues putTypeNull() {
        mContentValues.putNull(ActivityColumns.TYPE);
        return this;
    }

    /**
     * Maximum HR of the activity
     */
    public ActivityContentValues putMaxHr(@Nullable Integer value) {
        mContentValues.put(ActivityColumns.MAX_HR, value);
        return this;
    }

    public ActivityContentValues putMaxHrNull() {
        mContentValues.putNull(ActivityColumns.MAX_HR);
        return this;
    }

    /**
     * Avarage HR of the activity
     */
    public ActivityContentValues putAvgHr(@Nullable Integer value) {
        mContentValues.put(ActivityColumns.AVG_HR, value);
        return this;
    }

    public ActivityContentValues putAvgHrNull() {
        mContentValues.putNull(ActivityColumns.AVG_HR);
        return this;
    }

    /**
     * Avarage cadence of the activity
     */
    public ActivityContentValues putAvgCadence(@Nullable Integer value) {
        mContentValues.put(ActivityColumns.AVG_CADENCE, value);
        return this;
    }

    public ActivityContentValues putAvgCadenceNull() {
        mContentValues.putNull(ActivityColumns.AVG_CADENCE);
        return this;
    }

    /**
     * Status of the activity
     */
    public ActivityContentValues putDeleted(boolean value) {
        mContentValues.put(ActivityColumns.DELETED, value);
        return this;
    }


    /**
     * Workaround column
     */
    public ActivityContentValues putNullcolumnhack(@Nullable String value) {
        mContentValues.put(ActivityColumns.NULLCOLUMNHACK, value);
        return this;
    }

    public ActivityContentValues putNullcolumnhackNull() {
        mContentValues.putNull(ActivityColumns.NULLCOLUMNHACK);
        return this;
    }
}
