package org.runnerup.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.runnerup.common.util.Constants;

import java.util.ArrayList;

public class PathCursor {

    private final Cursor cursor;
    private ArrayList<Integer> ignoreIDs;
    private final int idxLocId;

    public PathCursor(SQLiteDatabase mDB, long activityId, String[] columns, int idxLocId, PathSimplifier simplifier) {

        if (simplifier != null) {
            ignoreIDs = simplifier.getNoisyLocationIDs(mDB, activityId);
            if (ignoreIDs.size() == 0) {
                ignoreIDs = null;
            }
        }
        this.idxLocId = idxLocId;

        cursor = mDB.query(Constants.DB.LOCATION.TABLE, columns,
                Constants.DB.LOCATION.ACTIVITY + " = " + activityId,
                null, null, null,
                null);
    }

    private boolean skipSimplified(boolean ok) {
        if (ignoreIDs != null) {
            while (ok && ignoreIDs.contains(cursor.getInt(idxLocId))) {
                ok = cursor.moveToNext();
            }
        }
        // useful?
        if (!ok) {
            cursor.close();
        }
        return ok;
    }

    /** Moves cursor to the first non-simplified location */
    public boolean moveToFirst() {
        return skipSimplified(cursor.moveToFirst());
    }
    /** Moves cursor to the next non-simplified location */
    public boolean moveToNext() {
        return skipSimplified(cursor.moveToNext());
    }
    /** Moves cursor to the non-simplified location after the 'skip' skipped locations  */
    public boolean move(int skip) {
        return skipSimplified(cursor.move(skip));
    }

    /** Simple wrapping of cursor methods */
    public int getInt(int idx) {
        return cursor.getInt(idx);
    }
    /** Simple wrapping of cursor methods */
    public double getDouble(int idx) {
        return cursor.getDouble(idx);
    }
    /** Simple wrapping of cursor methods */
    public long getLong(int idx) {
        return cursor.getLong(idx);
    }
    /** Simple wrapping of cursor methods */
    public float getFloat(int idx) {
        return cursor.getFloat(idx);
    }
    /** Simple wrapping of cursor methods */
    public boolean isNull(int idx) {
        return cursor.isNull(idx);
    }

    /** Simple wrapping of cursor methods */
    public void close() {
        if (!cursor.isClosed()) {
            cursor.close();
        }
    }
}
