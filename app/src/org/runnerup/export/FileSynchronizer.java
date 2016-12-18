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

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.text.TextUtils;

import org.runnerup.common.util.Constants.DB;
import org.runnerup.export.format.GPX;
import org.runnerup.export.format.TCX;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Locale;

@TargetApi(Build.VERSION_CODES.FROYO)
public class FileSynchronizer extends DefaultSynchronizer {

    public static final String NAME = "File";

    private long id = 0;
    private String mPath = null;
    
    FileSynchronizer() {
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(ContentValues config) {
        //Note: config contains a subset of account, primarily AUTH_CONFIG
        //Reuse AUTH_CONFIG to communicate with SyncManager to not change structure too much
        //path is also in URL (used for display), path is needed in connect()
        mPath = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
        id = config.getAsLong("_id");
    }

    @Override
    public String getAuthConfig() {
        return mPath;
    }

    @Override
    public boolean isConfigured() {
        return !TextUtils.isEmpty(mPath);
    }

    @Override
    public void reset() {
        mPath = null;
    }

    @Override
    public Status connect() {
        Status s = Status.NEED_AUTH;
        s.authMethod = AuthMethod.FILEPERMISSION;
        if (TextUtils.isEmpty(mPath))
            return s;
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

    @Override
    public Status upload(SQLiteDatabase db, final long mID) {
        Status s = Status.ERROR;
        s.activityId = mID;
        if ((s = connect()) != Status.OK) {
            return s;
        }
        ContentValues config = SyncManager.loadConfig(db, this.getName());
        String format = config.getAsString(DB.ACCOUNT.FORMAT);

        try {
            String fileBase = new File(mPath).getAbsolutePath() + File.separator +
            String.format(Locale.getDefault(), "RunnerUp_%04d.", mID);
            if (format.contains("tcx")) {
                TCX tcx = new TCX(db);
                File file = new File(fileBase + "tcx");
                OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
                tcx.export(mID, new OutputStreamWriter(out));
            }
            if (format.contains("gpx")) {
                GPX gpx = new GPX(db, true, true);
                File file = new File(fileBase + "gpx");
                OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
                gpx.export(mID, new OutputStreamWriter(out));
            }
            s = Status.OK;
        } catch (IOException e) {
            //Status is ERROR
        }
        return s;
    }

    @Override
    public boolean checkSupport(Feature f) {
        switch (f) {
            case UPLOAD:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void logout() {
    }
}
