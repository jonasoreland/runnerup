package org.runnerup.export;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.util.Constants.DB;

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
	private Set<String> pending = new HashSet<String>();
	private Map<String, Uploader> uploaders = new HashMap<String, Uploader>();

	public UploadManager(Activity activity) {
		this.activity = activity;
		mDBHelper = new DBHelper(activity);
		mDB = mDBHelper.getWritableDatabase();
	}

	public void close() {
		if (mDB != null) {
			mDB.close();
			mDB = null;
		}
		if (mDBHelper != null) {
			mDBHelper.close();
			mDBHelper = null;
		}
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

	public void add(ContentValues config) {
		if (config == null) {
			System.err.println("Add null!");
			assert (false);
			return;
		}

		String uploaderName = config.getAsString(DB.ACCOUNT.NAME);
		if (uploaderName == null) {
			System.err.println("name not found!");
			return;
		}
		if (uploaders.containsKey(uploaderName)) {
			return;
		}
		Uploader uploader = null;
		if (uploaderName.contentEquals("RunKeeper")) {
			uploader = new RunKeeperUploader();
		} else if (uploaderName.contentEquals("Garmin")) {
			uploader = new GarminUploader();
		} else if (uploaderName.contentEquals("FunBeat")) {
			uploader = new FunBeatUploader();
		}

		if (uploader != null) {
			uploader.init(config);
			uploaders.put(uploaderName, uploader);

			boolean enabled = !config.containsKey(DB.EXPORT.ACCOUNT);
			if (enabled) {
				pending.add(uploaderName);
			}
		}
	}

	public Intent configure(final String name) {
		Uploader l = uploaders.get(name);
		if (l == null) {
			return null;
		}
		return l.configure(activity);
	}

	public void enable(String tag) {
		if (uploaders.containsKey(tag))
			pending.add(tag);
	}

	public void disable(String tag) {
		pending.remove(tag);
	}

	private void disableUploader(Uploader currentUploader) {
	}

	private long mID = 0;
	private Uploader currentUploader = null;
	private ProgressDialog mSpinner = null;

	public void startUploading(long id) {
		mID = id;
		mSpinner = new ProgressDialog(activity);
		mSpinner.setTitle("Uploading (" + pending.size() + ")");
		mSpinner.show();
		nextUploader();
	}

	private void nextUploader() {
		if (pending.isEmpty()) {
			doneUploading();
			return;
		}

		mSpinner.setTitle("Uploading (" + pending.size() + ")");
		currentUploader = uploaders.get(pending.iterator().next());
		pending.remove(currentUploader.getName());
		mSpinner.setMessage("Configure " + currentUploader.getName());
		if (!currentUploader.isConfigured()) {
			switch (currentUploader.getAuthMethod()) {
			case OAUTH2:
				Intent i = currentUploader.configure(activity);
				activity.startActivityForResult(i, CONFIGURE_REQUEST);
				return;
			case POST:
				askUsernamePassword("", "", false);
				return;
			}
			return;
		}

		doLogin();
	}

	private void askUsernamePassword(String username, String password,
			boolean showPassword) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle(currentUploader.getName());
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
				testUserPass(tv1.getText().toString(),
						tv2.getText().toString(), cb.isChecked());
			}
		});
		builder.setNeutralButton("Skip", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				nextUploader();
			}
		});
		builder.setNegativeButton("Disable", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				disableUploader(currentUploader);
				nextUploader();
			}
		});
		final AlertDialog dialog = builder.create();
		dialog.show();
	}

	private void testUserPass(final String username, final String password,
			final boolean showPassword) {
		mSpinner.setMessage("Login " + currentUploader.getName());
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
				config.put("_id", currentUploader.getId());
				currentUploader.init(config);
				return params[0].login();
			}

			@Override
			protected void onPostExecute(Uploader.Status result) {
				if (result == Uploader.Status.OK) {
					String args[] = { Long.toString(currentUploader.getId()) };
					mDB.update(DB.ACCOUNT.TABLE, config, "_id = ?", args);
					doUpload();
					return;
				}
				if (result == Uploader.Status.CANCEL) {
					askUsernamePassword(username, password, showPassword);
					return;
				}
				nextUploader();
			}
		}.execute(currentUploader);

	}

	private void doLogin() {
		mSpinner.setMessage("Login " + currentUploader.getName());
		new AsyncTask<Uploader, String, Uploader.Status>() {

			@Override
			protected Uploader.Status doInBackground(Uploader... params) {
				return params[0].login();
			}

			@Override
			protected void onPostExecute(Uploader.Status result) {
				if (result == Uploader.Status.OK) {
					doUpload();
					return;
				}
				nextUploader();
			}
		}.execute(currentUploader);
	}

	private void doUpload() {
		mSpinner.setMessage("Uploading " + currentUploader.getName());
		new AsyncTask<Uploader, String, Uploader.Status>() {

			@Override
			protected Uploader.Status doInBackground(Uploader... params) {
				return params[0].upload(mDB, mID);
			}

			@Override
			protected void onPostExecute(Uploader.Status result) {
				if (result == Uploader.Status.OK) {
					uploadOK();
					return;
				}
				nextUploader();
			}
		}.execute(currentUploader);
	}

	private void uploadOK() {
		mSpinner.setMessage("Saving");
		ContentValues tmp = new ContentValues();
		tmp.put(DB.EXPORT.ACCOUNT, currentUploader.getId());
		tmp.put(DB.EXPORT.ACTIVITY, mID);
		tmp.put(DB.EXPORT.STATUS, 0);
		mDB.insert(DB.EXPORT.TABLE, null, tmp);
		nextUploader();
	}

	private void doneUploading() {
		mSpinner.dismiss();
		mSpinner = null;
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
			doLogin();
		} else {
			nextUploader();
		}
	}
}
