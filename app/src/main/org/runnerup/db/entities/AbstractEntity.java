/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.db.entities;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.sqlite.SQLiteDatabase;

import org.runnerup.common.util.Constants;

import java.util.Arrays;
import java.util.List;


public abstract class AbstractEntity implements DBEntity {

    private final ContentValues mContentValues;

    protected abstract List<String> getValidColumns();

    protected abstract String getTableName();

    protected abstract String getNullColumnHack();


    AbstractEntity() {
        this.mContentValues = new ContentValues();
    }

    /**
     * Returns the {@code ContentValues} wrapped by this object.
     */
    final ContentValues values() {
        return mContentValues;
    }

    public Long getId() {
        if (mContentValues.containsKey(Constants.DB.PRIMARY_KEY)) {
            return mContentValues.getAsLong(Constants.DB.PRIMARY_KEY);
        }
        return null;
    }

    private void setId(Long value) {
        values().put(Constants.DB.PRIMARY_KEY, value);
    }

    public long insert(SQLiteDatabase db) {
        this.setId(db.insert(getTableName(), getNullColumnHack(), values()));
        return this.getId();
    }

    public void update(SQLiteDatabase db) {
        if (getId() != null) {
            db.update(getTableName(), values(), Constants.DB.PRIMARY_KEY + " = ?", new String[]{Long.toString(getId())});
        } else {
            throw new IllegalArgumentException("Entity has no primary key");
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    void toContentValues(Cursor c) {
        if (c.isClosed() || c.isAfterLast() || c.isBeforeFirst()) {
            throw new CursorIndexOutOfBoundsException("Cursor not readable");
        }

        if (getValidColumns().containsAll(Arrays.asList(c.getColumnNames()))) {
            //noinspection AccessStaticViaInstance
            this.cursorRowToContentValues(c, values());
        } else {
            throw new IllegalArgumentException("Cursor " + c.toString() + " is incompatible with the Entity " + this.getClass().getName());
        }

        for (String column : getValidColumns()) {
            if (values().get(column) == null)
                values().remove(column);
        }
    }

    // This is a replacement for DatabaseUtils.cursorRowToContentValues
    // see https://code.google.com/p/android/issues/detail?id=22219
    @SuppressLint("NewApi")
    private static void cursorRowToContentValues(Cursor cursor, ContentValues values) {
        String[] columns = cursor.getColumnNames();
        int length = columns.length;
        for (int i = 0; i < length; i++) {
            switch (cursor.getType(i)) {
                case Cursor.FIELD_TYPE_NULL:
                    values.putNull(columns[i]);
                    break;
                case Cursor.FIELD_TYPE_INTEGER:
                    values.put(columns[i], cursor.getLong(i));
                    break;
                case Cursor.FIELD_TYPE_FLOAT:
                    values.put(columns[i], cursor.getDouble(i));
                    break;
                case Cursor.FIELD_TYPE_STRING:
                    values.put(columns[i], cursor.getString(i));
                    break;
                case Cursor.FIELD_TYPE_BLOB:
                    values.put(columns[i], cursor.getBlob(i));
                    break;
            }
        }
    }

    public void readByPrimaryKey(SQLiteDatabase DB, long primaryKey) {
        String[] cols = new String[getValidColumns().size()];
        getValidColumns().toArray(cols);
        Cursor cursor = DB.query(getTableName(), cols, "_id = "
                + primaryKey, null, null, null, null);
        //noinspection TryFinallyCanBeTryWithResources
        try {
            if (cursor.moveToFirst()) {
                toContentValues(cursor);
            }
        } finally {
            cursor.close();
        }
    }
}
