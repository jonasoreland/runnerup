package org.runnerup.content.db.provider.lap;

import java.util.Date;

import android.content.ContentResolver;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.runnerup.content.db.provider.base.AbstractContentValues;

/**
 * Content values wrapper for the {@code lap} table.
 */
public class LapContentValues extends AbstractContentValues {
    @Override
    public Uri uri() {
        return LapColumns.CONTENT_URI;
    }

    /**
     * Update row(s) using the values stored by this object and the given selection.
     *
     * @param contentResolver The content resolver to use.
     * @param where The selection to use (can be {@code null}).
     */
    public int update(ContentResolver contentResolver, @Nullable LapSelection where) {
        return contentResolver.update(uri(), values(), where == null ? null : where.sel(), where == null ? null : where.args());
    }

    /**
     * Id of the activity the lap belongs to
     */
    public LapContentValues putActivityId(int value) {
        mContentValues.put(LapColumns.ACTIVITY_ID, value);
        return this;
    }


    /**
     * Number of the lap
     */
    public LapContentValues putLap(int value) {
        mContentValues.put(LapColumns.LAP, value);
        return this;
    }


    /**
     * Type (intensity) of the lap
     */
    public LapContentValues putType(int value) {
        mContentValues.put(LapColumns.TYPE, value);
        return this;
    }


    /**
     * Duration of the lap
     */
    public LapContentValues putTime(@Nullable Integer value) {
        mContentValues.put(LapColumns.TIME, value);
        return this;
    }

    public LapContentValues putTimeNull() {
        mContentValues.putNull(LapColumns.TIME);
        return this;
    }

    /**
     * Distance of the lap
     */
    public LapContentValues putDistance(@Nullable Float value) {
        mContentValues.put(LapColumns.DISTANCE, value);
        return this;
    }

    public LapContentValues putDistanceNull() {
        mContentValues.putNull(LapColumns.DISTANCE);
        return this;
    }

    /**
     * Planned duration of the lap
     */
    public LapContentValues putPlannedTime(@Nullable Integer value) {
        mContentValues.put(LapColumns.PLANNED_TIME, value);
        return this;
    }

    public LapContentValues putPlannedTimeNull() {
        mContentValues.putNull(LapColumns.PLANNED_TIME);
        return this;
    }

    /**
     * Planned distance of the lap
     */
    public LapContentValues putPlannedDistance(@Nullable Float value) {
        mContentValues.put(LapColumns.PLANNED_DISTANCE, value);
        return this;
    }

    public LapContentValues putPlannedDistanceNull() {
        mContentValues.putNull(LapColumns.PLANNED_DISTANCE);
        return this;
    }

    /**
     * Planned pace of the lap
     */
    public LapContentValues putPlannedPace(@Nullable Float value) {
        mContentValues.put(LapColumns.PLANNED_PACE, value);
        return this;
    }

    public LapContentValues putPlannedPaceNull() {
        mContentValues.putNull(LapColumns.PLANNED_PACE);
        return this;
    }

    /**
     * Average HR of the lap
     */
    public LapContentValues putAvgHr(@Nullable Integer value) {
        mContentValues.put(LapColumns.AVG_HR, value);
        return this;
    }

    public LapContentValues putAvgHrNull() {
        mContentValues.putNull(LapColumns.AVG_HR);
        return this;
    }

    /**
     * Maximum HR of the lap
     */
    public LapContentValues putMaxHr(@Nullable Integer value) {
        mContentValues.put(LapColumns.MAX_HR, value);
        return this;
    }

    public LapContentValues putMaxHrNull() {
        mContentValues.putNull(LapColumns.MAX_HR);
        return this;
    }

    /**
     * Avarage cadence of the lap
     */
    public LapContentValues putAvgCadence(@Nullable Integer value) {
        mContentValues.put(LapColumns.AVG_CADENCE, value);
        return this;
    }

    public LapContentValues putAvgCadenceNull() {
        mContentValues.putNull(LapColumns.AVG_CADENCE);
        return this;
    }
}
