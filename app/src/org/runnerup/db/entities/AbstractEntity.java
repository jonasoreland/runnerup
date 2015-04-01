package org.runnerup.db.entities;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import org.runnerup.common.util.Constants;

import java.util.Arrays;
import java.util.List;

public abstract class AbstractEntity implements DBEntity {

    private final ContentValues mContentValues;

    protected abstract List<String> getValidColumns();

    protected abstract String getTableName();

    protected abstract String getNullColumnHack();


    public AbstractEntity() {
        this.mContentValues = new ContentValues();
    }

    /**
     * Returns the {@code ContentValues} wrapped by this object.
     */
    protected final ContentValues values() {
        return mContentValues;
    }

    public Long getId() {
        if (mContentValues.keySet().contains(Constants.DB.PRIMARY_KEY)) {
            return mContentValues.getAsLong(Constants.DB.PRIMARY_KEY);
        }
        return null;
    }

    public void setId(Long value) {
        values().put(Constants.DB.PRIMARY_KEY, value);
    }

    public long insert(SQLiteDatabase db) {
        return db.insert(getTableName(), getNullColumnHack(), values());
    }

    public void update(SQLiteDatabase db) {
        if (getId() != null) {
            db.update(getTableName(), values(), Constants.DB.PRIMARY_KEY + " = ?", new String[]{Long.toString(getId())});
        } else {
            throw new IllegalArgumentException("Entity has no primary key");
        }
    }

    protected void toContentValues(Cursor c) {
        if (c.isClosed() || c.isAfterLast() || c.isBeforeFirst()) {
            throw new CursorIndexOutOfBoundsException("Cursor not readable");
        }

        if (getValidColumns().containsAll(Arrays.asList(c.getColumnNames()))) {
            DatabaseUtils.cursorRowToContentValues(c, values());
        } else {
            throw new IllegalArgumentException("Cursor " + c.toString() + " is incompatible with the Entity " + this.getClass().getName());
        }
    }

}