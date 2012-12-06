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

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.export.UploadManager;
import org.runnerup.util.Constants;

import android.app.ListActivity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class AccountsActivity extends ListActivity implements Constants, OnCheckedChangeListener, OnClickListener {

	DBHelper mDBHelper = null;
	SQLiteDatabase mDB = null;
	ArrayList<Cursor> mCursors = new ArrayList<Cursor>();
	UploadManager uploadManager = null;

	/** Called when the activity is first created. */

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.accounts);

		mDBHelper = new DBHelper(this);
		mDB = mDBHelper.getReadableDatabase();
		uploadManager = new UploadManager(this);
		this.getListView().setDividerHeight(10);
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
				DB.ACCOUNT.ICON,
				DB.ACCOUNT.AUTH_CONFIG
		};

		Cursor c = mDB.query(DB.ACCOUNT.TABLE, from, null, null,
				null, null, null, null);
		CursorAdapter adapter = new AccountListAdapter(this, c);
		setListAdapter(adapter);
		mCursors.add(c);
	}

	public class AccountListAdapter extends CursorAdapter {
		LayoutInflater inflater;

		public AccountListAdapter(Context context, Cursor c) {
			super(context, c);
			inflater = LayoutInflater.from(context);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			ContentValues tmp = DBHelper.get(cursor);
			
			long id = tmp.getAsLong("_id");
			
			{
				ImageView im = (ImageView) view.findViewById(R.id.accountList_icon);
				TextView tv = (TextView) view.findViewById(R.id.accountList_name);
				if (cursor.isNull(cursor.getColumnIndex(DB.ACCOUNT.ICON))) {
					im.setVisibility(View.GONE);
					tv.setVisibility(View.VISIBLE);
					tv.setText(tmp.getAsString(DB.ACCOUNT.NAME));
				} else {
					im.setVisibility(View.VISIBLE);
					tv.setVisibility(View.GONE);
					im.setBackgroundResource(tmp.getAsInteger(DB.ACCOUNT.ICON));
				}
			}
			
			{
				TextView tv = (TextView) view.findViewById(R.id.accountList_id);
				tv.setText(Long.toString(id));
			}

			{
				CheckBox cb = (CheckBox) view.findViewById(R.id.accountList_enabled);
				if (tmp.getAsInteger(DB.ACCOUNT.ENABLED) != 0)
					cb.setChecked(true);
				else
					cb.setChecked(false);
				cb.setTag(id);
				cb.setOnCheckedChangeListener(AccountsActivity.this);
			}
			{
				CheckBox cb = (CheckBox) view.findViewById(R.id.accountList_send);
				if (tmp.getAsInteger(DB.ACCOUNT.DEFAULT) != 0)
					cb.setChecked(true);
				else
					cb.setChecked(false);
				cb.setTag(id);
				cb.setOnCheckedChangeListener(AccountsActivity.this);
			}
			{
				Button b = (Button) view.findViewById(R.id.accountList_configureButton);
				b.setTag(id);
				b.setOnClickListener(AccountsActivity.this);
				if (cursor.isNull(cursor.getColumnIndex(DB.ACCOUNT.AUTH_CONFIG))) {
					b.setText("Configure");
				} else {
					b.setText("Reset");
				}
			}
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			return inflater.inflate(R.layout.account_row, parent, false);
		}
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		Toast.makeText(this, "" + buttonView + ", " + isChecked, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onClick(View v) {
		Toast.makeText(this, "" + v, Toast.LENGTH_SHORT).show();		
	};
}