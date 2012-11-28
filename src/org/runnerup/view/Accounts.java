package org.runnerup.view;

import java.util.ArrayList;

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.util.Constants;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class Accounts extends ListActivity implements Constants {

	DBHelper mDBHelper = null;
	SQLiteDatabase mDB = null;
	ArrayList<Cursor> mCursors = new ArrayList<Cursor>();

	/** Called when the activity is first created. */

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.accounts);

		mDBHelper = new DBHelper(this);
		mDB = mDBHelper.getReadableDatabase();
		this.getListView().setDividerHeight(2);
		fillData();
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
		String[] from = new String[] { "_id", 
				DB.ACCOUNT.NAME,
				DB.ACCOUNT.URL,
				DB.ACCOUNT.DESCRIPTION,
				DB.ACCOUNT.ENABLED,
				DB.ACCOUNT.DEFAULT,
				null // DB.ACCOUNT.ICON
		};

		Cursor c = mDB.query(DB.ACCOUNT.TABLE, from, null, null,
				null, null, null, null);
		CursorAdapter adapter = new AccountListAdapter(this, c);
		setListAdapter(adapter);
		mCursors.add(c);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
	}

	public class AccountListAdapter extends CursorAdapter {
		LayoutInflater inflater;

		public AccountListAdapter(Context context, Cursor c) {
			super(context, c);
			inflater = LayoutInflater.from(context);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			int[] to = new int[] { R.id.historyList_id,
					R.id.historyList_startTime, R.id.historyList_distance,
					R.id.historyList_time, R.id.historyList_pace };

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
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			return inflater.inflate(R.layout.account_row, parent, false);
		}
	};
}