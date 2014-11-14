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

package org.runnerup.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.FROYO)
public class SimpleCursorLoader extends android.support.v4.content.CursorLoader {

    private final SQLiteDatabase mDB;
    private final String mTable;
    private final ForceLoadContentObserver mObserver;

    public SimpleCursorLoader(final Context context, final SQLiteDatabase db, final String table,
            final String[] projection,
            final String selection, final String[] selectionArgs, final String sortOrder) {
        super(context, null, projection, selection, selectionArgs, sortOrder);
        mDB = db;
        mTable = table;
        mObserver = new ForceLoadContentObserver();
    }

    @Override
    public Cursor loadInBackground() {
        final Cursor cursor = mDB.query(mTable, getProjection(), getSelection(),
                getSelectionArgs(), null, null, getSortOrder());
        if (cursor != null) {
            // Ensure the cursor window is filled
            cursor.getCount();
            cursor.registerContentObserver(mObserver);
        }
        return cursor;
    }
}
