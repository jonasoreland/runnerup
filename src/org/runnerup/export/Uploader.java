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

import java.io.File;
import java.util.List;

import org.runnerup.feed.FeedList.FeedUpdater;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.util.Pair;

public interface Uploader {

	enum AuthMethod {
		OAUTH2, USER_PASS
	}

	enum Status {
		OK, CANCEL, ERROR, INCORRECT_USAGE, SKIP, NEED_AUTH;

		public Exception ex = null;
		public AuthMethod authMethod = null;
	};

	enum Feature {
		WORKOUT_LIST, // list prepared workouts (e.g a interval program)
		GET_WORKOUT,  // download prepared workout
		FEED,         // list of activities by others (and self)
		UPLOAD,       // upload activity
		LIVE          // live feed of activity
		,SKIP_MAP     // skip map in upload
	};

	/**
	 * 
	 * @return
	 */
	public long getId();

	/**
	 * 
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
	 * 
	 * @param mID
	 * @param onUploadCallback
	 */
	public Status upload(SQLiteDatabase db, long mID);

	/**
	 * Check if an uploader supports a feature
	 * @param f
	 * @return
	 */
	public boolean checkSupport(Feature f);
	
	/**
	 * List workouts
	 * NOTE: this is not list of activities!
	 * @return list of Pair<Uploader,Workout>
	 */
	public Status listWorkouts(List<Pair<String,String>> list);
	
	/**
	 * Download workout with key and store it in dst
	 * NOTE: this is download activity
	 * @param dst
	 * @param key
	 * @return
	 */
	public void downloadWorkout(File dst, String key) throws Exception;
	
	/**
	 * logout
	 * 
	 * @return
	 */
	public void logout();

	/**
	 * 
	 * @param feedUpdater
	 * @return
	 */
	public Status getFeed(FeedUpdater feedUpdater);
	
	/**
	 * 
	 */
	public void liveLog(Context context, Location location, int type, double mElapsedDistanceMeter, double mElapsedTimeMillis);
}
