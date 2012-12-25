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
package org.runnerup.view;

import java.util.ArrayList;
import java.util.HashSet;

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.export.UploadManager;
import org.runnerup.export.Uploader;
import org.runnerup.export.oauth2client.OAuth2Activity;
import org.runnerup.util.Constants;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.TextView;

public class DetailActivity extends Activity implements Constants {

	long mID = 0;
	DBHelper mDBHelper = null;
	SQLiteDatabase mDB = null;
	ArrayList<Cursor> mCursors = new ArrayList<Cursor>();
	HashSet<String> pendingUploaders = new HashSet<String>();
	HashSet<String> alreadyUploadedUploaders = new HashSet<String>();

	static final String groups[] = { 
		"Laps", // lap list
		"Map", // map view
		"Notes", // activity notes
		"Reports"// uploads
	};
	ContentValues laps[] = null;
	ContentValues reports[] = null;
	
	int mode; // 0 == save 1 == details
	final static int MODE_SAVE = 0;
	final static int MODE_DETAILS = 1;

	Button saveButton = null;
	Button discardButton = null;
	Button resumeButton = null;
	TextView activityTime = null;
	TextView activityDistance = null;
	TextView activityPace = null;
	ExpandableListView expandableListView = null;
	
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

		expandableListView = (ExpandableListView) findViewById(R.id.ExpandableListView1);
		saveButton = (Button) findViewById(R.id.saveButton);
		discardButton = (Button) findViewById(R.id.discardButton);
		resumeButton = (Button) findViewById(R.id.resumeButton);
		activityTime = (TextView) findViewById(R.id.activityTime);
		activityDistance = (TextView) findViewById(R.id.activityDistance);
		activityPace = (TextView) findViewById(R.id.activityPace);
		
		if (this.mode == MODE_SAVE) {
			saveButton.setOnClickListener(saveButtonClick);
			discardButton.setOnClickListener(discardButtonClick);
			resumeButton.setOnClickListener(resumeButtonClick);

		} else if (this.mode == MODE_DETAILS) {
			saveButton.setVisibility(View.GONE);
			resumeButton.setVisibility(View.GONE);
			discardButton.setText("Delete activity");
			discardButton.setOnClickListener(deleteButtonClick);
		}

		{
			/**
			 * Laps
			 */
			String[] from = new String[] { "_id", DB.LAP.LAP, DB.LAP.TYPE,
					DB.LAP.TIME, DB.LAP.DISTANCE, DB.LAP.PLANNED_TIME,
					DB.LAP.PLANNED_DISTANCE, DB.LAP.PLANNED_PACE };

			Cursor c = mDB.query(DB.LAP.TABLE, from, "activity_id == " + mID,
					null, null, null, "_id", null);

			laps = DBHelper.toArray(c);
			c.close();
		}

		{
			/**
			 * Accounts/reports
			 */
		String sql = new String("SELECT DISTINCT " + "  acc._id, " // 0
				+ ("  acc." + DB.ACCOUNT.NAME + ", ")
				+ ("  acc." + DB.ACCOUNT.DESCRIPTION + ", ")
				+ ("  acc." + DB.ACCOUNT.DEFAULT + ", ")
				+ ("  acc."	+ DB.ACCOUNT.AUTH_METHOD + ", ")
				+ ("  acc." + DB.ACCOUNT.AUTH_CONFIG + ", ")
				+ ("  acc." + DB.ACCOUNT.ENABLED + ", ")     
				+ ("  rep._id as repid, ")
				+ ("  rep." + DB.EXPORT.ACCOUNT + ", ")
				+ ("  rep."	+ DB.EXPORT.ACTIVITY + ", ")
				+ ("  rep." + DB.EXPORT.STATUS )
				+ (" FROM " + DB.ACCOUNT.TABLE + " acc " ) 
				+ (" LEFT OUTER JOIN " + DB.EXPORT.TABLE + " rep ")
				+ (" ON ( acc._id = rep." + DB.EXPORT.ACCOUNT )
				+ ("     AND rep."	+ DB.EXPORT.ACTIVITY + " = " + mID + " )")
				+ (" WHERE acc." + DB.ACCOUNT.ENABLED + " != 0 ;"));

			Cursor c = mDB.rawQuery(sql, null);
			reports = DBHelper.toArray(c);
			c.close();
		}
		
		fillHeaderData();
		expandableListView.setAdapter(new Adapter(this));
		expandableListView.expandGroup(position(groups, "Laps"));
		expandableListView.expandGroup(position(groups, "Notes"));
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mDB.close();
		mDBHelper.close();
		uploadManager.close();
	}

	void fillHeaderData() {
		// Fields from the database (projection)
		// Must include the _id column for the adapter to work
		String[] from = new String[] { DB.ACTIVITY.START_TIME,
				DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME };

		Cursor c = mDB.query(DB.ACTIVITY.TABLE, from, "_id == " + mID, null,
				null, null, null, null);
		c.moveToFirst();

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
		if (!c.isNull(1)) {
			d = c.getFloat(1);
			activityDistance.setText(Long.toString((long) d) + " m");
		}

		long t = 0;
		if (!c.isNull(2)) {
			t = c.getLong(2);
			activityTime.setText(DateUtils.formatElapsedTime(t));
		}

		if (!c.isNull(1) && !c.isNull(2)) {
			activityPace.setText(DateUtils
					.formatElapsedTime((long) (1000 * t / d)) + "/km");
		}

		c.close();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
	    super.onWindowFocusChanged(hasFocus);
	    expandableListView.setIndicatorBounds(expandableListView.getRight()- 40, expandableListView.getWidth());
	}	
	
	class Adapter extends BaseExpandableListAdapter {
		LayoutInflater inflater = null;

		public Adapter(Context ctx) {
			super();
			inflater = LayoutInflater.from(ctx);
		}

		@Override
		public Object getChild(int groupPosition, int childPosition) {
			if (groups[groupPosition].contentEquals("laps"))
				return laps[childPosition];
			else if (groups[groupPosition].contentEquals("map"))
				return mID;
			else if (groups[groupPosition].contentEquals("notes"))
				return mID;
			else if (groups[groupPosition].contentEquals("reports"))
				return reports[childPosition];

			throw new IllegalArgumentException("Invalid groupPosition: " + groupPosition + ", max: " + groups.length);
		}

		@Override
		public long getChildId(int groupPosition, int childPosition) {
			if (groups[groupPosition].contentEquals("Laps"))
				return laps[childPosition].getAsLong("_id");
			else if (groups[groupPosition].contentEquals("Map"))
				return mID;
			else if (groups[groupPosition].contentEquals("Notes"))
				return mID;
			else if (groups[groupPosition].contentEquals("Reports"))
				return reports[childPosition].getAsLong("_id"); // account id

			throw new IllegalArgumentException("Invalid groupPosition: " + groupPosition + ", max: " + groups.length);
		}

		@Override
		public int getChildrenCount(int groupPosition) {
			if (groups[groupPosition].contentEquals("Laps"))
				return laps.length;
			else if (groups[groupPosition].contentEquals("Map"))
				return 1;
			else if (groups[groupPosition].contentEquals("Notes"))
				return 1;
			else if (groups[groupPosition].contentEquals("Reports"))
				return reports.length + 1;

			throw new IllegalArgumentException("Invalid groupPosition: " + groupPosition + ", max: " + groups.length);
		}

		@Override
		public Object getGroup(int groupPosition) {
			return groups[groupPosition];
		}

		@Override
		public int getGroupCount() {
			return groups.length;
		}

		@Override
		public long getGroupId(int groupPosition) {
			return groupPosition;
		}

		@Override
		public View getGroupView(int groupPosition, boolean isExpanded,
				View convertView, ViewGroup parent) {
			View view = inflater.inflate(R.layout.detail_row, parent, false);
			TextView tv = (TextView) view.findViewById(R.id.groupName);
			tv.setText(groups[groupPosition]);
			return view;
		}

		@Override
		public View getChildView(int groupPosition, int childPosition,
				boolean isLastChild, View convertView, ViewGroup parent) {
			
			if (groups[groupPosition].contentEquals("Laps"))
				return getLapsView(childPosition, isLastChild, convertView, parent);
			else if (groups[groupPosition].contentEquals("Map"))
				return getMapsView(childPosition, isLastChild, convertView, parent);
			else if (groups[groupPosition].contentEquals("Notes"))
				return getNotesView(childPosition, isLastChild, convertView, parent);
			else if (groups[groupPosition].contentEquals("Reports"))
				return getReportsView(childPosition, isLastChild, convertView, parent);

			throw new IllegalArgumentException("Invalid groupPosition: " + groupPosition + ", max: " + groups.length);
		}

		private View getLapsView(int childPosition, boolean isLastChild,
				View convertView, ViewGroup parent) {
			View view = inflater.inflate(R.layout.laplist_row, parent, false);
		    TextView tv1 = (TextView) view.findViewById(R.id.lapList_id);
	        tv1.setText(laps[childPosition].getAsString("_id"));
	        TextView tv2 = (TextView) view.findViewById(R.id.lapList_distance);			
			float d = laps[childPosition].getAsLong(DB.LAP.DISTANCE);
	        tv2.setText(d + "m");
	        TextView tv3 = (TextView) view.findViewById(R.id.lapList_time);			
	        long t = laps[childPosition].getAsLong(DB.LAP.TIME);
	        tv3.setText(DateUtils.formatElapsedTime(t));
	        TextView tv4 = (TextView) view.findViewById(R.id.lapList_pace);			
			tv4.setText(DateUtils.formatElapsedTime((long) (1000 * t / d))
					+ "/km");
	        return view;
		}

		private View getNotesView(int childPosition, boolean isLastChild,
				View convertView, ViewGroup parent) {
			// TODO Auto-generated method stub
			return new EditText(DetailActivity.this);
		}

		private View getMapsView(int childPosition, boolean isLastChild,
				View convertView, ViewGroup parent) {
			// TODO Auto-generated method stub
			return new View(DetailActivity.this);
		}

		private View getReportsView(int childPosition, boolean isLastChild,
				View convertView, ViewGroup parent) {

			if (childPosition == reports.length) {
				Button b = new Button(DetailActivity.this);
				b.setText("Configure accounts");
				b.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent i = new Intent(DetailActivity.this, AccountsActivity.class);
						DetailActivity.this.startActivityForResult(i, UploadManager.CONFIGURE_REQUEST + 1);
					}
				});
				return b;
			}
			
			View view = inflater.inflate(R.layout.reportlist_row, parent, false);

			TextView tv0 = (TextView) view.findViewById(R.id.accountId);
			CheckBox cb = (CheckBox) view.findViewById(R.id.reportSent);
			TextView tv1 = (TextView) view.findViewById(R.id.accountName);

			ContentValues tmp = reports[childPosition];

			String name = tmp.getAsString("name");
			cb.setTag(tmp.getAsString("name"));
			if (alreadyUploadedUploaders.contains(name)) {
				cb.setChecked(true);
			} else {
				cb.setChecked(true);
				cb.setEnabled(false);
				cb.setText("Sent");
			}
			cb.setOnCheckedChangeListener(DetailActivity.this.onSendChecked);

			tv0.setText(tmp.getAsString("_id"));
			tv1.setText(name);
			return view;
		}

		@Override
		public boolean hasStableIds() {
			return true;
		}

		@Override
		public boolean isChildSelectable(int groupPosition, int childPosition) {
			// TODO Auto-generated method stub
			return false;
		}
	};
	
	OnClickListener saveButtonClick = new OnClickListener() {
		public void onClick(View v) {
			DetailActivity.this.setResult(RESULT_OK);
			DetailActivity.this.finish();
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
			return;
		}
	}

	public static int position(String arr[], String key) {
		for (int i = 0; i < arr.length; i++) {
			if (arr[i].contentEquals(key))
					return i;
		}
		return -1;
	}
}