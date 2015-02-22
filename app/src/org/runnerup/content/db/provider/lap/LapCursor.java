package org.runnerup.content.db.provider.lap;

import java.util.Date;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.runnerup.content.db.provider.base.AbstractCursor;

/**
 * Cursor wrapper for the {@code lap} table.
 */
public class LapCursor extends AbstractCursor implements LapModel {
    public LapCursor(Cursor cursor) {
        super(cursor);
    }

    /**
     * Primary key.
     */
    public long getId() {
        return getLongOrNull(LapColumns._ID);
    }

    /**
     * Id of the activity the lap belongs to
     */
    public int getActivityId() {
        return getIntegerOrNull(LapColumns.ACTIVITY_ID);
    }

    /**
     * Number of the lap
     */
    public int getLap() {
        return getIntegerOrNull(LapColumns.LAP);
    }

    /**
     * Type (intensity) of the lap
     */
    public int getType() {
        return getIntegerOrNull(LapColumns.TYPE);
    }

    /**
     * Duration of the lap
     * Can be {@code null}.
     */
    @Nullable
    public Integer getTime() {
        return getIntegerOrNull(LapColumns.TIME);
    }

    /**
     * Distance of the lap
     * Can be {@code null}.
     */
    @Nullable
    public Float getDistance() {
        return getFloatOrNull(LapColumns.DISTANCE);
    }

    /**
     * Planned duration of the lap
     * Can be {@code null}.
     */
    @Nullable
    public Integer getPlannedTime() {
        return getIntegerOrNull(LapColumns.PLANNED_TIME);
    }

    /**
     * Planned distance of the lap
     * Can be {@code null}.
     */
    @Nullable
    public Float getPlannedDistance() {
        return getFloatOrNull(LapColumns.PLANNED_DISTANCE);
    }

    /**
     * Planned pace of the lap
     * Can be {@code null}.
     */
    @Nullable
    public Float getPlannedPace() {
        return getFloatOrNull(LapColumns.PLANNED_PACE);
    }

    /**
     * Average HR of the lap
     * Can be {@code null}.
     */
    @Nullable
    public Integer getAvgHr() {
        return getIntegerOrNull(LapColumns.AVG_HR);
    }

    /**
     * Maximum HR of the lap
     * Can be {@code null}.
     */
    @Nullable
    public Integer getMaxHr() {
        return getIntegerOrNull(LapColumns.MAX_HR);
    }

    /**
     * Avarage cadence of the lap
     * Can be {@code null}.
     */
    @Nullable
    public Integer getAvgCadence() {
        return getIntegerOrNull(LapColumns.AVG_CADENCE);
    }
}
