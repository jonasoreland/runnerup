/*
 * Copyright (C) 2016 gerhard.nospam@gmail.com
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

package org.runnerup.export;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.content.ActivityProvider;
import org.runnerup.db.PathSimplifier;
import org.runnerup.export.format.GPX;
import org.runnerup.export.format.TCX;
import org.runnerup.util.FileNameHelper;
import org.runnerup.workout.FileFormats;
import org.runnerup.workout.Sport;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;


public class FileSynchronizer extends DefaultSynchronizer {

    public static final String NAME = "File";

    private long id = 0;
    private Context mContext;
    private String mPath;
    private FileFormats mFormat;
    private PathSimplifier simplifier;

    private FileSynchronizer() {}

    FileSynchronizer(Context context, PathSimplifier simplifier) {
        this();
        this.mContext = context;
        this.simplifier = simplifier;
    }

    @Override
    public long getId() {
        return id;
    }

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getPublicUrl() {
        return "file://"
                + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                // Only for display
                ? Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getPath() + File.separator
                : "")
                + mPath;
    }

    @DrawableRes
    @Override
    public int getIconId() {return R.drawable.service_file;}

    @ColorRes
    @Override
    public int getColorId() {return R.color.colorPrimary;}

    static public String contentValuesToAuthConfig(ContentValues config) {
        FileSynchronizer f = new FileSynchronizer();
        f.mPath = config.getAsString(DB.ACCOUNT.URL);
        return f.getAuthConfig();
    }

    @Override
    public void init(ContentValues config) {
        String authConfig = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
        if (authConfig != null) {
            try {
                mFormat = new FileFormats(config.getAsString(DB.ACCOUNT.FORMAT));
                JSONObject tmp = new JSONObject(authConfig);
                //noinspection ConstantConditions
                mPath = tmp.optString(DB.ACCOUNT.URL, null);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mPath.startsWith(File.separator)) {
                    // Migrate to use scooped storage
                    mPath = mPath.substring(mPath.lastIndexOf(File.separator));
                }
            } catch (JSONException e) {
                Log.w(getName(), "init: Dropping config due to failure to parse json from " + authConfig + ", " + e);
            }
        }
        id = config.getAsLong("_id");
    }

    @NonNull
    @Override
    public String getAuthConfig() {
        JSONObject tmp = new JSONObject();
        if (isConfigured()) {
            try {
                tmp.put(DB.ACCOUNT.URL, mPath);
            } catch (JSONException e) {
                Log.w(getName(), "getAuthConfig: Failure to create json for " + mPath + ", " + e);
            }
        }
        return tmp.toString();
    }

    @Override
    public boolean isConfigured() {
        return !TextUtils.isEmpty(mPath);
    }

    @Override
    public void reset() {
        mPath = null;
    }

    @NonNull
    @Override
    public Status connect() {
        Status s = Status.NEED_AUTH;
        s.authMethod = AuthMethod.FILEPERMISSION;
        if (TextUtils.isEmpty(mPath)) {
            return s;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            s = Status.OK;
            return s;
        }
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            return s;
        }

        try {
            File dstDir = new File(mPath);
            //noinspection ResultOfMethodCallIgnored
            dstDir.mkdirs();
            if (dstDir.isDirectory()) {
                s = Status.OK;
            }
        } catch (SecurityException e) {
            //Status is NEED_AUTH
        }

        return s;
    }

    @NonNull
    @Override
    public Status upload(SQLiteDatabase db, final long mID) {
        Status s = connect();
        s.activityId = mID;
        if (s != Status.OK) {
            return s;
        }

        Sport sport = Sport.RUNNING;
        long startTime = 0;
        try {
            String[] columns = {
                    Constants.DB.ACTIVITY.SPORT,
                    DB.ACTIVITY.START_TIME,
            };
            try (Cursor c = db.query(DB.ACTIVITY.TABLE, columns, "_id = " + mID,
                    null, null, null, null)) {
                if (c.moveToFirst()) {
                    sport = Sport.valueOf(c.getInt(0));
                    startTime = c.getLong(1);
                }
            }

            String fileBase = FileNameHelper.getExportFileName(startTime, sport.TapiriikType());
            if (mFormat.contains(FileFormats.TCX)) {
                OutputStream out = getOutputStream(fileBase + FileFormats.TCX.getValue(), ActivityProvider.TCX_MIME);
                if (out == null) {
                    s = Status.ERROR;
                } else {
                    TCX tcx = new TCX(db, simplifier);
                    tcx.export(mID, new OutputStreamWriter(out));
                }
            }
            if (mFormat.contains(FileFormats.GPX)) {
                OutputStream out = getOutputStream(fileBase + FileFormats.GPX.getValue(), ActivityProvider.GPX_MIME);
                if (out == null) {
                    s = Status.ERROR;
                } else {
                    GPX gpx = new GPX(db, true, true, simplifier);
                    gpx.export(mID, new OutputStreamWriter(out));
                }
            }
        } catch (IOException e) {
            s = Status.ERROR;
        }
        return s;
    }

    private OutputStream getOutputStream(String fileName, String mimeType) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // mPath must be a relative location
            final String relativeLocation = Environment.DIRECTORY_DOCUMENTS + File.separator + mPath;

            final ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativeLocation);
            // TODO int mediaType = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ? 0 : MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT;
            contentValues.put(MediaStore.Files.FileColumns.MEDIA_TYPE, 0);
            contentValues.put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType);

            final ContentResolver resolver = mContext.getApplicationContext().getContentResolver();

            final Uri contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri uri = resolver.insert(contentUri, contentValues);
            if (uri == null) {
                Log.w(getName(), "No uri: " + contentUri + " " + fileName);
                return null;
            }
            return resolver.openOutputStream(uri);
        } else {
            String path = new File(mPath).getAbsolutePath() + File.separator + fileName;
            if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.w(getName(), "No permission to write to: " + path);
                return null;
            }
            File file = new File(path);
            return new BufferedOutputStream(new FileOutputStream(file));
        }
    }

    @Override
    public boolean checkSupport(Feature f) {
        switch (f) {
            case UPLOAD:
            case FILE_FORMAT:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void logout() {
    }
}
