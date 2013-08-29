/*
 * Copyright (C) 2013 jonas.oreland@gmail.com, weides@gmail.com
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

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.db.DBHelper;
import org.runnerup.util.Constants.DB;
import org.runnerup.util.Formatter;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.preference.PreferenceManager;

public class WebserviceLogger {
	private Formatter _formatter;
	DBHelper mDBHelper = null;
	SQLiteDatabase mDB = null;
	String accountName;
	String serverAdress;
	boolean active = false;
	Context context;

	public WebserviceLogger(Context context) {
		this.context = context;
		_formatter = new Formatter(context);
		mDBHelper = new DBHelper(context);
		mDB = mDBHelper.getReadableDatabase();
		serverAdress = PreferenceManager.getDefaultSharedPreferences(context)
				.getString("pref_runneruplive_serveradress",
						"http://weide.devsparkles.se/api/Resource/");
		active = PreferenceManager.getDefaultSharedPreferences(context)
				.getBoolean("pref_runneruplive_active", true);

		accountName = PreferenceManager.getDefaultSharedPreferences(context)
				.getString("pref_runneruplive_username", "");

		if (accountName != "")
			return;

		String[] from = new String[] { "_id", DB.ACCOUNT.NAME, DB.ACCOUNT.URL,
				DB.ACCOUNT.DESCRIPTION, DB.ACCOUNT.ENABLED, DB.ACCOUNT.FLAGS,
				DB.ACCOUNT.ICON, DB.ACCOUNT.AUTH_CONFIG };

		Cursor c = mDB.query(DB.ACCOUNT.TABLE, from, null, null, null, null,
				DB.ACCOUNT.ENABLED + " desc, " + DB.ACCOUNT.NAME);
		while (c.moveToNext()) {
			final String authToken = c.getString(7);
			if (authToken != null) {
				JSONObject tmp;
				try {
					tmp = new JSONObject(authToken);
					accountName = tmp.getString("username");
					if (accountName != "")
						return;
				} catch (final JSONException e) {
					e.printStackTrace();
				}
			}

		}
	}

	public void Log(Location location, int type, double mElapsedDistanceMeter, double mElapsedTimeMillis) {
		
		if (!active)
			return;
		
		long elapsedDistanceMeter = Math.round(mElapsedDistanceMeter);
		Intent msgIntent = new Intent(context, LiveService.class);
		msgIntent.putExtra(LiveService.PARAM_IN_LAT, location.getLatitude());
		msgIntent.putExtra(LiveService.PARAM_IN_LONG, location.getLongitude());
		msgIntent.putExtra(LiveService.PARAM_IN_TYPE, type);
		msgIntent.putExtra(LiveService.PARAM_IN_ELAPSED_DISTANCE, _formatter
				.formatDistance(Formatter.TXT_LONG, elapsedDistanceMeter));
		msgIntent.putExtra(
				LiveService.PARAM_IN_ELAPSED_TIME,
				_formatter.formatElapsedTime(Formatter.TXT_LONG,
						Math.round(mElapsedTimeMillis / 1000)));
		msgIntent.putExtra(
				LiveService.PARAM_IN_PACE,
				_formatter.formatPace(Formatter.TXT_SHORT, mElapsedTimeMillis
						/ (1000 * mElapsedDistanceMeter)));
		msgIntent.putExtra(LiveService.PARAM_IN_USERNAME, accountName);
		msgIntent.putExtra(LiveService.PARAM_IN_SERVERADRESS, serverAdress);

		context.startService(msgIntent);
	}
}
