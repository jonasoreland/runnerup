package org.runnerup.content.db.provider.audioschemes;

import java.util.Date;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.runnerup.content.db.provider.base.AbstractCursor;

/**
 * Cursor wrapper for the {@code audioschemes} table.
 */
public class AudioschemesCursor extends AbstractCursor implements AudioschemesModel {
    public AudioschemesCursor(Cursor cursor) {
        super(cursor);
    }

    /**
     * Primary key.
     */
    public long getId() {
        return getLongOrNull(AudioschemesColumns._ID);
    }

    /**
     * Name of the scheme
     * Cannot be {@code null}.
     */
    @NonNull
    public String getName() {
        String res = getStringOrNull(AudioschemesColumns.NAME);
        if (res == null)
            throw new NullPointerException("The value of 'name' in the database was null, which is not allowed according to the model definition");
        return res;
    }

    /**
     * The order of the scheme
     */
    public int getSortOrder() {
        return getIntegerOrNull(AudioschemesColumns.SORT_ORDER);
    }
}
