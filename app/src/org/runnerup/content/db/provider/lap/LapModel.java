package org.runnerup.content.db.provider.lap;

import org.runnerup.content.db.provider.base.BaseModel;

import java.util.Date;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * A lap summary
 */
public interface LapModel extends BaseModel {

    /**
     * Id of the activity the lap belongs to
     */
    int getActivityId();

    /**
     * Number of the lap
     */
    int getLap();

    /**
     * Type (intensity) of the lap
     */
    int getType();

    /**
     * Duration of the lap
     * Can be {@code null}.
     */
    @Nullable
    Integer getTime();

    /**
     * Distance of the lap
     * Can be {@code null}.
     */
    @Nullable
    Float getDistance();

    /**
     * Planned duration of the lap
     * Can be {@code null}.
     */
    @Nullable
    Integer getPlannedTime();

    /**
     * Planned distance of the lap
     * Can be {@code null}.
     */
    @Nullable
    Float getPlannedDistance();

    /**
     * Planned pace of the lap
     * Can be {@code null}.
     */
    @Nullable
    Float getPlannedPace();

    /**
     * Average HR of the lap
     * Can be {@code null}.
     */
    @Nullable
    Integer getAvgHr();

    /**
     * Maximum HR of the lap
     * Can be {@code null}.
     */
    @Nullable
    Integer getMaxHr();

    /**
     * Avarage cadence of the lap
     * Can be {@code null}.
     */
    @Nullable
    Integer getAvgCadence();
}
