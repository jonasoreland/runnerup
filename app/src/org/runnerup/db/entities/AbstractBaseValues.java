package org.runnerup.db.entities;

import android.content.ContentValues;

public abstract class AbstractBaseValues {
    protected final ContentValues mContentValues = new ContentValues();

    /**
     * Returns the {@code ContentValues} wrapped by this object.
     */
    public ContentValues values() {
        return mContentValues;
    }

}