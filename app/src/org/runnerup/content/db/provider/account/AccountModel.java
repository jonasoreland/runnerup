package org.runnerup.content.db.provider.account;

import org.runnerup.content.db.provider.base.BaseModel;

import java.util.Date;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * A Fitness Service
 */
public interface AccountModel extends BaseModel {

    /**
     * Name of the service
     * Cannot be {@code null}.
     */
    @NonNull
    String getName();

    /**
     * The description of the service
     * Can be {@code null}.
     */
    @Nullable
    String getDescription();

    /**
     * The URL of the service
     * Can be {@code null}.
     */
    @Nullable
    String getUrl();

    /**
     * The accepted format of the activity
     * Cannot be {@code null}.
     */
    @NonNull
    String getFormat();

    /**
     * Get the {@code default_send} value.
     */
    int getDefaultSend();

    /**
     * Status of the account
     */
    boolean getEnabled();

    /**
     * The authorization method
     * Cannot be {@code null}.
     */
    @NonNull
    String getAuthMethod();

    /**
     * The authorization config data
     * Cannot be {@code null}.
     */
    @NonNull
    String getAuthConfig();

    /**
     * The service icon
     */
    int getIcon();
}
