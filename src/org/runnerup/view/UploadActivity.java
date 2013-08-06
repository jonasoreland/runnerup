/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.export.UploadManager;
import org.runnerup.export.Uploader.Status;
import org.runnerup.util.Constants;
import org.runnerup.util.Formatter;

import android.app.ListActivity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.TextView;

public class UploadActivity extends ListActivity implements Constants {

	long uploaderID = -1;
	String uploader = null;
	Integer uploaderIcon = null;
	UploadManager uploadManager = null;

	DBHelper mDBHelper = null;
	SQLiteDatabase mDB = null;
	Formatter formatter = null;
	ArrayList<ContentValues> uploadActivities = new ArrayList<ContentValues>();

	int uploadCount = 0;
	Button uploadButton = null;
	CharSequence uploadButtonText = null;
	
	boolean uploading = false;
	StringBuffer cancelUploading = new StringBuffer();
	
	/** Called when the activity is first created. */

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.upload);

		Intent intent = getIntent();
		uploader = intent.getStringExtra("uploader");
		uploaderID = intent.getLongExtra("uploaderID", -1);
		if (intent.hasExtra("uploaderIcon"))
			uploaderIcon = intent.getIntExtra("uploaderIcon", 0);
		
		mDBHelper = new DBHelper(this);
		mDB = mDBHelper.getReadableDatabase();
		formatter = new Formatter(this);
		uploadManager = new UploadManager(this);
		this.getListView().setDividerHeight(1);
		setListAdapter(new UploadListAdapter(this));

		{
			Button btn = (Button) findViewById(R.id.accountUploadSetAll);
			btn.setOnClickListener(setAllButtonClick);
		}

		{
			Button btn = (Button) findViewById(R.id.accountUploadClearAll);
			btn.setOnClickListener(clearAllButtonClick);
		}

		{
			Button btn = (Button) findViewById(R.id.accountUploadButton);
			btn.setOnClickListener(uploadButtonClick);

			uploadButton = btn;
			uploadButtonText = btn.getText();
		}
		
		{
			TextView tv = (TextView) findViewById(R.id.accountUploadList_name);
			ImageView im = (ImageView) findViewById(R.id.accountUploadList_icon);
			tv.setText(uploader);
			if (uploaderIcon == null) {
				im.setVisibility(View.GONE);
				tv.setVisibility(View.VISIBLE);
			} else {
				im.setVisibility(View.VISIBLE);
				tv.setVisibility(View.GONE);
				im.setBackgroundResource(uploaderIcon.intValue());
			}
		}

		fillData();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	public void onBackPressed() {
		if (uploading == true) {
			/**
			 * Cancel
			 */
			cancelUploading.append("1");
			return;
		}
		super.onBackPressed();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		mDB.close();
		mDBHelper.close();
		uploadManager.close();
	}

	void fillData() {
		// Fields from the database (projection)
		// Must include the _id column for the adapter to work
		final String[] from = new String[] { "_id", DB.ACTIVITY.START_TIME,
				DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME, DB.ACTIVITY.SPORT };

		final String w = "NOT EXISTS (SELECT 1 FROM " + DB.EXPORT.TABLE + " r WHERE r." + DB.EXPORT.ACTIVITY + " = " + DB.ACTIVITY.TABLE + "._id " +
				" AND r." + DB.EXPORT.ACCOUNT + " = " + uploaderID + ")";
		
		Cursor c = mDB.query(DB.ACTIVITY.TABLE, from,
				" deleted == 0 AND " + w, null,
				null, null, "_id desc", "100");
		uploadActivities.clear();
		if (c.moveToFirst()) {
			do {
				uploadActivities.add(DBHelper.get(c));
			} while (c.moveToNext());
		}
		c.close();
		uploadCount = uploadActivities.size();
		requery();
	}

	void requery() {
		((BaseAdapter)this.getListAdapter()).notifyDataSetChanged();
		if (uploadCount > 0) {
			uploadButton.setText(uploadButtonText + " (" + uploadCount + ")");
			uploadButton.setEnabled(true);
		} else {
			uploadButton.setText(uploadButtonText);
			uploadButton.setEnabled(false);
		}
	}
	
	OnClickListener onActivityClick = new OnClickListener() {

		@Override
		public void onClick(View arg0) {
			long id = (Long) arg0.getTag();
			Intent intent = new Intent(UploadActivity.this, DetailActivity.class);
			intent.putExtra("ID", id);
			intent.putExtra("mode", "details");
			startActivityForResult(intent, 100);
		}
	};
	
	public class UploadListAdapter extends BaseAdapter {
		LayoutInflater inflater;

		public UploadListAdapter(Context context) {
			super();
			inflater = LayoutInflater.from(context);
		}

		@Override
		public int getCount() {
			return uploadActivities.size();
		}

		@Override
		public Object getItem(int arg0) {
			return uploadActivities.get(arg0);
		}

		@Override
		public long getItemId(int arg0) {
			return uploadActivities.get(arg0).getAsLong("_id");
		}

		@Override
		public View getView(int arg0, View arg1, ViewGroup parent) {
			View view = inflater.inflate(R.layout.upload_row, parent, false);
			ContentValues tmp = uploadActivities.get(arg0);

			long id = tmp.getAsLong("_id");
			float d = 0;
			if (tmp.containsKey(DB.ACTIVITY.DISTANCE)) {
				d = tmp.getAsFloat(DB.ACTIVITY.DISTANCE);
			}
			long t = 0;
			if (tmp.containsKey(DB.ACTIVITY.TIME)) {
				t = tmp.getAsFloat(DB.ACTIVITY.TIME).longValue();
			}

			{
				TextView tv = (TextView) view.findViewById(R.id.uploadList_startTime);
				if (tmp.containsKey(DB.ACTIVITY.START_TIME)) {
					tv.setText(formatter.formatDateTime(Formatter.TXT_LONG,
							tmp.getAsLong(DB.ACTIVITY.START_TIME)));
				} else {
					tv.setText("");
				}
			}

			{
				TextView tv = (TextView) view.findViewById(R.id.uploadList_distance);
				if (tmp.containsKey(DB.ACTIVITY.DISTANCE)) {
					tv.setText(formatter.formatDistance(Formatter.TXT_SHORT, (long) d));
				} else {
					tv.setText("");
				}
			}

			{
				TextView tv = (TextView) view.findViewById(R.id.uploadList_time);
				if (tmp.containsKey(DB.ACTIVITY.TIME)) {
					tv.setText(formatter.formatElapsedTime(Formatter.TXT_SHORT, t));
				} else {
					tv.setText("");
				}
			}

			{
				TextView tv = (TextView) view.findViewById(R.id.uploadList_pace);
				if (tmp.containsKey(DB.ACTIVITY.DISTANCE) && tmp.containsKey(DB.ACTIVITY.TIME) && d != 0 && t != 0) {
					tv.setText(formatter.formatPace(Formatter.TXT_LONG, t/d));
				} else {
					tv.setText("");
				}
			}

			{
				TextView tv = (TextView) view.findViewById(R.id.uploadList_sport);
				if (!tmp.containsKey(DB.ACTIVITY.SPORT)) {
					tv.setText("Running");
				} else {
					switch(tmp.getAsInteger(DB.ACTIVITY.SPORT)) {
					case DB.ACTIVITY.SPORT_RUNNING:
						tv.setText("Running");
						break;
					case DB.ACTIVITY.SPORT_BIKING:
						tv.setText("Biking");
						break;
					default:
						tv.setText("Unknown?? (" + tmp.getAsInteger(DB.ACTIVITY.SPORT) + ")");
						break;
					}
				}
			}

			{
				CheckBox cb = (CheckBox) view.findViewById(R.id.uploadList_check);
				cb.setTag(Integer.valueOf(arg0));
				cb.setOnCheckedChangeListener(checkedChangeClick);
				if (tmp.containsKey("skip")) {
					cb.setChecked(false);
				} else {
					cb.setChecked(true);
				}
			}

			view.setTag(id);
			view.setOnClickListener(onActivityClick);
			
			return view;
		}
	};

	OnCheckedChangeListener checkedChangeClick = new OnCheckedChangeListener() {

		@Override
		public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
			int pos = (Integer)arg0.getTag();
			ContentValues tmp = uploadActivities.get(pos);
			if (!tmp.containsKey("skip"))
				uploadCount--;
			if (arg1) {
				tmp.remove("skip");
				uploadCount++;
			} else {
				tmp.put("skip",  true);
			}
			requery();
		}
		
	};
	
	OnClickListener uploadButtonClick = new OnClickListener() {
		@Override
		public void onClick(View v) {
			ArrayList<Long> activities = new ArrayList<Long>();
			for (ContentValues tmp : uploadActivities) {
				if (!tmp.containsKey("skip"))
					activities.add(tmp.getAsLong("_id"));
			}
			if (activities.isEmpty()) {
				return;
			}

			System.err.println("Start uploading " + activities.size());
			uploading = true;
			cancelUploading.delete(0,  cancelUploading.length());
			uploadManager.uploadWorkouts(uploadCallback, uploader, activities, cancelUploading);
		}
	};

	UploadManager.Callback uploadCallback = new UploadManager.Callback() {

		@Override
		public void run(String uploader, Status status) {
			uploading = false;
			if (cancelUploading.length() > 0 || status == Status.CANCEL) {
				finish();
				return;
			}
			fillData();
		}
	};
	
	OnClickListener clearAllButtonClick = new OnClickListener() {
		@Override
		public void onClick(View v) {
			for (ContentValues tmp : uploadActivities) {
				tmp.put("skip",  true);
			}
			uploadCount = 0;
			requery();
		}
	};

	OnClickListener setAllButtonClick = new OnClickListener() {
		@Override
		public void onClick(View v) {
			for (ContentValues tmp : uploadActivities) {
				tmp.remove("skip");
			}
			uploadCount = uploadActivities.size();
			requery();
		}
	};

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		fillData();
	}
}