package org.runnerup.content.db.provider.export;

import java.util.Date;

import android.content.ContentResolver;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.runnerup.content.db.provider.base.AbstractContentValues;

/**
 * Content values wrapper for the {@code export} table.
 */
public class ExportContentValues extends AbstractContentValues {
    @Override
    public Uri uri() {
        return ExportColumns.CONTENT_URI;
    }

    /**
     * Update row(s) using the values stored by this object and the given selection.
     *
     * @param contentResolver The content resolver to use.
     * @param where The selection to use (can be {@code null}).
     */
    public int update(ContentResolver contentResolver, @Nullable ExportSelection where) {
        return contentResolver.update(uri(), values(), where == null ? null : where.sel(), where == null ? null : where.args());
    }

    /**
     * Id of the activity that's beeing exported
     */
    public ExportContentValues putActivityId(int value) {
        mContentValues.put(ExportColumns.ACTIVITY_ID, value);
        return this;
    }


    /**
     * The account to which the activity has been exported
     */
    public ExportContentValues putAccountId(@NonNull String value) {
        if (value == null) throw new IllegalArgumentException("accountId must not be null");
        mContentValues.put(ExportColumns.ACCOUNT_ID, value);
        return this;
    }


    /**
     * Status of the export
     */
    public ExportContentValues putStatus(@Nullable String value) {
        mContentValues.put(ExportColumns.STATUS, value);
        return this;
    }

    public ExportContentValues putStatusNull() {
        mContentValues.putNull(ExportColumns.STATUS);
        return this;
    }

    /**
     * External Id of the activity
     */
    public ExportContentValues putExtId(@Nullable String value) {
        mContentValues.put(ExportColumns.EXT_ID, value);
        return this;
    }

    public ExportContentValues putExtIdNull() {
        mContentValues.putNull(ExportColumns.EXT_ID);
        return this;
    }

    /**
     * Extra
     */
    public ExportContentValues putExtra(int value) {
        mContentValues.put(ExportColumns.EXTRA, value);
        return this;
    }

}
