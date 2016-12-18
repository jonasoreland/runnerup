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

package org.runnerup.content;

import android.annotation.TargetApi;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;

import org.runnerup.BuildConfig;
import org.runnerup.db.DBHelper;
import org.runnerup.export.format.FacebookCourse;
import org.runnerup.export.format.GPX;
import org.runnerup.export.format.GoogleStaticMap;
import org.runnerup.export.format.NikeXML;
import org.runnerup.export.format.RunKeeper;
import org.runnerup.export.format.TCX;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

@TargetApi(Build.VERSION_CODES.FROYO)
public class ActivityProvider extends ContentProvider {

    // The authority is the symbolic name for the provider class
    public static final String AUTHORITY = "org.runnerup.activity.provider";
    @SuppressWarnings("WeakerAccess")
    public static final String GPX_MIME = "application/gpx+xml";
    @SuppressWarnings("WeakerAccess")
    public static final String TCX_MIME = "application/vnd.garmin.tcx+xml";
    @SuppressWarnings("WeakerAccess")
    public static final String NIKE_MIME = "application/nike+xml";
    @SuppressWarnings("WeakerAccess")
    public static final String MAPS_MIME = "application/maps";
    @SuppressWarnings("WeakerAccess")
    public static final String FACEBOOK_COURSE_MIME = "application/facebook.course";
    //public static final String RUNKEEPER_MIME = "application/runkeeper+xml";

    // UriMatcher used to match against incoming requests
    @SuppressWarnings("WeakerAccess")
    static final int GPX = 1;
    @SuppressWarnings("WeakerAccess")
    static final int TCX = 2;
    @SuppressWarnings("WeakerAccess")
    static final int NIKE = 3;
    @SuppressWarnings("WeakerAccess")
    static final int MAPS = 4;
    @SuppressWarnings("WeakerAccess")
    static final int FACEBOOK_COURSE = 5;
    @SuppressWarnings("WeakerAccess")
    static final int RUNKEEPER = 6;
    private UriMatcher uriMatcher;

    @Override
    public boolean onCreate() {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTHORITY, "gpx/#/*", GPX);
        uriMatcher.addURI(AUTHORITY, "tcx/#/*", TCX);
        uriMatcher.addURI(AUTHORITY, "nike+xml/#/*", NIKE);
        uriMatcher.addURI(AUTHORITY, "maps/#/*", MAPS);
        uriMatcher.addURI(AUTHORITY, "facebook.course/#/*", FACEBOOK_COURSE);
        uriMatcher.addURI(AUTHORITY, "runkeeper/#/*", RUNKEEPER);
        return true;
    }

    private Pair<File, OutputStream> openCacheFile(String name) {
        for (int i = 0; i < 3; i++) {
            try {
                Context ctx = getContext();
                if (BuildConfig.DEBUG && ctx == null) { throw new AssertionError(); }
                //noinspection UnusedAssignment
                File path = null;
                switch (i) {
                    case 0:
                    default:
                        path = ctx.getExternalCacheDir();
                        break;
                    case 1:
                        path = ctx.getExternalFilesDir("tcx");
                        break;
                    case 2:
                        path = ctx.getCacheDir();
                        break;
                }
                @SuppressWarnings("ConstantConditions") final File file = new File(path.getAbsolutePath() + File.separator + name);
                final OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
                Log.e(getClass().getName(), Integer.toString(i) + ": putting cache file in: "
                        + file.getAbsolutePath());
                //noinspection Convert2Diamond
                return new Pair<File, OutputStream>(file, out);
            } catch (IOException | NullPointerException ignored) {
            }
        }

        return null;
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException {

        final int res = uriMatcher.match(uri);
        Log.e(getClass().getName(), "match(" + uri.toString() + "): " + res);
        switch (res) {
            case GPX:
            case TCX:
            case NIKE:
            case MAPS:
            case FACEBOOK_COURSE:
            case RUNKEEPER:
                final List<String> list = uri.getPathSegments();
                final String id = list.get(list.size() - 2);
                final long activityId = Long.parseLong(id);
                final String parcelFile = "activity." + list.get(list.size() - 3);
                final Pair<File, OutputStream> out = openCacheFile(parcelFile);
                if (out == null) {
                    Log.e(getClass().getName(), "Failed to open cacheFile(" + parcelFile + ")");
                    return null;
                }

                Log.e(getClass().getName(), "activity: " + activityId + ", file: "
                        + out.first.getAbsolutePath());
                SQLiteDatabase mDB = DBHelper.getReadableDatabase(getContext());
                try {
                    if (res == TCX) {
                        TCX tcx = new TCX(mDB);
                        tcx.export(activityId, new OutputStreamWriter(out.second));
                        Log.e(getClass().getName(), "export tcx");
                    } else if (res == GPX) {
                        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());
                        //The data must exist if log, use the log option as a possibility to "deactivate" too
                        boolean enabled = prefs.getBoolean(this.getContext().getString(org.runnerup.R.string.pref_log_gpx_accuracy), false);
                        GPX gpx = new GPX(mDB, true, enabled);
                        gpx.export(activityId, new OutputStreamWriter(out.second));
                        Log.e(getClass().getName(), "export gpx");
                    } else if (res == NIKE) {
                        NikeXML xml = new NikeXML(mDB);
                        xml.export(activityId, new OutputStreamWriter(out.second));
                    } else if (res == MAPS) {
                        GoogleStaticMap map = new GoogleStaticMap(mDB);
                        String str = map.export(activityId, 2000);
                        out.second.write(str.getBytes());
                    } else if (res == FACEBOOK_COURSE) {
                        FacebookCourse map = new FacebookCourse(getContext(), mDB);
                        final boolean includeMap = true;
                        String str = map.export(activityId, includeMap, null).toString();
                        out.second.write(str.getBytes());
                    } else {
                       //noinspection ConstantConditions
                       if (res == RUNKEEPER) {
                            RunKeeper map = new RunKeeper(mDB);
                            map.export(activityId, new OutputStreamWriter(out.second));
                        }
                    }
                    out.second.flush();
                    out.second.close();
                    Log.e(getClass().getName(), "wrote " + out.first.length() + " bytes...");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                DBHelper.closeDB(mDB);

                //noinspection UnnecessaryLocalVariable
                ParcelFileDescriptor pfd = ParcelFileDescriptor.open(out.first,
                        ParcelFileDescriptor.MODE_READ_ONLY);
                return pfd;
        }

        throw new FileNotFoundException("Unsupported uri: " + uri.toString());
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues contentvalues, String s,
                      String[] as) {
        return 0;
    }

    @Override
    public int delete(@NonNull Uri uri, String s, String[] as) {
        return 0;
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues contentvalues) {
        return null;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        switch (uriMatcher.match(uri)) {
            case GPX:
                return GPX_MIME;
            case TCX:
                return TCX_MIME;
            case NIKE:
                return NIKE_MIME;
            case MAPS:
                return MAPS_MIME;
            case FACEBOOK_COURSE:
                return FACEBOOK_COURSE_MIME;
        }
        return null;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String s, String[] as1,
                        String s1) {
        return null;
    }
}
