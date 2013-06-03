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

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.util.Constants;
import org.runnerup.util.Formatter;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class HistoryActivity extends ListActivity implements Constants {

	DBHelper mDBHelper = null;
	SQLiteDatabase mDB = null;
	Formatter formatter = null;
	ArrayList<Cursor> mCursors = new ArrayList<Cursor>();

	/** Called when the activity is first created. */

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.history);

		mDBHelper = new DBHelper(this);
		mDB = mDBHelper.getReadableDatabase();
		formatter = new Formatter(this);
		this.getListView().setDividerHeight(2);
		fillData();
	}

	@Override
	protected void onResume() {
		super.onResume();
		for (Cursor c : mCursors) {
			c.requery();
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mDB.close();
		mDBHelper.close();
		for (Cursor c : mCursors) {
			c.close();
		}
		mCursors.clear();
	}

	void fillData() {
		// Fields from the database (projection)
		// Must include the _id column for the adapter to work
		String[] from = new String[] { "_id", DB.ACTIVITY.START_TIME,
				DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME, DB.ACTIVITY.SPORT };

		Cursor c = mDB.query(DB.ACTIVITY.TABLE, from, "deleted == 0", null,
				null, null, "_id desc", null);
		CursorAdapter adapter = new HistoryListAdapter(this, c);
		setListAdapter(adapter);
		mCursors.add(c);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		Intent intent = new Intent(this, DetailActivity.class);
		intent.putExtra("ID", id);
		intent.putExtra("mode", "details");
		startActivity(intent);
	}

	public class HistoryListAdapter extends CursorAdapter {
		LayoutInflater inflater;

		public HistoryListAdapter(Context context, Cursor c) {
			super(context, c);
			inflater = LayoutInflater.from(context);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			int[] to = new int[] { R.id.historyList_id,
					R.id.historyList_startTime, R.id.historyList_distance,
					R.id.historyList_time, R.id.historyList_pace, R.id.historyList_sport };

			int id = cursor.getInt(0);
			long st = 0;
			if (!cursor.isNull(1)) {
				st = cursor.getLong(1); // start time
			}
			float d = 0;
			if (!cursor.isNull(2)) {
				d = cursor.getFloat(2); // distance
			}
			long t = 0;
			if (!cursor.isNull(3)) {
				t = cursor.getLong(3); // time (us)
			}

			{
				TextView tv = (TextView) view.findViewById(to[0]);
				tv.setText(Integer.toString(id));
			}

			{
				TextView tv = (TextView) view.findViewById(to[1]);
				if (!cursor.isNull(1)) {
					tv.setText(formatter.formatDateTime(Formatter.TXT_LONG, st));
				} else {
					tv.setText("");
				}
			}

			{
				TextView tv = (TextView) view.findViewById(to[2]);
				if (!cursor.isNull(2)) {
					tv.setText(formatter.formatDistance(Formatter.TXT_SHORT, (long)d));
				} else {
					tv.setText("");
				}
			}

			{
				TextView tv = (TextView) view.findViewById(to[3]);
				if (!cursor.isNull(3)) {
					tv.setText(formatter.formatElapsedTime(Formatter.TXT_SHORT, t));
				} else {
					tv.setText("");
				}
			}

			{
				TextView tv = (TextView) view.findViewById(to[4]);
				if (!cursor.isNull(3) && !cursor.isNull(3) && d != 0 && t != 0) {
					tv.setText(formatter.formatPace(Formatter.TXT_LONG, t/d));
				} else {
					tv.setText("");
				}
			}

			{
				TextView tv = (TextView) view.findViewById(to[5]);
				if (cursor.isNull(4)) {
					tv.setText("Running");
				} else {
					switch(cursor.getInt(4)) {
					case DB.ACTIVITY.SPORT_RUNNING:
						tv.setText("Running");
						break;
					case DB.ACTIVITY.SPORT_BIKING:
						tv.setText("Biking");
						break;
					default:
						tv.setText("Unknown?? (" + cursor.getInt(4) + ")");
						break;
					}
				}
			}
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			return inflater.inflate(R.layout.history_row, parent, false);
		}
	};
}