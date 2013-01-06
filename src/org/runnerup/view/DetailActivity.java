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
import org.runnerup.db.DBHelper;
import org.runnerup.export.UploadManager;
import org.runnerup.export.Uploader;
import org.runnerup.util.Constants;
import org.runnerup.widget.WidgetUtil;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
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
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

public class DetailActivity extends MapActivity implements Constants, TabContentFactory {

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

	View mapViewLayout = null;
	MapView mapView = null;
	AsyncTask<String,String,RouteOverlay> loadRouteTask = null;
	
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

		mapView = new MapView(this,"0xdNTxWhTMocROUoBkVEvmMpQvfAcivz7zNzXNQ");
		mapView.setBuiltInZoomControls(true);
		mapView.setClickable(true);
		mapViewLayout = mapView;

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
		tabSpec.setIndicator("Notes");
		tabSpec.setContent(R.id.tabMain);
		th.addTab(tabSpec);

		tabSpec = th.newTabSpec("laps");
		tabSpec.setIndicator("Laps");
		tabSpec.setContent(R.id.tabLap);
		th.addTab(tabSpec);

		tabSpec = th.newTabSpec("map");
		tabSpec.setIndicator("Map");
		tabSpec.setContent(this);
		th.addTab(tabSpec);

		tabSpec = th.newTabSpec("share");
		tabSpec.setIndicator("Upload");
		tabSpec.setContent(R.id.tabUpload);
		th.addTab(tabSpec);
		
		{
			int iCnt = th.getTabWidget().getChildCount();
			for(int i=0; i<iCnt; i++)
				th.getTabWidget().getChildAt(i).getLayoutParams().height /= 2;
		}

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
				item.setEnabled(false);
				saveButton.setEnabled(true);
			} else {
				saveActivity();
				edit = false;
				WidgetUtil.setEditable(notes,  false);
				item.setEnabled(true);
				saveButton.setEnabled(false);
			}
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
			String[] from = new String[] { "_id", DB.LAP.LAP, DB.LAP.TYPE,
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
			} else {
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
			System.err.println("keso: " + tmp.getAsString(DB.ACTIVITY.COMMENT));
			notes.setText(tmp.getAsString(DB.ACTIVITY.COMMENT));
		}
	}

	@Override
	public View createTabContent(String tag) {
		if (tag.contentEquals("map")) {
			return mapViewLayout;
		} else if (tag.contentEquals("notes")) {
			return notes;
		}
		return null;
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
			TextView tv1 = (TextView) view.findViewById(R.id.lapList_id);
			tv1.setText(laps[position].getAsString("_id"));
			TextView tv2 = (TextView) view.findViewById(R.id.lapList_distance);
			float d = laps[position].getAsLong(DB.LAP.DISTANCE);
			tv2.setText(d + "m");
			TextView tv3 = (TextView) view.findViewById(R.id.lapList_time);
			long t = laps[position].getAsLong(DB.LAP.TIME);
			tv3.setText(DateUtils.formatElapsedTime(t));
			TextView tv4 = (TextView) view.findViewById(R.id.lapList_pace);
			tv4.setText(DateUtils.formatElapsedTime((long) (1000 * t / d))
					+ "/km");
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
			cb.setTag(tmp.getAsString("name"));
			if (alreadyUploadedUploaders.contains(name)) {
				cb.setChecked(true);
				cb.setEnabled(false);
				cb.setText("Sent");
			} else {
				cb.setChecked(true);
				cb.setTag(name);
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
			DetailActivity.this.setResult(RESULT_CANCELED);
			DetailActivity.this.finish();
		}
	};

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
				pendingUploaders.add((String) arg0.getTag());
			} else {
				pendingUploaders.remove((String) arg0.getTag());
			}
		}

	};

	OnClickListener deleteButtonClick = new OnClickListener() {
		public void onClick(View v) {
			AlertDialog.Builder builder = new AlertDialog.Builder(
					DetailActivity.this);
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

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	class RouteOverlay extends Overlay {
        private Paint paint = new Paint();
        private Point p1 = new Point(0,0);
        private Point p2 = new Point(0,0);
   		ArrayList<GeoPoint> path = new ArrayList<GeoPoint>();	
   		GeoPoint minPoint = null;
   		GeoPoint maxPoint = null;
   		
   		@Override
        public void draw(Canvas canvas, MapView mapview, boolean shadow) {
            super.draw(canvas, mapview, shadow);
            
            paint.setAntiAlias(true);
            paint.setARGB(100, 255, 0, 0);
            paint.setStrokeWidth(3);

            GeoPoint last = path.get(0);
            mapview.getProjection().toPixels(last, p1);

            for(GeoPoint p : path){
                mapview.getProjection().toPixels(p, p2);
                if (p1.x == p2.x && p1.y == p2.y)
                	continue;
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint);
                p1.x = p2.x;
                p1.y = p2.y;
            }
        }

		public GeoPoint getCenter() {
			return new GeoPoint((maxPoint.getLatitudeE6() + minPoint.getLatitudeE6()) / 2,
					(maxPoint.getLongitudeE6() + minPoint.getLongitudeE6()) / 2);
		}
	};

	private void loadRoute() {
		final String[] from = new String[] { 
				DB.LOCATION.LATITUDE,
				DB.LOCATION.LONGITUDE,
				DB.LOCATION.TYPE };
		
		loadRouteTask = new AsyncTask<String,String,RouteOverlay>() {

			@Override
			protected RouteOverlay doInBackground(String... params) {
				RouteOverlay route = null;
				Cursor c = mDB.query(DB.LOCATION.TABLE, from, "activity_id == " + mID,
						null, null, null, "_id", null);
				if (c.moveToFirst()) {
					route = new RouteOverlay();
					int minLat = Integer.MAX_VALUE;
					int maxLat = Integer.MIN_VALUE;
					int minLong = Integer.MAX_VALUE;
					int maxLong = Integer.MIN_VALUE;
					do {
						int latE6 = (int)(c.getDouble(0) * 1000000);
						int longE6 = (int)(c.getDouble(1) * 1000000);
						minLat = Math.min(minLat, latE6);
						maxLat = Math.max(maxLat, latE6);
						minLong = Math.min(minLong, longE6);
						maxLong = Math.max(maxLong, longE6);
						route.path.add(new GeoPoint(latE6, longE6));
					} while (c.moveToNext());
					System.err.println("Finished loading " + route.path.size() + " points");
					route.minPoint = new GeoPoint(minLat, minLong);
					route.maxPoint = new GeoPoint(maxLat, maxLong);
				}
				c.close();
				return route;
			}

			@Override
			protected void onPostExecute(RouteOverlay overlay) {
				if (overlay != null) {
					if (mapView != null) {
						mapView.getOverlays().add(overlay);
						double fitFactor = 1;
						mapView.getController().zoomToSpan((int) (Math.abs(overlay.maxPoint.getLatitudeE6() - overlay.minPoint.getLatitudeE6()) * fitFactor),
								(int)(Math.abs(overlay.maxPoint.getLongitudeE6() - overlay.minPoint.getLongitudeE6()) * fitFactor));
						mapView.getController().animateTo(overlay.getCenter());
		                mapView.postInvalidate(); 
						System.err.println("Added overlay");
					}
				}
			}
		}.execute("kalle");
	}
}
