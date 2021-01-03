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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import org.runnerup.BuildConfig;
import org.runnerup.db.DBHelper;
import org.runnerup.db.PathSimplifier;
import org.runnerup.export.format.GPX;
import org.runnerup.export.format.TCX;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Objects;


public class ActivityProvider extends ContentProvider {

    // The authority is the symbolic name for the provider class
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".activity.provider";
    @SuppressWarnings("WeakerAccess")
    public static final String GPX_MIME = "application/gpx+xml";
    @SuppressWarnings("WeakerAccess")
    public static final String TCX_MIME = "application/vnd.garmin.tcx+xml";

    // UriMatcher used to match against incoming requests
    @SuppressWarnings("WeakerAccess")
    static final int GPX = 1;
    @SuppressWarnings("WeakerAccess")
    static final int TCX = 2;
    private UriMatcher uriMatcher;

    @Override
    public boolean onCreate() {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTHORITY, "gpx/#/*", GPX);
        uriMatcher.addURI(AUTHORITY, "tcx/#/*", TCX);
        return true;
    }

    private Pair<File, OutputStream> openCacheFile(String name) {
        for (int i = 0; i < 3; i++) {
            try {
                Context ctx = getContext();
                if (BuildConfig.DEBUG && ctx == null) { throw new AssertionError(); }
                File path;
                switch (i) {
                    case 0:
                    default:
                        path = Objects.requireNonNull(ctx).getExternalCacheDir();
                        break;
                    case 1:
                        path = Objects.requireNonNull(ctx).getExternalFilesDir("tcx");
                        break;
                    case 2:
                        path = Objects.requireNonNull(ctx).getCacheDir();
                        break;
                }
                @SuppressWarnings("ConstantConditions") final File file = new File(path.getAbsolutePath() + File.separator + name);
                final OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
                Log.e(getClass().getName(), i + ": putting cache file in: "
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

                PathSimplifier simplifier = PathSimplifier.getPathSimplifierForExport(getContext());

                try {
                    switch (res) {
                        case TCX:
                            TCX tcx = new TCX(mDB, simplifier);
                            tcx.export(activityId, new OutputStreamWriter(out.second));
                            Log.e(getClass().getName(), "export tcx");
                            break;
                        case GPX:
                            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());
                            //The data must exist if log, use the log option as a possibility to "deactivate" too
                            boolean extraData = prefs.getBoolean(this.getContext().getString(org.runnerup.R.string.pref_log_gpx_accuracy), false);
                            GPX gpx = new GPX(mDB, true, extraData, simplifier);
                            gpx.export(activityId, new OutputStreamWriter(out.second));
                            Log.e(getClass().getName(), "export gpx");
                            break;
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
        }
        return null;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String s, String[] as1,
                        String s1) {
        return null;
    }
}
