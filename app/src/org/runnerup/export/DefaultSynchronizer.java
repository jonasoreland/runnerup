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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.db.entities.LapEntity;
import org.runnerup.db.entities.LocationEntity;
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

@TargetApi(Build.VERSION_CODES.FROYO)
public abstract class DefaultSynchronizer implements Synchronizer {

    protected final Set<String> cookies = new HashSet<String>();
    protected final FormValues formValues = new FormValues();

    private Integer authNotice;

    public DefaultSynchronizer() {
        super();
        logout();
    }

    @Override
    public long getId() {
        return 0;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void init(ContentValues config) {
        //Note that only auth_config can be expected here
        //Other config can be retrieved from db in Upload()
    }

    @Override
    public String getAuthConfig() {
        return null;
    }

    public Intent getAuthIntent(Activity a) {
        return null;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public void reset() {

    }

    @Override
    public Status connect() {
        return null;
    }

    /** Below are default empty methods from Synchronizer */
    public Status getAuthResult(int resultCode, Intent data) {
        return Status.OK;
    }

    public Status upload(SQLiteDatabase db, long mID) {
        Status s = Status.ERROR;
        s.activityId = mID;
        return s;
    }

    public boolean checkSupport(Synchronizer.Feature f) {
        return false;
    }

    public Status listWorkouts(List<Pair<String, String>> list) {
        return Status.OK;
    }

    public void downloadWorkout(File dst, String key) throws Exception {
    }

    @Override
    public Status listActivities(List<SyncActivityItem> list) {
        return Status.INCORRECT_USAGE;
    }

    @Override
    public final Status download(SQLiteDatabase db, SyncActivityItem item) {
        return persistActivity(db, download(item));
    }

    protected ActivityEntity download(SyncActivityItem item) {
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
        activity.putPoints(new ArrayList<LocationEntity>(activity.getLocationPoints()));
        // insert location and end transaction unsuccessfully
        if (DBHelper.bulkInsert(activity.getLocationPoints(), db) != activity.getLocationPoints().size()) {
            db.endTransaction();
            return Status.ERROR;
        }

        //update with activity id
        activity.putLaps(new ArrayList<LapEntity>(activity.getLaps()));
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

    public Status getFeed(FeedUpdater feedUpdater) {
        return Status.OK;
    }

    @Override
    public Status refreshToken() {
        return Status.ERROR;
    }

    protected void addCookies(HttpURLConnection conn) {
        boolean first = true;
        StringBuilder buf = new StringBuilder();
        for (String cookie : cookies) {
            if (!first)
                buf.append("; ");
            buf.append(cookie.split(";", 2)[0]);
            first = false;
        }
        conn.addRequestProperty("Cookie", buf.toString());
    }

    protected void getCookies(HttpURLConnection conn) {
        List<String> connCookies = conn.getHeaderFields().get("Set-Cookie");
        /**
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

    protected void clearCookies() {
        cookies.clear();
    }

    protected String getFormValues(HttpURLConnection conn) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder buf = new StringBuilder();
        String s = null;
        while ((s = in.readLine()) != null) {
            buf.append(s);
        }
        String html = buf.toString();
        Map<String, String> values = SyncHelper.parseHtml(html);
        formValues.putAll(values);
        return html;
    }

    @Override
    public Integer getAuthNotice() {
        return authNotice;
    }

    @Override
    public void setAuthNotice(Integer authNotice) {
        this.authNotice = authNotice;
    }
}
