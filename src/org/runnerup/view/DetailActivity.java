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

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.export.UploadManager;
import org.runnerup.export.format.RunKeeper;
import org.runnerup.export.format.TCX;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class DetailActivity extends Activity implements Constants {

	long mID = 0;
	DBHelper mDBHelper = null;
	SQLiteDatabase mDB = null;

	int mode; // 0 == save 1 == details
	final static int MODE_SAVE = 0;
	final static int MODE_DETAILS = 1;

	Button saveButton = null;
	Button discardButton = null;
	Button resumeButton = null;
	TextView activityTime = null;
	TextView activityDistance = null;
	TextView activityPace = null;
	ListView lapList = null;
	ListView reportList = null;
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

		lapList = (ListView) findViewById(R.id.lapList);
		reportList = (ListView) findViewById(R.id.accountList);

		if (this.mode == MODE_SAVE) {
			saveButton.setOnClickListener(saveButtonClick);
			discardButton.setOnClickListener(discardButtonClick);
			resumeButton.setOnClickListener(resumeButtonClick);

		} else if (this.mode == MODE_DETAILS) {
			saveButton.setText("OK");
			resumeButton.setText("Upload activity");
			discardButton.setText("Delete activity");
			discardButton.setOnClickListener(deleteButtonClick);
			resumeButton.setOnClickListener(uploadButtonClick);
		}

		lapList.setDividerHeight(2);
		reportList.setDividerHeight(2);
		fillHeaderData();
		fillLapData();
		fillReportData();
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

	void fillLapData() {
		// Fields from the database (projection)
		// Must include the _id column for the adapter to work
		String[] from = new String[] { "_id", DB.LAP.LAP,
				// DB.LAP.TYPE,
				DB.LAP.TIME, DB.LAP.DISTANCE, DB.LAP.PLANNED_TIME,
				DB.LAP.PLANNED_DISTANCE, DB.LAP.PLANNED_PACE };

		Cursor c = mDB.query(DB.LAP.TABLE, from, "activity_id == " + mID, null,
				null, null, "_id", null);
		this.startManagingCursor(c);
		CursorAdapter adapter = new LapListAdapter(this, c);
		lapList.setAdapter(adapter);
	}

	class LapListAdapter extends CursorAdapter {
		LayoutInflater inflater;
		java.text.DateFormat mDF = null;
		java.text.DateFormat mTF = null;

		public LapListAdapter(Context context, Cursor c) {
			super(context, c);
			inflater = LayoutInflater.from(context);
			mDF = android.text.format.DateFormat.getDateFormat(context);
			mTF = android.text.format.DateFormat.getTimeFormat(context);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			// id
			// DB.LAP.LAP,
			// // DB.LAP.TYPE,
			// DB.LAP.TIME,
			// DB.LAP.DISTANCE,
			int[] to = new int[] { R.id.lapList_id,
					// R.id.lapList_type,
					R.id.lapList_time, R.id.lapList_distance, R.id.lapList_pace };

			long id = cursor.getLong(0);
			long t = cursor.getLong(2);
			float d = cursor.getFloat(3);

			{
				TextView tv = (TextView) view.findViewById(R.id.lapList_type);
				tv.setText("lap");
			}

			if (!cursor.isNull(0)) {
				TextView tv = (TextView) view.findViewById(to[0]);
				tv.setText(Long.toString(id));
			}
			if (!cursor.isNull(2)) {
				TextView tv = (TextView) view.findViewById(to[1]);
				tv.setText(DateUtils.formatElapsedTime(t));
			}
			if (!cursor.isNull(3)) {
				TextView tv = (TextView) view.findViewById(to[2]);
				tv.setText("" + (long) d + " m");
			}
			if (!cursor.isNull(2) && !cursor.isNull(3)) {
				TextView tv = (TextView) view.findViewById(to[3]);
				tv.setText(DateUtils.formatElapsedTime((long) (1000 * t / d))
						+ "/km");
			}
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			return inflater.inflate(R.layout.laplist_row, parent, false);
		}
	};

	void fillReportData() {
		String sql = new String("SELECT DISTINCT " + "  acc._id, " // 0
				+ "  acc."
				+ DB.ACCOUNT.NAME
				+ ", " // 1
				+ "  acc."
				+ DB.ACCOUNT.DESCRIPTION
				+ ", " // 2
				+ "  acc."
				+ DB.ACCOUNT.DEFAULT
				+ ", " // 3
				+ "  acc."
				+ DB.ACCOUNT.AUTH_METHOD
				+ ", " // 4
				+ "  acc."
				+ DB.ACCOUNT.AUTH_CONFIG
				+ ", " // 5
				+ "  rep._id as repid, " // 6
				+ "  rep."
				+ DB.EXPORT.ACCOUNT
				+ ", " // 7
				+ "  rep."
				+ DB.EXPORT.ACTIVITY
				+ ", " // 8
				+ "  rep."
				+ DB.EXPORT.STATUS // 9
				+ " FROM " + DB.ACCOUNT.TABLE + " acc " + " LEFT OUTER JOIN "
				+ DB.EXPORT.TABLE + " rep " + " ON ( acc._id = rep."
				+ DB.EXPORT.ACCOUNT
				+ "                                    AND rep."
				+ DB.EXPORT.ACTIVITY + " = " + mID + " )" + " WHERE acc."
				+ DB.ACCOUNT.ENABLED + " != 0 ;");

		ArrayList<ContentValues> list = new ArrayList<ContentValues>();
		{
			Cursor c = mDB.rawQuery(sql, null);
			if (c.moveToFirst()) {
				do {
					ContentValues tmp = DBHelper.get(c);
					list.add(tmp);
					uploadManager.add(tmp);
				} while (c.moveToNext());
			}
			c.close();
		}
		ContentValues arr[] = new ContentValues[list.size()];
		ReportListAdapter adapter = new ReportListAdapter(this,
				list.toArray(arr));
		reportList.setAdapter(adapter);
	}

	class ReportListAdapter extends ArrayAdapter<ContentValues> {
		private final Context context;
		private final ContentValues values[];

		public ReportListAdapter(Context context, ContentValues values[]) {
			super(context, R.layout.reportlist_row, values);
			this.context = context;
			this.values = values;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			LayoutInflater inflater = LayoutInflater.from(context);
			View view = inflater
					.inflate(R.layout.reportlist_row, parent, false);
			TextView tv0 = (TextView) view.findViewById(R.id.accountId);
			CheckBox cb = (CheckBox) view.findViewById(R.id.reportSent);
			TextView tv1 = (TextView) view.findViewById(R.id.accountName);

			ContentValues tmp = values[position];

			cb.setTag(tmp.getAsString("name"));
			if (!tmp.containsKey(DB.EXPORT.ACCOUNT)) {
				cb.setChecked(true);
			} else {
				cb.setChecked(true);
				cb.setEnabled(false);
			}
			cb.setOnCheckedChangeListener(DetailActivity.this.onSendChecked);

			tv0.setText(tmp.getAsString("_id"));
			tv1.setText(tmp.getAsString("name"));
			return view;
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
			uploadManager.startUploading(mID);
		}
	};

	public OnCheckedChangeListener onSendChecked = new OnCheckedChangeListener() {

		@Override
		public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
			if (arg1 == true) {
				uploadManager.enable((String) arg0.getTag());
			} else {
				uploadManager.disable((String) arg0.getTag());
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
}