package org.runnerup.content.db.provider;

import java.util.Arrays;

import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import org.runnerup.BuildConfig;
import org.runnerup.content.db.provider.base.BaseContentProvider;
import org.runnerup.content.db.provider.account.AccountColumns;
import org.runnerup.content.db.provider.activity.ActivityColumns;
import org.runnerup.content.db.provider.audioschemes.AudioschemesColumns;
import org.runnerup.content.db.provider.export.ExportColumns;
import org.runnerup.content.db.provider.feed.FeedColumns;
import org.runnerup.content.db.provider.lap.LapColumns;
import org.runnerup.content.db.provider.location.LocationColumns;

public class RunnerUpDbProvider extends BaseContentProvider {
    private static final String TAG = RunnerUpDbProvider.class.getSimpleName();

    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final String TYPE_CURSOR_ITEM = "vnd.android.cursor.item/";
    private static final String TYPE_CURSOR_DIR = "vnd.android.cursor.dir/";

    public static final String AUTHORITY = "org.runnerup.workout.db.provider";
    public static final String CONTENT_URI_BASE = "content://" + AUTHORITY;

    private static final int URI_TYPE_ACCOUNT = 0;
    private static final int URI_TYPE_ACCOUNT_ID = 1;

    private static final int URI_TYPE_ACTIVITY = 2;
    private static final int URI_TYPE_ACTIVITY_ID = 3;

    private static final int URI_TYPE_AUDIOSCHEMES = 4;
    private static final int URI_TYPE_AUDIOSCHEMES_ID = 5;

    private static final int URI_TYPE_EXPORT = 6;
    private static final int URI_TYPE_EXPORT_ID = 7;

    private static final int URI_TYPE_FEED = 8;
    private static final int URI_TYPE_FEED_ID = 9;

    private static final int URI_TYPE_LAP = 10;
    private static final int URI_TYPE_LAP_ID = 11;

    private static final int URI_TYPE_LOCATION = 12;
    private static final int URI_TYPE_LOCATION_ID = 13;



    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        URI_MATCHER.addURI(AUTHORITY, AccountColumns.TABLE_NAME, URI_TYPE_ACCOUNT);
        URI_MATCHER.addURI(AUTHORITY, AccountColumns.TABLE_NAME + "/#", URI_TYPE_ACCOUNT_ID);
        URI_MATCHER.addURI(AUTHORITY, ActivityColumns.TABLE_NAME, URI_TYPE_ACTIVITY);
        URI_MATCHER.addURI(AUTHORITY, ActivityColumns.TABLE_NAME + "/#", URI_TYPE_ACTIVITY_ID);
        URI_MATCHER.addURI(AUTHORITY, AudioschemesColumns.TABLE_NAME, URI_TYPE_AUDIOSCHEMES);
        URI_MATCHER.addURI(AUTHORITY, AudioschemesColumns.TABLE_NAME + "/#", URI_TYPE_AUDIOSCHEMES_ID);
        URI_MATCHER.addURI(AUTHORITY, ExportColumns.TABLE_NAME, URI_TYPE_EXPORT);
        URI_MATCHER.addURI(AUTHORITY, ExportColumns.TABLE_NAME + "/#", URI_TYPE_EXPORT_ID);
        URI_MATCHER.addURI(AUTHORITY, FeedColumns.TABLE_NAME, URI_TYPE_FEED);
        URI_MATCHER.addURI(AUTHORITY, FeedColumns.TABLE_NAME + "/#", URI_TYPE_FEED_ID);
        URI_MATCHER.addURI(AUTHORITY, LapColumns.TABLE_NAME, URI_TYPE_LAP);
        URI_MATCHER.addURI(AUTHORITY, LapColumns.TABLE_NAME + "/#", URI_TYPE_LAP_ID);
        URI_MATCHER.addURI(AUTHORITY, LocationColumns.TABLE_NAME, URI_TYPE_LOCATION);
        URI_MATCHER.addURI(AUTHORITY, LocationColumns.TABLE_NAME + "/#", URI_TYPE_LOCATION_ID);
    }

    @Override
    protected SQLiteOpenHelper createSqLiteOpenHelper() {
        return RunnerUpSQLiteOpenHelper.getInstance(getContext());
    }

    @Override
    protected boolean hasDebug() {
        return DEBUG;
    }

    @Override
    public String getType(Uri uri) {
        int match = URI_MATCHER.match(uri);
        switch (match) {
            case URI_TYPE_ACCOUNT:
                return TYPE_CURSOR_DIR + AccountColumns.TABLE_NAME;
            case URI_TYPE_ACCOUNT_ID:
                return TYPE_CURSOR_ITEM + AccountColumns.TABLE_NAME;

            case URI_TYPE_ACTIVITY:
                return TYPE_CURSOR_DIR + ActivityColumns.TABLE_NAME;
            case URI_TYPE_ACTIVITY_ID:
                return TYPE_CURSOR_ITEM + ActivityColumns.TABLE_NAME;

            case URI_TYPE_AUDIOSCHEMES:
                return TYPE_CURSOR_DIR + AudioschemesColumns.TABLE_NAME;
            case URI_TYPE_AUDIOSCHEMES_ID:
                return TYPE_CURSOR_ITEM + AudioschemesColumns.TABLE_NAME;

            case URI_TYPE_EXPORT:
                return TYPE_CURSOR_DIR + ExportColumns.TABLE_NAME;
            case URI_TYPE_EXPORT_ID:
                return TYPE_CURSOR_ITEM + ExportColumns.TABLE_NAME;

            case URI_TYPE_FEED:
                return TYPE_CURSOR_DIR + FeedColumns.TABLE_NAME;
            case URI_TYPE_FEED_ID:
                return TYPE_CURSOR_ITEM + FeedColumns.TABLE_NAME;

            case URI_TYPE_LAP:
                return TYPE_CURSOR_DIR + LapColumns.TABLE_NAME;
            case URI_TYPE_LAP_ID:
                return TYPE_CURSOR_ITEM + LapColumns.TABLE_NAME;

            case URI_TYPE_LOCATION:
                return TYPE_CURSOR_DIR + LocationColumns.TABLE_NAME;
            case URI_TYPE_LOCATION_ID:
                return TYPE_CURSOR_ITEM + LocationColumns.TABLE_NAME;

        }
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (DEBUG) Log.d(TAG, "insert uri=" + uri + " values=" + values);
        return super.insert(uri, values);
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        if (DEBUG) Log.d(TAG, "bulkInsert uri=" + uri + " values.length=" + values.length);
        return super.bulkInsert(uri, values);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (DEBUG) Log.d(TAG, "update uri=" + uri + " values=" + values + " selection=" + selection + " selectionArgs=" + Arrays.toString(selectionArgs));
        return super.update(uri, values, selection, selectionArgs);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (DEBUG) Log.d(TAG, "delete uri=" + uri + " selection=" + selection + " selectionArgs=" + Arrays.toString(selectionArgs));
        return super.delete(uri, selection, selectionArgs);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (DEBUG)
            Log.d(TAG, "query uri=" + uri + " selection=" + selection + " selectionArgs=" + Arrays.toString(selectionArgs) + " sortOrder=" + sortOrder
                    + " groupBy=" + uri.getQueryParameter(QUERY_GROUP_BY) + " having=" + uri.getQueryParameter(QUERY_HAVING) + " limit=" + uri.getQueryParameter(QUERY_LIMIT));
        return super.query(uri, projection, selection, selectionArgs, sortOrder);
    }

    @Override
    protected QueryParams getQueryParams(Uri uri, String selection, String[] projection) {
        QueryParams res = new QueryParams();
        String id = null;
        int matchedId = URI_MATCHER.match(uri);
        switch (matchedId) {
            case URI_TYPE_ACCOUNT:
            case URI_TYPE_ACCOUNT_ID:
                res.table = AccountColumns.TABLE_NAME;
                res.idColumn = AccountColumns._ID;
                res.tablesWithJoins = AccountColumns.TABLE_NAME;
                res.orderBy = AccountColumns.DEFAULT_ORDER;
                break;

            case URI_TYPE_ACTIVITY:
            case URI_TYPE_ACTIVITY_ID:
                res.table = ActivityColumns.TABLE_NAME;
                res.idColumn = ActivityColumns._ID;
                res.tablesWithJoins = ActivityColumns.TABLE_NAME;
                res.orderBy = ActivityColumns.DEFAULT_ORDER;
                break;

            case URI_TYPE_AUDIOSCHEMES:
            case URI_TYPE_AUDIOSCHEMES_ID:
                res.table = AudioschemesColumns.TABLE_NAME;
                res.idColumn = AudioschemesColumns._ID;
                res.tablesWithJoins = AudioschemesColumns.TABLE_NAME;
                res.orderBy = AudioschemesColumns.DEFAULT_ORDER;
                break;

            case URI_TYPE_EXPORT:
            case URI_TYPE_EXPORT_ID:
                res.table = ExportColumns.TABLE_NAME;
                res.idColumn = ExportColumns._ID;
                res.tablesWithJoins = ExportColumns.TABLE_NAME;
                res.orderBy = ExportColumns.DEFAULT_ORDER;
                break;

            case URI_TYPE_FEED:
            case URI_TYPE_FEED_ID:
                res.table = FeedColumns.TABLE_NAME;
                res.idColumn = FeedColumns._ID;
                res.tablesWithJoins = FeedColumns.TABLE_NAME;
                res.orderBy = FeedColumns.DEFAULT_ORDER;
                break;

            case URI_TYPE_LAP:
            case URI_TYPE_LAP_ID:
                res.table = LapColumns.TABLE_NAME;
                res.idColumn = LapColumns._ID;
                res.tablesWithJoins = LapColumns.TABLE_NAME;
                res.orderBy = LapColumns.DEFAULT_ORDER;
                break;

            case URI_TYPE_LOCATION:
            case URI_TYPE_LOCATION_ID:
                res.table = LocationColumns.TABLE_NAME;
                res.idColumn = LocationColumns._ID;
                res.tablesWithJoins = LocationColumns.TABLE_NAME;
                res.orderBy = LocationColumns.DEFAULT_ORDER;
                break;

            default:
                throw new IllegalArgumentException("The uri '" + uri + "' is not supported by this ContentProvider");
        }

        switch (matchedId) {
            case URI_TYPE_ACCOUNT_ID:
            case URI_TYPE_ACTIVITY_ID:
            case URI_TYPE_AUDIOSCHEMES_ID:
            case URI_TYPE_EXPORT_ID:
            case URI_TYPE_FEED_ID:
            case URI_TYPE_LAP_ID:
            case URI_TYPE_LOCATION_ID:
                id = uri.getLastPathSegment();
        }
        if (id != null) {
            if (selection != null) {
                res.selection = res.table + "." + res.idColumn + "=" + id + " and (" + selection + ")";
            } else {
                res.selection = res.table + "." + res.idColumn + "=" + id;
            }
        } else {
            res.selection = selection;
        }
        return res;
    }
}
