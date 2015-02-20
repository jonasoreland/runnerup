package org.runnerup.content.db.provider.audioschemes;

import java.util.Date;

import android.content.ContentResolver;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.runnerup.content.db.provider.base.AbstractContentValues;

/**
 * Content values wrapper for the {@code audioschemes} table.
 */
public class AudioschemesContentValues extends AbstractContentValues {
    @Override
    public Uri uri() {
        return AudioschemesColumns.CONTENT_URI;
    }

    /**
     * Update row(s) using the values stored by this object and the given selection.
     *
     * @param contentResolver The content resolver to use.
     * @param where The selection to use (can be {@code null}).
     */
    public int update(ContentResolver contentResolver, @Nullable AudioschemesSelection where) {
        return contentResolver.update(uri(), values(), where == null ? null : where.sel(), where == null ? null : where.args());
    }

    /**
     * Name of the scheme
     */
    public AudioschemesContentValues putName(@NonNull String value) {
        if (value == null) throw new IllegalArgumentException("name must not be null");
        mContentValues.put(AudioschemesColumns.NAME, value);
        return this;
    }


    /**
     * The order of the scheme
     */
    public AudioschemesContentValues putSortOrder(int value) {
        mContentValues.put(AudioschemesColumns.SORT_ORDER, value);
        return this;
    }

}
