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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
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

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.export.SyncManager;
import org.runnerup.export.Synchronizer;
import org.runnerup.export.Synchronizer.Status;
import org.runnerup.util.Bitfield;
import org.runnerup.widget.WidgetUtil;

import java.util.ArrayList;

@TargetApi(Build.VERSION_CODES.FROYO)
public class AccountActivity extends Activity implements Constants {

    long synchronizerID = -1;
    String synchronizer = null;
    Integer synchronizerIcon = null;

    DBHelper mDBHelper = null;
    SQLiteDatabase mDB = null;
    final ArrayList<Cursor> mCursors = new ArrayList<Cursor>();

    long flags;
    SyncManager syncManager = null;

    /** Called when the activity is first created. */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account);
        WidgetUtil.addLegacyOverflowButton(getWindow());

        Intent intent = getIntent();
        synchronizer = intent.getStringExtra("synchronizer");


        mDBHelper = new DBHelper(this);
        mDB = mDBHelper.getReadableDatabase();
        syncManager = new SyncManager(this);
        fillData();

        Synchronizer upd = syncManager.getSynchronizerByName(synchronizer);

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
            if (upd.checkSupport(Synchronizer.Feature.ACTIVITY_LIST) && upd.checkSupport(Synchronizer.Feature.GET_ACTIVITY)) {
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
        mDB.close();
        mDBHelper.close();
        for (Cursor c : mCursors) {
            c.close();
        }
        mCursors.clear();
        syncManager.close();
    }

    void fillData() {
        // Fields from the database (projection)
        // Must include the _id column for the adapter to work
        String[] from = new String[] {
                "_id",
                DB.ACCOUNT.NAME,
                DB.ACCOUNT.URL,
                DB.ACCOUNT.DESCRIPTION,
                DB.ACCOUNT.ENABLED,
                DB.ACCOUNT.FLAGS,
                DB.ACCOUNT.ICON,
                DB.ACCOUNT.AUTH_CONFIG
        };

        String args[] = {
                synchronizer
        };
        Cursor c = mDB.query(DB.ACCOUNT.TABLE, from, DB.ACCOUNT.NAME + " = ?", args,
                null, null, null);

        if (c.moveToFirst()) {
            ContentValues tmp = DBHelper.get(c);
            synchronizerID = tmp.getAsLong("_id");
            Synchronizer synchronizer = syncManager.add(tmp);

            {
                ImageView im = (ImageView) findViewById(R.id.account_list_icon);
                TextView tv = (TextView) findViewById(R.id.account_list_name);
                tv.setText(tmp.getAsString(DB.ACCOUNT.NAME));
                if (c.isNull(c.getColumnIndex(DB.ACCOUNT.ICON))) {
                    im.setVisibility(View.GONE);
                    tv.setVisibility(View.VISIBLE);
                } else {
                    im.setVisibility(View.VISIBLE);
                    tv.setVisibility(View.GONE);
                    im.setBackgroundResource(tmp.getAsInteger(DB.ACCOUNT.ICON));
                    synchronizerIcon = tmp.getAsInteger(DB.ACCOUNT.ICON);
                }
            }

            addRow("", null);

            if (tmp.containsKey(DB.ACCOUNT.URL)) {
                Button btn = new Button(this);
                btn.setText(tmp.getAsString(DB.ACCOUNT.URL));
                btn.setOnClickListener(urlButtonClick);
                btn.setTag(tmp.getAsString(DB.ACCOUNT.URL));
                addRow(getResources().getString(R.string.Website) + ":", btn);
            }

            flags = tmp.getAsLong(DB.ACCOUNT.FLAGS);
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

    void addRow(String string, View btn) {
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
        }
        return true;
    }

    final OnClickListener clearUploadsButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    AccountActivity.this);
            builder.setTitle(getString(R.string.Clear_uploads));
            builder.setMessage(getResources().getString(R.string.Clear_uploads_from_phone,
                    synchronizer));
            builder.setPositiveButton(getString(R.string.OK),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            syncManager.clearUploadsByName(callback, synchronizer);
                        }
                    });

            builder.setNegativeButton(getString(R.string.Cancel),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Do nothing but close the dialog
                            dialog.dismiss();
                        }

                    });
            builder.show();
        }
    };

    final OnClickListener uploadButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final Intent intent = new Intent(AccountActivity.this, UploadActivity.class);
            intent.putExtra("synchronizer", synchronizer);
            intent.putExtra("synchronizerID", synchronizerID);
            intent.putExtra("mode", SyncManager.SyncMode.UPLOAD.name());
            if (synchronizerIcon != null)
                intent.putExtra("synchronizerIcon", synchronizerIcon.intValue());
            AccountActivity.this.startActivityForResult(intent, 113);
        }
    };

    final OnClickListener downloadButtonClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            final Intent intent = new Intent(AccountActivity.this, UploadActivity.class);
            intent.putExtra("synchronizer", synchronizer);
            intent.putExtra("synchronizerID", synchronizerID);
            intent.putExtra("mode", SyncManager.SyncMode.DOWNLOAD.name());
            if (synchronizerIcon != null)
                intent.putExtra("synchronizerIcon", synchronizerIcon.intValue());
            AccountActivity.this.startActivityForResult(intent, 113);
        }
    };

    final OnClickListener urlButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final Intent intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse((String) v
                    .getTag()));
            startActivity(intent);
        }
    };

    final OnCheckedChangeListener sendCBChecked = new OnCheckedChangeListener() {
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
                    synchronizer
            };
            mDB.update(DB.ACCOUNT.TABLE, tmp, "name = ?", args);
        }
    };

    final OnClickListener okButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            finish();
        }
    };

    final OnClickListener disconnectButtonClick = new OnClickListener() {
        public void onClick(View v) {
            final CharSequence items[] = {
                getString(R.string.Clear_uploads_from_phone)
            };
            final boolean selected[] = {
                true
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    AccountActivity.this);
            builder.setTitle(getString(R.string.Disconnect_account));
            builder.setPositiveButton(getString(R.string.OK),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            syncManager.disableSynchronizer(disconnectCallback, synchronizer,
                                    selected[0]);
                        }
                    });
            builder.setNegativeButton(getString(R.string.Cancel),
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

    final SyncManager.Callback callback = new SyncManager.Callback() {
        @Override
        public void run(String synchronizerName, Status status) {
        }
    };

    final SyncManager.Callback disconnectCallback = new SyncManager.Callback() {
        @Override
        public void run(String synchronizerName, Status status) {
            finish();
        }
    };

}
