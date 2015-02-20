package org.runnerup.content.db.provider.activity;

import java.util.Date;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.runnerup.content.db.provider.base.AbstractCursor;

/**
 * Cursor wrapper for the {@code activity} table.
 */
public class ActivityCursor extends AbstractCursor implements ActivityModel {
    public ActivityCursor(Cursor cursor) {
        super(cursor);
    }

    /**
     * Primary key.
     */
    public long getId() {
        return getLongOrNull(ActivityColumns._ID);
    }

    /**
     * Start time of the activity
     */
    public long getStartTime() {
        return getLongOrNull(ActivityColumns.START_TIME);
    }

    /**
     * Distance of the activity
     * Can be {@code null}.
     */
    @Nullable
    public Float getDistance() {
        return getFloatOrNull(ActivityColumns.DISTANCE);
    }

    /**
     * Duration of the activity
     * Can be {@code null}.
     */
    @Nullable
    public Long getTime() {
        return getLongOrNull(ActivityColumns.TIME);
    }

    /**
     * Name of the activity
     * Can be {@code null}.
     */
    @Nullable
    public String getName() {
        return getStringOrNull(ActivityColumns.NAME);
    }

    /**
     * Comment for the activity
     * Can be {@code null}.
     */
    @Nullable
    public String getComment() {
        return getStringOrNull(ActivityColumns.COMMENT);
    }

    /**
     * Sport type of the activity
     * Can be {@code null}.
     */
    @Nullable
    public Integer getType() {
        return getIntegerOrNull(ActivityColumns.TYPE);
    }

    /**
     * Maximum HR of the activity
     * Can be {@code null}.
     */
    @Nullable
    public Integer getMaxHr() {
        return getIntegerOrNull(ActivityColumns.MAX_HR);
    }

    /**
     * Avarage HR of the activity
     * Can be {@code null}.
     */
    @Nullable
    public Integer getAvgHr() {
        return getIntegerOrNull(ActivityColumns.AVG_HR);
    }

    /**
     * Avarage cadence of the activity
     * Can be {@code null}.
     */
    @Nullable
    public Integer getAvgCadence() {
        return getIntegerOrNull(ActivityColumns.AVG_CADENCE);
    }

    /**
     * Status of the activity
     */
    public boolean getDeleted() {
        return getBooleanOrNull(ActivityColumns.DELETED);
    }

    /**
     * Workaround column
     * Can be {@code null}.
     */
    @Nullable
    public String getNullcolumnhack() {
        return getStringOrNull(ActivityColumns.NULLCOLUMNHACK);
    }
}
