/*
 * Copyright (C) 2012 jonas.oreland@gmail.com
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

import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.export.util.FormValues;
import org.runnerup.export.util.SyncHelper;
import org.runnerup.feed.FeedList.FeedUpdater;
import org.runnerup.util.SyncActivityItem;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public abstract class DefaultSynchronizer implements Synchronizer {

    final Set<String> cookies = new HashSet<>();
    final FormValues formValues = new FormValues();

    DefaultSynchronizer() {
        super();
        logout();
    }

    @Override
    public long getId() {
        return 0;
    }

    @NonNull
    @Override
    public String getName() {
        return null;
    }

    @DrawableRes
    @Override
    public int getIconId() {
        //0 is used if the resource id cannot be found
        return 0;
    }

    @ColorRes
    @Override
    public int getColorId() {
        return R.color.serviceDefault;
    }

    @Override
    public void init(ContentValues config) {
        //Note that only auth_config can be expected here
        //Other config can be retrieved from db in Upload()
    }

    @NonNull
    @Override
    public abstract String getAuthConfig();

    /**
     * Must be implemented for AuthMethod.OAUTH2
     * @param a
     * @return
     */
    @NonNull
    @Override
    public Intent getAuthIntent(AppCompatActivity a) {
        Log.e(getName(), "getAuthIntent: getAuthIntent must be implemented for OAUTH2");
        return new Intent();
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public void reset() {}

    // Below are default empty methods from Synchronizer
    @NonNull
    @Override
    public Status connect() {
        return Status.OK;
    }

    @NonNull
    public Status getAuthResult(int resultCode, Intent data) {
        return Status.OK;
    }

    @NonNull
    public Status upload(SQLiteDatabase db, long mID) {
        Status s = Status.ERROR;
        s.activityId = mID;
        return s;
    }

    @NonNull
    public Status getExternalId(SQLiteDatabase db, Status uploadStatus) {
        return Status.ERROR;
    }

    public boolean checkSupport(Synchronizer.Feature f) {
        return false;
    }

    @NonNull
    public Status listWorkouts(List<Pair<String, String>> list) {
        return Status.OK;
    }

    public void downloadWorkout(File dst, String key) throws Exception {
    }

    @NonNull
    @Override
    public Status listActivities(List<SyncActivityItem> list) {
        return Status.INCORRECT_USAGE;
    }

    @NonNull
    @Override
    public final Status download(SQLiteDatabase db, SyncActivityItem item) {
        return persistActivity(db, download(item));
    }

    ActivityEntity download(SyncActivityItem item) {
        Log.e(Constants.LOG, "No download method implemented for the synchronizer " + getName());
        return null;
    }

    private Status persistActivity(SQLiteDatabase db, ActivityEntity activity) {
        //no activity at all means something went wrong
        if (activity == null) {
            return Status.ERROR;
        }
        //manual activity need to have at least this information
        if (activity.getSport() == null || activity.getStartTime() == null || activity.getTime() == null || activity.getDistance() == null) {
            return Status.ERROR;
        }

        db.beginTransaction();
        if (activity.insert(db) == SyncManager.ERROR_ACTIVITY_ID) {
            db.endTransaction();
            return Status.ERROR;
        }

        //update with activity id
        activity.putPoints(new ArrayList<>(activity.getLocationPoints()));
        // insert location and end transaction unsuccessfully
        if (DBHelper.bulkInsert(activity.getLocationPoints(), db) != activity.getLocationPoints().size()) {
            db.endTransaction();
            return Status.ERROR;
        }

        //update with activity id
        activity.putLaps(new ArrayList<>(activity.getLaps()));
        // insert all lap objects
        if (DBHelper.bulkInsert(activity.getLaps(), db) != activity.getLaps().size()) {
            db.endTransaction();
            return Status.ERROR;
        }
        db.setTransactionSuccessful();
        db.endTransaction();

        Status s = Status.OK;
        s.activityId = activity.getId();
        return s;
    }

    public void logout() {
        clearCookies();
        formValues.clear();
    }

    @NonNull
    public Status getFeed(FeedUpdater feedUpdater) {
        return Status.OK;
    }

    @Override

    @NonNull
    public Status refreshToken() {
        return Status.ERROR;
    }

    void addCookies(HttpURLConnection conn) {
        boolean first = true;
        StringBuilder buf = new StringBuilder();
        for (String cookie : cookies) {
            if (!first)
                buf.append("; ")
                        .append(cookie.split(";", 2)[0]);
            first = false;
        }
        conn.addRequestProperty("Cookie", buf.toString());
    }

    void getCookies(HttpURLConnection conn) {
        List<String> connCookies = conn.getHeaderFields().get("Set-Cookie");
        /*
         * work-around for weird android 2.2 bug ref
         * http://stackoverflow.com/questions
         * /9134657/nullpointer-exception-with-
         * cookie-on-android-2-2-works-fine-on-2-3-and-above
         */
        if (connCookies == null)
            connCookies = conn.getHeaderFields().get("set-cookie");

        if (connCookies != null) {
            cookies.addAll(connCookies);
        }
    }

    void clearCookies() {
        cookies.clear();
    }

    String getFormValues(HttpURLConnection conn) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder buf = new StringBuilder();
        String s;
        while ((s = in.readLine()) != null) {
            buf.append(s);
        }
        String html = buf.toString();
        Map<String, String> values = SyncHelper.parseHtml(html);
        formValues.putAll(values);
        return html;
    }

    @StringRes
    @Override
    public int getAuthNotice() {
        return 0;
    }

    @Override
    public String getPublicUrl() {
        return null;
    }

    @Override
    public String getActivityUrl(String extId) { return null; }

    //Common helper
    //JSON getString() interprets null as the string "null"
    static String noNullStr(String s) { return ("null".equals(s)) ? null : s; }
}
