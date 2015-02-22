package org.runnerup.content.db.provider.feed;

import org.runnerup.content.db.provider.base.BaseModel;

import java.util.Date;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * A Service Feed entry
 */
public interface FeedModel extends BaseModel {

    /**
     * Id of the account that the feed entry belongs to
     */
    int getAccountId();

    /**
     * External Id of the feed entry
     * Can be {@code null}.
     */
    @Nullable
    String getExtId();

    /**
     * Type of the feed entry
     */
    int getEntryType();

    /**
     * Sub-Type of the feed entry
     * Can be {@code null}.
     */
    @Nullable
    Integer getType();

    /**
     * Time of the feed entry
     */
    long getStartTime();

    /**
     * Duration of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    Integer getDuration();

    /**
     * Distance of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    Float getDistance();

    /**
     * User Id of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    String getUserId();

    /**
     * First name of the user of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    String getUserFirstName();

    /**
     * Last name of the user of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    String getUserLastName();

    /**
     * Image URL of the user of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    String getUserImageUrl();

    /**
     * Notes of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    String getNotes();

    /**
     * Comments of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    String getComments();

    /**
     * URL the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    String getUrl();

    /**
     * Flags of the feed entry activity
     * Can be {@code null}.
     */
    @Nullable
    String getFlags();
}
