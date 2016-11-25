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

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TableRow;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.DBHelper;
import org.runnerup.export.Synchronizer.AuthMethod;
import org.runnerup.export.Synchronizer.Status;
import org.runnerup.feed.FeedList;
import org.runnerup.feedwidget.FeedWidgetProvider;
import org.runnerup.tracker.WorkoutObserver;
import org.runnerup.util.Bitfield;
import org.runnerup.util.Encryption;
import org.runnerup.util.SyncActivityItem;
import org.runnerup.workout.WorkoutSerializer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@TargetApi(Build.VERSION_CODES.FROYO)
public class SyncManager {
    public static final int CONFIGURE_REQUEST = 1;
    public static final long ERROR_ACTIVITY_ID = -1L;

    private SQLiteDatabase mDB = null;
    private Activity mActivity = null;
    private Context mContext = null;
    private final Map<String, Synchronizer> synchronizers = new HashMap<>();
    private final Map<Long, Synchronizer> synchronizersById = new HashMap<>();

    private ProgressDialog mSpinner = null;

    public enum SyncMode {
        DOWNLOAD(R.string.Downloading_from_1s),
        UPLOAD(R.string.Uploading_to_1s);

        final int textId;

        SyncMode(int textId) {
            this.textId = textId;
        }

        public int getTextId() {
            return textId;
        }
    }

    public interface Callback {
        void run(String synchronizerName, Synchronizer.Status status);
    }

    private void init(Activity activity, Context context, ProgressDialog spinner) {
        this.mActivity = activity;
        this.mContext = context;
        mDB = DBHelper.getWritableDatabase(context);
        mSpinner = spinner;
        mSpinner.setCancelable(false);
    }
    public SyncManager(Activity activity) {
        init(activity, activity, new ProgressDialog(activity));
    }

    public SyncManager(Context context) {
        init(null, context, new ProgressDialog(context));
    }
    public SyncManager(Context context, ProgressDialog spinner) {
        init(null, context, spinner);
    }

    public synchronized void close() {
        if (mDB != null) {
            DBHelper.closeDB(mDB);
        }
    }

    public void clear() {
        synchronizers.clear();
        synchronizersById.clear();
    }

    public long load(String synchronizerName) {
        String from[] = new String[] {
                "_id", DB.ACCOUNT.NAME, DB.ACCOUNT.AUTH_CONFIG, DB.ACCOUNT.FLAGS
        };
        String args[] = {
                synchronizerName
        };
        Cursor c = mDB.query(DB.ACCOUNT.TABLE, from, DB.ACCOUNT.NAME + " = ?",
                args, null, null, null, null);
        long id = -1;
        if (c.moveToFirst()) {
            ContentValues config = DBHelper.get(c);
            //noinspection ConstantConditions
            id = config.getAsLong("_id");
            add(config);
        }
        c.close();
        return id;
    }

    public static ContentValues loadConfig(SQLiteDatabase db, String synchronizerName) {
        ContentValues config = null;
                String from[] = new String[] {
                "_id", DB.ACCOUNT.NAME, DB.ACCOUNT.AUTH_CONFIG, DB.ACCOUNT.FLAGS, DB.ACCOUNT.FORMAT
        };
        String args[] = {
                synchronizerName
        };
        Cursor c = db.query(DB.ACCOUNT.TABLE, from, DB.ACCOUNT.NAME + " = ?",
                args, null, null, null, null);
        if (c.moveToFirst()) {
            config = DBHelper.get(c);
        }
        c.close();
        return config;
    }

    @SuppressWarnings("null")
    public Synchronizer add(ContentValues config) {
        if (config == null) {
            Log.e(getClass().getName(), "Add null!");
            if (BuildConfig.DEBUG) { throw new AssertionError(); }
            return null;
        }

        String synchronizerName = config.getAsString(DB.ACCOUNT.NAME);
        if (synchronizerName == null) {
            Log.e(getClass().getName(), "name not found!");
            return null;
        }
        if (synchronizers.containsKey(synchronizerName)) {
            return synchronizers.get(synchronizerName);
        }
        Synchronizer synchronizer = null;
        if (synchronizerName.contentEquals(RunKeeperSynchronizer.NAME)) {
            synchronizer = new RunKeeperSynchronizer(this);
        } else if (synchronizerName.contentEquals(GarminSynchronizer.NAME)) {
            synchronizer = new GarminSynchronizer(this);
        } else if (synchronizerName.contentEquals(FunBeatSynchronizer.NAME)) {
            synchronizer = new FunBeatSynchronizer(this);
        } else if (synchronizerName.contentEquals(MapMyRunSynchronizer.NAME)) {
            synchronizer = new MapMyRunSynchronizer(this);
        } else if (synchronizerName.contentEquals(NikePlusSynchronizer.NAME)) {
            synchronizer = new NikePlusSynchronizer(this);
        } else if (synchronizerName.contentEquals(JoggSESynchronizer.NAME)) {
            synchronizer = new JoggSESynchronizer(this);
        } else if (synchronizerName.contentEquals(EndomondoSynchronizer.NAME)) {
            synchronizer = new EndomondoSynchronizer(this);
        } else if (synchronizerName.contentEquals(RunningAHEADSynchronizer.NAME)) {
            synchronizer = new RunningAHEADSynchronizer(this);
        } else if (synchronizerName.contentEquals(RunnerUpLiveSynchronizer.NAME)) {
            synchronizer = new RunnerUpLiveSynchronizer(mContext);
        } else if (synchronizerName.contentEquals(DigifitSynchronizer.NAME)) {
            synchronizer = new DigifitSynchronizer(this);
        } else if (synchronizerName.contentEquals(StravaSynchronizer.NAME)) {
            synchronizer = new StravaSynchronizer(this);
        } else if (synchronizerName.contentEquals(FacebookSynchronizer.NAME)) {
            synchronizer = new FacebookSynchronizer(mContext, this);
        } else if (synchronizerName.contentEquals(GooglePlusSynchronizer.NAME)) {
            synchronizer = new GooglePlusSynchronizer(this);
        } else if (synchronizerName.contentEquals(RuntasticSynchronizer.NAME)) {
            synchronizer = new RuntasticSynchronizer(this);
        } else if (synchronizerName.contentEquals(GoogleFitSynchronizer.NAME)) {
            synchronizer = new GoogleFitSynchronizer(mContext, this);
        } else if (synchronizerName.contentEquals(RunningFreeOnlineSynchronizer.NAME)) {
            synchronizer = new RunningFreeOnlineSynchronizer();
        } else if (synchronizerName.contentEquals(FileSynchronizer.NAME)) {
            synchronizer = new FileSynchronizer();
        } else if (synchronizerName.contentEquals(RunalyzeSynchronizer.NAME)) {
            synchronizer = new RunalyzeSynchronizer();
        }

        if (synchronizer != null) {
            if (!config.containsKey(DB.ACCOUNT.FLAGS)) {
                if (BuildConfig.DEBUG) {
                    if (BuildConfig.DEBUG) { throw new AssertionError(); }
                }
            }
            synchronizer.init(config);
            synchronizer.setAuthNotice(config.getAsInteger(Constants.DB.ACCOUNT.AUTH_NOTICE));
            synchronizers.put(synchronizerName, synchronizer);
            synchronizersById.put(synchronizer.getId(), synchronizer);
        }
        return synchronizer;
    }

    public boolean isConfigured(final String name) {
        Synchronizer l = synchronizers.get(name);
        return l == null || l.isConfigured();
    }

    public Synchronizer getSynchronizer(long id) {
        return synchronizersById.get(id);
    }

    public Synchronizer getSynchronizerByName(String name) {
        return synchronizers.get(name);
    }

    public void connect(final Callback callback, final String name, final boolean uploading) {
        Synchronizer l = synchronizers.get(name);
        if (l == null) {
            callback.run(name, Synchronizer.Status.INCORRECT_USAGE);
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
                    public void run(String synchronizerName, Status status) {
                        mSpinner.dismiss();
                        callback.run(synchronizerName, status);
                    }
                }, l, s.authMethod);
                return;
        }
    }

    private void handleRefreshResult(Synchronizer synchronizer, Status status) {
        switch (status) {
            case ERROR:
                synchronizer.reset();
                return;
            case OK: {
                ContentValues tmp = new ContentValues();
                tmp.put("_id", synchronizer.getId());
                tmp.put(DB.ACCOUNT.AUTH_CONFIG, synchronizer.getAuthConfig());
                String args[] = {
                        Long.toString(synchronizer.getId())
                };
                mDB.update(DB.ACCOUNT.TABLE, tmp, "_id = ?", args);
                return;
            }
        }
        mSpinner.dismiss();
    }

    private Synchronizer authSynchronizer = null;
    private Callback authCallback = null;

    private void handleAuth(Callback callback, final Synchronizer l, AuthMethod authMethod) {
        authSynchronizer = l;
        authCallback = callback;
        switch (authMethod) {
            case OAUTH2:
                mActivity.startActivityForResult(l.getAuthIntent(mActivity), CONFIGURE_REQUEST);
                return;
            case USER_PASS:
            case USER_PASS_URL:
                askUsernamePassword(l, authMethod);
                return;
            case FILEPERMISSION:
                askFileUrl(l);
                return;
        }
    }

    private void handleAuthComplete(Synchronizer synchronizer, Status s) {
        handleAuthComplete(synchronizer, s, null, null);
    }
    private void handleAuthComplete(Synchronizer synchronizer, Status s, String url, String format) {
        Callback cb = authCallback;
        authCallback = null;
        authSynchronizer = null;
        switch (s) {
            case CANCEL:
            case ERROR:
            case INCORRECT_USAGE:
            case SKIP:
                synchronizer.reset();
                cb.run(synchronizer.getName(), s);
                return;
            case OK: {
                ContentValues tmp = new ContentValues();
                tmp.put("_id", synchronizer.getId());
                tmp.put(DB.ACCOUNT.AUTH_CONFIG, synchronizer.getAuthConfig());
                if (!TextUtils.isEmpty(url)) {
                    tmp.put(DB.ACCOUNT.URL, url);
                }
                if (!TextUtils.isEmpty(format)) {
                    tmp.put(DB.ACCOUNT.FORMAT, format);
                }
                String args[] = {
                    Long.toString(synchronizer.getId())
                };
                mDB.update(DB.ACCOUNT.TABLE, tmp, "_id = ?", args);
                cb.run(synchronizer.getName(), s);
                return;
            }
            case NEED_AUTH:
                handleAuth(cb, synchronizer, s.authMethod);
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

    private void askUsernamePassword(final Synchronizer sync, final AuthMethod authMethod) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle(sync.getName());

        final View view = View.inflate(mActivity, R.layout.userpass, null);
        final CheckBox cb = (CheckBox) view.findViewById(R.id.showpass);
        final TextView tv1 = (TextView) view.findViewById(R.id.username);
        final TextView tv2 = (TextView) view.findViewById(R.id.password_input);
        final TextView urlInput = (TextView) view.findViewById(R.id.url_input);
        final TextView tvAuthNotice = (TextView) view.findViewById(R.id.textViewAuthNotice);
        final TableRow rowUrl = (TableRow) view.findViewById(R.id.table_row_url);
        String authConfigStr = sync.getAuthConfig();
        final JSONObject authConfig = newObj(authConfigStr);
        String username = authConfig != null ? authConfig.optString("username", "") : null;
        String password = authConfig != null ? authConfig.optString("password", "") : null;
        tv1.setText(username);
        tv2.setText(password);
        cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                tv2.setInputType(InputType.TYPE_CLASS_TEXT
                        | (isChecked ? 0
                        : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            }
        });
        if (sync.getAuthNotice() != null) {
            tvAuthNotice.setVisibility(View.VISIBLE);
            tvAuthNotice.setText(sync.getAuthNotice());
        } else {
            tvAuthNotice.setVisibility(View.GONE);
        }
        if (AuthMethod.USER_PASS_URL.equals(authMethod)) {
            rowUrl.setVisibility(View.VISIBLE);
            urlInput.setText(authConfig.optString(DB.ACCOUNT.URL));
        } else {
            rowUrl.setVisibility(View.GONE);
        }

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(view);
        builder.setPositiveButton(getResources().getString(R.string.OK), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    //noinspection ConstantConditions
                    authConfig.put("username", tv1.getText());
                    authConfig.put("password", tv2.getText());
                    if (authMethod == AuthMethod.USER_PASS_URL) {
                        authConfig.put(DB.ACCOUNT.URL, urlInput.getText());
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                testUserPass(sync, authConfig);
            }
        });
        builder.setNeutralButton("Skip", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                handleAuthComplete(sync, Status.SKIP);
            }
        });
        builder.setNegativeButton(getResources().getString(R.string.Cancel), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                handleAuthComplete(sync, Status.SKIP);
            }
        });
        builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialogInterface, int i, KeyEvent keyEvent) {
                if (i == KeyEvent.KEYCODE_BACK && keyEvent.getAction() == KeyEvent.ACTION_UP) {
                    handleAuthComplete(sync, Status.CANCEL);
                }
                return false;
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.show();
    }


    private void testUserPass(final Synchronizer l, final JSONObject authConfig) {
        mSpinner.setTitle("Testing login " + l.getName());

        new AsyncTask<Synchronizer, String, Synchronizer.Status>() {

            final ContentValues config = new ContentValues();

            @Override
            protected Synchronizer.Status doInBackground(Synchronizer... params) {
                config.put(DB.ACCOUNT.AUTH_CONFIG, authConfig.toString());
                config.put("_id", l.getId());
                l.init(config);
                try {
                    return params[0].connect();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return Synchronizer.Status.ERROR;
                }
            }

            @Override
            protected void onPostExecute(Synchronizer.Status result) {
                handleAuthComplete(l, result);
            }
        }.execute(l);
    }

    private void askFileUrl(final Synchronizer sync) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle(sync.getName());

        final View view = View.inflate(mActivity, R.layout.filepermission, null);
        final TextView tv1 = (TextView) view.findViewById(R.id.fileuri);
        final TextView tvAuthNotice = (TextView) view.findViewById(R.id.textViewAuthNotice);

        final CheckBox cbtcx = (CheckBox) view.findViewById(R.id.tcxformat);
        final CheckBox cbgpx = (CheckBox) view.findViewById(R.id.gpxformat);
        cbtcx.setChecked(true);

        String path;
        if (Build.VERSION.SDK_INT >= 19) {
            //noinspection InlinedApi
            path = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS).getPath();
        } else {
            path = Environment.getExternalStorageDirectory().getPath();
        }
        path += File.separator + "RunnerUp";
        tv1.setText(path);

        String s = "";
        if(!checkStoragePermissions(mActivity)) {
            s = "Note: Storage permission must be granted in Android settings";
        }
        if (sync.getAuthNotice() != null) {
            s += sync.getAuthNotice();
        }
        if ("".equals(s)) {
            tvAuthNotice.setVisibility(View.VISIBLE);
            tvAuthNotice.setText(s);
        } else {
            tvAuthNotice.setVisibility(View.GONE);
        }

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(view);
        builder.setPositiveButton(getResources().getString(R.string.OK), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //Set default values
                ContentValues config = new ContentValues();
                config.put(DB.ACCOUNT.AUTH_CONFIG, tv1.getText().toString());
                config.put("_id", sync.getId());
                String format = "";
                if (cbtcx.isChecked()) {
                    format = "tcx,";
                }
                if (cbgpx.isChecked()) {
                    format += "gpx,";
                }
                sync.init(config);

                //Set URL used for displaying
                String url;
                try {
                    url = (new File(sync.getAuthConfig())).toURI().toURL().toString();
                } catch (MalformedURLException e) {
                    url = "";
                }

                handleAuthComplete(sync, sync.connect(), url, format);
            }
        });
        builder.setNegativeButton(getResources().getString(R.string.Cancel), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                handleAuthComplete(sync, Status.SKIP);
            }
        });
        builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialogInterface, int i, KeyEvent keyEvent) {
                if (i == KeyEvent.KEYCODE_BACK && keyEvent.getAction() == KeyEvent.ACTION_UP) {
                    handleAuthComplete(sync, Status.CANCEL);
                }
                return false;
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private boolean checkStoragePermissions(final Activity activity) {
        boolean result = true;
        String[] requiredPerms;
        if (Build.VERSION.SDK_INT >= 16) {
            //noinspection InlinedApi
            requiredPerms = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
        } else {
            requiredPerms = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
        }
        List<String> defaultPerms = new ArrayList<>();
        for (final String perm : requiredPerms) {
            if (ContextCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED) {
                //Normally, ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
                //should be used here but this is needed anyway (no specific motivation)
                defaultPerms.add(perm);
            }
        }
        if (defaultPerms.size() > 0) {
            // Request permission, dont care about the result
            final String[] perms = new String[defaultPerms.size()];
            defaultPerms.toArray(perms);
            ActivityCompat.requestPermissions(activity, perms, 0);
            result = false;
        }
        return result;
    }

    private long mID = 0;
    private Callback uploadCallback = null;
    private HashSet<String> pendingSynchronizers = null;

    public void startUploading(Callback callback, HashSet<String> synchronizers, long id) {
        mID = id;
        uploadCallback = callback;
        pendingSynchronizers = synchronizers;
        mSpinner.setTitle("Uploading (" + pendingSynchronizers.size() + ")");
        mSpinner.show();
        nextSynchronizer();
    }

    private void nextSynchronizer() {
        if (pendingSynchronizers.isEmpty()) {
            doneUploading();
            return;
        }

        mSpinner.setTitle("Uploading (" + pendingSynchronizers.size() + ")");
        final Synchronizer synchronizer = synchronizers.get(pendingSynchronizers.iterator().next());
        pendingSynchronizers.remove(synchronizer.getName());
        doUpload(synchronizer);
    }

    private void doUpload(final Synchronizer synchronizer) {
        final ProgressDialog copySpinner = mSpinner;
        final SQLiteDatabase copyDB = DBHelper.getWritableDatabase(mContext);

        copySpinner.setMessage(getResources().getString(SyncMode.UPLOAD.getTextId(), synchronizer.getName()));

        new AsyncTask<Synchronizer, String, Synchronizer.Status>() {

            @Override
            protected Synchronizer.Status doInBackground(Synchronizer... params) {
                try {
                    return params[0].upload(copyDB, mID);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return Synchronizer.Status.ERROR;
                }
            }

            @Override
            protected void onPostExecute(Synchronizer.Status result) {
                switch (result) {
                    case CANCEL:
                        disableSynchronizer(disableSynchronizerCallback, synchronizer, false);
                        return;
                    case SKIP:
                    case ERROR:
                    case INCORRECT_USAGE:
                        nextSynchronizer();
                        return;
                    case OK:
                        syncOK(synchronizer, copySpinner, copyDB, mID);
                        nextSynchronizer();
                        return;
                    case NEED_AUTH: // should be handled inside connect "loop"
                        handleAuth(new Callback() {
                            @Override
                            public void run(String synchronizerName,
                                    Synchronizer.Status status) {
                                switch (status) {
                                    case CANCEL:
                                        disableSynchronizer(disableSynchronizerCallback, synchronizer, false);
                                        return;
                                    case SKIP:
                                    case ERROR:
                                    case INCORRECT_USAGE:
                                    case NEED_AUTH: // should be handled inside
                                                    // connect "loop"
                                        nextSynchronizer();
                                        return;
                                    case OK:
                                        doUpload(synchronizer);
                                        return;
                                }
                            }
                        }, synchronizer, result.authMethod);
                        return;
                }
            }
        }.execute(synchronizer);
    }

    private final Callback disableSynchronizerCallback = new Callback() {
        @Override
        public void run(String synchronizerName, Status status) {
            nextSynchronizer();
        }
    };

    private void syncOK(Synchronizer synchronizer, ProgressDialog copySpinner, SQLiteDatabase copyDB,
                        long id) {
        copySpinner.setMessage(getResources().getString(R.string.Saving));
        ContentValues tmp = new ContentValues();
        tmp.put(DB.EXPORT.ACCOUNT, synchronizer.getId());
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

        handleAuthComplete(authSynchronizer, authSynchronizer.getAuthResult(resultCode, data));
    }

    public void disableSynchronizer(Callback disconnectCallback, String synchronizer, boolean clearUploads) {
        disableSynchronizer(disconnectCallback, synchronizers.get(synchronizer), clearUploads);
    }

    private void disableSynchronizer(Callback callback, Synchronizer synchronizer, boolean clearUploads) {
        resetDB(callback, synchronizer, clearUploads);
    }

    private void resetDB(final Callback callback, final Synchronizer synchronizer, final boolean clearUploads) {
        final String args[] = {
            Long.toString(synchronizer.getId())
        };
        ContentValues config = new ContentValues();
        config.putNull(DB.ACCOUNT.AUTH_CONFIG);
        mDB.update(DB.ACCOUNT.TABLE, config, "_id = ?", args);

        if (clearUploads) {
            mDB.delete(DB.EXPORT.TABLE, DB.EXPORT.ACCOUNT + " = ?", args);
        }

        synchronizer.reset();
        callback.run(synchronizer.getName(), Synchronizer.Status.OK);
    }

    public void clearUploadsByName(Callback callback, String synchronizerName) {
        Synchronizer synchronizer = synchronizers.get(synchronizerName);
        final String args[] = {
                Long.toString(synchronizer.getId())
        };
        mDB.delete(DB.EXPORT.TABLE, DB.EXPORT.ACCOUNT + " = ?", args);
        callback.run(synchronizerName, Synchronizer.Status.OK);
    }

    public void clearUpload(String name, long id) {
        Synchronizer synchronizer = synchronizers.get(name);
        if (synchronizer != null) {
            final String args[] = {
                    Long.toString(synchronizer.getId()), Long.toString(id)
            };
            mDB.delete(DB.EXPORT.TABLE, DB.EXPORT.ACCOUNT + " = ? AND " + DB.EXPORT.ACTIVITY
                    + " = ?", args);
        }
    }

    public void loadActivityList(final List<SyncActivityItem> items, final String synchronizerName, final Callback callback) {
        mSpinner.setTitle(getResources().getString(R.string.Loading_activities));
        mSpinner.setMessage(getResources().getString(R.string.Fetching_activities_from_1s, synchronizerName));
        mSpinner.show();

        new AsyncTask<Synchronizer, String, Status>() {
            @Override
            protected Synchronizer.Status doInBackground(Synchronizer... params) {
                return params[0].listActivities(items);
            }

            @Override
            protected void onPostExecute(Synchronizer.Status result) {
                callback.run(synchronizerName, result);
                mSpinner.dismiss();
            }
        }.execute(synchronizers.get(synchronizerName));
    }


    public static class WorkoutRef {
        public WorkoutRef(String synchronizerName, String key, String workoutName) {
            this.synchronizer = synchronizerName;
            this.workoutKey = key;
            this.workoutName = workoutName;
        }

        public final String synchronizer;
        public final String workoutKey;
        public final String workoutName;
    }

    private Callback listWorkoutCallback = null;
    private HashSet<String> pendingListWorkout = null;
    private ArrayList<WorkoutRef> workoutRef = null;

    public void loadWorkoutList(ArrayList<WorkoutRef> workoutRef, Callback callback,
            HashSet<String> wourkouts) {

        listWorkoutCallback = callback;
        pendingListWorkout = wourkouts;
        this.workoutRef = workoutRef;

        mSpinner.setTitle("Loading workout list (" + pendingListWorkout.size() + ")");
        mSpinner.show();
        nextListWorkout();
    }

    private void nextListWorkout() {
        if (pendingListWorkout.isEmpty()) {
            doneListing();
            return;
        }

        mSpinner.setTitle("Loading workout list (" + pendingListWorkout.size() + ")");
        Synchronizer synchronizer = synchronizers.get(pendingListWorkout.iterator().next());
        pendingListWorkout.remove(synchronizer.getName());
        mSpinner.setMessage("Configure " + synchronizer.getName());
        if (!synchronizer.isConfigured()) {
            nextListWorkout();
            return;
        }
        doListWorkout(synchronizer);
    }

    private void doListWorkout(final Synchronizer synchronizer) {
        final ProgressDialog copySpinner = mSpinner;

        copySpinner.setMessage("Listing from " + synchronizer.getName());
        final ArrayList<Pair<String, String>> list = new ArrayList<>();

        new AsyncTask<Synchronizer, String, Synchronizer.Status>() {

            @Override
            protected Synchronizer.Status doInBackground(Synchronizer... params) {
                try {
                    return params[0].listWorkouts(list);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return Synchronizer.Status.ERROR;
                }
            }

            @Override
            protected void onPostExecute(Synchronizer.Status result) {
                switch (result) {
                    case CANCEL:
                    case ERROR:
                    case INCORRECT_USAGE:
                    case SKIP:
                        break;
                    case OK:
                        for (Pair<String, String> w : list) {
                            workoutRef.add(new WorkoutRef(synchronizer.getName(), w.first, w.second));
                        }
                        break;
                    case NEED_AUTH:
                        handleAuth(new Callback() {
                            @Override
                            public void run(String synchronizerName,
                                    Synchronizer.Status status) {
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
                                        doListWorkout(synchronizer);
                                        break;
                                }
                            }
                        }, synchronizer, result.authMethod);
                        return;
                }
                nextListWorkout();
            }
        }.execute(synchronizer);
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
        new AsyncTask<String, String, Synchronizer.Status>() {

            @Override
            protected void onProgressUpdate(String... values) {
                mSpinner.setMessage("Loading " + values[0] + " from " + values[1]);
            }

            @Override
            protected Synchronizer.Status doInBackground(String... params0) {
                for (WorkoutRef ref : pendingWorkouts) {
                    publishProgress(ref.workoutName, ref.synchronizer);
                    Synchronizer synchronizer = synchronizers.get(ref.synchronizer);
                    File f = WorkoutSerializer.getFile(mContext, ref.workoutName);
                    File w = f;
                    if (f.exists()) {
                        w = WorkoutSerializer.getFile(mContext, ref.workoutName + ".tmp");
                    }
                    try {
                        synchronizer.downloadWorkout(w, ref.workoutKey);
                        if (w != f) {
                            if (!compareFiles(w, f)) {
                                Log.e(getClass().getName(), "overwriting " + f.getPath() + " with "
                                        + w.getPath());
                                // TODO dialog
                                //noinspection ResultOfMethodCallIgnored
                                f.delete();
                                //noinspection ResultOfMethodCallIgnored
                                w.renameTo(f);
                            } else {
                                Log.e(getClass().getName(), "file identical...deleting temporary "
                                        + w.getPath());
                                //noinspection ResultOfMethodCallIgnored
                                w.delete();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        //noinspection ResultOfMethodCallIgnored
                        w.delete();
                    }
                }
                return Synchronizer.Status.OK;
            }

            @Override
            protected void onPostExecute(Synchronizer.Status result) {
                mSpinner.dismiss();
                if (callback != null) {
                    callback.run(null, Synchronizer.Status.OK);
                }
            }
        }.execute("string");
    }

    private static boolean compareFiles(File w, File f) {
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
            //f1, f2 checked
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
     * Load synchronizer private data
     *
     * @param synchronizer The instance
     * @return The private data
     * @throws Exception
     */
    String loadData(Synchronizer synchronizer) throws Exception {
        InputStream in = mContext.getAssets()
                .open(synchronizer.getName() + ".data");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String key = Encryption.calculateRFC2104HMAC("RunnerUp",
                synchronizer.getName());
        Encryption.decrypt(in, out, key);

        return out.toString();
    }

    private Resources getResources() {
        return mContext.getResources();
    }

    public Context getContext() {
        return mContext;
    }

    /**
     * Synch set of activities for a specific synchronizer
     */
    private List<SyncActivityItem> syncActivitiesList = null;
    private Callback syncActivityCallback = null;
    private StringBuffer cancelSync = null;

    public void syncActivities(SyncMode mode, Callback synchCallback, String synchronizerName, List<SyncActivityItem> list,
                               final StringBuffer cancel) {

        prepareSpinnerForSync(list, cancel, mode, synchronizerName);

        load(synchronizerName);
        final Synchronizer synchronizer = synchronizers.get(synchronizerName);
        if (synchronizer == null) {
            synchCallback.run(synchronizerName, Status.INCORRECT_USAGE);
            return;
        }
        cancelSync = cancel;
        syncActivityCallback = synchCallback;
        syncActivitiesList = list;
        mSpinner.show();
        Button noButton = mSpinner.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (noButton != null) {
            noButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        synchronized (cancel) {
                            cancel.append('t');
                        }
                        if (mSpinner != null)
                            mSpinner.setMessage(getResources().getString(R.string.Cancellingplease_wait));
                    }
                });
        }
        syncNextActivity(synchronizer, mode);
    }

    private void prepareSpinnerForSync(List<SyncActivityItem> list, final StringBuffer cancel, SyncMode mode, String synchronizerName) {
        String msg = getResources().getString(mode.getTextId(), synchronizerName);
        mSpinner.setTitle(msg);
        mSpinner.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel",
                new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        synchronized (cancel) {
                            cancel.append('t');
                        }
                        mSpinner.setMessage(getResources().getString(R.string.Cancellingplease_wait));
                    }
                });
        mSpinner.setCancelable(false);
        mSpinner.setCanceledOnTouchOutside(false);
        mSpinner.setMax(list.size());
    }

    private boolean checkCancel(StringBuffer cancel) {
        if (cancel != null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (cancel) {
                return cancel.length() > 0;
            }
        }
        return false;
    }

    private void syncNextActivity(final Synchronizer synchronizer, SyncMode mode) {
        if (checkCancel(cancelSync)) {
            mSpinner.cancel();
            syncActivityCallback.run(synchronizer.getName(), Synchronizer.Status.CANCEL);
            return;
        }

        if (syncActivitiesList.size() == 0) {
            mSpinner.cancel();
            syncActivityCallback.run(synchronizer.getName(), Synchronizer.Status.OK);
            return;
        }

        mSpinner.setProgress(syncActivitiesList.size());
        SyncActivityItem ai = syncActivitiesList.get(0);
        syncActivitiesList.remove(0);
        doSyncMulti(synchronizer, mode, ai);
    }

    private void doSyncMulti(final Synchronizer synchronizer, final SyncMode mode, final SyncActivityItem activityItem) {
        final ProgressDialog copySpinner = mSpinner;
        final SQLiteDatabase copyDB = DBHelper.getWritableDatabase(mContext);

        copySpinner.setMessage(Long.toString(1 + syncActivitiesList.size()) + " remaining");
        new AsyncTask<Synchronizer, String, Status>() {

            @Override
            protected Synchronizer.Status doInBackground(Synchronizer... params) {
                try {
                    switch (mode) {
                        case UPLOAD:
                            return synchronizer.upload(copyDB, activityItem.getId());
                        case DOWNLOAD:
                            return synchronizer.download(copyDB, activityItem);
                    }
                    return Synchronizer.Status.ERROR;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return Synchronizer.Status.ERROR;
                }
            }

            @Override
            protected void onPostExecute(Synchronizer.Status result) {
                switch (result) {
                    case CANCEL:
                    case ERROR:
                    case INCORRECT_USAGE:
                    case SKIP:
                        break;
                    case OK:
                        syncOK(synchronizer, copySpinner, copyDB, result.activityId);
                        break;
                    case NEED_AUTH:
                        handleAuth(new Callback() {
                            @Override
                            public void run(String synchronizerName,
                                    Synchronizer.Status status) {
                                switch (status) {
                                    case CANCEL:
                                    case SKIP:
                                    case ERROR:
                                    case INCORRECT_USAGE:
                                    case NEED_AUTH: // should be handled inside
                                                    // connect "loop"
                                        syncActivitiesList.clear();
                                        syncNextActivity(synchronizer, mode);
                                        break;
                                    case OK:
                                        doSyncMulti(synchronizer, mode, activityItem);
                                        break;
                                }
                            }
                        }, synchronizer, result.authMethod);

                        break;
                }
                syncNextActivity(synchronizer, mode);
            }
        }.execute(synchronizer);
    }

    private Callback feedCallback = null;
    private Set<String> feedProviders = null;
    private FeedList feedList = null;
    private StringBuffer feedCancel = null;

    public void synchronizeFeed(Callback cb, Set<String> providers, FeedList dst, StringBuffer cancel) {
        feedCallback = cb;
        feedProviders = providers;
        feedList = dst;
        feedCancel = cancel;
        nextSyncFeed();
    }

    private void nextSyncFeed() {
        if (checkCancel(feedCancel)) {
            feedProviders.clear();
        }

        if (feedProviders.isEmpty()) {
            //update feed widgets, if any
            Log.i(getClass().getSimpleName(), "Feed sync ended");
            FeedWidgetProvider.RefreshWidget(getContext());

            if (feedCallback != null) {
                Callback cb = feedCallback;
                feedCallback = null;
                cb.run(null, Synchronizer.Status.OK);
            }
            return;
        }

        final String providerName = feedProviders.iterator().next();
        feedProviders.remove(providerName);
        final Synchronizer synchronizer = synchronizers.get(providerName);
        syncFeed(synchronizer);
    }

    private void syncFeed(final Synchronizer synchronizer) {
        if (synchronizer == null) {
            nextSyncFeed();
            return;
        }

        final FeedList.FeedUpdater feedUpdater = feedList.getUpdater();
        feedUpdater.start(synchronizer.getName());
        new AsyncTask<Synchronizer, String, Synchronizer.Status>() {

            @Override
            protected Synchronizer.Status doInBackground(Synchronizer... params) {
                try {
                    return params[0].getFeed(feedUpdater);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return Synchronizer.Status.ERROR;
                }
            }

            @Override
            protected void onPostExecute(Synchronizer.Status result) {
                if (result == Synchronizer.Status.OK) {
                    feedUpdater.complete();
                } else if (result == Synchronizer.Status.NEED_AUTH) {
                    handleAuth(new Callback() {
                        @Override
                        public void run(String synchronizerName, Synchronizer.Status s2) {
                            if (s2 == Synchronizer.Status.OK) {
                                syncFeed(synchronizer);
                            } else {
                                nextSyncFeed();
                            }
                        }
                    }, synchronizer, result.authMethod);
                    return;
                } else {
                    if (result.ex != null)
                        result.ex.printStackTrace();
                }
                nextSyncFeed();
            }
        }.execute(synchronizer);
    }

    public void loadLiveLoggers(List<WorkoutObserver> liveLoggers) {
        liveLoggers.clear();
        Resources res = getResources();
        String key = res.getString(R.string.pref_runneruplive_active);
        if (!PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(key, false)) {
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
                    Synchronizer u = add(config);
                    if (u.isConfigured() && u.checkSupport(Synchronizer.Feature.LIVE) &&
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

    public Set<String> feedSynchronizersSet(Context ctx) {
        Set<String> set = new HashSet<>();
        String[] from = new String[] {
                "_id",
                DB.ACCOUNT.NAME,
                DB.ACCOUNT.ENABLED,
                DB.ACCOUNT.AUTH_CONFIG,
                DB.ACCOUNT.FLAGS
        };

        SQLiteDatabase db = DBHelper.getReadableDatabase(ctx);
        Cursor c = db.query(DB.ACCOUNT.TABLE, from, null, null, null, null, null);
        if (c.moveToFirst()) {
            do {
                final ContentValues tmp = DBHelper.get(c);
                final Synchronizer synchronizer = add(tmp);
                @SuppressWarnings("ConstantConditions") final String name = tmp.getAsString(DB.ACCOUNT.NAME);
                final long flags = tmp.getAsLong(DB.ACCOUNT.FLAGS);
                if (isConfigured(name) &&
                        Bitfield.test(flags, DB.ACCOUNT.FLAG_FEED) &&
                        synchronizer.checkSupport(Synchronizer.Feature.FEED)) {
                    set.add(name);
                }
            } while (c.moveToNext());
        }
        c.close();
        DBHelper.closeDB(db);
        return set;
    }
}
