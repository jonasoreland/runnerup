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
import java.util.HashSet;
import java.util.List;

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.export.UploadManager;
import org.runnerup.export.UploadManager.WorkoutRef;
import org.runnerup.export.Uploader;
import org.runnerup.export.Uploader.Status;
import org.runnerup.util.Constants;

import android.app.Activity;
import android.content.ContentValues;
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
import android.widget.ListView;
import android.widget.TextView;

public class ManageWorkoutsActivity extends Activity implements Constants {

	DBHelper mDBHelper = null;
	SQLiteDatabase mDB = null;
	HashSet<String> pendingUploaders = new HashSet<String>();
	HashSet<UploadManager.WorkoutRef> pendingWorkouts = new HashSet<UploadManager.WorkoutRef>();
	
	ContentValues accounts[] = new ContentValues[1];
	ArrayList<BaseAdapter> adapters = new ArrayList<BaseAdapter>(2);
	ArrayList<UploadManager.WorkoutRef> workoutRef = new ArrayList<UploadManager.WorkoutRef>();
	
	boolean uploading = false;
	Button loadListButton = null;
	Button loadWorkoutsButton;
	
	UploadManager uploadManager = null;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.manage_workouts_account_list);

		mDBHelper = new DBHelper(this);
		mDB = mDBHelper.getReadableDatabase();
		uploadManager = new UploadManager(this);
		
		loadListButton = (Button) findViewById(R.id.listWorkoutsButton);
		loadListButton.setOnClickListener(loadListButtonClick);
		
		loadWorkoutsButton = (Button) findViewById(R.id.syncronizeWorkoutsButton);
		loadWorkoutsButton.setOnClickListener(loadWorkoutButtonClick);
		
		requery();
		listLocal();
		
		{
			ListView lv = (ListView) findViewById(R.id.listWorkoutsAccountList);
			AccountListAdapter adapter = new AccountListAdapter();
			adapters.add(adapter);
			lv.setAdapter(adapter);
		}

		{
			ListView lv = (ListView) findViewById(R.id.listWorkouts);
			WorkoutListAdapter adapter = new WorkoutListAdapter();
			adapters.add(adapter);
			lv.setAdapter(adapter);
		}
	}

	void listLocal() {
		ArrayList<UploadManager.WorkoutRef> newlist = new ArrayList<UploadManager.WorkoutRef>();
		String [] list = org.runnerup.view.WorkoutListAdapter.load(this);
		if (list != null) {
			for (String s : list) {
				newlist.add(new UploadManager.WorkoutRef(null, null, s));
			}
		}
		workoutRef = filter(workoutRef, newlist, new Filter<UploadManager.WorkoutRef>() {
			@Override
			public boolean match(WorkoutRef t) {
				return t.uploader != null;
			}});
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		mDB.close();
		mDBHelper.close();
		uploadManager.close();
	}
	
	void requery() {
		ContentValues alluploaders[] = null;
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
							+ ("  acc." + DB.ACCOUNT.ENABLED + " ")
							+ (" FROM " + DB.ACCOUNT.TABLE + " acc ")
							+ (" WHERE acc." + DB.ACCOUNT.ENABLED + " != 0 ")
							+ ("   AND acc." + DB.ACCOUNT.AUTH_CONFIG + " is not null"));

			Cursor c = mDB.rawQuery(sql, null);
			alluploaders = DBHelper.toArray(c);
			c.close();
		}

		pendingUploaders.clear();
		ArrayList<ContentValues> list = new ArrayList<ContentValues>(alluploaders.length);
		for (ContentValues tmp : alluploaders) {
			Uploader uploader = uploadManager.add(tmp);
			if (uploader != null && uploader.checkSupport(Uploader.Feature.WORKOUT_LIST)) {
				boolean showList = true;
				if (showList) {
					pendingUploaders.add(tmp.getAsString(DB.ACCOUNT.NAME));
				}
				list.add(tmp);
			}
		}
		accounts = list.toArray(accounts);
		list = null;
		alluploaders = null;
		
		for (BaseAdapter a : adapters) {
			a.notifyDataSetChanged();
		}
	}

	class AccountListAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return accounts.length + 1;
		}

		@Override
		public Object getItem(int position) {
			if (position < accounts.length)
				return accounts[position];
			return this;
		}

		@Override
		public long getItemId(int position) {
			if (position < accounts.length)
				return accounts[position].getAsLong("_id");

			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (position == accounts.length) {
				Button b = new Button(ManageWorkoutsActivity.this);
				b.setText("Configure accounts");
				b.setBackgroundResource(R.drawable.btn_blue);
				b.setTextColor(getResources().getColorStateList(R.color.btn_text_color));
				b.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent i = new Intent(ManageWorkoutsActivity.this,
								AccountsActivity.class);
						ManageWorkoutsActivity.this.startActivityForResult(i,
								UploadManager.CONFIGURE_REQUEST + 1);
					}
				});
				return b;
			}

			LayoutInflater inflater = LayoutInflater.from(ManageWorkoutsActivity.this);
			View view = inflater.inflate(R.layout.reportlist_row, parent, false);

			TextView tv0 = (TextView) view.findViewById(R.id.accountId);
			CheckBox cb = (CheckBox) view.findViewById(R.id.reportSent);
			TextView tv1 = (TextView) view.findViewById(R.id.accountName);

			ContentValues tmp = accounts[position];

			String name = tmp.getAsString("name");
			cb.setTag(name);
			boolean showList = true;
			if (showList) {
				cb.setChecked(true);
			} else {
				cb.setChecked(false);
			}
			cb.setText("List remote workouts");
			cb.setEnabled(true);
			cb.setOnCheckedChangeListener(ManageWorkoutsActivity.this.onListChecked);

			tv0.setText(tmp.getAsString("_id"));
			tv1.setText(name);
			return view;
		}
		
	};

	class WorkoutListAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return workoutRef.size();
		}

		@Override
		public Object getItem(int position) {
			if (position < workoutRef.size())
				return workoutRef.get(position);
			return this;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			LayoutInflater inflater = LayoutInflater.from(ManageWorkoutsActivity.this);
			View view = inflater.inflate(R.layout.manage_workouts_list_row, parent, false);

			TextView tv0 = (TextView) view.findViewById(R.id.downloadWorkoutAccount);
			TextView tv1 = (TextView) view.findViewById(R.id.downloadWorkoutName);
			CheckBox cb = (CheckBox) view.findViewById(R.id.downloadWorkoutCheckbox);

			WorkoutRef tmp = workoutRef.get(position);
			if (tmp.uploader == null) {
				tv0.setText("<phone>");
				cb.setVisibility(View.INVISIBLE);
			} else {
				tv0.setText(tmp.uploader);
				cb.setVisibility(View.VISIBLE);
				cb.setText("Download");
				cb.setOnCheckedChangeListener(onWorkoutChecked);
				cb.setChecked(false);
			}
			tv1.setText(tmp.workoutName);
			cb.setTag(tmp);
			return view;
		}
	};

	@Override
	public void onBackPressed() {
		if (uploading == true) {
			/**
			 * Ignore while uploading
			 */
			return;
		}
		super.onBackPressed();
	}
	
	interface Filter<T> {
		boolean match(T t);
	};

	ArrayList<UploadManager.WorkoutRef> filter(List<UploadManager.WorkoutRef> list, Filter<UploadManager.WorkoutRef> f) {
		ArrayList<UploadManager.WorkoutRef> newlist = new ArrayList<UploadManager.WorkoutRef>();
		return filter(list, newlist, f);
	}

	ArrayList<UploadManager.WorkoutRef> filter(List<UploadManager.WorkoutRef> list, ArrayList<WorkoutRef> newlist, Filter<UploadManager.WorkoutRef> f) {
		for (UploadManager.WorkoutRef w : list) {
			if (f.match(w))
				newlist.add(w);
		}
		return newlist;
	}
	
	OnClickListener loadListButtonClick = new OnClickListener() {
		public void onClick(View v) {
			uploading = true;
			workoutRef = filter(workoutRef, new Filter<UploadManager.WorkoutRef>() {
				@Override
				public boolean match(WorkoutRef t) {
					return t.uploader == null;
				}});
			uploadManager.loadWorkoutList(workoutRef, new UploadManager.Callback() {
				@Override
				public void run(String uploader, Uploader.Status status) {
					uploading = false;
					requery();
				}
			}, pendingUploaders);
		}
	};

	public OnCheckedChangeListener onListChecked = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
			if (arg1 == true) {
				boolean empty = pendingUploaders.isEmpty();
				pendingUploaders.add((String) arg0.getTag());
				if (empty) {
					loadListButton.setEnabled(true);
				}
			} else {
				pendingUploaders.remove((String) arg0.getTag());
				if (pendingUploaders.isEmpty()) {
					loadListButton.setEnabled(false);
				}
			}
		}

	};

	public OnCheckedChangeListener onWorkoutChecked = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton arg0,
				boolean isChecked) {
			UploadManager.WorkoutRef workout = (UploadManager.WorkoutRef) arg0.getTag();
			if (isChecked == false) {
				pendingWorkouts.remove(workout);
			} else {
				pendingWorkouts.add(workout);
			}
		}
		
	};

	
	OnClickListener loadWorkoutButtonClick = new OnClickListener() {
		public void onClick(View v) {
			uploading = true;
			uploadManager.loadWorkouts(pendingWorkouts, new UploadManager.Callback() {
				@Override
				public void run(String uploader, Status status) {
					uploading = false;
					listLocal();
				}});
		}
	};

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == UploadManager.CONFIGURE_REQUEST) {
			uploadManager.onActivityResult(requestCode, resultCode, data);
		}
		requery();
	}
}
