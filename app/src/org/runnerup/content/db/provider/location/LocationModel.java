package org.runnerup.content.db.provider.location;

import org.runnerup.content.db.provider.base.BaseModel;

import java.util.Date;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * A lap summary
 */
public interface LocationModel extends BaseModel {

    /**
     * Id of the activity the location point belongs to
     */
    int getActivityId();

    /**
     * Lap number of the activity the location point belongs to
     */
    int getLap();

    /**
     * Type of the location point
     */
    int getType();

    /**
     * The moment in time when the location point was recorded
     */
    long getTime();

    /**
     * Longitude of the location
     */
    float getLongitude();

    /**
     * Latitude of the location
     */
    float getLatitude();

    /**
     * Accuracy of the location
     * Can be {@code null}.
     */
    @Nullable
    Float getAccurancy();

    /**
     * Altitude of the location
     * Can be {@code null}.
     */
    @Nullable
    Float getAltitude();

    /**
     * Speed of the location
     * Can be {@code null}.
     */
    @Nullable
    Float getSpeed();

    /**
     * Bearing of the location
     * Can be {@code null}.
     */
    @Nullable
    Float getBearing();

    /**
     * HR at the location
     * Can be {@code null}.
     */
    @Nullable
    Integer getHr();

    /**
     * Cadence at the location
     * Can be {@code null}.
     */
    @Nullable
    Integer getCadence();
}
