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
package org.runnerup.view;

import java.util.ArrayList;
import java.util.HashSet;

import org.runnerup.R;
import org.runnerup.db.ActivityCleaner;
import org.runnerup.db.DBHelper;
import org.runnerup.export.UploadManager;
import org.runnerup.export.Uploader;
import org.runnerup.util.Constants;
import org.runnerup.widget.WidgetUtil;
import org.runnerup.workout.Intensity;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

public class DetailActivity extends FragmentActivity implements Constants {

	long mID = 0;
	DBHelper mDBHelper = null;
	SQLiteDatabase mDB = null;
	HashSet<String> pendingUploaders = new HashSet<String>();
	HashSet<String> alreadyUploadedUploaders = new HashSet<String>();
	
	ContentValues laps[] = null;
	ContentValues reports[] = null;
	ArrayList<BaseAdapter> adapters = new ArrayList<BaseAdapter>(2);
	
	int mode; // 0 == save 1 == details
	final static int MODE_SAVE = 0;
	final static int MODE_DETAILS = 1;
	boolean edit = false;

	Button saveButton = null;
	Button discardButton = null;
	Button resumeButton = null;
	TextView activityTime = null;
	TextView activityPace = null;
	TextView activityDistance = null;

	EditText notes = null;
	MenuItem recomputeMenuItem = null;
	
	View mapViewLayout = null;
	GoogleMap map = null;
	View mapView = null;
	LatLngBounds mapBounds = null;
	AsyncTask<String, String, Route> loadRouteTask = null;
	
	UploadManager uploadManager = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.detail);
		Intent intent = getIntent();
		mID = intent.getLongExtra("ID", -1);
		String mode = intent.getStringExtra("mode");

		mDBHelper = new DBHelper(this);
		mDB = mDBHelper.getReadableDatabase();
		uploadManager = new UploadManager(this);

		if (mode.contentEquals("save")) {
			this.mode = MODE_SAVE;
		} else if (mode.contentEquals("details")) {
			this.mode = MODE_DETAILS;
		} else {
			assert (false);
		}

		saveButton = (Button) findViewById(R.id.saveButton);
		discardButton = (Button) findViewById(R.id.discardButton);
		resumeButton = (Button) findViewById(R.id.resumeButton);
		activityTime = (TextView) findViewById(R.id.activityTime);
		activityDistance = (TextView) findViewById(R.id.activityDistance);
		activityPace = (TextView) findViewById(R.id.activityPace);
		notes = (EditText) findViewById(R.id.notesText);
		notes.setHint("Notes about your workout");
		map = ((SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.map)).getMap();

		if (map != null) {
			map.setOnCameraChangeListener(new OnCameraChangeListener() {

				@Override
				public void onCameraChange(CameraPosition arg0) {
					if (mapBounds != null) {
						// Move camera.
						map.moveCamera(CameraUpdateFactory.newLatLngBounds(mapBounds, 5));
						// Remove listener to prevent position reset on camera move.
						map.setOnCameraChangeListener(null);
					}
				}
			});
		}
		saveButton.setOnClickListener(saveButtonClick);
		if (this.mode == MODE_SAVE) {
			discardButton.setOnClickListener(discardButtonClick);
			resumeButton.setOnClickListener(resumeButtonClick);
		} else if (this.mode == MODE_DETAILS) {
			saveButton.setEnabled(false);
			discardButton.setVisibility(View.GONE);
			resumeButton.setVisibility(View.GONE);
			resumeButton.setText("Upload activity");
			resumeButton.setOnClickListener(uploadButtonClick);
			WidgetUtil.setEditable(notes,  false);
		}

		fillHeaderData();
		requery();

		loadRoute();

		TabHost th = (TabHost)findViewById(R.id.tabhost);
		th.setup();
		TabSpec tabSpec = th.newTabSpec("notes");
		tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, "Notes"));
		tabSpec.setContent(R.id.tabMain);
		th.addTab(tabSpec);

		tabSpec = th.newTabSpec("laps");
		tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, "Laps"));
		tabSpec.setContent(R.id.tabLap);
		th.addTab(tabSpec);

		tabSpec = th.newTabSpec("map");
		tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, "Map"));
		tabSpec.setContent(R.id.tabMap);
		th.addTab(tabSpec);

		tabSpec = th.newTabSpec("share");
		tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, "Upload"));
		tabSpec.setContent(R.id.tabUpload);
		th.addTab(tabSpec);
		
		{
			ListView lv = (ListView) findViewById(R.id.laplist);
			LapListAdapter adapter = new LapListAdapter();
			adapters.add(adapter);
			lv.setAdapter(adapter);
		}
		{
			ListView lv = (ListView) findViewById(R.id.reportList);
			ReportListAdapter adapter = new ReportListAdapter();
			adapters.add(adapter);
			lv.setAdapter(adapter);
		}
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (mode == MODE_DETAILS) {
			getMenuInflater().inflate(R.menu.detail_menu, menu);
			recomputeMenuItem = menu.getItem(2);
		}
		return true;
	}

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_delete_activity:
			deleteButtonClick.onClick(null);
			break;
		case R.id.menu_edit_activity:
			if (edit == false) {
				edit = true;
				WidgetUtil.setEditable(notes,  true);
				notes.requestFocus();
				saveButton.setEnabled(true);
				if (recomputeMenuItem != null)
					recomputeMenuItem.setEnabled(true);
				requery();
			}
			break;
		case R.id.menu_recompute_activity:
			ActivityCleaner.recompute(mDB, mID);
			requery();
			break;
		}
		return true;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mDB.close();
		mDBHelper.close();
		uploadManager.close();
	}
	
	void requery() {
		{
			/**
			 * Laps
			 */
			String[] from = new String[] { "_id", DB.LAP.LAP, DB.LAP.INTENSITY,
					DB.LAP.TIME, DB.LAP.DISTANCE, DB.LAP.PLANNED_TIME,
					DB.LAP.PLANNED_DISTANCE, DB.LAP.PLANNED_PACE };

			Cursor c = mDB.query(DB.LAP.TABLE, from, DB.LAP.ACTIVITY + " == " + mID,
					null, null, null, "_id", null);

			laps = DBHelper.toArray(c);
			c.close();
		}

		{
			/**
			 * Accounts/reports
			 */
			String sql = new String(
					"SELECT DISTINCT "
							+ "  acc._id, " // 0
							+ ("  acc." + DB.ACCOUNT.NAME + ", ")
							+ ("  acc." + DB.ACCOUNT.DESCRIPTION + ", ")
							+ ("  acc." + DB.ACCOUNT.DEFAULT + ", ")
							+ ("  acc." + DB.ACCOUNT.AUTH_METHOD + ", ")
							+ ("  acc." + DB.ACCOUNT.AUTH_CONFIG + ", ")
							+ ("  acc." + DB.ACCOUNT.ENABLED + ", ")
							+ ("  rep._id as repid, ")
							+ ("  rep." + DB.EXPORT.ACCOUNT + ", ")
							+ ("  rep." + DB.EXPORT.ACTIVITY + ", ")
							+ ("  rep." + DB.EXPORT.STATUS)
							+ (" FROM " + DB.ACCOUNT.TABLE + " acc ")
							+ (" LEFT OUTER JOIN " + DB.EXPORT.TABLE + " rep ")
							+ (" ON ( acc._id = rep." + DB.EXPORT.ACCOUNT)
							+ ("     AND rep." + DB.EXPORT.ACTIVITY + " = "
									+ mID + " )")
							+ (" WHERE acc." + DB.ACCOUNT.ENABLED + " != 0 ")
							+ ("   AND acc." + DB.ACCOUNT.AUTH_CONFIG + " is not null"));

			Cursor c = mDB.rawQuery(sql, null);
			reports = DBHelper.toArray(c);
			c.close();
		}

		alreadyUploadedUploaders.clear();
		pendingUploaders.clear();
		for (ContentValues tmp : reports) {
			uploadManager.add(tmp);
			if (tmp.containsKey("repid")) {
				alreadyUploadedUploaders.add(tmp.getAsString(DB.ACCOUNT.NAME));
			} else if (tmp.containsKey(DB.ACCOUNT.DEFAULT) && tmp.getAsInteger(DB.ACCOUNT.DEFAULT) != 0) {
				pendingUploaders.add(tmp.getAsString(DB.ACCOUNT.NAME));
			}
		}

		if (this.mode == MODE_DETAILS && pendingUploaders.isEmpty()) {
			resumeButton.setVisibility(View.GONE);
		} else {
			resumeButton.setVisibility(View.VISIBLE);
		}

		for (BaseAdapter a : adapters) {
			a.notifyDataSetChanged();
		}
	}

	void fillHeaderData() {
		// Fields from the database (projection)
		// Must include the _id column for the adapter to work
		String[] from = new String[] { DB.ACTIVITY.START_TIME,
				DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME, DB.ACTIVITY.COMMENT };

		Cursor c = mDB.query(DB.ACTIVITY.TABLE, from, "_id == " + mID, null,
				null, null, null, null);
		c.moveToFirst();
		ContentValues tmp = DBHelper.get(c);
		c.close();

		java.text.DateFormat DF = android.text.format.DateFormat
				.getDateFormat(this);
		java.text.DateFormat TF = android.text.format.DateFormat
				.getTimeFormat(this);

		long st = 0;
		if (false && !c.isNull(0)) {
			st = c.getLong(0);
			activityTime.setText(DF.format(st * 1000) + " "
					+ TF.format(st * 1000));
		}
		float d = 0;
		if (tmp.containsKey(DB.ACTIVITY.DISTANCE)) {
			d = tmp.getAsFloat(DB.ACTIVITY.DISTANCE);
			activityDistance.setText(Long.toString((long) d) + " m");
		}

		float t = 0;
		if (tmp.containsKey(DB.ACTIVITY.TIME)) {
			t = tmp.getAsFloat(DB.ACTIVITY.TIME);
			activityTime.setText(DateUtils.formatElapsedTime((long)t));
		}

		if (d != 0 && t != 0) {
			activityPace.setText(DateUtils
					.formatElapsedTime((long) (1000 * t / d)) + "/km");
		}

		if (tmp.containsKey(DB.ACTIVITY.COMMENT)) {
			notes.setText(tmp.getAsString(DB.ACTIVITY.COMMENT));
		}
	}

	class LapListAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return laps.length;
		}

		@Override
		public Object getItem(int position) {
			return laps[position];
		}

		@Override
		public long getItemId(int position) {
			return laps[position].getAsLong("_id");
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			LayoutInflater inflater = LayoutInflater.from(DetailActivity.this);
			View view = inflater.inflate(R.layout.laplist_row, parent, false);
			TextView tv0 = (TextView) view.findViewById(R.id.lapList_type);
			int  i = laps[position].getAsInteger(DB.LAP.INTENSITY);
			switch (Intensity.values()[i]) {
			case ACTIVE:
				break;
			case COOLDOWN:
				tv0.setText("(cooldown)");
				break;
			case RESTING:
				tv0.setText("(rest)");
				break;
			case WARMUP:
				tv0.setText("(warmup)");
				break;
			
			}
			TextView tv1 = (TextView) view.findViewById(R.id.lapList_id);
			tv1.setText(laps[position].getAsString("_id"));
			TextView tv2 = (TextView) view.findViewById(R.id.lapList_distance);
			float d = laps[position].containsKey(DB.LAP.DISTANCE) ? laps[position].getAsFloat(DB.LAP.DISTANCE) : 0;
			tv2.setText(d + "m");
			TextView tv3 = (TextView) view.findViewById(R.id.lapList_time);
			long t = laps[position].containsKey(DB.LAP.TIME) ? laps[position].getAsLong(DB.LAP.TIME) : 0;
			tv3.setText(DateUtils.formatElapsedTime(t));
			TextView tv4 = (TextView) view.findViewById(R.id.lapList_pace);
			if (t != 0 && d != 0)
			{
				tv4.setText(DateUtils.formatElapsedTime((long) (1000 * t / d))
						+ "/km");
			}
			else
			{
				tv4.setText("");
			}
			return view;
		}
	};

	class ReportListAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return reports.length + 1;
		}

		@Override
		public Object getItem(int position) {
			if (position < reports.length)
				return reports[position];
			return this;
		}

		@Override
		public long getItemId(int position) {
			if (position < reports.length)
				return reports[position].getAsLong("_id");

			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (position == reports.length) {
				Button b = new Button(DetailActivity.this);
				b.setText("Configure accounts");
				b.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent i = new Intent(DetailActivity.this,
								AccountsActivity.class);
						DetailActivity.this.startActivityForResult(i,
								UploadManager.CONFIGURE_REQUEST + 1);
					}
				});
				return b;
			}

			LayoutInflater inflater = LayoutInflater.from(DetailActivity.this);
			View view = inflater.inflate(R.layout.reportlist_row, parent, false);

			TextView tv0 = (TextView) view.findViewById(R.id.accountId);
			CheckBox cb = (CheckBox) view.findViewById(R.id.reportSent);
			TextView tv1 = (TextView) view.findViewById(R.id.accountName);

			ContentValues tmp = reports[position];

			String name = tmp.getAsString("name");
			cb.setTag(name);
			if (alreadyUploadedUploaders.contains(name)) {
				cb.setChecked(true);
				cb.setEnabled(false);
				cb.setText("Uploaded");
			} else if (tmp.containsKey(DB.ACCOUNT.DEFAULT) && tmp.getAsInteger(DB.ACCOUNT.DEFAULT) != 0) {
				cb.setChecked(true);
			} else {
				cb.setChecked(false);
			}
			if (mode == MODE_DETAILS && !alreadyUploadedUploaders.contains(name)) {
				cb.setEnabled(edit);
			}
			cb.setOnCheckedChangeListener(DetailActivity.this.onSendChecked);

			tv0.setText(tmp.getAsString("_id"));
			tv1.setText(name);
			return view;
		}
		
	};
	
	void saveActivity() {
		ContentValues tmp = new ContentValues();
		tmp.put(DB.ACTIVITY.COMMENT, notes.getText().toString());
		String whereArgs[] = { Long.toString(mID) };
		mDB.update(DB.ACTIVITY.TABLE, tmp, "_id = ?", whereArgs);
	}
	
	OnClickListener saveButtonClick = new OnClickListener() {
		public void onClick(View v) {
			saveActivity();
			if (mode == MODE_DETAILS) {
				edit = false;
				WidgetUtil.setEditable(notes,  false);
				saveButton.setEnabled(false);
				if (recomputeMenuItem != null)
					recomputeMenuItem.setEnabled(false);
				requery();
				return;
			}
			uploadManager.startUploading(new UploadManager.Callback() {
				@Override
				public void run(String uploader, Uploader.Status status) {
					DetailActivity.this.setResult(RESULT_OK);
					DetailActivity.this.finish();
				}
			}, pendingUploaders, mID);
		}
	};

	OnClickListener discardButtonClick = new OnClickListener() {
		public void onClick(View v) {
			AlertDialog.Builder builder = new AlertDialog.Builder(DetailActivity.this);
			builder.setTitle("Discard activity");
			builder.setMessage("Are you sure?");
			builder.setPositiveButton("Yes",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
							DetailActivity.this.setResult(RESULT_CANCELED);
							DetailActivity.this.finish();
						}
					});
			builder.setNegativeButton("No",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							// Do nothing but close the dialog
							dialog.dismiss();
						}

					});
			builder.show();
		}
	};

	@Override
	public void onBackPressed() {
		if (mode == MODE_SAVE) {
			discardButtonClick.onClick(discardButton);
		} else {
			super.onBackPressed();
		}
	}
	
	OnClickListener resumeButtonClick = new OnClickListener() {
		public void onClick(View v) {
			DetailActivity.this.setResult(RESULT_FIRST_USER);
			DetailActivity.this.finish();
		}
	};

	OnClickListener uploadButtonClick = new OnClickListener() {
		public void onClick(View v) {
			uploadManager.startUploading(new UploadManager.Callback() {
				@Override
				public void run(String uploader, Uploader.Status status) {
					requery();
				}
			}, pendingUploaders, mID);
		}
	};

	public OnCheckedChangeListener onSendChecked = new OnCheckedChangeListener() {

		@Override
		public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
			if (arg1 == true) {
				boolean empty = pendingUploaders.isEmpty();
				pendingUploaders.add((String) arg0.getTag());
				if (empty) {
					resumeButton.setVisibility(View.VISIBLE);
				}
			} else {
				pendingUploaders.remove((String) arg0.getTag());
				if (pendingUploaders.isEmpty()) {
					resumeButton.setVisibility(View.GONE);
				}
			}
		}

	};

	OnClickListener deleteButtonClick = new OnClickListener() {
		public void onClick(View v) {
			AlertDialog.Builder builder = new AlertDialog.Builder(
					DetailActivity.this);
			builder.setTitle("Delete activity");
			builder.setMessage("Are you sure?");
			builder.setPositiveButton("Yes",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							ContentValues tmp = new ContentValues();
							tmp.put("deleted", 1);
							String args[] = { "" + mID };
							mDB.update(DB.ACTIVITY.TABLE, tmp, "_id = ?", args);
							dialog.dismiss();
						}
					});
			builder.setNegativeButton("No",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							// Do nothing but close the dialog
							dialog.dismiss();
						}

					});
			builder.show();
		}
	};

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == UploadManager.CONFIGURE_REQUEST) {
			uploadManager.onActivityResult(requestCode, resultCode, data);
		}
		requery();
	}

	public static int position(String arr[], String key) {
		for (int i = 0; i < arr.length; i++) {
			if (arr[i].contentEquals(key))
				return i;
		}
		return -1;
	}

	class Route {
		PolylineOptions path = new PolylineOptions();
		LatLngBounds.Builder bounds = new LatLngBounds.Builder();
		ArrayList<MarkerOptions> markers = new ArrayList<MarkerOptions>(10);
		
		Route () {
			path.color(Color.RED);
			path.width(3);
		}
	};
	
	private void loadRoute() {
		final String[] from = new String[] { 
				DB.LOCATION.LATITUDE,
				DB.LOCATION.LONGITUDE,
				DB.LOCATION.TYPE };
		
		loadRouteTask = new AsyncTask<String,String,Route>() {

			@Override
			protected Route doInBackground(String... params) {
				int cnt = 0;
				Route route = null;
				Cursor c = mDB.query(DB.LOCATION.TABLE, from, "activity_id == " + mID,
						null, null, null, "_id", null);
				if (c.moveToFirst()) {
					route = new Route();
					double acc_distance = 0;
					int cnt_distance = 0;
					LatLng lastLocation = null;
					do {
						cnt++;
						LatLng point = new LatLng(c.getDouble(0), c.getDouble(1));
						route.path.add(point);
						route.bounds.include(point);
						int type = c.getInt(2);
						MarkerOptions m;
						switch (type) {
						case DB.LOCATION.TYPE_START:
						case DB.LOCATION.TYPE_END:
						case DB.LOCATION.TYPE_PAUSE:
						case DB.LOCATION.TYPE_RESUME:
							m = new MarkerOptions();
							m.position((lastLocation = point));
							m.title(type == DB.LOCATION.TYPE_START ? "Start" :
									type == DB.LOCATION.TYPE_END ? "Stop" :
									type == DB.LOCATION.TYPE_PAUSE ? "Pause" :
									type == DB.LOCATION.TYPE_RESUME ? "Resume" : "<Unknown>");
							m.snippet(null);
							m.draggable(false);
							route.markers.add(m);
							break;
						case DB.LOCATION.TYPE_GPS:
							float res[] = { 0 };
							Location.distanceBetween(lastLocation.latitude, lastLocation.longitude, point.latitude, point.longitude, res);
							acc_distance += res[0];
							if (acc_distance >= 1000) {
								cnt_distance ++;
								acc_distance = 0;
								m = new MarkerOptions();
								m.position(point);
								m.title("" + cnt_distance + " km");
								m.snippet(null);
								m.draggable(false);
								route.markers.add(m);
							}
							lastLocation = point;
							break;
						}
					} while (c.moveToNext());
					System.err.println("Finished loading " + cnt + " points");
				}
				c.close();
				return route;
			}

			@Override
			protected void onPostExecute(Route route) {
				if (route != null) {
					if (map != null) {
						map.addPolyline(route.path);
						mapBounds = route.bounds.build();
						System.err.println("Added polyline");
						int cnt = 0;
						for (MarkerOptions m : route.markers) {
							cnt++;
							map.addMarker(m);
						}
						System.err.println("Added " + cnt + " markers");

						route = new Route(); // release mem for old...
					}
				}
			}
		}.execute("kalle");
	}
}
