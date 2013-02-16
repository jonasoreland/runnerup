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
import org.runnerup.util.Constants;

import android.app.ListActivity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

public class FeedActivity extends ListActivity implements Constants {

	DBHelper mDBHelper = null;
	SQLiteDatabase mDB = null;
	ArrayList<Cursor> mCursors = new ArrayList<Cursor>();

	/** Called when the activity is first created. */

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.history);

		mDBHelper = new DBHelper(this);
		mDB = mDBHelper.getReadableDatabase();
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
}