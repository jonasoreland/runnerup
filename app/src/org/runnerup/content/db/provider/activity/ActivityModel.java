package org.runnerup.content.db.provider.activity;

import org.runnerup.content.db.provider.base.BaseModel;

import java.util.Date;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * A activity summary
 */
public interface ActivityModel extends BaseModel {

    /**
     * Start time of the activity
     */
    long getStartTime();

    /**
     * Distance of the activity
     * Can be {@code null}.
     */
    @Nullable
    Float getDistance();

    /**
     * Duration of the activity
     * Can be {@code null}.
     */
    @Nullable
    Long getTime();

    /**
     * Name of the activity
     * Can be {@code null}.
     */
    @Nullable
    String getName();

    /**
     * Comment for the activity
     * Can be {@code null}.
     */
    @Nullable
    String getComment();

    /**
     * Sport type of the activity
     * Can be {@code null}.
     */
    @Nullable
    Integer getType();

    /**
     * Maximum HR of the activity
     * Can be {@code null}.
     */
    @Nullable
    Integer getMaxHr();

    /**
     * Avarage HR of the activity
     * Can be {@code null}.
     */
    @Nullable
    Integer getAvgHr();

    /**
     * Avarage cadence of the activity
     * Can be {@code null}.
     */
    @Nullable
    Integer getAvgCadence();

    /**
     * Status of the activity
     */
    boolean getDeleted();

    /**
     * Workaround column
     * Can be {@code null}.
     */
    @Nullable
    String getNullcolumnhack();
}
