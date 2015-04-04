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
import android.util.Pair;

import org.runnerup.export.util.FormValues;
import org.runnerup.export.util.SyncHelper;
import org.runnerup.feed.FeedList.FeedUpdater;
import org.runnerup.util.SyncActivityItem;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@TargetApi(Build.VERSION_CODES.FROYO)
public class DefaultUploader implements Uploader {

    protected final Set<String> cookies = new HashSet<String>();
    protected final FormValues formValues = new FormValues();

    public DefaultUploader() {
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

    /** Below are default empty methods from Uploader */
    public Status getAuthResult(int resultCode, Intent data) {
        return Status.OK;
    }

    public Pair<Status, Long> upload(SQLiteDatabase db, long mID) {
        return Pair.create(Status.ERROR, mID);
    }

    public boolean checkSupport(Uploader.Feature f) {
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
    public Pair<Status, Long> download(SQLiteDatabase db, SyncActivityItem item) {
        return Pair.create(Status.ERROR, -1L);
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
}
