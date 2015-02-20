package org.runnerup.content.db.provider.account;

import android.content.ContentResolver;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.runnerup.content.db.provider.base.AbstractContentValues;

/**
 * Content values wrapper for the {@code account} table.
 */
public class AccountContentValues extends AbstractContentValues {
    @Override
    public Uri uri() {
        return AccountColumns.CONTENT_URI;
    }

    /**
     * Update row(s) using the values stored by this object and the given selection.
     *
     * @param contentResolver The content resolver to use.
     * @param where The selection to use (can be {@code null}).
     */
    public int update(ContentResolver contentResolver, @Nullable AccountSelection where) {
        return contentResolver.update(uri(), values(), where == null ? null : where.sel(), where == null ? null : where.args());
    }

    /**
     * Name of the service
     */
    public AccountContentValues putName(@NonNull String value) {
        if (value == null) throw new IllegalArgumentException("name must not be null");
        mContentValues.put(AccountColumns.NAME, value);
        return this;
    }


    /**
     * The description of the service
     */
    public AccountContentValues putDescription(@Nullable String value) {
        mContentValues.put(AccountColumns.DESCRIPTION, value);
        return this;
    }

    public AccountContentValues putDescriptionNull() {
        mContentValues.putNull(AccountColumns.DESCRIPTION);
        return this;
    }

    /**
     * The URL of the service
     */
    public AccountContentValues putUrl(@Nullable String value) {
        mContentValues.put(AccountColumns.URL, value);
        return this;
    }

    public AccountContentValues putUrlNull() {
        mContentValues.putNull(AccountColumns.URL);
        return this;
    }

    /**
     * The accepted format of the activity
     */
    public AccountContentValues putFormat(@NonNull String value) {
        if (value == null) throw new IllegalArgumentException("format must not be null");
        mContentValues.put(AccountColumns.FORMAT, value);
        return this;
    }


    public AccountContentValues putDefaultSend(int value) {
        mContentValues.put(AccountColumns.FLAGS, value);
        return this;
    }


    /**
     * Status of the account
     */
    public AccountContentValues putEnabled(boolean value) {
        mContentValues.put(AccountColumns.ENABLED, value);
        return this;
    }


    /**
     * The authorization method
     */
    public AccountContentValues putAuthMethod(@NonNull String value) {
        if (value == null) throw new IllegalArgumentException("authMethod must not be null");
        mContentValues.put(AccountColumns.AUTH_METHOD, value);
        return this;
    }


    /**
     * The authorization config data
     */
    public AccountContentValues putAuthConfig(@NonNull String value) {
        if (value == null) throw new IllegalArgumentException("authConfig must not be null");
        mContentValues.put(AccountColumns.AUTH_CONFIG, value);
        return this;
    }


    /**
     * The service icon
     */
    public AccountContentValues putIcon(int value) {
        mContentValues.put(AccountColumns.ICON, value);
        return this;
    }

}
