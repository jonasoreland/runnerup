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

package org.runnerup.export;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import org.runnerup.util.SyncActivityItem;
import org.runnerup.feed.FeedList.FeedUpdater;

import java.io.File;
import java.util.List;

public interface Uploader {

    public enum RequestMethod { GET, POST, PATCH, PUT; }

    enum AuthMethod {
        OAUTH2, USER_PASS

    }

    enum Status {
        OK, CANCEL, ERROR, INCORRECT_USAGE, SKIP, NEED_AUTH, NEED_REFRESH;
        public Exception ex = null;
        public AuthMethod authMethod = null;

    }
    enum Feature {
        WORKOUT_LIST, // list prepared workouts (e.g a interval program)
        GET_WORKOUT, // download prepared workout
        FEED, // list of activities by others (and self)
        UPLOAD, // upload activity
        LIVE, // live feed of activity
        SKIP_MAP, // skip map in upload
        ACTIVITY_LIST, //list recorded activities
        GET_ACTIVITY //downlaod recorded activity

    }

    /**
     * @return
     */
    public long getId();

    /**
     * @return name of this uploader
     */
    public String getName();

    /**
     * Init uploader
     *
     * @param config
     */
    public void init(ContentValues config);

    /**
	 *
	 */
    public String getAuthConfig();

    /**
	 *
	 */
    public Intent getAuthIntent(Activity activity);

    /**
     * Is uploader configured
     */
    public boolean isConfigured();

    /**
     * Reset configuration (i.e password, oauth-token...)
     */
    public void reset();

    /**
     * Connect
     *
     * @return true ok false cancel/fail
     */
    public Status connect();

    /**
     * handle result from authIntent
     */
    public Status getAuthResult(int resultCode, Intent data);

    /**
     * @param db
     * @param mID
     */
    public Pair<Status, Long> upload(SQLiteDatabase db, long mID);

    /**
     * Check if an uploader supports a feature
     *
     * @param f
     * @return
     */
    public boolean checkSupport(Feature f);

    /**
     * List workouts NOTE: this is not list of activities!
     *
     * @return list of Pair<Uploader,Workout>
     */
    public Status listWorkouts(List<Pair<String, String>> list);

    /**
     * Download workout with key and store it in dst NOTE: this is download
     * activity
     *
     * @param dst
     * @param key
     * @return
     */
    public void downloadWorkout(File dst, String key) throws Exception;

    /**
     * List all recorded and online stored activities
     *
     * @return Status
     */
    public Status listActivities(List<SyncActivityItem> list);
    /**
     * Download a selected activity and records in the RunnerUp database
     *  @param db
     * @param item the ActivityItem of the activity to be downloaded
     */
    public Pair<Status, Long> download(SQLiteDatabase db, SyncActivityItem item);

    /**
     * logout
     *
     * @return
     */
    public void logout();


    /**
     * @param feedUpdater
     * @return
     */
    public Status getFeed(FeedUpdater feedUpdater);

    public Status refreshToken();
}
