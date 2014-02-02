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
import org.runnerup.export.Uploader;
import org.runnerup.export.Uploader.Status;
import org.runnerup.util.Bitfield;
import org.runnerup.util.Constants;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import android.os.Build;
import android.annotation.TargetApi;

@TargetApi(Build.VERSION_CODES.FROYO)
public class AccountActivity extends Activity implements Constants {

	long uploaderID = -1;
	String uploader = null;
	Integer uploaderIcon = null;
	
	DBHelper mDBHelper = null;
	SQLiteDatabase mDB = null;
	ArrayList<Cursor> mCursors = new ArrayList<Cursor>();

	long flags;
	UploadManager uploadManager = null;

	/** Called when the activity is first created. */

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.account);

		Intent intent = getIntent();
		uploader = intent.getStringExtra("uploader");
		
		mDBHelper = new DBHelper(this);
		mDB = mDBHelper.getReadableDatabase();
		uploadManager = new UploadManager(this);
		fillData();

		
		{
			Button btn = (Button) findViewById(R.id.okAccountButton);
			btn.setOnClickListener(okButtonClick);
		}

		{
			Button btn = (Button) findViewById(R.id.accountUploadButton);
			btn.setOnClickListener(uploadButtonClick);
		}

		{
			Button btn = (Button) findViewById(R.id.disconnectAccountButton);
			btn.setOnClickListener(disconnectButtonClick);
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
				DB.ACCOUNT.FLAGS,
				DB.ACCOUNT.ICON,
				DB.ACCOUNT.AUTH_CONFIG
		};

		String args[] = { uploader };
		Cursor c = mDB.query(DB.ACCOUNT.TABLE, from, DB.ACCOUNT.NAME + " = ?", args,
				null, null, null);
		
		if (c.moveToFirst()) {
			ContentValues tmp = DBHelper.get(c);
			uploaderID = tmp.getAsLong("_id");
			Uploader uploader = uploadManager.add(tmp);
			
			{
				ImageView im = (ImageView) findViewById(R.id.accountList_icon);
				TextView tv = (TextView) findViewById(R.id.accountList_name);
				tv.setText(tmp.getAsString(DB.ACCOUNT.NAME));
				if (c.isNull(c.getColumnIndex(DB.ACCOUNT.ICON))) {
					im.setVisibility(View.GONE);
					tv.setVisibility(View.VISIBLE);
				} else {
					im.setVisibility(View.VISIBLE);
					tv.setVisibility(View.GONE);
					im.setBackgroundResource(tmp.getAsInteger(DB.ACCOUNT.ICON));
					uploaderIcon = tmp.getAsInteger(DB.ACCOUNT.ICON);
				}
			}

			addRow("", null);

			if (tmp.containsKey(DB.ACCOUNT.URL)) {
				Button btn = new Button(this);
				btn.setText(tmp.getAsString(DB.ACCOUNT.URL));
				btn.setOnClickListener(urlButtonClick);
				btn.setTag(tmp.getAsString(DB.ACCOUNT.URL));
				addRow("Website:", btn);
			}

			flags = tmp.getAsLong(DB.ACCOUNT.FLAGS);
			if (uploader.checkSupport(Uploader.Feature.UPLOAD)) {
				CheckBox cb = new CheckBox(this);
				cb.setTag(Integer.valueOf(DB.ACCOUNT.FLAG_UPLOAD));
				cb.setChecked(Bitfield.test(flags, DB.ACCOUNT.FLAG_UPLOAD)); 
				cb.setOnCheckedChangeListener(sendCBChecked);
				addRow("Automatic upload", cb);
			} else {
				Button btn = (Button) findViewById(R.id.accountUploadButton);
				btn.setVisibility(View.GONE);
			}

			if (uploader.checkSupport(Uploader.Feature.FEED)) {
				CheckBox cb = new CheckBox(this);
				cb.setTag(Integer.valueOf(DB.ACCOUNT.FLAG_FEED));
				cb.setChecked(Bitfield.test(flags, DB.ACCOUNT.FLAG_FEED)); 
				cb.setOnCheckedChangeListener(sendCBChecked);
				addRow("Feed", cb);
			}

			if (uploader.checkSupport(Uploader.Feature.LIVE)) {
				CheckBox cb = new CheckBox(this);
				cb.setTag(Integer.valueOf(DB.ACCOUNT.FLAG_LIVE));
				cb.setChecked(Bitfield.test(flags, DB.ACCOUNT.FLAG_LIVE)); 
				cb.setOnCheckedChangeListener(sendCBChecked);
				addRow("Live", cb);
			}

			if (uploader.checkSupport(Uploader.Feature.SKIP_MAP)) {
				CheckBox cb = new CheckBox(this);
				cb.setTag(Integer.valueOf(DB.ACCOUNT.FLAG_SKIP_MAP));
				cb.setChecked(! Bitfield.test(flags, DB.ACCOUNT.FLAG_SKIP_MAP)); 
				cb.setOnCheckedChangeListener(sendCBChecked);
				addRow("Include map in post", cb);
			}
		}
		mCursors.add(c);
	}
	
	void addRow(String string, View btn) {
		TableLayout table = (TableLayout) findViewById(R.id.accountTable);
		TableRow row = new TableRow(this);
		TextView title = new TextView(this);
		title.setText(string);
		row.addView(title);
		if (btn != null)
			row.addView(btn);
		table.addView(row);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.account_menu, menu);
		return true;
	}

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_clear_uploads:
			clearUploadsButtonClick.onClick(null);
			break;
		case R.id.menu_upload_workouts:
			uploadButtonClick.onClick(null);
			break;
		case R.id.menu_disconnect_account:
			disconnectButtonClick.onClick(null);
			break;
		}
		return true;
	}
		
	OnClickListener clearUploadsButtonClick = new OnClickListener() {
		@Override
		public void onClick(View v) {
			AlertDialog.Builder builder = new AlertDialog.Builder(
					AccountActivity.this);
			builder.setTitle("Clear uploads");
			builder.setMessage("Note that workouts are not removed from " + uploader + ", only from RunnerUp list of workouts uploaded to " + uploader);
			builder.setPositiveButton("OK",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							uploadManager.clearUploads(callback, uploader);
						}
					});

			builder.setNegativeButton("Cancel",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							// Do nothing but close the dialog
							dialog.dismiss();
						}

					});
			builder.show();
		}
	};
	
	OnClickListener uploadButtonClick = new OnClickListener() {
		@Override
		public void onClick(View v) {
			final Intent intent = new Intent(AccountActivity.this, UploadActivity.class);
			intent.putExtra("uploader", uploader);
			intent.putExtra("uploaderID", uploaderID);
			if (uploaderIcon != null)
				intent.putExtra("uploaderIcon", uploaderIcon.intValue());
			AccountActivity.this.startActivityForResult(intent, 113);
		}
	};

	OnClickListener urlButtonClick = new OnClickListener() {
		@Override
		public void onClick(View v) {
			final Intent intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse((String)v.getTag()));
			startActivity(intent);
		}		
	};
	
	OnCheckedChangeListener sendCBChecked = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			ContentValues tmp = new ContentValues();
			int flag = ((Integer)buttonView.getTag()).intValue();
			switch (flag) {
			case DB.ACCOUNT.FLAG_UPLOAD:
			case DB.ACCOUNT.FLAG_FEED:
			case DB.ACCOUNT.FLAG_LIVE:
				flags = Bitfield.set(flags, flag, isChecked);
				break;
			case DB.ACCOUNT.FLAG_SKIP_MAP:
				flags = Bitfield.set(flags, flag, !isChecked);
			}
			tmp.put(DB.ACCOUNT.FLAGS, flags);
			String args[] = { uploader };
			mDB.update(DB.ACCOUNT.TABLE, tmp, "name = ?", args);
		}
	};
	
	OnClickListener okButtonClick = new OnClickListener() {
		@Override
		public void onClick(View v) {
			finish();
		}
	};

	OnClickListener disconnectButtonClick = new OnClickListener() {
		public void onClick(View v) {
			final CharSequence items[] = { "Clear uploads" };
			final boolean selected[] = { true };
			AlertDialog.Builder builder = new AlertDialog.Builder(
					AccountActivity.this);
			builder.setTitle("Disconnect account");
			builder.setPositiveButton("OK",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							uploadManager.disableUploader(disconnectCallback, uploader,
									selected[0]);
						}
					});
			builder.setNegativeButton("Cancel",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							// Do nothing but close the dialog
							dialog.dismiss();
						}

					});
			builder.setMultiChoiceItems(items, selected,
					new OnMultiChoiceClickListener() {
						@Override
						public void onClick(DialogInterface arg0, int arg1,
								boolean arg2) {
							selected[arg1] = arg2;
						}
					});
			builder.show();
		}
	};
	
	UploadManager.Callback callback = new UploadManager.Callback() {
		@Override
		public void run(String uploader, Status status) {
		}
	};

	UploadManager.Callback disconnectCallback = new UploadManager.Callback() {
		@Override
		public void run(String uploader, Status status) {
			finish();
		}
	};

}