package org.runnerup.content.db.provider;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.DefaultDatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import org.runnerup.BuildConfig;
import org.runnerup.common.util.Constants;
import org.runnerup.content.db.provider.account.AccountColumns;
import org.runnerup.content.db.provider.activity.ActivityColumns;
import org.runnerup.content.db.provider.activity.ActivityContentValues;
import org.runnerup.content.db.provider.activity.ActivityCursor;
import org.runnerup.content.db.provider.activity.ActivitySelection;
import org.runnerup.content.db.provider.audioschemes.AudioschemesColumns;
import org.runnerup.content.db.provider.export.ExportColumns;
import org.runnerup.content.db.provider.export.ExportSelection;
import org.runnerup.content.db.provider.feed.FeedColumns;
import org.runnerup.content.db.provider.lap.LapColumns;
import org.runnerup.content.db.provider.lap.LapSelection;
import org.runnerup.content.db.provider.location.LocationColumns;
import org.runnerup.content.db.provider.location.LocationSelection;

import java.util.ArrayList;

public class RunnerUpSQLiteOpenHelper extends SQLiteOpenHelper {
    private static final String TAG = RunnerUpSQLiteOpenHelper.class.getSimpleName();

    public static final String DATABASE_FILE_NAME = "runnerup.db";
    private static final int DATABASE_VERSION = 27;
    private static RunnerUpSQLiteOpenHelper sInstance;
    private final Context mContext;
    private final RunnerUpSQLiteOpenHelperCallbacks mOpenHelperCallbacks;

    // @formatter:off
    public static final String SQL_CREATE_TABLE_ACCOUNT = "CREATE TABLE IF NOT EXISTS "
            + AccountColumns.TABLE_NAME + " ( "
            + AccountColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + AccountColumns.NAME + " TEXT NOT NULL, "
            + AccountColumns.DESCRIPTION + " TEXT, "
            + AccountColumns.URL + " TEXT, "
            + AccountColumns.FORMAT + " TEXT NOT NULL, "
            + AccountColumns.FLAGS + " INTEGER NOT NULL DEFAULT 7, "
            + AccountColumns.ENABLED + " INTEGER NOT NULL DEFAULT 1, "
            + AccountColumns.AUTH_METHOD + " TEXT NOT NULL, "
            + AccountColumns.AUTH_CONFIG + " TEXT NOT NULL, "
            + AccountColumns.ICON + " INTEGER NOT NULL "
            + ", CONSTRAINT unique_name UNIQUE (name)"
            + " );";

    public static final String SQL_CREATE_TABLE_ACTIVITY = "CREATE TABLE IF NOT EXISTS "
            + ActivityColumns.TABLE_NAME + " ( "
            + ActivityColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + ActivityColumns.START_TIME + " INTEGER NOT NULL DEFAULT 'strftime('%s','now')', "
            + ActivityColumns.DISTANCE + " REAL, "
            + ActivityColumns.TIME + " INTEGER, "
            + ActivityColumns.NAME + " TEXT, "
            + ActivityColumns.COMMENT + " TEXT, "
            + ActivityColumns.TYPE + " INTEGER, "
            + ActivityColumns.MAX_HR + " INTEGER, "
            + ActivityColumns.AVG_HR + " INTEGER, "
            + ActivityColumns.AVG_CADENCE + " INTEGER, "
            + ActivityColumns.DELETED + " INTEGER NOT NULL DEFAULT 0, "
            + ActivityColumns.NULLCOLUMNHACK + " TEXT "
            + " );";

    public static final String SQL_CREATE_TABLE_AUDIOSCHEMES = "CREATE TABLE IF NOT EXISTS "
            + AudioschemesColumns.TABLE_NAME + " ( "
            + AudioschemesColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + AudioschemesColumns.NAME + " TEXT NOT NULL, "
            + AudioschemesColumns.SORT_ORDER + " INTEGER NOT NULL "
            + ", CONSTRAINT unique_name UNIQUE (name)"
            + " );";

    public static final String SQL_CREATE_TABLE_EXPORT = "CREATE TABLE IF NOT EXISTS "
            + ExportColumns.TABLE_NAME + " ( "
            + ExportColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + ExportColumns.ACTIVITY_ID + " INTEGER NOT NULL, "
            + ExportColumns.ACCOUNT_ID + " TEXT NOT NULL, "
            + ExportColumns.STATUS + " TEXT, "
            + ExportColumns.EXT_ID + " TEXT, "
            + ExportColumns.EXTRA + " INTEGER NOT NULL DEFAULT 1 "
            + " );";

    public static final String SQL_CREATE_TABLE_FEED = "CREATE TABLE IF NOT EXISTS "
            + FeedColumns.TABLE_NAME + " ( "
            + FeedColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + FeedColumns.ACCOUNT_ID + " INTEGER NOT NULL, "
            + FeedColumns.EXT_ID + " TEXT, "
            + FeedColumns.ENTRY_TYPE + " INTEGER NOT NULL, "
            + FeedColumns.TYPE + " INTEGER, "
            + FeedColumns.START_TIME + " INTEGER NOT NULL, "
            + FeedColumns.DURATION + " INTEGER, "
            + FeedColumns.DISTANCE + " REAL, "
            + FeedColumns.USER_ID + " TEXT, "
            + FeedColumns.USER_FIRST_NAME + " TEXT, "
            + FeedColumns.USER_LAST_NAME + " TEXT, "
            + FeedColumns.USER_IMAGE_URL + " TEXT, "
            + FeedColumns.NOTES + " TEXT, "
            + FeedColumns.COMMENTS + " TEXT, "
            + FeedColumns.URL + " TEXT, "
            + FeedColumns.FLAGS + " TEXT "
            + " );";

    public static final String SQL_CREATE_INDEX_FEED_START_TIME = "CREATE INDEX IDX_FEED_START_TIME "
            + " ON " + FeedColumns.TABLE_NAME + " ( " + FeedColumns.START_TIME + " );";

    public static final String SQL_CREATE_TABLE_LAP = "CREATE TABLE IF NOT EXISTS "
            + LapColumns.TABLE_NAME + " ( "
            + LapColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + LapColumns.ACTIVITY_ID + " INTEGER NOT NULL, "
            + LapColumns.LAP + " INTEGER NOT NULL, "
            + LapColumns.TYPE + " INTEGER NOT NULL DEFAULT 0, "
            + LapColumns.TIME + " INTEGER, "
            + LapColumns.DISTANCE + " REAL, "
            + LapColumns.PLANNED_TIME + " INTEGER, "
            + LapColumns.PLANNED_DISTANCE + " REAL, "
            + LapColumns.PLANNED_PACE + " REAL, "
            + LapColumns.AVG_HR + " INTEGER, "
            + LapColumns.MAX_HR + " INTEGER, "
            + LapColumns.AVG_CADENCE + " INTEGER "
            + " );";

    public static final String SQL_CREATE_TABLE_LOCATION = "CREATE TABLE IF NOT EXISTS "
            + LocationColumns.TABLE_NAME + " ( "
            + LocationColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + LocationColumns.ACTIVITY_ID + " INTEGER NOT NULL, "
            + LocationColumns.LAP + " INTEGER NOT NULL, "
            + LocationColumns.TYPE + " INTEGER NOT NULL, "
            + LocationColumns.TIME + " INTEGER NOT NULL, "
            + LocationColumns.LONGITUDE + " REAL NOT NULL, "
            + LocationColumns.LATITUDE + " REAL NOT NULL, "
            + LocationColumns.ACCURANCY + " REAL, "
            + LocationColumns.ALTITUDE + " REAL, "
            + LocationColumns.SPEED + " REAL, "
            + LocationColumns.BEARING + " REAL, "
            + LocationColumns.HR + " INTEGER, "
            + LocationColumns.CADENCE + " INTEGER "
            + " );";

    // @formatter:on

    public static RunnerUpSQLiteOpenHelper getInstance(Context context) {
        // Use the application context, which will ensure that you
        // don't accidentally leak an Activity's context.
        // See this article for more information: http://bit.ly/6LRzfx
        if (sInstance == null) {
            sInstance = newInstance(context.getApplicationContext());
        }
        return sInstance;
    }

    private static RunnerUpSQLiteOpenHelper newInstance(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            return newInstancePreHoneycomb(context);
        }
        return newInstancePostHoneycomb(context);
    }


    /*
     * Pre Honeycomb.
     */
    private static RunnerUpSQLiteOpenHelper newInstancePreHoneycomb(Context context) {
        return new RunnerUpSQLiteOpenHelper(context);
    }

    private RunnerUpSQLiteOpenHelper(Context context) {
        super(context, DATABASE_FILE_NAME, null, DATABASE_VERSION);
        mContext = context;
        mOpenHelperCallbacks = new RunnerUpSQLiteOpenHelperCallbacks();
    }


    /*
     * Post Honeycomb.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static RunnerUpSQLiteOpenHelper newInstancePostHoneycomb(Context context) {
        return new RunnerUpSQLiteOpenHelper(context, new DefaultDatabaseErrorHandler());
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private RunnerUpSQLiteOpenHelper(Context context, DatabaseErrorHandler errorHandler) {
        super(context, DATABASE_FILE_NAME, null, DATABASE_VERSION, errorHandler);
        mContext = context;
        mOpenHelperCallbacks = new RunnerUpSQLiteOpenHelperCallbacks();
    }


    @Override
    public void onCreate(SQLiteDatabase db) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onCreate");
        mOpenHelperCallbacks.onPreCreate(mContext, db);
        db.execSQL(SQL_CREATE_TABLE_ACCOUNT);
        db.execSQL(SQL_CREATE_TABLE_ACTIVITY);
        db.execSQL(SQL_CREATE_TABLE_AUDIOSCHEMES);
        db.execSQL(SQL_CREATE_TABLE_EXPORT);
        db.execSQL(SQL_CREATE_TABLE_FEED);
        db.execSQL(SQL_CREATE_INDEX_FEED_START_TIME);
        db.execSQL(SQL_CREATE_TABLE_LAP);
        db.execSQL(SQL_CREATE_TABLE_LOCATION);
        mOpenHelperCallbacks.onPostCreate(mContext, db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            setForeignKeyConstraintsEnabled(db);
        }
        mOpenHelperCallbacks.onOpen(mContext, db);
    }

    private void setForeignKeyConstraintsEnabled(SQLiteDatabase db) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            setForeignKeyConstraintsEnabledPreJellyBean(db);
        } else {
            setForeignKeyConstraintsEnabledPostJellyBean(db);
        }
    }

    private void setForeignKeyConstraintsEnabledPreJellyBean(SQLiteDatabase db) {
        db.execSQL("PRAGMA foreign_keys=ON;");
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setForeignKeyConstraintsEnabledPostJellyBean(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        mOpenHelperCallbacks.onUpgrade(mContext, db, oldVersion, newVersion);
    }


    public static void deleteActivity(Context context, long id) {
        ExportSelection export = new ExportSelection();
        export.id(id).delete(context.getContentResolver());

        LocationSelection location = new LocationSelection();
        location.id(id).delete(context.getContentResolver());

        LapSelection lap = new LapSelection();
        lap.id(id).delete(context.getContentResolver());

        ActivitySelection activity = new ActivitySelection();
        activity.id(id).delete(context.getContentResolver());
    }

    public static void purgeDeletedActivities(final Context ctx, final ProgressDialog dialog,
                                              final Runnable onComplete) {

        ActivitySelection where = new ActivitySelection();
        where.deleted(false);
        ActivityCursor c = where.query(ctx.getContentResolver());

        final ArrayList<Long> list = new ArrayList<Long>(10);
        if (c.moveToFirst()) {
            do {
                list.add(c.getId());
            } while (c.moveToNext());
        }
        c.close();

        if (list.size() > 0) {
            new AsyncTask<Long, Void, Void>() {

                @Override
                protected void onPreExecute() {
                    dialog.setMax(list.size());
                    super.onPreExecute();
                }

                @Override
                protected Void doInBackground(Long... args) {
                    for (Long id : list) {
                        deleteActivity(ctx, id);
                        dialog.incrementProgressBy(1);
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    if (onComplete != null)
                        onComplete.run();
                }
            }.execute((long) 2);
        } else {
            if (onComplete != null)
                onComplete.run();
        }
    }
}
