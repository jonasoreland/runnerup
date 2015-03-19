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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.DBHelper;
import org.runnerup.export.Uploader.AuthMethod;
import org.runnerup.export.Uploader.Status;
import org.runnerup.export.format.ActivityItem;
import org.runnerup.feed.FeedList;
import org.runnerup.tracker.WorkoutObserver;
import org.runnerup.util.Encryption;
import org.runnerup.workout.WorkoutSerializer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@TargetApi(Build.VERSION_CODES.FROYO)
public class UploadManager {

    public static final int CONFIGURE_REQUEST = 1;

    private DBHelper mDBHelper = null;
    private SQLiteDatabase mDB = null;
    private Activity activity = null;
    private Context context = null;
    private final Map<String, Uploader> uploaders = new HashMap<String, Uploader>();
    private final Map<Long, Uploader> uploadersById = new HashMap<Long, Uploader>();
    private ProgressDialog mSpinner = null;

    public enum SyncMode { DOWNLOAD, UPLOAD; };

    public interface Callback {
        void run(String uploader, Uploader.Status status);
    }

    public UploadManager(Activity activity) {
        this.activity = activity;
        this.context = activity;
        mDBHelper = new DBHelper(activity);
        mDB = mDBHelper.getWritableDatabase();
        mSpinner = new ProgressDialog(activity);
        mSpinner.setCancelable(false);
    }

    public UploadManager(Context context) {
        this.activity = null;
        this.context = context;
        mDBHelper = new DBHelper(context);
        mDB = mDBHelper.getWritableDatabase();
        mSpinner = new ProgressDialog(context);
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

    public void remove(String uploaderName) {
        Uploader u = uploaders.get(uploaderName);
        uploaders.remove(uploaderName);
        if (u != null) {
            this.uploadersById.remove(u.getId());
        }
    }

    public void clear() {
        uploaders.clear();
        uploadersById.clear();
    }

    public long load(String uploaderName) {
        String from[] = new String[] {
                "_id", DB.ACCOUNT.NAME, DB.ACCOUNT.AUTH_CONFIG, DB.ACCOUNT.FLAGS
        };
        String args[] = {
            uploaderName
        };
        Cursor c = mDB.query(DB.ACCOUNT.TABLE, from, DB.ACCOUNT.NAME + " = ?",
                args, null, null, null, null);
        long id = -1;
        if (c.moveToFirst()) {
            ContentValues config = DBHelper.get(c);
            id = config.getAsLong("_id");
            add(config);
        }
        c.close();
        return id;
    }

    @SuppressWarnings("null")
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
            uploader = new RunKeeperUploader(this);
        } else if (uploaderName.contentEquals(GarminUploader.NAME)) {
            uploader = new GarminUploader(this);
        } else if (uploaderName.contentEquals(FunBeatUploader.NAME)) {
            uploader = new FunBeatUploader(this);
        } else if (uploaderName.contentEquals(MapMyRunUploader.NAME)) {
            uploader = new MapMyRunUploader(this);
        } else if (uploaderName.contentEquals(NikePlus.NAME)) {
            uploader = new NikePlus(this);
        } else if (uploaderName.contentEquals(JoggSE.NAME)) {
            uploader = new JoggSE(this);
        } else if (uploaderName.contentEquals(Endomondo.NAME)) {
            uploader = new Endomondo(this);
        } else if (uploaderName.contentEquals(RunningAHEAD.NAME)) {
            uploader = new RunningAHEAD(this);
        } else if (uploaderName.contentEquals(RunnerUpLive.NAME)) {
            uploader = new RunnerUpLive(context);
        } else if (uploaderName.contentEquals(DigifitUploader.NAME)) {
            uploader = new DigifitUploader(this);
        } else if (uploaderName.contentEquals(Strava.NAME)) {
            uploader = new Strava(this);
        } else if (uploaderName.contentEquals(Facebook.NAME)) {
            uploader = new Facebook(context, this);
        } else if (uploaderName.contentEquals(GooglePlus.NAME)) {
            uploader = new GooglePlus(this);
        } else if (uploaderName.contentEquals(RuntasticUploader.NAME)) {
            uploader = new RuntasticUploader(this);
        } else if (uploaderName.contentEquals(GoogleFitUploader.NAME)) {
            uploader = new GoogleFitUploader(context, this);
        }

        if (uploader != null) {
            if (!config.containsKey(DB.ACCOUNT.FLAGS)) {
                if (BuildConfig.DEBUG) {
                    String s = null;
                    s.charAt(3);
                }
            }
            uploader.init(config);
            uploaders.put(uploaderName, uploader);
            uploadersById.put(uploader.getId(), uploader);
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

    public Uploader getUploader(long id) {
        return uploadersById.get(id);
    }

    public Uploader getUploaderByName(String name) {
        return uploaders.get(name);
    }

    public void connect(final Callback callback, final String name, final boolean uploading) {
        Uploader l = uploaders.get(name);
        if (l == null) {
            callback.run(name, Uploader.Status.INCORRECT_USAGE);
            return;
        }
        Status s = l.connect();
        switch (s) {
            case OK:
                callback.run(name, s);
                return;
            case CANCEL:
            case SKIP:
            case INCORRECT_USAGE:
            case ERROR:
                l.reset();
                callback.run(name, s);
                return;
            case NEED_REFRESH:
                mSpinner.show();
                handleRefreshResult(l, l.refreshToken());
            case NEED_AUTH:
                mSpinner.show();
                handleAuth(new Callback() {
                    @Override
                    public void run(String uploader, Status status) {
                        mSpinner.dismiss();
                        callback.run(uploader, status);
                    }
                }, l, s.authMethod);
                return;
        }
    }

    private void handleRefreshResult(Uploader uploader, Status status) {
        switch (status) {
            case ERROR:
                uploader.reset();
                return;
            case OK: {
                ContentValues tmp = new ContentValues();
                tmp.put("_id", uploader.getId());
                tmp.put(DB.ACCOUNT.AUTH_CONFIG, uploader.getAuthConfig());
                String args[] = {
                        Long.toString(uploader.getId())
                };
                mDB.update(DB.ACCOUNT.TABLE, tmp, "_id = ?", args);
                return;
            }
        }
        mSpinner.dismiss();
    }

    Uploader authUploader = null;
    Callback authCallback = null;

    private void handleAuth(Callback callback, Uploader l, AuthMethod authMethod) {
        authUploader = l;
        authCallback = callback;
        switch (authMethod) {
            case OAUTH2:
                activity.startActivityForResult(l.getAuthIntent(activity), CONFIGURE_REQUEST);
                return;
            case USER_PASS:
                askUsernamePassword(l, false);
                return;
        }
    }

    private void handleAuthComplete(Uploader uploader, Status s) {
        Callback cb = authCallback;
        authCallback = null;
        authUploader = null;
        switch (s) {
            case CANCEL:
            case ERROR:
            case INCORRECT_USAGE:
            case SKIP:
                uploader.reset();
                cb.run(uploader.getName(), s);
                return;
            case OK: {
                ContentValues tmp = new ContentValues();
                tmp.put("_id", uploader.getId());
                tmp.put(DB.ACCOUNT.AUTH_CONFIG, uploader.getAuthConfig());
                String args[] = {
                    Long.toString(uploader.getId())
                };
                mDB.update(DB.ACCOUNT.TABLE, tmp, "_id = ?", args);
                cb.run(uploader.getName(), s);
                return;
            }
            case NEED_AUTH:
                handleAuth(cb, uploader, s.authMethod);
                return;
        }
    }

    private JSONObject newObj(String str) {
        try {
            return new JSONObject(str);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void askUsernamePassword(final Uploader l, boolean showPassword) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(l.getName());
        // Get the layout inflater
        LayoutInflater inflater = activity.getLayoutInflater();
        final View view = inflater.inflate(R.layout.userpass, null);
        final CheckBox cb = (CheckBox) view.findViewById(R.id.showpass);
        final TextView tv1 = (TextView) view.findViewById(R.id.username);
        final TextView tv2 = (TextView) view.findViewById(R.id.password_input);
        String authConfigStr = l.getAuthConfig();
        final JSONObject authConfig = newObj(authConfigStr);
        String username = authConfig.optString("username", "");
        String password = authConfig.optString("password", "");
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
        builder.setPositiveButton(getResources().getString(R.string.ok), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    authConfig.put("username", tv1.getText());
                    authConfig.put("password", tv2.getText());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                testUserPass(l, authConfig, cb.isChecked());
            }
        });
        builder.setNeutralButton("Skip", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                handleAuthComplete(l, Status.SKIP);
            }
        });
        builder.setNegativeButton(getResources().getString(R.string.cancel), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                handleAuthComplete(l, Status.SKIP);
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void testUserPass(final Uploader l, final JSONObject authConfig,
            final boolean showPassword) {
        mSpinner.setTitle("Testing login " + l.getName());

        new AsyncTask<Uploader, String, Uploader.Status>() {

            final ContentValues config = new ContentValues();

            @Override
            protected Uploader.Status doInBackground(Uploader... params) {
                config.put(DB.ACCOUNT.AUTH_CONFIG, authConfig.toString());
                config.put("_id", l.getId());
                l.init(config);
                try {
                    return params[0].connect();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return Uploader.Status.ERROR;
                }
            }

            @Override
            protected void onPostExecute(Uploader.Status result) {
                handleAuthComplete(l, result);
            }
        }.execute(l);
    }

    private long mID = 0;
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
        final Uploader uploader = uploaders.get(pendingUploaders.iterator().next());
        pendingUploaders.remove(uploader.getName());
        doUpload(uploader);
    }

    private void doUpload(final Uploader uploader) {
        final ProgressDialog copySpinner = mSpinner;
        final SQLiteDatabase copyDB = mDBHelper.getWritableDatabase();

        copySpinner.setMessage("Uploading " + uploader.getName());

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
                switch (result) {
                    case CANCEL:
                        disableUploader(disableUploaderCallback, uploader, false);
                        return;
                    case SKIP:
                    case ERROR:
                    case INCORRECT_USAGE:
                        nextUploader();
                        return;
                    case OK:
                        uploadOK(uploader, copySpinner, copyDB, mID);
                        nextUploader();
                        return;
                    case NEED_AUTH: // should be handled inside connect "loop"
                        handleAuth(new Callback() {
                            @Override
                            public void run(String uploaderName,
                                    org.runnerup.export.Uploader.Status status) {
                                switch (status) {
                                    case CANCEL:
                                        disableUploader(disableUploaderCallback, uploader, false);
                                        return;
                                    case SKIP:
                                    case ERROR:
                                    case INCORRECT_USAGE:
                                    case NEED_AUTH: // should be handled inside
                                                    // connect "loop"
                                        nextUploader();
                                        return;
                                    case OK:
                                        doUpload(uploader);
                                        return;
                                }
                            }
                        }, uploader, result.authMethod);
                        return;
                }
            }
        }.execute(uploader);
    }

    private final Callback disableUploaderCallback = new Callback() {
        @Override
        public void run(String uploader, Status status) {
            nextUploader();
        }
    };

    private void uploadOK(Uploader uploader, ProgressDialog copySpinner, SQLiteDatabase copyDB,
            long id) {
        copySpinner.setMessage(getResources().getString(R.string.saving));
        ContentValues tmp = new ContentValues();
        tmp.put(DB.EXPORT.ACCOUNT, uploader.getId());
        tmp.put(DB.EXPORT.ACTIVITY, id);
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

        handleAuthComplete(authUploader, authUploader.getAuthResult(resultCode, data));
    }

    public void disableUploader(Callback disconnectCallback, String uploader, boolean clearUploads) {
        disableUploader(disconnectCallback, uploaders.get(uploader), clearUploads);
    }

    public void disableUploader(Callback callback, Uploader uploader, boolean clearUploads) {
        resetDB(callback, uploader, clearUploads);
    }

    void resetDB(final Callback callback, final Uploader uploader, final boolean clearUploads) {
        final String args[] = {
            Long.toString(uploader.getId())
        };
        ContentValues config = new ContentValues();
        config.putNull(DB.ACCOUNT.AUTH_CONFIG);
        mDB.update(DB.ACCOUNT.TABLE, config, "_id = ?", args);

        if (clearUploads) {
            mDB.delete(DB.EXPORT.TABLE, DB.EXPORT.ACCOUNT + " = ?", args);
        }

        uploader.reset();
        callback.run(uploader.getName(), Uploader.Status.OK);
    }

    public void clearUploads(Callback callback, String uploader) {
        final String args[] = {
            uploader
        };
        mDB.delete(DB.EXPORT.TABLE, DB.EXPORT.ACCOUNT + " = ?", args);
        callback.run(uploader, Uploader.Status.OK);
    }

    public void clearUpload(String name, long id) {
        Uploader uploader = uploaders.get(name);
        if (uploader != null) {
            final String args[] = {
                    Long.toString(uploader.getId()), Long.toString(id)
            };
            mDB.delete(DB.EXPORT.TABLE, DB.EXPORT.ACCOUNT + " = ? AND " + DB.EXPORT.ACTIVITY
                    + " = ?", args);
        }
    }

    public void loadActivityList(final List<ActivityItem> items, final String uploader, final Callback callback) {
        mSpinner.setTitle("Loading activities");
        mSpinner.setMessage("Listing activities from " + uploader);
        mSpinner.show();

        new AsyncTask<Uploader, String, Status>() {
            @Override
            protected Uploader.Status doInBackground(Uploader... params) {
                return params[0].listActivities(items);
            }

            @Override
            protected void onPostExecute(Uploader.Status result) {
                callback.run(uploader, result);
                mSpinner.dismiss();
            }
        }.execute(uploaders.get(uploader));
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
    }

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
        Uploader uploader = uploaders.get(pendingListWorkout.iterator().next());
        pendingListWorkout.remove(uploader.getName());
        mSpinner.setMessage("Configure " + uploader.getName());
        if (!uploader.isConfigured()) {
            nextListWorkout();
            return;
        }
        doListWorkout(uploader);
    }

    protected void doListWorkout(final Uploader uploader) {
        final ProgressDialog copySpinner = mSpinner;

        copySpinner.setMessage("Listing from " + uploader.getName());
        final ArrayList<Pair<String, String>> list = new ArrayList<Pair<String, String>>();

        new AsyncTask<Uploader, String, Uploader.Status>() {

            @Override
            protected Uploader.Status doInBackground(Uploader... params) {
                try {
                    return params[0].listWorkouts(list);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return Uploader.Status.ERROR;
                }
            }

            @Override
            protected void onPostExecute(Uploader.Status result) {
                switch (result) {
                    case CANCEL:
                    case ERROR:
                    case INCORRECT_USAGE:
                    case SKIP:
                        break;
                    case OK:
                        for (Pair<String, String> w : list) {
                            workoutRef.add(new WorkoutRef(uploader.getName(), w.first, w.second));
                        }
                        break;
                    case NEED_AUTH:
                        handleAuth(new Callback() {
                            @Override
                            public void run(String uploaderName,
                                    org.runnerup.export.Uploader.Status status) {
                                switch (status) {
                                    case CANCEL:
                                    case SKIP:
                                    case ERROR:
                                    case INCORRECT_USAGE:
                                    case NEED_AUTH: // should be handled inside
                                                    // connect "loop"
                                        nextListWorkout();
                                        break;
                                    case OK:
                                        doListWorkout(uploader);
                                        break;
                                }
                            }
                        }, uploader, result.authMethod);
                        return;
                }
                nextListWorkout();
            }
        }.execute(uploader);
    }

    private void doneListing() {
        mSpinner.dismiss();
        Callback cb = listWorkoutCallback;
        listWorkoutCallback = null;
        if (cb != null)
            cb.run(null, Status.OK);
    }

    public void loadWorkouts(final HashSet<WorkoutRef> pendingWorkouts, final Callback callback) {
        int cnt = pendingWorkouts.size();
        mSpinner.setTitle("Downloading workouts (" + cnt + ")");
        mSpinner.show();
        new AsyncTask<String, String, Uploader.Status>() {

            @Override
            protected void onProgressUpdate(String... values) {
                mSpinner.setMessage("Loading " + values[0] + " from " + values[1]);
            }

            @Override
            protected Uploader.Status doInBackground(String... params0) {
                for (WorkoutRef ref : pendingWorkouts) {
                    publishProgress(ref.workoutName, ref.uploader);
                    Uploader uploader = uploaders.get(ref.uploader);
                    File f = WorkoutSerializer.getFile(context, ref.workoutName);
                    File w = f;
                    if (f.exists()) {
                        w = WorkoutSerializer.getFile(context, ref.workoutName + ".tmp");
                    }
                    try {
                        uploader.downloadWorkout(w, ref.workoutKey);
                        if (w != f) {
                            if (compareFiles(w, f) != true) {
                                System.err.println("overwriting " + f.getPath() + " with "
                                        + w.getPath());
                                // TODO dialog
                                f.delete();
                                w.renameTo(f);
                            } else {
                                System.err.println("file identical...deleting temporary "
                                        + w.getPath());
                                w.delete();
                            }
                        }
                    } catch (Exception e) {
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
                    callback.run(null, Uploader.Status.OK);
                }
            }
        }.execute("string");
    }

    public static boolean compareFiles(File w, File f) {
        if (w.length() != f.length())
            return false;

        boolean cmp = true;
        FileInputStream f1 = null;
        FileInputStream f2 = null;
        try {
            f1 = new FileInputStream(w);
            f2 = new FileInputStream(f);
            byte buf1[] = new byte[1024];
            byte buf2[] = new byte[1024];
            do {
                int cnt1 = f1.read(buf1);
                int cnt2 = f2.read(buf2);
                if (cnt1 <= 0 || cnt2 <= 0)
                    break;

                if (!java.util.Arrays.equals(buf1, buf2)) {
                    cmp = false;
                    break;
                }
            } while (true);
        } catch (Exception ex) {
        }

        if (f1 != null) {
            try {
                f1.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (f2 != null) {
            try {
                f2.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return cmp;
    }

    /**
     * Load uploader private data
     * 
     * @param uploader
     * @return
     * @throws Exception
     */
    String loadData(Uploader uploader) throws Exception {
        InputStream in = context.getAssets()
                .open(uploader.getName() + ".data");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String key = Encryption.calculateRFC2104HMAC("RunnerUp",
                uploader.getName());
        Encryption.decrypt(in, out, key);

        return out.toString();
    }

    /**
     * Get preferences
     * 
     * @return
     */
    public SharedPreferences getPreferences(Uploader uploader) {
        if (uploader == null)
            return PreferenceManager.getDefaultSharedPreferences(context);
        else
            return context.getSharedPreferences(uploader.getName(), Context.MODE_PRIVATE);
    }

    public Resources getResources() {
        return context.getResources();
    }

    public Context getContext() {
        return context;
    }

    /**
     * Upload set of activities for a specific uploader
     */
    List<ActivityItem> syncActivitiesList = null;
    Callback syncActivityCallback = null;
    StringBuffer cancelSync = null;

    public void syncActivities(SyncMode mode, Callback uploadCallback, String synchronizerName, List<ActivityItem> list,
                               final StringBuffer cancel) {

        prepareSpinnerForSync(list, cancel, mode, synchronizerName);

        load(synchronizerName);
        final Uploader synchronizer = uploaders.get(synchronizerName);
        if (synchronizer == null) {
            uploadCallback.run(synchronizerName, Status.INCORRECT_USAGE);
            return;
        }
        cancelSync = cancel;
        syncActivityCallback = uploadCallback;
        syncActivitiesList = list;
        mSpinner.show();

        mSpinner.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        synchronized (cancel) {
                            cancel.append('t');
                        }
                        mSpinner.setMessage(getResources().getString(R.string.cancelling_please_wait));
                    }
                });

        syncNextActivity(synchronizer, mode);
    }

    private void prepareSpinnerForSync(List<ActivityItem> list, final StringBuffer cancel, SyncMode mode, String uploader) {
        String msg = mode.name() + uploader;
        mSpinner.setTitle(msg);
        mSpinner.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel",
                new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        synchronized (cancel) {
                            cancel.append('t');
                        }
                        mSpinner.setMessage(getResources().getString(R.string.cancelling_please_wait));
                    }
                });
        mSpinner.setCancelable(false);
        mSpinner.setCanceledOnTouchOutside(false);
        mSpinner.setMax(list.size());
    }

    protected boolean checkCancel(StringBuffer cancel) {
        synchronized (cancel) {
            return cancel.length() > 0;
        }
    }

    void syncNextActivity(final Uploader uploader, SyncMode mode) {
        if (checkCancel(cancelSync)) {
            mSpinner.cancel();
            syncActivityCallback.run(uploader.getName(), Uploader.Status.CANCEL);
            return;
        }

        if (syncActivitiesList.size() == 0) {
            mSpinner.cancel();
            syncActivityCallback.run(uploader.getName(), Uploader.Status.OK);
            return;
        }

        mSpinner.setProgress(syncActivitiesList.size());
        ActivityItem ai = syncActivitiesList.get(0);
        syncActivitiesList.remove(0);
        doSyncMulti(uploader, mode, ai);
    }

    private void doSyncMulti(final Uploader uploader, final SyncMode mode, final ActivityItem activityItem) {
        final ProgressDialog copySpinner = mSpinner;
        final SQLiteDatabase copyDB = mDBHelper.getWritableDatabase();

        copySpinner.setMessage(Long.toString(1 + syncActivitiesList.size()) + " remaining");
        new AsyncTask<Uploader, String, Status>() {

            @Override
            protected Uploader.Status doInBackground(Uploader... params) {
                try {
                    Uploader.Status result = Uploader.Status.ERROR;
                    switch (mode) {
                        case UPLOAD:
                            result = uploader.upload(copyDB, activityItem.getId());
                            break;
                        case DOWNLOAD:
                            result = uploader.download(copyDB, activityItem);
                            break;
                    }
                    return result;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return Uploader.Status.ERROR;
                }
            }

            @Override
            protected void onPostExecute(Uploader.Status result) {
                switch (result) {
                    case CANCEL:
                    case ERROR:
                    case INCORRECT_USAGE:
                    case SKIP:
                        break;
                    case OK:
                        uploadOK(uploader, copySpinner, copyDB, activityItem.getId());
                        break;
                    case NEED_AUTH:
                        handleAuth(new Callback() {
                            @Override
                            public void run(String uploaderName,
                                    Uploader.Status status) {
                                switch (status) {
                                    case CANCEL:
                                    case SKIP:
                                    case ERROR:
                                    case INCORRECT_USAGE:
                                    case NEED_AUTH: // should be handled inside
                                                    // connect "loop"
                                        syncActivitiesList.clear();
                                        syncNextActivity(uploader, mode);
                                        break;
                                    case OK:
                                        doSyncMulti(uploader, mode, activityItem);
                                        break;
                                }
                            }
                        }, uploader, result.authMethod);

                        break;
                }
                syncNextActivity(uploader, mode);
            }
        }.execute(uploader);
    }

    Callback feedCallback = null;
    Set<String> feedUploaders = null;
    FeedList feedList = null;
    StringBuffer feedCancel = null;

    public void syncronizeFeed(Callback cb, Set<String> uploaders, FeedList dst, StringBuffer cancel) {
        feedCallback = cb;
        feedUploaders = uploaders;
        feedList = dst;
        feedCancel = cancel;
        nextSyncFeed();
    }

    void nextSyncFeed() {
        if (checkCancel(feedCancel)) {
            feedUploaders.clear();
        }

        if (feedUploaders.isEmpty()) {
            Callback cb = feedCallback;
            feedCallback = null;
            cb.run(null, Uploader.Status.OK);
            return;
        }

        final String uploaderName = feedUploaders.iterator().next();
        feedUploaders.remove(uploaderName);
        final Uploader uploader = uploaders.get(uploaderName);
        syncFeed(uploader);
    }

    void syncFeed(final Uploader uploader) {
        if (uploader == null) {
            nextSyncFeed();
            return;
        }

        final FeedList.FeedUpdater feedUpdater = feedList.getUpdater();
        feedUpdater.start(uploader.getName());
        new AsyncTask<Uploader, String, Uploader.Status>() {

            @Override
            protected Uploader.Status doInBackground(Uploader... params) {
                try {
                    return params[0].getFeed(feedUpdater);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return Uploader.Status.ERROR;
                }
            }

            @Override
            protected void onPostExecute(Uploader.Status result) {
                if (result == Uploader.Status.OK) {
                    feedUpdater.complete();
                } else if (result == Uploader.Status.NEED_AUTH) {
                    handleAuth(new Callback() {
                        @Override
                        public void run(String uploaderName, Uploader.Status s2) {
                            if (s2 == Uploader.Status.OK) {
                                syncFeed(uploader);
                            } else {
                                nextSyncFeed();
                            }
                        }
                    }, uploader, result.authMethod);
                    return;
                } else {
                    if (result.ex != null)
                        result.ex.printStackTrace();
                }
                nextSyncFeed();
            }
        }.execute(uploader);
    }

    public void loadLiveLoggers(List<WorkoutObserver> liveLoggers) {
        liveLoggers.clear();
        Resources res = getResources();
        String key = res.getString(R.string.pref_runneruplive_active);
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, false) == false) {
            return;
        }

        String from[] = new String[] {
                "_id", DB.ACCOUNT.NAME, DB.ACCOUNT.AUTH_CONFIG, DB.ACCOUNT.FLAGS
        };

        Cursor c = null;
        try {
            c = mDB.query(DB.ACCOUNT.TABLE, from,
                    "( " + DB.ACCOUNT.FLAGS + "&" + (1 << DB.ACCOUNT.FLAG_LIVE) + ") != 0",
                    null, null, null, null, null);
            if (c.moveToFirst()) {
                do {
                    ContentValues config = DBHelper.get(c);
                    Uploader u = add(config);
                    if (u.isConfigured() && u.checkSupport(Uploader.Feature.LIVE) &&
                            (u instanceof WorkoutObserver)) {
                        liveLoggers.add((WorkoutObserver) u);
                    }
                } while (c.moveToNext());
            }
        } finally {
            if (c != null)
                c.close();
        }
    }
}
