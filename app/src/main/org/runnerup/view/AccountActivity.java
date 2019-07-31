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

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.export.FileSynchronizer;
import org.runnerup.export.RunnerUpLiveSynchronizer;
import org.runnerup.export.SyncManager;
import org.runnerup.export.Synchronizer;
import org.runnerup.export.Synchronizer.Status;
import org.runnerup.util.Bitfield;
import org.runnerup.widget.WidgetUtil;

import java.util.ArrayList;


public class AccountActivity extends AppCompatActivity implements Constants {
    private String mSynchronizerName = null;
    private SQLiteDatabase mDB = null;
    private final ArrayList<Cursor> mCursors = new ArrayList<>();

    private long flags;
    private SyncManager syncManager = null;
    private EditText mRunnerUpLiveApiAddress = null;

    /** Called when the activity is first created. */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account);

        WidgetUtil.addLegacyOverflowButton(getWindow());

        Intent intent = getIntent();
        mSynchronizerName = intent.getStringExtra("synchronizer");

        mDB = DBHelper.getReadableDatabase(this);
        syncManager = new SyncManager(this);
        fillData();

        Synchronizer synchronizer = syncManager.getSynchronizerByName(mSynchronizerName);
        if (synchronizer == null) {
            return;
        }

        {
            Button btn = (Button) findViewById(R.id.ok_account_button);
            btn.setOnClickListener(okButtonClick);
        }

        {
            Button btn = (Button) findViewById(R.id.account_upload_button);
            btn.setOnClickListener(uploadButtonClick);
        }

        {
            Button btn = (Button) findViewById(R.id.account_download_button);
            if (synchronizer.checkSupport(Synchronizer.Feature.ACTIVITY_LIST) &&
                synchronizer.checkSupport(Synchronizer.Feature.GET_ACTIVITY)) {
                btn.setOnClickListener(downloadButtonClick);
            } else {
                btn.setVisibility(View.GONE);
            }
        }

        {
            Button btn = (Button) findViewById(R.id.disconnect_account_button);
            btn.setOnClickListener(disconnectButtonClick);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (Cursor c : mCursors) {
            c.close();
        }
        DBHelper.closeDB(mDB);
        mCursors.clear();
        syncManager.close();
    }

    private void fillData() {
        // Fields from the database (projection)
        // Must include the _id column for the adapter to work
        String[] from = new String[]{
                "_id", DB.ACCOUNT.NAME, DB.ACCOUNT.FLAGS, DB.ACCOUNT.AUTH_CONFIG
        };

        String args[] = {
                mSynchronizerName
        };
        Cursor c = mDB.query(DB.ACCOUNT.TABLE, from, DB.ACCOUNT.NAME + " = ?", args,
                null, null, null);

        if (c.moveToFirst()) {
            Synchronizer synchronizer;
            {
                ContentValues tmp = DBHelper.get(c);
                synchronizer = syncManager.add(tmp);
                flags = tmp.getAsLong(DB.ACCOUNT.FLAGS);
                if (synchronizer == null) {
                    return;
                }
            }

            {
                ImageView im = (ImageView) findViewById(R.id.account_list_icon);
                TextView tv = (TextView) findViewById(R.id.account_list_name);
                if (synchronizer.getIconId() == 0 || mSynchronizerName.equals(FileSynchronizer.NAME)) {
                    if (!TextUtils.isEmpty(synchronizer.getPublicUrl())) {
                        tv.setText(synchronizer.getPublicUrl());
                        tv.setTag(synchronizer.getPublicUrl());
                        // FileSynchronizer: SDK 24 requires the file URI to be handled as FileProvider
                        // Something like OI File Manager is needed too
                        if (Build.VERSION.SDK_INT < 24 || !synchronizer.getName().equals(FileSynchronizer.NAME)) {
                            tv.setOnClickListener(urlButtonClick);
                        }
                    }
                    else {
                        tv.setText(synchronizer.getName());
                    }
                    im.setVisibility(View.GONE);
                    tv.setVisibility(View.VISIBLE);
                } else {
                    im.setImageDrawable(ContextCompat.getDrawable(this, synchronizer.getIconId()));
                    if (!TextUtils.isEmpty(synchronizer.getPublicUrl())) {
                        im.setTag(synchronizer.getPublicUrl());
                        im.setOnClickListener(urlButtonClick);
                    }
                    im.setVisibility(View.VISIBLE);
                    tv.setVisibility(View.GONE);
                }
            }

            if (synchronizer.getName().equals(RunnerUpLiveSynchronizer.NAME)) {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
                final Resources res = this.getResources();
                final String POST_URL = "http://weide.devsparkles.se/api/Resource/";
                String postUrl = prefs.getString(res.getString(R.string.pref_runneruplive_serveradress), POST_URL);

                mRunnerUpLiveApiAddress = new EditText(this.getApplicationContext());
                mRunnerUpLiveApiAddress.setSingleLine();
                mRunnerUpLiveApiAddress.setText(postUrl, TextView.BufferType.EDITABLE);
                addRow(getResources().getString(R.string.RunnerUp_live_address) + ":", mRunnerUpLiveApiAddress);
            }

            if (synchronizer.checkSupport(Synchronizer.Feature.UPLOAD)) {
                CheckBox cb = new CheckBox(this);
                cb.setTag(DB.ACCOUNT.FLAG_UPLOAD);
                cb.setChecked(Bitfield.test(flags, DB.ACCOUNT.FLAG_UPLOAD));
                cb.setOnCheckedChangeListener(sendCBChecked);
                addRow(getResources().getString(R.string.Automatic_upload), cb);
            } else {
                Button btn = (Button) findViewById(R.id.account_upload_button);
                btn.setVisibility(View.GONE);
            }

            if (synchronizer.checkSupport(Synchronizer.Feature.FEED)) {
                CheckBox cb = new CheckBox(this);
                cb.setTag(DB.ACCOUNT.FLAG_FEED);
                cb.setChecked(Bitfield.test(flags, DB.ACCOUNT.FLAG_FEED));
                cb.setOnCheckedChangeListener(sendCBChecked);
                addRow("Feed", cb);
            }

            if (synchronizer.checkSupport(Synchronizer.Feature.LIVE)) {
                CheckBox cb = new CheckBox(this);
                cb.setTag(DB.ACCOUNT.FLAG_LIVE);
                cb.setChecked(Bitfield.test(flags, DB.ACCOUNT.FLAG_LIVE));
                cb.setOnCheckedChangeListener(sendCBChecked);
                addRow(getResources().getString(R.string.Live), cb);
            }

            if (synchronizer.checkSupport(Synchronizer.Feature.SKIP_MAP)) {
                CheckBox cb = new CheckBox(this);
                cb.setTag(DB.ACCOUNT.FLAG_SKIP_MAP);
                cb.setChecked(!Bitfield.test(flags, DB.ACCOUNT.FLAG_SKIP_MAP));
                cb.setOnCheckedChangeListener(sendCBChecked);
                addRow(getResources().getString(R.string.Include_map_in_post), cb);
            }
        }
        mCursors.add(c);
    }

    private void addRow(String string, View btn) {
        TableLayout table = (TableLayout) findViewById(R.id.account_table);
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
            case android.R.id.home:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private final OnClickListener clearUploadsButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    AccountActivity.this)
                    .setTitle(getString(R.string.Clear_uploads))
                    .setMessage(getResources().getString(R.string.Clear_uploads_from_phone))
                    .setPositiveButton(getString(R.string.OK),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            syncManager.clearUploadsByName(callback, mSynchronizerName);
                        }
                    })
                    .setNegativeButton(getString(R.string.Cancel),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Do nothing but close the dialog
                            dialog.dismiss();
                        }

                    });
            builder.show();
        }
    };

    private final OnClickListener uploadButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final Intent intent = new Intent(AccountActivity.this, UploadActivity.class);
            intent.putExtra("synchronizer", mSynchronizerName);
            intent.putExtra("mode", SyncManager.SyncMode.UPLOAD.name());
            AccountActivity.this.startActivityForResult(intent, 113);
        }
    };

    private final OnClickListener downloadButtonClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            final Intent intent = new Intent(AccountActivity.this, UploadActivity.class);
            intent.putExtra("synchronizer", mSynchronizerName);
            intent.putExtra("mode", SyncManager.SyncMode.DOWNLOAD.name());
            AccountActivity.this.startActivityForResult(intent, 113);
        }
    };

    private final OnClickListener urlButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final Intent intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse((String) v
                    .getTag()));
            startActivity(intent);
        }
    };

    private final OnCheckedChangeListener sendCBChecked = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            ContentValues tmp = new ContentValues();
            int flag = (Integer) buttonView.getTag();
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
            String args[] = {
                    mSynchronizerName
            };
            mDB.update(DB.ACCOUNT.TABLE, tmp, "name = ?", args);
        }
    };

    private final OnClickListener okButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mRunnerUpLiveApiAddress != null) {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                final Resources res = getResources();

                prefs.edit().putString(res.getString(R.string.pref_runneruplive_serveradress),
                        mRunnerUpLiveApiAddress.getText().toString()).apply();
                mRunnerUpLiveApiAddress = null;
            }
            finish();
        }
    };

    private final OnClickListener disconnectButtonClick = new OnClickListener() {
        public void onClick(View v) {
            final CharSequence items[] = {
                getString(R.string.Clear_uploads_from_phone)
            };
            final boolean selected[] = {
                true
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    AccountActivity.this)
                    .setTitle(getString(R.string.Disconnect_account))
                    .setPositiveButton(getString(R.string.OK),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            syncManager.disableSynchronizer(disconnectCallback, mSynchronizerName,
                                    selected[0]);
                        }
                    })
                    .setNegativeButton(getString(R.string.Cancel),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Do nothing but close the dialog
                            dialog.dismiss();
                        }

                    })
                    .setMultiChoiceItems(items, selected,
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

    private final SyncManager.Callback callback = new SyncManager.Callback() {
        @Override
        public void run(String synchronizerName, Status status) {
        }
    };

    private final SyncManager.Callback disconnectCallback = new SyncManager.Callback() {
        @Override
        public void run(String synchronizerName, Status status) {
            finish();
        }
    };

}
