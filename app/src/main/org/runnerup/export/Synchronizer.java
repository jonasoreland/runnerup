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

import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import org.runnerup.feed.FeedList.FeedUpdater;
import org.runnerup.util.SyncActivityItem;

import java.io.File;
import java.util.List;

public interface Synchronizer {

    enum RequestMethod { GET, POST, PATCH, PUT }

    enum AuthMethod {
        NONE, OAUTH2, USER_PASS, FILEPERMISSION, USER_PASS_URL
    }

    enum Status {
        OK, CANCEL, ERROR, INCORRECT_USAGE, SKIP, NEED_AUTH, NEED_REFRESH;
        @Nullable
        public Exception ex = null;
        @NonNull
        public AuthMethod authMethod = AuthMethod.NONE;
        public Long activityId = SyncManager.ERROR_ACTIVITY_ID;
        @NonNull
        public ExternalIdStatus externalIdStatus = ExternalIdStatus.NONE;
        @Nullable
        public String externalId = null ;
    }

    enum ExternalIdStatus {
        NONE, PENDING, OK;

        int getInt() {
            return getInt(this);
        }
        public static int getInt(ExternalIdStatus s) {
            if (s == PENDING) {
                return 1;
            } else if (s == OK) {
                return 2;
            }
            return 0;
        }
    }

    enum Feature {
        WORKOUT_LIST, // list prepared workouts (e.g a interval program)
        GET_WORKOUT, // download prepared workout
        FEED, // list of activities by others (and self)
        UPLOAD, // upload activity
        LIVE, // live feed of activity
        SKIP_MAP, // skip map in upload
        ACTIVITY_LIST, //list recorded activities
        GET_ACTIVITY, //download recorded activity
        FILE_FORMAT // upload as file in different possible formats
    }

    /**
     * @return The numeric db identifier for the synchronizer
     */
    long getId();

    /**
     * @return name of this synchronizer
     */
    @NonNull
    String getName();

    /**
     * @return The icon resource id
     */
    @DrawableRes
    int getIconId();

    /**
     * @return The color resource id
     */
    @ColorRes
    int getColorId();

    /**
     * Init synchronizer
     *
     * @param config specific for each synchronizer
     */
    void init(ContentValues config);

    /**
     * The synchronizer specific config format
     */
    @NonNull
    String getAuthConfig();

    @NonNull
    Intent getAuthIntent(AppCompatActivity activity);

    /**
     * Is synchronizer configured
     */
    boolean isConfigured();

    /**
     * Reset configuration (i.e password, oauth-token...)
     */
    void reset();

    /**
     * Connect
     *
     * @return the status
     */
    @NonNull
    Status connect();

    /**
     * handle result from authIntent
     */
    @NonNull
    Status getAuthResult(int resultCode, Intent data);

    /**
     * @param db
     * @param mID
     */
    @NonNull
    Status upload(SQLiteDatabase db, long mID);

    /**
     * Get the external identifier for the service
     * Done in the background, can take substantial time for some services
     * @param db
     * @param uploadStatus The status with the (temporary) identifier for the upload
     * @return the external ID in Status
     */
    @NonNull
    Status getExternalId(SQLiteDatabase db, Status uploadStatus);

    /**
     * Check if an synchronizer supports a feature
     *
     * @param f
     * @return
     */
    boolean checkSupport(Feature f);

    /**
     * List workouts NOTE: this is not list of activities!
     *
     * @return list of Pair<synchronizerName,Workout>
     */
    @NonNull
    Status listWorkouts(List<Pair<String, String>> list);

    /**
     * Download workout with key and store it in dst NOTE: this is download
     * activity
     *
     * @param dst
     * @param key
     */
    void downloadWorkout(File dst, String key) throws Exception;

    /**
     * List all recorded and online stored activities
     *
     * @return Status
     */
    @NonNull
    Status listActivities(List<SyncActivityItem> list);

    /**
     * Download a selected activity and records in the RunnerUp database
     *  @param db
     * @param item the ActivityItem of the activity to be downloaded
     */
    @NonNull
    Status download(SQLiteDatabase db, SyncActivityItem item);

    /**
     * logout
     *
     */
    void logout();

    /**
     * @param feedUpdater
     * @return
     */
    @NonNull
    Status getFeed(FeedUpdater feedUpdater);

    @NonNull
    Status refreshToken();

    /**
     * Get any authorization user notice to be shown when user enters username/password.
     * @return A string resource id or 0.
     */
    @StringRes
    int getAuthNotice();

    /**
     * Get the public URL
     */
    String getPublicUrl();

    /**
     * Get the external URL for the activity, to open in app or on the web
     * @param externalId
     * @return
     */
    String getActivityUrl(String externalId);
}
