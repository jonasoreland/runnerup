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
import org.runnerup.export.UploadManager;
import org.runnerup.export.Uploader;
import org.runnerup.export.Uploader.Status;
import org.runnerup.util.Constants;

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
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class AccountListActivity extends ListActivity implements Constants {

	DBHelper mDBHelper = null;
	SQLiteDatabase mDB = null;
	ArrayList<Cursor> mCursors = new ArrayList<Cursor>();
	UploadManager uploadManager = null;
	
	/** Called when the activity is first created. */

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.account_list);

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
		uploadManager.close();
	}

	void fillData() {
		// Fields from the database (projection)
		// Must include the _id column for the adapter to work
		String[] from = new String[] { "_id", 
				DB.ACCOUNT.NAME,
				DB.ACCOUNT.URL,
				DB.ACCOUNT.DESCRIPTION,
				DB.ACCOUNT.ENABLED,
				DB.ACCOUNT.ICON,
				DB.ACCOUNT.AUTH_CONFIG
		};

		Cursor c = mDB.query(DB.ACCOUNT.TABLE, from, null, null,
				null, null, DB.ACCOUNT.ENABLED + " desc, " + DB.ACCOUNT.NAME);
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
			
			String id = tmp.getAsString(DB.ACCOUNT.NAME);
			uploadManager.add(tmp);
			
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
			
			boolean configured = uploadManager.isConfigured(id);
			
			{
				Button b = (Button) view.findViewById(R.id.accountList_configureButton);
				b.setTag(id);
				b.setOnClickListener(configureButtonClick);
				if (configured) {
					b.setText("Edit");
					b.setBackgroundDrawable(getResources().getDrawable(R.drawable.btn_blue));
				} else {
					b.setText("Connect");
					b.setBackgroundDrawable(getResources().getDrawable(R.drawable.btn_green));
				}
			}
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			return inflater.inflate(R.layout.account_row, parent, false);
		}
	}

	OnClickListener configureButtonClick = new OnClickListener() {
		public void onClick(View v) {
			final String uploader = (String)v.getTag();
			if (uploadManager.isConfigured(uploader)) {
				startActivity(uploader, true);
			} else {
				uploadManager.connect(callback, uploader, false);
			}
		}
	};
	
	UploadManager.Callback callback = new UploadManager.Callback() {
		@Override
		public void run(String uploader, Status status) {
			if (status == Uploader.Status.OK) {
				startActivity(uploader, false);
			}
		}
	};

	void startActivity(String uploader, boolean edit) {
		Intent intent = new Intent(AccountListActivity.this, AccountActivity.class);
		intent.putExtra("uploader", uploader);
		intent.putExtra("edit", edit);
		AccountListActivity.this.startActivityForResult(intent, UploadManager.CONFIGURE_REQUEST + 1000);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == UploadManager.CONFIGURE_REQUEST) {
			uploadManager.onActivityResult(requestCode, resultCode, data);
		} else if (requestCode == UploadManager.CONFIGURE_REQUEST + 1000) {
			uploadManager.clear();
			for (Cursor c : mCursors) {
				c.requery();
			}
		}
	}

}