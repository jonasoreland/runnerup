package org.runnerup.content.db.provider.export;

import java.util.Date;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.runnerup.content.db.provider.base.AbstractCursor;

/**
 * Cursor wrapper for the {@code export} table.
 */
public class ExportCursor extends AbstractCursor implements ExportModel {
    public ExportCursor(Cursor cursor) {
        super(cursor);
    }

    /**
     * Primary key.
     */
    public long getId() {
        return getLongOrNull(ExportColumns._ID);
    }

    /**
     * Id of the activity that's beeing exported
     */
    public int getActivityId() {
        return getIntegerOrNull(ExportColumns.ACTIVITY_ID);
    }

    /**
     * The account to which the activity has been exported
     * Cannot be {@code null}.
     */
    @NonNull
    public String getAccountId() {
        String res = getStringOrNull(ExportColumns.ACCOUNT_ID);
        if (res == null)
            throw new NullPointerException("The value of 'account_id' in the database was null, which is not allowed according to the model definition");
        return res;
    }

    /**
     * Status of the export
     * Can be {@code null}.
     */
    @Nullable
    public String getStatus() {
        return getStringOrNull(ExportColumns.STATUS);
    }

    /**
     * External Id of the activity
     * Can be {@code null}.
     */
    @Nullable
    public String getExtId() {
        return getStringOrNull(ExportColumns.EXT_ID);
    }

    /**
     * Extra
     */
    public int getExtra() {
        return getIntegerOrNull(ExportColumns.EXTRA);
    }
}
