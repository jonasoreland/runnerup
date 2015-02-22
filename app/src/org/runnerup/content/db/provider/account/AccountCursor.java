package org.runnerup.content.db.provider.account;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.runnerup.content.db.provider.base.AbstractCursor;

/**
 * Cursor wrapper for the {@code account} table.
 */
public class AccountCursor extends AbstractCursor implements AccountModel {
    public AccountCursor(Cursor cursor) {
        super(cursor);
    }

    /**
     * Primary key.
     */
    public long getId() {
        return getLongOrNull(AccountColumns._ID);
    }

    /**
     * Name of the service
     * Cannot be {@code null}.
     */
    @NonNull
    public String getName() {
        String res = getStringOrNull(AccountColumns.NAME);
        if (res == null)
            throw new NullPointerException("The value of 'name' in the database was null, which is not allowed according to the model definition");
        return res;
    }

    /**
     * The description of the service
     * Can be {@code null}.
     */
    @Nullable
    public String getDescription() {
        return getStringOrNull(AccountColumns.DESCRIPTION);
    }

    /**
     * The URL of the service
     * Can be {@code null}.
     */
    @Nullable
    public String getUrl() {
        return getStringOrNull(AccountColumns.URL);
    }

    /**
     * The accepted format of the activity
     * Cannot be {@code null}.
     */
    @NonNull
    public String getFormat() {
        String res = getStringOrNull(AccountColumns.FORMAT);
        if (res == null)
            throw new NullPointerException("The value of 'format' in the database was null, which is not allowed according to the model definition");
        return res;
    }

    /**
     * Get the {@code default_send} value.
     */
    public int getDefaultSend() {
        return getIntegerOrNull(AccountColumns.FLAGS);
    }

    /**
     * Status of the account
     */
    public boolean getEnabled() {
        return getBooleanOrNull(AccountColumns.ENABLED);
    }

    /**
     * The authorization method
     * Cannot be {@code null}.
     */
    @NonNull
    public String getAuthMethod() {
        String res = getStringOrNull(AccountColumns.AUTH_METHOD);
        if (res == null)
            throw new NullPointerException("The value of 'auth_method' in the database was null, which is not allowed according to the model definition");
        return res;
    }

    /**
     * The authorization config data
     * Cannot be {@code null}.
     */
    @NonNull
    public String getAuthConfig() {
        String res = getStringOrNull(AccountColumns.AUTH_CONFIG);
        if (res == null)
            throw new NullPointerException("The value of 'auth_config' in the database was null, which is not allowed according to the model definition");
        return res;
    }

    /**
     * The service icon
     */
    public int getIcon() {
        return getIntegerOrNull(AccountColumns.ICON);
    }
}
