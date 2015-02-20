package org.runnerup.content.db.provider.base;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.util.Log;

public abstract class BaseContentProvider extends ContentProvider {
    public static final String QUERY_NOTIFY = "QUERY_NOTIFY";
    public static final String QUERY_GROUP_BY = "QUERY_GROUP_BY";
    public static final String QUERY_HAVING = "QUERY_HAVING";
    public static final String QUERY_LIMIT = "QUERY_LIMIT";

    public static class QueryParams {
        public String table;
        public String tablesWithJoins;
        public String idColumn;
        public String selection;
        public String orderBy;
    }


    protected abstract QueryParams getQueryParams(Uri uri, String selection, String[] projection);
    protected abstract boolean hasDebug();

    protected abstract SQLiteOpenHelper createSqLiteOpenHelper();

    protected SQLiteOpenHelper mSqLiteOpenHelper;

    @Override
    public final boolean onCreate() {
        if (hasDebug()) {
            // Enable logging of SQL statements as they are executed.
            try {
                Class<?> sqliteDebugClass = Class.forName("android.database.sqlite.SQLiteDebug");
                Field field = sqliteDebugClass.getDeclaredField("DEBUG_SQL_STATEMENTS");
                field.setAccessible(true);
                field.set(null, true);

                // Uncomment the following block if you also want logging of execution time (more verbose)
                // field = sqliteDebugClass.getDeclaredField("DEBUG_SQL_TIME");
                // field.setAccessible(true);
                // field.set(null, true);
            } catch (Throwable t) {
                if (hasDebug()) Log.w(getClass().getSimpleName(), "Could not enable SQLiteDebug logging", t);
            }
        }
        mSqLiteOpenHelper = createSqLiteOpenHelper();
        return false;
    }


    @Override
    public Uri insert(Uri uri, ContentValues values) {
        String table = uri.getLastPathSegment();
        long rowId = mSqLiteOpenHelper.getWritableDatabase().insertOrThrow(table, null, values);
        if (rowId == -1) return null;
        String notify;
        if (((notify = uri.getQueryParameter(QUERY_NOTIFY)) == null || "true".equals(notify))) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return uri.buildUpon().appendEncodedPath(String.valueOf(rowId)).build();
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        String table = uri.getLastPathSegment();
        SQLiteDatabase db = mSqLiteOpenHelper.getWritableDatabase();
        int res = 0;
        db.beginTransaction();
        try {
            for (ContentValues v : values) {
                long id = db.insert(table, null, v);
                db.yieldIfContendedSafely();
                if (id != -1) {
                    res++;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        String notify;
        if (res != 0 && ((notify = uri.getQueryParameter(QUERY_NOTIFY)) == null || "true".equals(notify))) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        return res;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        QueryParams queryParams = getQueryParams(uri, selection, null);
        int res = mSqLiteOpenHelper.getWritableDatabase().update(queryParams.table, values, queryParams.selection, selectionArgs);
        String notify;
        if (res != 0 && ((notify = uri.getQueryParameter(QUERY_NOTIFY)) == null || "true".equals(notify))) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return res;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        QueryParams queryParams = getQueryParams(uri, selection, null);
        int res = mSqLiteOpenHelper.getWritableDatabase().delete(queryParams.table, queryParams.selection, selectionArgs);
        String notify;
        if (res != 0 && ((notify = uri.getQueryParameter(QUERY_NOTIFY)) == null || "true".equals(notify))) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return res;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String groupBy = uri.getQueryParameter(QUERY_GROUP_BY);
        String having = uri.getQueryParameter(QUERY_HAVING);
        String limit = uri.getQueryParameter(QUERY_LIMIT);
        QueryParams queryParams = getQueryParams(uri, selection, projection);
        projection = ensureIdIsFullyQualified(projection, queryParams.table, queryParams.idColumn);
        Cursor res = mSqLiteOpenHelper.getReadableDatabase().query(queryParams.tablesWithJoins, projection, queryParams.selection, selectionArgs, groupBy,
                having, sortOrder == null ? queryParams.orderBy : sortOrder, limit);
        res.setNotificationUri(getContext().getContentResolver(), uri);
        return res;
    }

    private String[] ensureIdIsFullyQualified(String[] projection, String tableName, String idColumn) {
        if (projection == null) return null;
        String[] res = new String[projection.length];
        for (int i = 0; i < projection.length; i++) {
            if (projection[i].equals(idColumn)) {
                res[i] = tableName + "." + idColumn + " AS " + BaseColumns._ID;
            } else {
                res[i] = projection[i];
            }
        }
        return res;
    }

    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations) throws OperationApplicationException {
        HashSet<Uri> urisToNotify = new HashSet<Uri>(operations.size());
        for (ContentProviderOperation operation : operations) {
            urisToNotify.add(operation.getUri());
        }
        SQLiteDatabase db = mSqLiteOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            int numOperations = operations.size();
            ContentProviderResult[] results = new ContentProviderResult[numOperations];
            int i = 0;
            for (ContentProviderOperation operation : operations) {
                results[i] = operation.apply(this, results, i);
                if (operation.isYieldAllowed()) {
                    db.yieldIfContendedSafely();
                }
                i++;
            }
            db.setTransactionSuccessful();
            for (Uri uri : urisToNotify) {
                getContext().getContentResolver().notifyChange(uri, null);
            }
            return results;
        } finally {
            db.endTransaction();
        }
    }


    public static Uri notify(Uri uri, boolean notify) {
        return uri.buildUpon().appendQueryParameter(QUERY_NOTIFY, String.valueOf(notify)).build();
    }

    public static Uri groupBy(Uri uri, String groupBy) {
        return uri.buildUpon().appendQueryParameter(QUERY_GROUP_BY, groupBy).build();
    }

    public static Uri having(Uri uri, String having) {
        return uri.buildUpon().appendQueryParameter(QUERY_HAVING, having).build();
    }

    public static Uri limit(Uri uri, String limit) {
        return uri.buildUpon().appendQueryParameter(QUERY_LIMIT, limit).build();
    }
}
