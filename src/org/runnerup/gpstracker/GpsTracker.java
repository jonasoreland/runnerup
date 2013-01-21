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
package org.runnerup.gpstracker;

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.gpstracker.filter.PersistentGpsLoggerListener;
import org.runnerup.util.Constants;
import org.runnerup.workout.Workout;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;

/**
 * GpsTracker - this class tracks Location updates
 * 
 * @author jonas.oreland@gmail.com
 * 
 */
public class GpsTracker extends android.app.Service implements
		LocationListener, Constants {

	private static final int NOTIFICATION_ID = 1;

	/**
	 * Work-around for http://code.google.com/p/android/issues/detail?id=23937
	 */
	boolean mBug23937Checked = false;
	long mBug23937Delta = 0;
	
	/**
	 *
	 */
	long mLapId = 0;
	long mActivityId = 0;
	double mElapsedTimeMillis = 0;
	double mElapsedDistance = 0;

	enum State {
		INIT, LOGGING, STARTED, PAUSED
	};

	State state = State.INIT;
	int mLocationType = DB.LOCATION.TYPE_START;

	/**
	 * Last location given by LocationManager
	 */
	Location mLastLocation = null;

	/**
	 * Last location given by LocationManager when in state STARTED
	 */
	Location mActivityLastLocation = null;

	DBHelper mDBHelper = null;
	SQLiteDatabase mDB = null;
	PersistentGpsLoggerListener mDBWriter = null;
	PowerManager.WakeLock mWakeLock = null;

	private Workout workout = null;

	@Override
	public void onCreate() {
		mDBHelper = new DBHelper(this);
		mDB = mDBHelper.getWritableDatabase();
		wakelock(false);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// We want this service to continue running until it is explicitly
		// stopped, so return sticky.
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		if (mDB != null) {
			mDB.close();
			mDB = null;
		}

		if (mDBHelper != null) {
			mDBHelper.close();
			mDBHelper = null;
		}

		stopLogging();
	}

	public void setWorkout(Workout w) {
		this.workout = w;
		w.setGpsTracker(this);
	}

	public Workout getWorkout() {
		return this.workout;
	}

	public void startLogging() {
		assert (state == State.INIT);
		wakelock(true);
		String frequency_ms = PreferenceManager.getDefaultSharedPreferences(
				this).getString("pref_pollInterval", "500");
		String frequency_meters = PreferenceManager
				.getDefaultSharedPreferences(this).getString(
						"pref_pollDistance", "5");
		LocationManager lm = (LocationManager) this
				.getSystemService(LOCATION_SERVICE);
		lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				Integer.valueOf(frequency_ms),
				Integer.valueOf(frequency_meters), this);
		state = State.LOGGING;
	}

	public boolean isLogging() {
		switch (state) {
		case INIT:
			return false;
		case LOGGING:
		case STARTED: 
		case PAUSED:
			return true;
		}
		return true;
	}

	public long getBug23937Delta() {
		return mBug23937Delta;
	}
	
	public long createActivity() {
		assert (state == State.INIT);
		/**
		 * Create an Activity instance
		 */
		ContentValues tmp = new ContentValues();
		mActivityId = mDB.insert(DB.ACTIVITY.TABLE, "nullColumnHack", tmp);
		tmp.put(DB.LOCATION.ACTIVITY, mActivityId);
		tmp.put(DB.LOCATION.LAP, 0); // always start with lap 0
		mDBWriter = new PersistentGpsLoggerListener(mDB, DB.LOCATION.TABLE, tmp);
		return mActivityId;
	}

	public void start() {
		assert (state == State.LOGGING);
		state = State.STARTED;
		mElapsedTimeMillis = 0;
		mElapsedDistance = 0;
		// TODO: check if mLastLocation is recent enough
		mActivityLastLocation = null;
		setNextLocationType(DB.LOCATION.TYPE_START); // New location update will
														// be tagged with START
	}
	
	public void startOrResume() {
		switch (state) {
		case INIT:
			assert(false);
			break;
		case LOGGING:
			start();
			break;
		case PAUSED:
			resume();
			break;
		case STARTED:
			break;
		}		
	}
	
	public void setForeground(Class<?> client) {
		Notification note = new Notification(R.drawable.icon,
				"RunnerUp activity started", System.currentTimeMillis());
		Intent i = new Intent(this, client);
		i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);

		note.setLatestEventInfo(this, "RunnerUp", "Tracking", pi);
		note.flags |= Notification.FLAG_NO_CLEAR;

		startForeground(NOTIFICATION_ID, note);
	}

	public void newLap(ContentValues tmp) {
		tmp.put(DB.LAP.ACTIVITY, mActivityId);
		mLapId = mDB.insert(DB.LAP.TABLE, null, tmp);
		ContentValues key = mDBWriter.getKey();
		key.put(DB.LOCATION.LAP, tmp.getAsLong(DB.LAP.LAP));
		mDBWriter.setKey(key);
	}

	public void saveLap(ContentValues tmp) {
		tmp.put(DB.LAP.ACTIVITY, mActivityId);
		String key[] = { Long.toString(mLapId) };
		mDB.update(DB.LAP.TABLE, tmp, "_id = ?", key);
	}

	public void stopOrPause() {
		switch (state) {
		case INIT:
		case LOGGING:
		case PAUSED:
			break;
		case STARTED:
			stop();
		}		
	}

	public void stop() {
		setNextLocationType(DB.LOCATION.TYPE_PAUSE);
		if (mActivityLastLocation != null) {
			/**
			 * This saves mLastLocation as a PAUSE location
			 */
			internalOnLocationChanged(mActivityLastLocation);
		}

		ContentValues tmp = new ContentValues();
		tmp.put(Constants.DB.ACTIVITY.DISTANCE, mElapsedDistance);
		tmp.put(Constants.DB.ACTIVITY.TIME, getTime());
		String key[] = { Long.toString(mActivityId) };
		mDB.update(DB.ACTIVITY.TABLE, tmp, "_id = ?", key);
		state = State.PAUSED;
	}

	private void internalOnLocationChanged(Location arg0) {
		long save = mBug23937Delta;
		mBug23937Delta = 0;
		onLocationChanged(arg0);
		mBug23937Delta = save;
	}

	public boolean isPaused() {
		return state == State.PAUSED;
	}

	public void resume() {
		assert (state == State.PAUSED);
		// TODO: check is mLastLocation is recent enough
		mActivityLastLocation = mLastLocation;
		state = State.STARTED;
		setNextLocationType(DB.LOCATION.TYPE_RESUME);
		if (mActivityLastLocation != null) {
			/**
			 * save last know location as resume location
			 */
			internalOnLocationChanged(mActivityLastLocation);
		}
	}

	private void stopLogging() {
		assert (state == State.PAUSED || state == State.LOGGING);
		wakelock(false);
		if (state != State.INIT) {
			LocationManager lm = (LocationManager) this
					.getSystemService(LOCATION_SERVICE);
			lm.removeUpdates(this);
			state = State.INIT;
		}
	}

	public void completeActivity(boolean save) {
		assert (state == State.PAUSED);
		save = true;
		if (save) {
			setNextLocationType(DB.LOCATION.TYPE_END);
			if (mActivityLastLocation != null) {
				mDBWriter.onLocationChanged(mActivityLastLocation);
			}

			ContentValues tmp = new ContentValues();
			tmp.put(Constants.DB.ACTIVITY.DISTANCE, mElapsedDistance);
			tmp.put(Constants.DB.ACTIVITY.TIME, getTime());
			String key[] = { Long.toString(mActivityId) };
			mDB.update(DB.ACTIVITY.TABLE, tmp, "_id = ?", key);
		} else {
			// TODO write discard code
		}
		this.stopForeground(true);
		stopLogging();
	}

	void setNextLocationType(int newType) {
		ContentValues key = mDBWriter.getKey();
		key.put(DB.LOCATION.TYPE, newType);
		mDBWriter.setKey(key);
		mLocationType = newType;
	}

	public double getTime() {
		return mElapsedTimeMillis / 1000;
	}

	public double getDistance() {
		return mElapsedDistance;
	}

	public Location getLastKnownLocation() {
		return mLastLocation;
	}

	@Override
	public void onLocationChanged(Location arg0) {
		if (mBug23937Checked == false) {
			long gpsTime = arg0.getTime();
			long utcTime = System.currentTimeMillis();
			if (gpsTime > utcTime + 3 * 1000) {
				mBug23937Delta = utcTime - gpsTime;
			} else {
				mBug23937Delta = 0;
			}
			mBug23937Checked = true;
		}
		if (mBug23937Delta != 0) {
			arg0.setTime(arg0.getTime() + mBug23937Delta);
		}
		
		if (state == State.STARTED) {
			if (mActivityLastLocation != null) {
				double timeDiff = (double) (arg0.getTime() - mActivityLastLocation
						.getTime());
				double distDiff = arg0.distanceTo(mActivityLastLocation);
				assert (timeDiff >= 0);
				assert (distDiff >= 0);
				mElapsedTimeMillis += timeDiff;
				mElapsedDistance += distDiff;
			}
			mActivityLastLocation = arg0;

			mDBWriter.onLocationChanged(arg0);

			switch (mLocationType) {
			case DB.LOCATION.TYPE_START:
			case DB.LOCATION.TYPE_RESUME:
				setNextLocationType(DB.LOCATION.TYPE_GPS);
				break;
			case DB.LOCATION.TYPE_GPS:
				break;
			case DB.LOCATION.TYPE_PAUSE:
				break;
			case DB.LOCATION.TYPE_END:
				assert (false);
			}
		}
		mLastLocation = arg0;
	}

	@Override
	public void onProviderDisabled(String arg0) {
	}

	@Override
	public void onProviderEnabled(String arg0) {
	}

	@Override
	public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
	}

	/**
	 * Service interface stuff...
	 */

	public class LocalBinder extends android.os.Binder {
		public GpsTracker getService() {
			return GpsTracker.this;
		}
	}

	private final IBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	private void wakelock(boolean get) {
		if (mWakeLock != null) {
			if (mWakeLock.isHeld()) {
				mWakeLock.release();
			}
			mWakeLock = null;
		}
		if (get) {
			PowerManager pm = (PowerManager) this
					.getSystemService(Context.POWER_SERVICE);
			mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
					"RunnerUp");
			if (mWakeLock != null) {
				mWakeLock.acquire();
			}
		}
	}
}
