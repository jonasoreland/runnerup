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
package org.runnerup.export;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.export.UploadManager.Callback;
import org.runnerup.export.Uploader.Status;
import org.runnerup.util.Constants.DB;
import org.runnerup.workout.WorkoutSerializer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.text.InputType;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

public class UploadManager {

	public static final int CONFIGURE_REQUEST = 1;

	private DBHelper mDBHelper = null;
	private SQLiteDatabase mDB = null;
	private Activity activity = null;
	private Map<String, Uploader> uploaders = new HashMap<String, Uploader>();
	private ProgressDialog mSpinner = null;

	public interface Callback {
		void run(String uploader, Uploader.Status status);
	};
	
	public UploadManager(Activity activity) {
		this.activity = activity;
		mDBHelper = new DBHelper(activity);
		mDB = mDBHelper.getWritableDatabase();
		mSpinner = new ProgressDialog(activity);
		mSpinner.setCancelable(false);
	}

	public synchronized void close() {
		if (mDB != null) {
			mDB.close();
			mDB = null;
		}
		if (mDBHelper != null) {
			mDBHelper.close();
			mDBHelper = null;
		}
	}

	public synchronized boolean isClosed() {
		return mDB == null;
	}
	
	public void load(String uploaderName) {
		String from[] = new String[] { "_id", DB.ACCOUNT.AUTH_CONFIG };
		String args[] = { uploaderName };
		Cursor c = mDB.query(DB.ACCOUNT.TABLE, from, DB.ACCOUNT.NAME + " = ?",
				args, null, null, null, null);
		if (c.moveToFirst()) {
			ContentValues config = DBHelper.get(c);
			add(config);
		}
		c.close();
	}

	public Uploader add(ContentValues config) {
		if (config == null) {
			System.err.println("Add null!");
			assert (false);
			return null;
		}

		String uploaderName = config.getAsString(DB.ACCOUNT.NAME);
		if (uploaderName == null) {
			System.err.println("name not found!");
			return null;
		}
		if (uploaders.containsKey(uploaderName)) {
			return uploaders.get(uploaderName);
		}
		Uploader uploader = null;
		if (uploaderName.contentEquals(RunKeeperUploader.NAME)) {
			uploader = new RunKeeperUploader();
		} else if (uploaderName.contentEquals(GarminUploader.NAME)) {
			uploader = new GarminUploader();
		} else if (uploaderName.contentEquals(FunBeatUploader.NAME)) {
			uploader = new FunBeatUploader();
		} else if (uploaderName.contentEquals(MapMyRunUploader.NAME))
			uploader = new MapMyRunUploader();

		if (uploader != null) {
			uploader.init(config);
			uploaders.put(uploaderName, uploader);
		}
		return uploader;
	}

	public boolean isConfigured(final String name) {
		Uploader l = uploaders.get(name);
		if (l == null) {
			return true;
		}
		return l.isConfigured();
	}

	public boolean isEnabled(String name) {
		Uploader l = uploaders.get(name);
		if (l == null) {
			return false;
		}
		return l.isConfigured();
	}

	private Callback configureCallback = null;
	public void configure(final Callback callback, final String name, final boolean uploading) {
		Uploader l = uploaders.get(name);
		if (l == null) {
			return;
		}
		currentUploader = l;
		configureCallback = callback;
		switch (l.getAuthMethod()) {
		case OAUTH2:
			Intent i = l.configure(activity);
			activity.startActivityForResult(i, CONFIGURE_REQUEST);
			return;
		case POST:
			askUsernamePassword(callback, l, "", "", false, uploading);
			return;
		}
	}

	private long mID = 0;
	private Uploader currentUploader = null;
	private Callback uploadCallback = null;
	private HashSet<String> pendingUploaders = null;
	
	public void startUploading(Callback callback, HashSet<String> uploaders, long id) {
		mID = id;
		uploadCallback = callback;
		pendingUploaders = uploaders;
		mSpinner.setTitle("Uploading (" + pendingUploaders.size() + ")");
		mSpinner.show();
		nextUploader();
	}

	private void nextUploader() {
		if (pendingUploaders.isEmpty()) {
			doneUploading();
			return;
		}

		mSpinner.setTitle("Uploading (" + pendingUploaders.size() + ")");
		currentUploader = uploaders.get(pendingUploaders.iterator().next());
		pendingUploaders.remove(currentUploader.getName());
		mSpinner.setMessage("Configure " + currentUploader.getName());
		if (!currentUploader.isConfigured()) {
			configure(usernamePasswordCallback, currentUploader.getName(), true);
			return;
		}
		doLogin(doLoginUploaderCallback);
	}
	
	private Callback doLoginUploaderCallback = new Callback() {
		@Override
		public void run(String uploader, Status status) {
			if (status == Uploader.Status.OK) {
				doUpload();
				return;
			}
			nextUploader();
		}
	};
	
	private Callback usernamePasswordCallback = new Callback() {
		@Override
		public void run(String uploader, Status status) {
			switch(status) {
			case CANCEL:
				disableUploader(disableUploaderCallback, currentUploader.getName());
				return;
			case SKIP:
			case ERROR:
			case INCORRECT_USAGE:
				nextUploader();
				break;
			case OK:
				doLogin(doLoginUploaderCallback);
				break;
			}
		}
	};
	
	private Callback disableUploaderCallback = new Callback() {
		@Override
		public void run(String uploader, Status status) {
			nextUploader();
		}
	};
	
	private void askUsernamePassword(final Callback callback, final Uploader l, String username, String password,
			boolean showPassword, final boolean uploading) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle(l.getName());
		// Get the layout inflater
		LayoutInflater inflater = activity.getLayoutInflater();
		final View view = inflater.inflate(R.layout.userpass, null);
		final CheckBox cb = (CheckBox) view.findViewById(R.id.showpass);
		final TextView tv1 = (TextView) view.findViewById(R.id.username);
		final TextView tv2 = (TextView) view.findViewById(R.id.passwordInput);
		tv1.setText(username);
		tv2.setText(password);
		cb.setChecked(showPassword);
		tv2.setInputType(InputType.TYPE_CLASS_TEXT
				| (showPassword ? 0 : InputType.TYPE_TEXT_VARIATION_PASSWORD));
		cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				tv2.setInputType(InputType.TYPE_CLASS_TEXT
						| (isChecked ? 0
								: InputType.TYPE_TEXT_VARIATION_PASSWORD));
			}
		});

		// Inflate and set the layout for the dialog
		// Pass null as the parent view because its going in the dialog layout
		builder.setView(view);
		builder.setPositiveButton("OK", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				testUserPass(callback, l, tv1.getText().toString(),
						tv2.getText().toString(), cb.isChecked(), uploading);
			}
		});
		if (uploading) {
			builder.setNeutralButton("Skip", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				callback.run(l.getName(), Status.SKIP);
			}
		});
		builder.setNegativeButton("Disable", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				callback.run(l.getName(), Status.CANCEL);
			}
		});
		} else {
			builder.setNegativeButton("Cancel", new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					callback.run(l.getName(), Status.CANCEL);
				}
			});
		}
		final AlertDialog dialog = builder.create();
		dialog.show();
	}

	private void testUserPass(final Callback callback, final Uploader l, final String username, final String password,
			final boolean showPassword, final boolean uploading) {
		if (uploading) {
			mSpinner.setMessage("Login " + l.getName());
		} else {
			mSpinner.setTitle("Testing login " + l.getName());
			mSpinner.show();
		}
		new AsyncTask<Uploader, String, Uploader.Status>() {

			ContentValues config = new ContentValues();

			@Override
			protected Uploader.Status doInBackground(Uploader... params) {
				JSONObject obj = new JSONObject();
				try {
					obj.put("username", username);
					obj.put("password", password);
				} catch (JSONException e) {
					e.printStackTrace();
					return Uploader.Status.ERROR;
				}
				config.put(DB.ACCOUNT.AUTH_CONFIG, obj.toString());
				config.put("_id", l.getId());
				l.init(config);
				try {
					return params[0].login(config);
				} catch (Exception ex) {
					ex.printStackTrace();
					return Uploader.Status.ERROR;
				}
			}

			@Override
			protected void onPostExecute(Uploader.Status result) {
				if (!uploading) {
					mSpinner.dismiss();
				}
				
				if (result == Uploader.Status.OK) {
					String args[] = { Long.toString(l.getId()) };
					mDB.update(DB.ACCOUNT.TABLE, config, "_id = ?", args);
					callback.run(l.getName(), Uploader.Status.OK);
					return;
				}
				if (result == Uploader.Status.CANCEL) {
					askUsernamePassword(callback, l, username, password, showPassword, uploading);
					return;
				}
				callback.run(l.getName(), Uploader.Status.ERROR);
			}
		}.execute(l);

	}

	private void doLogin(final Callback callback) {
		mSpinner.setMessage("Login " + currentUploader.getName());
		new AsyncTask<Uploader, String, Uploader.Status>() {

			@Override
			protected Uploader.Status doInBackground(Uploader... params) {
				try {
					return params[0].login(null);
				} catch (Exception ex) {
					ex.printStackTrace();
					return Uploader.Status.ERROR;
				}
			}

			@Override
			protected void onPostExecute(Uploader.Status result) {
				if (isClosed()) {
					return;
				}
				
				callback.run(currentUploader.getName(), result);
			}
		}.execute(currentUploader);
	}

	private void doUpload() {
		final ProgressDialog copySpinner = mSpinner;
		final SQLiteDatabase copyDB = mDBHelper.getWritableDatabase();

		copySpinner.setMessage("Uploading " + currentUploader.getName());

		new AsyncTask<Uploader, String, Uploader.Status>() {

			@Override
			protected Uploader.Status doInBackground(Uploader... params) {
				try {
					return params[0].upload(copyDB, mID);
				} catch (Exception ex) {
					ex.printStackTrace();
					return Uploader.Status.ERROR;
				}
			}

			@Override
			protected void onPostExecute(Uploader.Status result) {
				if (result == Uploader.Status.OK) {
					uploadOK(copySpinner, copyDB);
				}
				copyDB.close();
				nextUploader();
			}
		}.execute(currentUploader);
	}

	private void uploadOK(ProgressDialog copySpinner, SQLiteDatabase copyDB) {
		copySpinner.setMessage("Saving");
		ContentValues tmp = new ContentValues();
		tmp.put(DB.EXPORT.ACCOUNT, currentUploader.getId());
		tmp.put(DB.EXPORT.ACTIVITY, mID);
		tmp.put(DB.EXPORT.STATUS, 0);
		copyDB.insert(DB.EXPORT.TABLE, null, tmp);
	}

	private void doneUploading() {
		mSpinner.dismiss();
		final Callback cb = uploadCallback;
		uploadCallback = null;
		if (cb != null)
			cb.run(null, null);

	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode != CONFIGURE_REQUEST)
			return;

		if (resultCode == Activity.RESULT_OK) {
			String authConfig = data.getStringExtra(DB.ACCOUNT.AUTH_CONFIG);
			ContentValues tmp = new ContentValues();
			tmp.put(DB.ACCOUNT.AUTH_CONFIG, authConfig);
			String args[] = { Long.toString(currentUploader.getId()) };
			mDB.update(DB.ACCOUNT.TABLE, tmp, "_id = ?", args);
			tmp.put("_id", currentUploader.getId());
			currentUploader.init(tmp);
			configureCallback.run(currentUploader.getName(), Uploader.Status.OK);
		} else {
			configureCallback.run(currentUploader.getName(), Uploader.Status.ERROR);
		}
	}

	private Callback disableCallback = null;
	public void disableUploader(Callback callback, String name) {
		disableCallback = callback;
		Uploader l = uploaders.get(name);
		if (l == null) {
			disableCallback = null;
			callback.run(name, Uploader.Status.SKIP);
			return;
		}
		switch (l.getAuthMethod()) {
		case OAUTH2:
			resetDB(name);
			return;
		case POST:
			resetDB(name);
			return;
		}
	}

	void resetDB(final String name) {
		Uploader l = uploaders.get(name);
		String args[] = { Long.toString(l.getId()) };
		ContentValues config = new ContentValues();
		config.putNull(DB.ACCOUNT.AUTH_CONFIG);
		mDB.update(DB.ACCOUNT.TABLE, config, "_id = ?", args);

		l.reset();
		
		Callback callback = disableCallback;
		disableCallback = null;
		callback.run(name, Uploader.Status.OK);
	}

	public static class WorkoutRef {
		public WorkoutRef(String uploader, String key, String name) {
			this.uploader = uploader;
			this.workoutKey = key;
			this.workoutName = name;
		}
		public final String uploader;
		public final String workoutKey;
		public final String workoutName;
	};
	
	Callback listWorkoutCallback = null;
	HashSet<String> pendingListWorkout = null;
	ArrayList<WorkoutRef> workoutRef = null;
	public void loadWorkoutList(ArrayList<WorkoutRef> workoutRef, Callback callback,
			HashSet<String> uploaders) {
		
		listWorkoutCallback = callback;
		pendingListWorkout = uploaders;
		this.workoutRef = workoutRef;
		
		mSpinner.setTitle("Loading workout list (" + pendingListWorkout.size() + ")");
		mSpinner.show();
		nextListWorkout();
	}

	public void nextListWorkout() {
		if (pendingListWorkout.isEmpty()) {
			doneListing();
			return;
		}

		mSpinner.setTitle("Loading workout list (" + pendingListWorkout.size() + ")");
		currentUploader = uploaders.get(pendingListWorkout.iterator().next());
		pendingListWorkout.remove(currentUploader.getName());
		mSpinner.setMessage("Configure " + currentUploader.getName());
		if (!currentUploader.isConfigured()) {
			nextListWorkout();
			return;
		}
		doLogin(new Callback() {
			@Override
			public void run(String uploader, Status status) {
				if (status == Uploader.Status.OK) {
					doListWorkout();
				}
			}
		});
	}

	protected void doListWorkout() {
		final ProgressDialog copySpinner = mSpinner;

		copySpinner.setMessage("Listing from " + currentUploader.getName());

		new AsyncTask<Uploader, String, Uploader.Status>() {

			@Override
			protected Uploader.Status doInBackground(Uploader... params) {
				try {
					ArrayList<Pair<String,String>> list = new ArrayList<Pair<String,String>>();
					Uploader.Status ret = params[0].listWorkouts(list);
					if (ret == Uploader.Status.OK) {
						for (Pair<String,String> w : list) {
							workoutRef.add(new WorkoutRef(params[0].getName(), w.first, w.second));
						}
					}
					return ret;
				} catch (Exception ex) {
					ex.printStackTrace();
					return Uploader.Status.ERROR;
				}
			}

			@Override
			protected void onPostExecute(Uploader.Status result) {
				nextListWorkout();
			}
		}.execute(currentUploader);
	}

	private void doneListing() {
		mSpinner.dismiss();
		Callback cb = listWorkoutCallback;
		listWorkoutCallback = null;
		if (cb != null)
			cb.run(null, null);
	}

	public void loadWorkouts(final HashSet<WorkoutRef> pendingWorkouts, final Callback callback) {
		int cnt = pendingWorkouts.size();
		mSpinner.setTitle("Downloading workouts (" + cnt + ")");
		mSpinner.show();
		new AsyncTask<Uploader, String, Uploader.Status>() {


			@Override
			protected void onProgressUpdate(String... values) {
				mSpinner.setMessage("Loading " + values[0] + " from " + values[1]);
			}

			@Override
			protected Uploader.Status doInBackground(Uploader... params) {
				for (WorkoutRef ref : pendingWorkouts) {
					publishProgress(ref.workoutName, ref.uploader);
					Uploader uploader = uploaders.get(ref.uploader);
					File f = WorkoutSerializer.getFile(activity, ref.workoutName);
					File w = f;
					if (f.exists()) {
						w = WorkoutSerializer.getFile(activity, ref.workoutName + ".tmp");
					}
					try {
						uploader.downloadWorkout(w, ref.workoutKey);
						if (w != f) {
							if (compareFiles(w, f) != true) {
								//@TODO dialog
								f.delete();
								w.renameTo(f);
							} else {
								w.delete();
							}
						}
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						w.delete();
					}
				}
				return Uploader.Status.OK;
			}

			@Override
			protected void onPostExecute(Uploader.Status result) {
				mSpinner.dismiss();
				if (callback != null) {
					callback.run(null,  Uploader.Status.OK);
				}
			}
		}.execute(currentUploader);
	}

	protected boolean compareFiles(File w, File f) {
		// TODO Auto-generated method stub
		return false;
	}
}