/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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

package org.runnerup.db;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.entities.DBEntity;
import org.runnerup.export.DigifitSynchronizer;
import org.runnerup.export.EndomondoSynchronizer;
import org.runnerup.export.FacebookSynchronizer;
import org.runnerup.export.FileSynchronizer;
import org.runnerup.export.FunBeatSynchronizer;
import org.runnerup.export.GarminSynchronizer;
import org.runnerup.export.GoogleFitSynchronizer;
import org.runnerup.export.GooglePlusSynchronizer;
import org.runnerup.export.JoggSESynchronizer;
import org.runnerup.export.MapMyRunSynchronizer;
import org.runnerup.export.NikePlusSynchronizer;
import org.runnerup.export.RunKeeperSynchronizer;
import org.runnerup.export.RunalyzeSynchronizer;
import org.runnerup.export.RunnerUpLiveSynchronizer;
import org.runnerup.export.RunningAHEADSynchronizer;
import org.runnerup.export.RunningFreeOnlineSynchronizer;
import org.runnerup.export.RuntasticSynchronizer;
import org.runnerup.export.StravaSynchronizer;
import org.runnerup.export.SyncManager;
import org.runnerup.export.Synchronizer;
import org.runnerup.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@TargetApi(Build.VERSION_CODES.FROYO)
public class DBHelper extends SQLiteOpenHelper implements
        Constants {

    private static final int DBVERSION = 32;
    private static final String DBNAME = "runnerup.db";

    private static final String CREATE_TABLE_DBINFO = "create table "
            + DB.DBINFO.TABLE + " ( "
            + ("_id integer primary key CHECK (_id = 0), ")
            + (DB.DBINFO.ACCOUNT_VERSION + " integer not null default 0")
            + ");";

    private static final String CREATE_TABLE_ACTIVITY = "create table "
            + DB.ACTIVITY.TABLE + " ( "
            + ("_id integer primary key autoincrement, ")
            + (DB.ACTIVITY.START_TIME + " integer not null default (strftime('%s','now')),")
            + (DB.ACTIVITY.DISTANCE + " real, ")
            + (DB.ACTIVITY.TIME + " integer, ")
            + (DB.ACTIVITY.NAME + " text,")
            + (DB.ACTIVITY.COMMENT + " text,")
            + (DB.ACTIVITY.SPORT + " integer,")
            + (DB.ACTIVITY.AVG_HR + " integer, ")
            + (DB.ACTIVITY.MAX_HR + " integer, ")
            + (DB.ACTIVITY.AVG_CADENCE + " real, ")
            + (DB.ACTIVITY.META_DATA + " text, ")
            + ("deleted integer not null default 0, ")
            + "nullColumnHack text null"
            + ");";

    private static final String CREATE_TABLE_LOCATION = "create table "
            + DB.LOCATION.TABLE + " ( "
            + ("_id integer primary key autoincrement, ")
            + (DB.LOCATION.ACTIVITY + " integer not null, ")
            + (DB.LOCATION.LAP + " integer not null, ")
            + (DB.LOCATION.TYPE + " integer not null, ")
            + (DB.LOCATION.TIME + " integer not null, ")
            + (DB.LOCATION.LONGITUDE + " real not null, ")
            + (DB.LOCATION.LATITUDE + " real not null, ")
            + (DB.LOCATION.ALTITUDE + " real, ")
            + (DB.LOCATION.HR + " integer, ")
            + (DB.LOCATION.CADENCE + " real, ")
            + (DB.LOCATION.TEMPERATURE + " real, ")
            + (DB.LOCATION.PRESSURE + " real, ")
            + (DB.LOCATION.ELAPSED + " real, ")
            + (DB.LOCATION.DISTANCE + " real, ")
            + (DB.LOCATION.GPS_ALTITUDE + " real, ")
            + (DB.LOCATION.ACCURANCY + " real, ")
            + (DB.LOCATION.SPEED + " real, ")
            + (DB.LOCATION.BEARING + " real, ")
            + (DB.LOCATION.SATELLITES + " integer ")
            + ");";

    private static final String CREATE_TABLE_LAP = "create table "
            + DB.LAP.TABLE + " ( "
            + ("_id integer primary key autoincrement, ")
            + (DB.LAP.ACTIVITY + " integer not null, ")
            + (DB.LAP.LAP + " integer not null, ")
            + (DB.LAP.INTENSITY + " integer not null default 0, ")
            + (DB.LAP.TIME + " integer, ")
            + (DB.LAP.DISTANCE + " real, ")
            + (DB.LAP.PLANNED_TIME + " integer, ")
            + (DB.LAP.PLANNED_DISTANCE + " real, ")
            + (DB.LAP.PLANNED_PACE + " real, ")
            + (DB.LAP.AVG_HR + " integer, ")
            + (DB.LAP.MAX_HR + " integer, ")
            + (DB.LAP.AVG_CADENCE + " real ")
            + ");";

    private static final String CREATE_TABLE_ACCOUNT = "create table "
            + DB.ACCOUNT.TABLE + " ( "
            + ("_id integer primary key autoincrement, ")
            + (DB.ACCOUNT.NAME + " text not null, ")
            + (DB.ACCOUNT.FLAGS + " integer not null default " + DB.ACCOUNT.DEFAULT_FLAGS + ", ")
            + (DB.ACCOUNT.ENABLED + " integer not null default 1,") //Account is not hidden/disabled
            + (DB.ACCOUNT.AUTH_CONFIG + " text, ") //Stored configuration data
            + "UNIQUE (" + DB.ACCOUNT.NAME + ")"
            + ");";

    private static final String CREATE_TABLE_REPORT = "create table "
            + DB.EXPORT.TABLE + " ( "
            + "_id integer primary key autoincrement, "
            + DB.EXPORT.ACTIVITY + " integer not null, "
            + DB.EXPORT.ACCOUNT + " integer not null, "
            + DB.EXPORT.STATUS + " text, "
            + DB.EXPORT.EXTERNAL_ID + " text, "
            + DB.EXPORT.EXTRA + " integer not null default 1"
            + ");";

    private static final String CREATE_TABLE_AUDIO_SCHEMES = "create table "
            + DB.AUDIO_SCHEMES.TABLE + " ( "
            + ("_id integer primary key autoincrement, ")
            + (DB.AUDIO_SCHEMES.NAME + " text not null, ")
            + (DB.AUDIO_SCHEMES.SORT_ORDER + " integer not null, ")
            + ("unique (" + DB.AUDIO_SCHEMES.NAME + ")")
            + ");";

    private static final String CREATE_TABLE_FEED = "create table "
            + DB.FEED.TABLE + " ( "
            + ("_id integer primary key autoincrement, ")
            + (DB.FEED.ACCOUNT_ID + " integer not null, ")
            + (DB.FEED.EXTERNAL_ID + " text, ")
            + (DB.FEED.FEED_TYPE + " integer not null, ")
            + (DB.FEED.FEED_SUBTYPE + " integer, ")
            + (DB.FEED.FEED_TYPE_STRING + " text, ")
            + (DB.FEED.START_TIME + " integer not null, ")
            + (DB.FEED.DURATION + " integer, ")
            + (DB.FEED.DISTANCE + " double, ")
            + (DB.FEED.USER_ID + " text, ")
            + (DB.FEED.USER_FIRST_NAME + " text, ")
            + (DB.FEED.USER_LAST_NAME + " text, ")
            + (DB.FEED.USER_IMAGE_URL + " text, ")
            + (DB.FEED.NOTES + " text, ")
            + (DB.FEED.COMMENTS + " text, ")
            + (DB.FEED.URL + " text, ")
            + (DB.FEED.FLAGS + " text ")
            + ");";

    private static final String CREATE_INDEX_FEED = "create index "
            + "if not exists FEED_START_TIME "
            + (" on " + DB.FEED.TABLE + " (" + DB.FEED.START_TIME
            + ")");

    private static DBHelper sInstance = null;

    private static synchronized DBHelper getHelper(Context context) {
        if (sInstance == null) {
            sInstance = new DBHelper(context.getApplicationContext(), 1);
        }
        return sInstance;
    }

    @Override
    public synchronized void close() {
        if (sInstance != null) {
            // don't close
            return;
        }
        super.close();
    }

    private static SQLiteDatabase sReadableDB = null;
    private static SQLiteDatabase sWritableDB = null;

    public static synchronized SQLiteDatabase getReadableDatabase(Context context) {
        if (sReadableDB == null) {
            sReadableDB =getHelper(context).getReadableDatabase();
        }
        return sReadableDB;
    }

    public static synchronized SQLiteDatabase getWritableDatabase(Context context) {
        if (sWritableDB == null) {
            sWritableDB =getHelper(context).getReadableDatabase();
        }
        return sWritableDB;
    }

    public static synchronized void closeDB(SQLiteDatabase db) {
    }

    private DBHelper(Context context, int a) {
        super(context, DBNAME, null, DBVERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase arg0) {
        arg0.execSQL(CREATE_TABLE_DBINFO);
        arg0.execSQL(CREATE_TABLE_ACTIVITY);
        arg0.execSQL(CREATE_TABLE_LAP);
        arg0.execSQL(CREATE_TABLE_LOCATION);
        arg0.execSQL(CREATE_TABLE_ACCOUNT);
        arg0.execSQL(CREATE_TABLE_REPORT);
        arg0.execSQL(CREATE_TABLE_AUDIO_SCHEMES);
        arg0.execSQL(CREATE_TABLE_FEED);
        arg0.execSQL(CREATE_INDEX_FEED);

        onUpgrade(arg0, 0, DBVERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase arg0, int oldVersion, int newVersion) {
        Log.e(getClass().getName(), "onUpgrade: oldVersion: " + oldVersion + ", newVersion: " + newVersion);

        if (newVersion < oldVersion) {
            throw new java.lang.UnsupportedOperationException(
                    "Downgrade not supported");
        }

        if (oldVersion > 0 && oldVersion < 5 && newVersion >= 5) {
            arg0.execSQL("alter table account add column icon integer");
        }

        if (oldVersion > 0 && oldVersion < 7 && newVersion >= 7) {
            arg0.execSQL(CREATE_TABLE_AUDIO_SCHEMES);
        }

        if (oldVersion > 0 && oldVersion < 16 && newVersion >= 16) {
            echoDo(arg0, "alter table " + DB.LOCATION.TABLE + " add column " + DB.LOCATION.HR
                    + " int");
        }

        //Recreated DBVERSION 31->32
        //if (oldVersion > 0 && oldVersion < 10 && newVersion >= 10) {
        //    recreateAccount(arg0);
        //}

        if (oldVersion > 0 && oldVersion < 17 && newVersion >= 17) {
            arg0.execSQL(CREATE_TABLE_FEED);
            arg0.execSQL(CREATE_INDEX_FEED);
            echoDo(arg0, "update account set " + DB.ACCOUNT.FLAGS + " = " + DB.ACCOUNT.FLAGS
                    + " + " + (1 << DB.ACCOUNT.FLAG_FEED));
        }

        if (oldVersion > 0 && oldVersion < 18 && newVersion >= 18) {
            echoDo(arg0,
                    "update account set " + DB.ACCOUNT.AUTH_CONFIG + " = '{ \"access_token\":\"' || " + DB.ACCOUNT.AUTH_CONFIG + " || '\" }' where " + DB.ACCOUNT.AUTH_CONFIG + " is not null and " + "auth_method" +"='oauth2';");
        }

        if (oldVersion > 0 && oldVersion < 19 && newVersion >= 19) {
            echoDo(arg0, "update account set " + DB.ACCOUNT.FLAGS + " = " + DB.ACCOUNT.FLAGS
                    + " + " + (1 << DB.ACCOUNT.FLAG_LIVE));
        }

        if (oldVersion > 0 && oldVersion < 24 && newVersion >= 24) {
            echoDo(arg0, "alter table " + DB.LAP.TABLE + " add column " + DB.LAP.AVG_HR
                    + " integer");
            echoDo(arg0, "alter table " + DB.LAP.TABLE + " add column " + DB.LAP.MAX_HR
                    + " integer");
            echoDo(arg0, "alter table " + DB.ACTIVITY.TABLE + " add column " + DB.ACTIVITY.AVG_HR
                    + " integer");
            echoDo(arg0, "alter table " + DB.ACTIVITY.TABLE + " add column " + DB.ACTIVITY.MAX_HR
                    + " integer");
        }

        if (oldVersion > 0 && oldVersion < 25 && newVersion >= 25) {
            echoDo(arg0, "alter table " + DB.LAP.TABLE + " add column " + DB.LAP.AVG_CADENCE
                    + " real");
            echoDo(arg0, "alter table " + DB.LOCATION.TABLE + " add column " + DB.LOCATION.CADENCE
                    + " real");
            echoDo(arg0, "alter table " + DB.ACTIVITY.TABLE + " add column "
                    + DB.ACTIVITY.AVG_CADENCE + " real");
        }

        if (oldVersion > 0 && oldVersion < 31 && newVersion >= 31) {
            echoDo(arg0, "alter table " + DB.LOCATION.TABLE + " add column " + DB.LOCATION.TEMPERATURE
                    + " real");
            echoDo(arg0, "alter table " + DB.LOCATION.TABLE + " add column " + DB.LOCATION.PRESSURE
                    + " real");
            echoDo(arg0, "alter table " + DB.LOCATION.TABLE + " add column " + DB.LOCATION.GPS_ALTITUDE
                    + " real");
            echoDo(arg0, "alter table " + DB.LOCATION.TABLE + " add column " + DB.LOCATION.SATELLITES
                    + " integer");
            echoDo(arg0, "alter table " + DB.LOCATION.TABLE + " add column " + DB.LOCATION.ELAPSED
                    + " real");
            echoDo(arg0, "alter table " + DB.LOCATION.TABLE + " add column " + DB.LOCATION.DISTANCE
                    + " real");
            echoDo(arg0, "alter table " + DB.ACTIVITY.TABLE + " add column " + DB.ACTIVITY.META_DATA
                    + " text");
        }

        if (oldVersion > 0 && oldVersion < 32 && newVersion >= 32) {
            //Migrate storage of parameters, FORMAT is removed
            String from[] = { "_id", DB.ACCOUNT.FORMAT, DB.ACCOUNT.AUTH_CONFIG };
            String args[] = { FileSynchronizer.NAME };
            Cursor c = arg0.query(DB.ACCOUNT.TABLE, from,
                    DB.ACCOUNT.NAME + " = ? and " + DB.ACCOUNT.AUTH_CONFIG + " is not null", args,
                    null, null, null);

            if (c.moveToFirst()) {
                ContentValues tmp = DBHelper.get(c);
                //URL was stored in AUTH_CONFIG previously, FORMAT migrated too
                tmp.put(DB.ACCOUNT.URL, tmp.getAsString(DB.ACCOUNT.AUTH_CONFIG));
                String authConfig = FileSynchronizer.contentValuesToAuthConfig(tmp);
                tmp = new ContentValues();
                tmp.put(DB.ACCOUNT.AUTH_CONFIG, authConfig);
                arg0.update(DB.ACCOUNT.TABLE, tmp, DB.ACCOUNT.NAME + " = ?", args);
            }
            c.close();
            recreateAccount(arg0);
        }

        if (oldVersion < 32 && newVersion >= 32) {
            if (oldVersion > 0) {
                arg0.execSQL(CREATE_TABLE_DBINFO);
            }
            ContentValues tmp = new ContentValues();
            tmp.put(DB.DBINFO.ACCOUNT_VERSION, 0);
            tmp.put("_id", 0);
            arg0.insert(DB.DBINFO.TABLE, null, tmp);
        }
    }

    @Override
    public void onOpen(SQLiteDatabase arg0) {
        //Update "database contents"
        //Only changes that can be safely applied backward/forward compatible
        //(other still need to update DBVERSION)

        //Version for ACCOUNT info
        String from[] = { "_id" };
        String args[] = { "1" }; //ACCOUNT VERSION
        Cursor c = arg0.query(DB.DBINFO.TABLE, from,
                DB.DBINFO.ACCOUNT_VERSION + " = ?", args,
                null, null, null);

        if (c.getCount() == 0) {
            insertAccounts(arg0);
            ContentValues tmp = new ContentValues();
            //One row only in the table, so no selection
            tmp.put(DB.DBINFO.ACCOUNT_VERSION, args[0]);
            arg0.update(DB.DBINFO.TABLE, tmp, null, null);
        }
        c.close();
    }

    private static void echoDo(SQLiteDatabase arg0, String str) {
        Log.e("DBHelper", "execSQL(" + str + ")");
        arg0.execSQL(str);
    }

    private void recreateAccount(SQLiteDatabase arg0) {
        StringBuilder newtab = new StringBuilder();
        newtab.append(CREATE_TABLE_ACCOUNT);
        newtab.replace(0,
                ("create table " + DB.ACCOUNT.TABLE).length(),
                "create table " + DB.ACCOUNT.TABLE + "_new");
        String copy =
                "insert into " + DB.ACCOUNT.TABLE + "_new" +
                        "(_id, " +
                        DB.ACCOUNT.NAME + ", " +
                        DB.ACCOUNT.FLAGS + ", " +
                        DB.ACCOUNT.ENABLED + ", " +
                        DB.ACCOUNT.AUTH_CONFIG + ") " +
                        "select " +
                        "_id, " +
                        DB.ACCOUNT.NAME + ", " +
                        DB.ACCOUNT.FLAGS + ", " +
                        DB.ACCOUNT.ENABLED + ", " +
                        DB.ACCOUNT.AUTH_CONFIG + " " +
                        "FROM " + DB.ACCOUNT.TABLE;
        try {
            echoDo(arg0, newtab.toString());
            echoDo(arg0, copy);
            echoDo(arg0, "alter table " + DB.ACCOUNT.TABLE + " rename to " + DB.ACCOUNT.TABLE
                    + "_old");
            echoDo(arg0, "alter table " + DB.ACCOUNT.TABLE + "_new" + " rename to "
                    + DB.ACCOUNT.TABLE);
            echoDo(arg0, "drop table " + DB.ACCOUNT.TABLE + "_old");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void insertAccounts(SQLiteDatabase arg0) {
        //The accounts must exist in the database, but normally the default values are sufficient
        //ENABLED, FLAGS need to be set if ever changed (like disabled or later enabled)
        //"Minor changes" like adding a new syncher can be handled with updating DB.DBINFO.ACCOUNT_VERSION
        insertAccount(arg0, GarminSynchronizer.NAME, 0, -1);
        insertAccount(arg0, RunKeeperSynchronizer.NAME);
        insertAccount(arg0, JoggSESynchronizer.NAME);
        insertAccount(arg0, FunBeatSynchronizer.NAME);
        insertAccount(arg0, MapMyRunSynchronizer.NAME);
        insertAccount(arg0, NikePlusSynchronizer.NAME);
        insertAccount(arg0, EndomondoSynchronizer.NAME);
        insertAccount(arg0, RunningAHEADSynchronizer.NAME);
        insertAccount(arg0, DigifitSynchronizer.NAME);
        insertAccount(arg0, StravaSynchronizer.NAME);
        insertAccount(arg0, RunnerUpLiveSynchronizer.NAME, 0, (int) (1 << DB.ACCOUNT.FLAG_LIVE));
        insertAccount(arg0, FacebookSynchronizer.NAME);
        //insertAccount(arg0, GooglePlusSynchronizer.NAME);
        //DBVERSION 26
        insertAccount(arg0, RuntasticSynchronizer.NAME, 0, -1);
        //DBVERSION 27
        insertAccount(arg0, GoogleFitSynchronizer.NAME, 0, -1);
        //DBVERSION 28
        insertAccount(arg0, RunningFreeOnlineSynchronizer.NAME, 0, -1);
        //DBVERSION 29
        insertAccount(arg0, FileSynchronizer.NAME);
        //DBVERSION 30
        insertAccount(arg0, RunalyzeSynchronizer.NAME);
    }

    private static void insertAccount(SQLiteDatabase arg0, String name) {
        insertAccount(arg0, name, -1, -1);
    }
    
    private static void insertAccount(SQLiteDatabase arg0, String name, int enabled, int flags) {
        ContentValues arg1 = new ContentValues();
        arg1.put(DB.ACCOUNT.NAME, name);
        if (enabled >= 0) {
            arg1.put(DB.ACCOUNT.ENABLED, enabled);
        }
        if (flags >= 0) {
            arg1.put(DB.ACCOUNT.ENABLED, flags);
        }

        //SQLite has no UPSERT command. Optimize for no change.
        long newId = arg0.insertWithOnConflict(DB.ACCOUNT.TABLE, null, arg1, SQLiteDatabase.CONFLICT_IGNORE);
        if (newId == -1 && arg1.size() > 1) {
            //values could be updated
            String arr[] = {
                    arg1.getAsString(DB.ACCOUNT.NAME)
            };
            arg0.update(DB.ACCOUNT.TABLE, arg1, DB.ACCOUNT.NAME + " = ?", arr);
            Log.v("DBhelper", "update: " + arg1);
        }
    }

    public static ContentValues get(Cursor c) {
        if (c.isClosed() || c.isAfterLast() || c.isBeforeFirst())
            return null;
        ContentValues ret = new ContentValues();
        final int cnt = c.getColumnCount();
        for (int i = 0; i < cnt; i++) {
            if (!c.isNull(i)) {
                ret.put(c.getColumnName(i), c.getString(i));
            }
        }
        return ret;
    }

    public static ContentValues[] toArray(Cursor c) {
        ArrayList<ContentValues> list = new ArrayList<ContentValues>();
        if (c.moveToFirst()) {
            do {
                list.add(get(c));
            } while (c.moveToNext());
        }
        return list.toArray(new ContentValues[list.size()]);
    }

    public static void deleteActivity(SQLiteDatabase db, long id) {
        Log.e("DBHelper", "deleting activity: " + id);
        String args[] = {
                Long.toString(id)
        };
        db.delete(DB.EXPORT.TABLE, DB.EXPORT.ACTIVITY + " = ?", args);
        db.delete(DB.LOCATION.TABLE, DB.LOCATION.ACTIVITY + " = ?", args);
        db.delete(DB.LAP.TABLE, DB.LAP.ACTIVITY + " = ?", args);
        db.delete(DB.ACTIVITY.TABLE, "_id = ?", args);
    }

    public static void purgeDeletedActivities(Context ctx, final ProgressDialog dialog,
                                              final Runnable onComplete) {

        final DBHelper mDBHelper = DBHelper.getHelper(ctx);
        final SQLiteDatabase db = mDBHelper.getWritableDatabase();
        String from[] = { "_id" };
        Cursor c = db.query(DB.ACTIVITY.TABLE, from, "deleted <> 0",
                null, null, null, null, null);
        final ArrayList<Long> list = new ArrayList<Long>(10);
        if (c.moveToFirst()) {
            do {
                list.add(c.getLong(0));
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
                        deleteActivity(db, id);
                        dialog.incrementProgressBy(1);
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    db.close();
                    mDBHelper.close();
                    if (onComplete != null)
                        onComplete.run();
                }
            }.execute((long) 2);
        } else {
            db.close();
            mDBHelper.close();
            if (onComplete != null)
                onComplete.run();
        }
    }

    public static int bulkInsert(List<? extends DBEntity> objectList, SQLiteDatabase db) {
        int result = 0;
        for (DBEntity obj : objectList) {
            long id = obj.insert(db);
            if (id != -1) {
                result++;
            }
        }
        return result;
    }

    public static String getDbPath(Context ctx) {
        return ctx.getFilesDir().getPath() + "/../databases/runnerup.db";
    }

    public static void importDatabase(Context ctx, String from) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle("Import runnerup.db from " + from);
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }

        };
        String to = getDbPath(ctx);
        try {
            int cnt = FileUtil.copyFile(to, from);
            builder.setMessage("Copied " + cnt + " bytes");
            builder.setPositiveButton(ctx.getString(R.string.Great), listener);
        } catch (IOException e) {
            builder.setMessage("Exception: " + e.toString());
            builder.setNegativeButton(ctx.getString(R.string.Darn), listener);
        }
        builder.show();
    }
}
