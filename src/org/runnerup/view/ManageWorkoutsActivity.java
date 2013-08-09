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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.export.UploadManager;
import org.runnerup.export.UploadManager.Callback;
import org.runnerup.export.UploadManager.WorkoutRef;
import org.runnerup.export.Uploader;
import org.runnerup.export.Uploader.Status;
import org.runnerup.util.Constants;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutSerializer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

public class ManageWorkoutsActivity extends Activity implements Constants {

	DBHelper mDBHelper = null;
	SQLiteDatabase mDB = null;

	static final String PHONE_STRING = "my phone";
	
	HashSet<UploadManager.WorkoutRef> pendingWorkouts = new HashSet<UploadManager.WorkoutRef>();
	ArrayList<ContentValues> providers = new ArrayList<ContentValues>();
	HashMap<String, ArrayList<UploadManager.WorkoutRef>> workouts = new HashMap<String, ArrayList<UploadManager.WorkoutRef>>();
	WorkoutAccountListAdapter adapter = null;
	
	HashSet<String> loadedProviders = new HashSet<String>();
	
	boolean uploading = false;
	ExpandableListView list = null;
	CompoundButton currentlySelectedWorkout = null;
	Button downloadButton = null;
	Button deleteButton = null;
	
	UploadManager uploadManager = null;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.manage_workouts);

		mDBHelper = new DBHelper(this);
		mDB = mDBHelper.getReadableDatabase();
		uploadManager = new UploadManager(this);
		adapter = new WorkoutAccountListAdapter(this);
		list = (ExpandableListView) findViewById(R.id.expandableListView);
		list.setAdapter(adapter);
		downloadButton = (Button) findViewById(R.id.downloadWorkoutButton);
		downloadButton.setOnClickListener(downloadButtonClick);
		deleteButton = (Button) findViewById(R.id.deleteWorkoutButton);
		deleteButton.setOnClickListener(deleteButtonClick);
		
		handleButtons();
		
		requery();
		listLocal();

		Uri data = getIntent().getData();
		if (data != null) {
			getIntent().setData(null);
			String fileName = getFilename(data);
			if (fileName == null)
				fileName = "noname";
			
			try {
				importData(fileName, data);
			} catch (Exception e) {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Problem");
				builder.setMessage("Failed to import: " + fileName);
				builder.setPositiveButton("OK, darn!",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								dialog.dismiss();
								ManageWorkoutsActivity.this.finish();
								return;
							}
						});
				builder.show();
				return;
			}
			return;
		}
		// launch home Activity (with FLAG_ACTIVITY_CLEAR_TOP)
	}

	private String getFilename(Uri data) {
		System.out.println("scheme: " + data.toString());
		String name = null;
		if (ContentResolver.SCHEME_FILE.contentEquals(data.getScheme())) {
			name = data.getLastPathSegment();
		} else if (ContentResolver.SCHEME_CONTENT.contentEquals(data.getScheme())){
			String projection[] = { MediaStore.MediaColumns.DISPLAY_NAME };
			Cursor c = getContentResolver().query(data, projection, null, null, null);
			if (c != null) {
				c.moveToFirst();
				final int fileNameColumnId = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
				if (fileNameColumnId >= 0)
					name = c.getString(fileNameColumnId);
				c.close();
			}
		}
		return name;
	}
	
	private void importData(final String fileName, final Uri data) throws Exception {
		final ContentResolver cr = getContentResolver();
		InputStream is = cr.openInputStream(data);
		if (is == null) {
			throw new Exception("Failed to get input stream"); 
		}
		Workout w = WorkoutSerializer.readJSON(new BufferedReader(new InputStreamReader(is)));
		is.close();
		if (w == null)
			throw new Exception("Failed to parse content");

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Import workout");
		builder.setMessage("Do you want to import workout: " + fileName);
		builder.setPositiveButton("Yes!",
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				try {
					saveImport(fileName, cr.openInputStream(data));
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				launchMain(fileName);
				return;
			}
		});
		builder.setNegativeButton("No way",
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// Do nothing but close the dialog
				dialog.dismiss();
				finish();
				return;
			}
		});
		builder.show();
		return;
	}

	private void saveImport(String file, InputStream is) throws IOException {
		File f = WorkoutSerializer.getFile(this, file);
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f));
		BufferedInputStream in = new BufferedInputStream(is);
		byte buf[] = new byte[1024];
		while (in.read(buf) > 0) {
			out.write(buf);
		}
		in.close();
		out.close();
	}
	
	protected void launchMain(String fileName) {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		pref.edit().putString("advancedWorkout", fileName).commit();

		Intent intent = new Intent(this, MainLayout.class);
		intent.putExtra("mode", StartActivity.TAB_ADVANCED);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
		finish();
		return;
	}

	private void handleButtons() {
		if (currentlySelectedWorkout == null) {
			downloadButton.setEnabled(false);
			deleteButton.setEnabled(false);
			return;
		}

		WorkoutRef selected = (WorkoutRef) currentlySelectedWorkout.getTag();
		if (PHONE_STRING.contentEquals(selected.uploader)) {
			downloadButton.setEnabled(false);
			deleteButton.setEnabled(true);
		} else {
			downloadButton.setEnabled(true);
			deleteButton.setEnabled(false);
		}
	}

	void listLocal() {
		ArrayList<UploadManager.WorkoutRef> newlist = new ArrayList<UploadManager.WorkoutRef>();
		String [] list = org.runnerup.view.WorkoutListAdapter.load(this);
		if (list != null) {
			for (String s : list) {
				newlist.add(new UploadManager.WorkoutRef(PHONE_STRING,  null, s));
			}
		}

		workouts.remove(PHONE_STRING);
		workouts.put(PHONE_STRING, newlist);
		adapter.notifyDataSetChanged();
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
							+ ("  acc." + DB.ACCOUNT.AUTH_METHOD + ", ")
							+ ("  acc." + DB.ACCOUNT.AUTH_CONFIG + ", ")
							+ ("  acc." + DB.ACCOUNT.ENABLED + " ")
							+ (" FROM " + DB.ACCOUNT.TABLE + " acc "));

			Cursor c = mDB.rawQuery(sql, null);
			alluploaders = DBHelper.toArray(c);
			c.close();
		}

		providers.clear();
		
		ContentValues phone = new ContentValues();
		phone.put(DB.ACCOUNT.NAME, PHONE_STRING);
		providers.add(phone);
		
		for (ContentValues tmp : alluploaders) {
			Uploader uploader = uploadManager.add(tmp);
			if (uploader != null && uploader.checkSupport(Uploader.Feature.WORKOUT_LIST)) {
				providers.add(tmp);

				workouts.remove(uploader.getName());
				workouts.put(uploader.getName(), new ArrayList<WorkoutRef>());
			}
		}
		
		adapter.notifyDataSetChanged();
	}

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
	
	OnClickListener downloadButtonClick = new OnClickListener() {

		@Override
		public void onClick(View v) {
			if (currentlySelectedWorkout == null)
				return;

			final WorkoutRef selected = (WorkoutRef) currentlySelectedWorkout.getTag();
			ArrayList<WorkoutRef> local = workouts.get(PHONE_STRING);
			if (contains(local, selected)) {
				AlertDialog.Builder builder = new AlertDialog.Builder(ManageWorkoutsActivity.this);
				builder.setTitle("Downloading " + selected.workoutName + " will overwrite " + PHONE_STRING + " workout with same name");
				builder.setMessage("Are you sure?");
				builder.setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								dialog.dismiss();
								downloadWorkout(selected);
								return;
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
				return;
			}
			
			downloadWorkout(selected);
		}

		private void downloadWorkout(WorkoutRef selected) {
			uploading = true;
			HashSet<WorkoutRef> list = new HashSet<WorkoutRef>();
			list.add((WorkoutRef) currentlySelectedWorkout.getTag());
			uploadManager.loadWorkouts(list, new UploadManager.Callback() {
				@Override
				public void run(String uploader, Uploader.Status status) {
					uploading = false;
					currentlySelectedWorkout = null;
					listLocal();
					handleButtons();
				}
			});
		}

		private boolean contains(ArrayList<WorkoutRef> local,
				WorkoutRef selected) {
			for (WorkoutRef w : local) {
				if (selected.workoutName.contentEquals(w.workoutName)) {
					return true;
				}
			}
			return false;
		}
	};
	
	private OnClickListener deleteButtonClick = new OnClickListener() {

		@Override
		public void onClick(View v) {
			if (currentlySelectedWorkout == null)
				return;

			final WorkoutRef selected = (WorkoutRef) currentlySelectedWorkout.getTag();
			AlertDialog.Builder builder = new AlertDialog.Builder(ManageWorkoutsActivity.this);
			builder.setTitle("Delete workout " + selected.workoutName);
			builder.setMessage("Are you sure?");
			builder.setPositiveButton("Yes",
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					deleteWorkout(selected);
					return;
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
			return;
		}
	};

	private void deleteWorkout(WorkoutRef selected) {
		File f = WorkoutSerializer.getFile(this, selected.workoutName);
		f.delete();
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		if (selected.workoutName.contentEquals(pref.getString("advancedWorkout", ""))) {
			pref.edit().putString("advancedWorkout", "").commit();
		}
		currentlySelectedWorkout = null;
		listLocal();
	}
	
	public OnCheckedChangeListener onWorkoutChecked = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton arg0, boolean isChecked) {
			if (currentlySelectedWorkout != null) {
				currentlySelectedWorkout.setChecked(false);
			}
			if (isChecked) {
				currentlySelectedWorkout = arg0;
			} else {
				currentlySelectedWorkout = null;
			}
			handleButtons();
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

	class WorkoutAccountListAdapter extends BaseExpandableListAdapter {

		Context context;
		
		WorkoutAccountListAdapter(Context ctx) {
			context = ctx;
		}
		
		String getProvider(int index) {
			return providers.get(index).getAsString(DB.ACCOUNT.NAME);
		}
		
		@Override
		public Object getChild(int groupPosition, int childPosition) {
			return workouts.get(getProvider(groupPosition)).get(childPosition);
		}

		@Override
		public long getChildId(int groupPosition, int childPosition) {
			return 0;
		}

		@Override
		public View getChildView(int groupPosition, int childPosition,
				boolean isLastChild, View view, ViewGroup parent) {

			if (view == null || !(view instanceof LinearLayout)) {
				LayoutInflater infalInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = infalInflater.inflate(R.layout.manage_workouts_list_row, null);
			}

			WorkoutRef workout = workouts.get(getProvider(groupPosition)).get(childPosition);
			RadioButton cb = (RadioButton) view.findViewById(R.id.downloadWorkoutCheckbox);
			TextView tv = (TextView) view.findViewById(R.id.downloadWorkoutName);
			
			cb.setTag(workout);
			cb.setChecked(currentlySelectedWorkout != null && currentlySelectedWorkout.getTag() == workout);
			cb.setOnCheckedChangeListener(onWorkoutChecked);
			tv.setText(workout.workoutName);
			return view;
		}

		@Override
		public int getChildrenCount(int groupPosition) {
			return workouts.get(getProvider(groupPosition)).size();
		}

		@Override
		public Object getGroup(int groupPosition) {
			return providers.get(groupPosition);
		}

		@Override
		public int getGroupCount() {
			return providers.size();
		}

		@Override
		public long getGroupId(int groupPosition) {
			return 0;
		}

		@Override
		public View getGroupView(int groupPosition, boolean isExpanded,
				View convertView, ViewGroup parent) {
			TextView view = null;
			if (convertView != null && convertView instanceof TextView)
				view = (TextView)convertView;
			else
				view = new TextView(context);

			view.setGravity(Gravity.CENTER_HORIZONTAL);
			view.setText(getProvider(groupPosition));
			view.setBackgroundResource(android.R.drawable.btn_default_small);
			view.setTextAppearance(context, R.style.ButtonTextGrey);
			return view;
		}

		@Override
		public boolean hasStableIds() {
			return false;
		}

		@Override
		public boolean isChildSelectable(int groupPosition, int childPosition) {
			return false;
		}

		int saveGroupPosition;
		@Override
		public void onGroupExpanded(int groupPosition) {
			String provider = getProvider(groupPosition);
			if (PHONE_STRING.contentEquals(provider)) {
				super.onGroupExpanded(groupPosition);
				return;
			}

			if (loadedProviders.contains(provider)) {
				super.onGroupExpanded(groupPosition);
				return;
			}

			uploading = true;
			saveGroupPosition = groupPosition;

			if (!uploadManager.isConfigured(provider)) {
				uploadManager.configure(onUploaderConfiguredCallback, provider, false);
			}
			else {
				onUploaderConfiguredCallback.run(provider,  Uploader.Status.OK);
			}
		}

		Callback onUploaderConfiguredCallback = new Callback() {
			@Override
			public void run(String uploader, Status status) {
				System.out.println("status: " + status);
				if (status != Uploader.Status.OK) {
					uploading = false;
					return;
				}

				ArrayList<WorkoutRef> list = workouts.get(uploader);
				list.clear();
				
				HashSet<String> tmp = new HashSet<String>();
				tmp.add(uploader);

				uploadManager.loadWorkoutList(list, onLoadWorkoutListCallback, tmp);
			}
		};
		
		private void onGroupExpandedImpl() {
			super.onGroupExpanded(saveGroupPosition);
		}

		private Callback onLoadWorkoutListCallback = new Callback () {

			@Override
			public void run(String uploader, Status status) {
				uploading = false;
				if (status == Status.OK) {
					loadedProviders.add(getProvider(saveGroupPosition));
					adapter.notifyDataSetChanged();
					onGroupExpandedImpl();
				}
			}
		};

		@Override
		public void onGroupCollapsed(int groupPosition) {
			super.onGroupCollapsed(groupPosition);
			String provider = getProvider(groupPosition);
			if (currentlySelectedWorkout != null) {
				WorkoutRef ref = (WorkoutRef) currentlySelectedWorkout.getTag();
				if (ref.uploader.contentEquals(provider)) {
					currentlySelectedWorkout.setChecked(false);
					currentlySelectedWorkout = null;
				}
			}
		}
	};
}
