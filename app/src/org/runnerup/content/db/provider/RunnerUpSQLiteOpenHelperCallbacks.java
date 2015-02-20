package org.runnerup.content.db.provider;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.content.db.provider.account.AccountColumns;
import org.runnerup.content.db.provider.account.AccountContentValues;
import org.runnerup.content.db.provider.account.AccountCursor;
import org.runnerup.content.db.provider.account.AccountSelection;
import org.runnerup.content.db.provider.activity.ActivityColumns;
import org.runnerup.content.db.provider.lap.LapColumns;
import org.runnerup.content.db.provider.location.LocationColumns;
import org.runnerup.export.DigifitUploader;
import org.runnerup.export.Endomondo;
import org.runnerup.export.Facebook;
import org.runnerup.export.FunBeatUploader;
import org.runnerup.export.GarminUploader;
import org.runnerup.export.GoogleFitUploader;
import org.runnerup.export.GooglePlus;
import org.runnerup.export.JoggSE;
import org.runnerup.export.MapMyRunUploader;
import org.runnerup.export.NikePlus;
import org.runnerup.export.RunKeeperUploader;
import org.runnerup.export.RunnerUpLive;
import org.runnerup.export.RunningAHEAD;
import org.runnerup.export.RuntasticUploader;
import org.runnerup.export.Strava;

import java.util.ArrayList;
import java.util.List;

/**
 * Implement your custom database creation or upgrade code here.
 *
 * This file will not be overwritten if you re-run the content provider generator.
 */
public class RunnerUpSQLiteOpenHelperCallbacks {
    private static final String TAG = RunnerUpSQLiteOpenHelperCallbacks.class.getSimpleName();

    public void onOpen(final Context context, final SQLiteDatabase db) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onOpen");
        // Insert your db open code here.
    }

    public void onPreCreate(final Context context, final SQLiteDatabase db) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onPreCreate");
        // Insert your db creation code here. This is called before your tables are created.
    }

    public void onPostCreate(final Context context, final SQLiteDatabase db) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onPostCreate");
        // Insert your db creation code here. This is called after your tables are created.
    }

    public void onUpgrade(final Context context, final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        if (BuildConfig.DEBUG)
            Log.d(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
        // Insert your upgrading code here.

        System.err.println("onUpgrade: oldVersion: " + oldVersion + ", newVersion: " + newVersion);

        if (newVersion < oldVersion) {
            throw new java.lang.UnsupportedOperationException(
                    "Downgrade not supported");
        }

        if (oldVersion > 0 && oldVersion < 5 && newVersion >= 5) {
            db.execSQL("ALTER TABLE " + AccountColumns.TABLE_NAME + " ADD COLUMN " + AccountColumns.ICON + " INTEGER");
        }

        if (oldVersion > 0 && oldVersion < 7 && newVersion >= 7) {
            db.execSQL(RunnerUpSQLiteOpenHelper.SQL_CREATE_TABLE_AUDIOSCHEMES);
        }

        if (oldVersion > 0 && oldVersion < 16 && newVersion >= 16) {
            echoDo(db, "ALTER TABLE " + LocationColumns.TABLE_NAME + " ADD COLUMN " + LocationColumns.HR + " INTEGER");
        }

        if (oldVersion > 0 && oldVersion < 10 && newVersion >= 10) {
            recreateAccount(context, db);
        }

        if (oldVersion > 0 && oldVersion < 17 && newVersion >= 17) {
            db.execSQL(RunnerUpSQLiteOpenHelper.SQL_CREATE_TABLE_FEED);
            db.execSQL(RunnerUpSQLiteOpenHelper.SQL_CREATE_INDEX_FEED_START_TIME);
            echoDo(db, "UPDATE " + AccountColumns.TABLE_NAME + " SET " + AccountColumns.FLAGS + " = " + AccountColumns.FLAGS
                    + " + " + (1 << Constants.DB.ACCOUNT.FLAG_FEED));
        }

        if (oldVersion > 0 && oldVersion < 18 && newVersion >= 18) {
            echoDo(db,
                    "UPDATE " + AccountColumns.TABLE_NAME + " SET " + AccountColumns.AUTH_CONFIG + "= '{ \"access_token\":\"' || auth_config || '\" }' where" + AccountColumns.AUTH_CONFIG + " IS NOT NULL AND " + AccountColumns.AUTH_METHOD + " = 'oauth2';");
        }

        if (oldVersion > 0 && oldVersion < 19 && newVersion >= 19) {
            echoDo(db, "UPDATE " + AccountColumns.TABLE_NAME + " SET " + AccountColumns.FLAGS + " = " + AccountColumns.FLAGS
                    + " + " + (1 << Constants.DB.ACCOUNT.FLAG_LIVE));
        }

        if (oldVersion > 0 && oldVersion < 24 && newVersion >= 24) {
            echoDo(db, "ALTER TABLE " + LapColumns.TABLE_NAME + " ADD COLUMN " + LapColumns.AVG_HR
                    + " INTEGER");
            echoDo(db, "ALTER TABLE " + LapColumns.TABLE_NAME + " ADD COLUMN " + LapColumns.MAX_HR
                    + " INTEGER");
            echoDo(db, "ALTER TABLE " + ActivityColumns.TABLE_NAME + " ADD COLUMN " + ActivityColumns.AVG_HR
                    + " INTEGER");
            echoDo(db, "ALTER TABLE " + Constants.DB.ACTIVITY.TABLE + " ADD COLUMN " + ActivityColumns.MAX_HR
                    + " INTEGER");
        }

        if (oldVersion > 0 && oldVersion < 25 && newVersion >= 25) {
            echoDo(db, "ALTER TABLE " + LapColumns.TABLE_NAME + " ADD COLUMN " + LapColumns.AVG_CADENCE
                    + " INTEGER");
            echoDo(db, "ALTER TABLE " + LocationColumns.TABLE_NAME + " ADD COLUMN " + LocationColumns.CADENCE
                    + " INTEGER");
            echoDo(db, "ALTER TABLE " + ActivityColumns.TABLE_NAME + " ADD COLUMN " + ActivityColumns.AVG_CADENCE + " INTEGER");
        }

        insertAccounts(context, db, newVersion);

    }

    private static void echoDo(SQLiteDatabase db, String str) {
        System.err.println("execSQL(" + str + ")");
        db.execSQL(str);
    }

    private void recreateAccount(Context context, SQLiteDatabase db) {
        AccountCursor c = null;
        try {
            AccountSelection where = new AccountSelection();
            c = where.query(context.getContentResolver());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (c != null)
            c.close();

        StringBuilder newtab = new StringBuilder();
        newtab.append(RunnerUpSQLiteOpenHelper.SQL_CREATE_TABLE_ACCOUNT);
        newtab.replace(0,
                ("CREATE TABLE " + AccountColumns.TABLE_NAME).length(),
                "CREATE TABLE " + AccountColumns.TABLE_NAME + "_new");
        String copy =
                "INSERT INTO " + AccountColumns.TABLE_NAME + "_new" +
                        "(_id, " +
                        AccountColumns.NAME + ", " +
                        AccountColumns.URL + ", " +
                        AccountColumns.DESCRIPTION + ", " +
                        AccountColumns.FORMAT + ", " +
                        AccountColumns.FLAGS + ", " +
                        AccountColumns.ENABLED + ", " +
                        AccountColumns.AUTH_METHOD + ", " +
                        AccountColumns.AUTH_CONFIG + ") " +
                        "SELECT " +
                        "_id, " +
                        AccountColumns.NAME + ", " +
                        AccountColumns.URL + ", " +
                        AccountColumns.DESCRIPTION + ", " +
                        AccountColumns.FORMAT + ", " +
                        AccountColumns.FLAGS + ", " +
                        AccountColumns.ENABLED + ", " +
                        AccountColumns.AUTH_METHOD + ", " +
                        AccountColumns.AUTH_CONFIG + " " +
                        "FROM " + AccountColumns.TABLE_NAME;
        try {
            echoDo(db, newtab.toString());
            echoDo(db, copy);
            echoDo(db, "ALTER TABLE " + AccountColumns.TABLE_NAME + " RENAME TO " + AccountColumns.TABLE_NAME
                    + "_old");
            echoDo(db, "ALTER TABLE " + AccountColumns.TABLE_NAME + "_new" + " RENAME TO "
                    + Constants.DB.ACCOUNT.TABLE);
            echoDo(db, "DROP TABLE " + AccountColumns.TABLE_NAME + "_old");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void insertAccounts(Context context, SQLiteDatabase db, int version) {
        boolean yet = true;
        boolean notyet = false;
        List<ContentValues> accounts = new ArrayList<ContentValues>();

        if (notyet) {
            /**
             * just removing warning from notyet
             */
        }

        if (yet) {
            AccountContentValues account = new AccountContentValues();
            account.putName(GarminUploader.NAME);
            account.putFormat("tcx");
            account.putAuthMethod("post");
            account.putIcon(R.drawable.a0_garminlogo);
            account.putUrl("http://connect.garmin.com/");
            accounts.add(account.values());
        }

        if (yet) {
            AccountContentValues account = new AccountContentValues();
            account.putName(RunKeeperUploader.NAME);
            account.putFormat("runkeeper");
            account.putAuthMethod("oauth2");
            account.putIcon(R.drawable.a1_rklogo);
            account.putUrl("http://runkeeper.com/");
            accounts.add(account.values());
        }

        if (yet) {
            AccountContentValues account = new AccountContentValues();
            account.putName(JoggSE.NAME);
            account.putFormat("gpx");
            account.putAuthMethod("post");
            account.putIcon(R.drawable.a5_jogg);
            account.putUrl("http://jogg.se/");
            accounts.add(account.values());
        }

        if (yet) {
            AccountContentValues account = new AccountContentValues();
            account.putName(FunBeatUploader.NAME);
            account.putFormat("tcx");
            account.putAuthMethod("post");
            account.putIcon(R.drawable.a2_funbeatlogo);
            account.putUrl("http://www.funbeat.se/");
            accounts.add(account.values());
        }

        if (yet) {
            AccountContentValues account = new AccountContentValues();
            account.putName(MapMyRunUploader.NAME);
            account.putFormat("tcx");
            account.putAuthMethod("post");
            account.putIcon(R.drawable.a3_mapmyrun_logo);
            account.putUrl("http://www.mapmyrun.com/");
            accounts.add(account.values());
        }

        if (yet) {
            AccountContentValues account = new AccountContentValues();
            account.putName(NikePlus.NAME);
            account.putFormat("nikeplus,gpx");
            account.putAuthMethod("post");
            account.putIcon(R.drawable.a4_nikeplus);
            account.putUrl("http://nikeplus.nike.com");
            accounts.add(account.values());
        }

        if (yet) {
            AccountContentValues account = new AccountContentValues();
            account.putName(Endomondo.NAME);
            account.putFormat("endomondotrack");
            account.putAuthMethod("post");
            account.putIcon(R.drawable.a6_endomondo);
            account.putUrl("http://www.endomondo.com");
            accounts.add(account.values());
        }

        if (yet) {
            AccountContentValues account = new AccountContentValues();
            account.putName(RunningAHEAD.NAME);
            account.putFormat("tcx");
            account.putAuthMethod("oauth2");
            account.putIcon(R.drawable.a7_runningahead);
            account.putUrl("http://www.runningahead.com");
            accounts.add(account.values());
        }

        if (yet) {
            AccountContentValues account = new AccountContentValues();
            account.putName(DigifitUploader.NAME);
            account.putFormat("tcx");
            account.putAuthMethod("post");
            account.putIcon(R.drawable.a9_digifit);
            account.putUrl("http://www.digifit.com");
            accounts.add(account.values());
        }

        if (yet) {
            AccountContentValues account = new AccountContentValues();
            account.putName(Strava.NAME);
            account.putFormat("tcx");
            account.putAuthMethod("post");
            account.putIcon(R.drawable.a10_strava);
            account.putUrl("http://www.strava.com");
            accounts.add(account.values());
        }

        if (yet) {
            AccountContentValues account = new AccountContentValues();
            account.putName(RunnerUpLive.NAME);
            account.putFormat("");
            account.putAuthMethod("none");
            account.putIcon(R.drawable.a8_runneruplive);
            account.putUrl("http://weide.devsparkles.se/Demo/Map");
            account.putDefaultSend((int) (1 << Constants.DB.ACCOUNT.FLAG_LIVE));
            accounts.add(account.values());
        }

        if (yet) {
            AccountContentValues account = new AccountContentValues();
            account.putName(Facebook.NAME);
            account.putFormat("");
            account.putAuthMethod("oauth2");
            account.putIcon(R.drawable.a11_facebook);
            account.putUrl("http://www.facebook.com");
            accounts.add(account.values());
        }

        if (notyet) {
            AccountContentValues account = new AccountContentValues();
            account.putName(GooglePlus.NAME);
            account.putFormat("");
            account.putAuthMethod("oauth2");
            account.putIcon(R.drawable.a12_googleplus);
            account.putUrl("https://plus.google.com");
            accounts.add(account.values());
        }

        if (version >= 26) {
            AccountContentValues account = new AccountContentValues();
            account.putName(RuntasticUploader.NAME);
            account.putFormat("tcx");
            account.putAuthMethod("post");
            account.putIcon(R.drawable.a13_runtastic);
            account.putUrl("http://www.runtastic.com");
            accounts.add(account.values());
        }

        if (version >= 27) {
            AccountContentValues account = new AccountContentValues();
            account.putName(GoogleFitUploader.NAME);
            account.putFormat("");
            account.putAuthMethod("oauth2");
            account.putIcon(R.drawable.a14_googlefit);
            account.putUrl("https://fit.google.com");
            accounts.add(account.values());
        }

        if (accounts.size() > 0) {
            ContentValues[] asArray = new ContentValues[accounts.size()];
            context.getContentResolver().bulkInsert(AccountColumns.CONTENT_URI, accounts.toArray(asArray));
        }

    }
}
