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

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.export.SyncManager;
import org.runnerup.export.Synchronizer;
import org.runnerup.export.Synchronizer.Status;
import org.runnerup.util.Bitfield;
import org.runnerup.common.util.Constants;
import org.runnerup.util.SimpleCursorLoader;
import org.runnerup.widget.WidgetUtil;

@TargetApi(Build.VERSION_CODES.FROYO)
public class AccountListActivity extends FragmentActivity implements Constants,
        LoaderCallbacks<Cursor> {

    DBHelper mDBHelper = null;
    SQLiteDatabase mDB = null;
    SyncManager mSyncManager = null;
    boolean mTabFormat = false;
    ListView mListView;
    CursorAdapter mCursorAdapter;

    /** Called when the activity is first created. */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_list);
        WidgetUtil.addLegacyOverflowButton(getWindow());

        mDBHelper = new DBHelper(this);
        mDB = mDBHelper.getReadableDatabase();
        mSyncManager = new SyncManager(this);
        mListView = (ListView) findViewById(R.id.account_list);
        mListView.setDividerHeight(10);
        mCursorAdapter = new AccountListAdapter(this, null);
        mListView.setAdapter(mCursorAdapter);
        getSupportLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDB.close();
        mDBHelper.close();
        mSyncManager.close();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.account_list_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_tab_format:
                mTabFormat = !mTabFormat;
                item.setTitle(getString(R.string.Icon_list));
                getSupportLoaderManager().restartLoader(0, null, this);
                break;
        }
        return true;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        String[] from = new String[] {
                "_id",
                DB.ACCOUNT.NAME,
                DB.ACCOUNT.URL,
                DB.ACCOUNT.DESCRIPTION,
                DB.ACCOUNT.ENABLED,
                DB.ACCOUNT.ICON,
                DB.ACCOUNT.AUTH_CONFIG,
                DB.ACCOUNT.FLAGS
        };

        return new SimpleCursorLoader(this, mDB, DB.ACCOUNT.TABLE, from, null, null,
                DB.ACCOUNT.ENABLED + " desc, " + DB.ACCOUNT.AUTH_CONFIG + " is null, " + DB.ACCOUNT.NAME);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> arg0, Cursor arg1) {
        mCursorAdapter.swapCursor(arg1);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
        mCursorAdapter.swapCursor(null);
    }

    class AccountListAdapter extends CursorAdapter {
        final LayoutInflater inflater;

        public AccountListAdapter(Context context, Cursor c) {
            super(context, c, true);
            inflater = LayoutInflater.from(context);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ContentValues tmp = DBHelper.get(cursor);

            final String id = tmp.getAsString(DB.ACCOUNT.NAME);
            final Synchronizer synchronizer = mSyncManager.add(tmp);
            final long flags = tmp.getAsLong(DB.ACCOUNT.FLAGS);

            ImageView accountIcon = (ImageView) view.findViewById(R.id.account_list_icon);
            TextView accountNameText = (TextView) view.findViewById(R.id.account_list_name);
            CheckBox accountUploadBox = (CheckBox) view.findViewById(R.id.account_list_upload);
            CheckBox accountFeedBox = (CheckBox) view.findViewById(R.id.account_list_feed);
            Button accountConfigureBtn = (Button) view.findViewById(R.id.account_list_configure_button);

            // upload box
            accountUploadBox.setTag(id);
            accountUploadBox.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
                    setFlag(arg0.getTag(), DB.ACCOUNT.FLAG_UPLOAD, arg1);
                }
            });

            // feed box
            accountFeedBox.setTag(id);
            accountFeedBox.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
                    setFlag(arg0.getTag(), DB.ACCOUNT.FLAG_FEED, arg1);
                }

            });

            boolean configured = mSyncManager.isConfigured(id);
            if (!mTabFormat) {
                if (cursor.isNull(cursor.getColumnIndex(DB.ACCOUNT.ICON))) {
                    accountIcon.setVisibility(View.GONE);
                    accountNameText.setVisibility(View.VISIBLE);
                    accountNameText.setText(tmp.getAsString(DB.ACCOUNT.NAME));
                } else {
                    accountIcon.setVisibility(View.VISIBLE);
                    accountNameText.setVisibility(View.GONE);
                    accountIcon.setBackgroundResource(tmp
                            .getAsInteger(DB.ACCOUNT.ICON));
                }
                accountUploadBox.setVisibility(View.GONE);
                accountFeedBox.setVisibility(View.GONE);
            } else {
                accountIcon.setVisibility(View.GONE);
                accountNameText.setVisibility(View.VISIBLE);
                accountNameText.setText(id);
                if (configured && synchronizer.checkSupport(Synchronizer.Feature.UPLOAD)) {
                    accountUploadBox.setEnabled(true);
                    accountUploadBox.setChecked(Bitfield.test(flags, DB.ACCOUNT.FLAG_UPLOAD));
                    accountUploadBox.setVisibility(View.VISIBLE);
                } else {
                    accountUploadBox.setVisibility(View.INVISIBLE);
                }
                if (configured && synchronizer.checkSupport(Synchronizer.Feature.FEED)) {
                    accountFeedBox.setEnabled(true);
                    accountFeedBox.setChecked(Bitfield.test(flags, DB.ACCOUNT.FLAG_FEED));
                    accountFeedBox.setVisibility(View.VISIBLE);
                } else {
                    accountFeedBox.setVisibility(View.INVISIBLE);
                }
            }

            //configure button
            {
                accountConfigureBtn.setTag(id);
                accountConfigureBtn.setOnClickListener(configureButtonClick);
                if (configured) {
                    accountConfigureBtn.setText(getString(R.string.edit));
                    WidgetUtil.setBackground(accountConfigureBtn, getResources().getDrawable(
                            R.drawable.btn_blue));
                } else {
                    accountConfigureBtn.setText(getString(R.string.Connect));
                    WidgetUtil.setBackground(accountConfigureBtn, getResources().getDrawable(
                            R.drawable.btn_green));
                }
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return inflater.inflate(R.layout.account_row, parent, false);
        }
    }

    final OnClickListener configureButtonClick = new OnClickListener() {
        public void onClick(View v) {
            final String synchronizerName = (String) v.getTag();
            if (mSyncManager.isConfigured(synchronizerName)) {
                startActivity(synchronizerName, true);
            } else {
                mSyncManager.connect(callback, synchronizerName, false);
            }
        }
    };

    private void setFlag(Object obj, int flag, boolean val) {
        String name = (String) obj;
        if (val) {
            long bitval = (1 << flag);
            mDB.execSQL("update " + DB.ACCOUNT.TABLE + " set " + DB.ACCOUNT.FLAGS + " = ( " +
                    DB.ACCOUNT.FLAGS + "|" + bitval + ") where " + DB.ACCOUNT.NAME + " = \'" + name
                    + "\'");
        } else {
            long mask = ~(long) (1 << flag);
            mDB.execSQL("update " + DB.ACCOUNT.TABLE + " set " + DB.ACCOUNT.FLAGS + " = ( " +
                    DB.ACCOUNT.FLAGS + "&" + mask + ") where " + DB.ACCOUNT.NAME + " = \'" + name
                    + "\'");
        }
    }

    final SyncManager.Callback callback = new SyncManager.Callback() {
        @Override
        public void run(String synchronizerName, Status status) {
            if (status == Synchronizer.Status.OK) {
                startActivity(synchronizerName, false);
            }
        }
    };

    void startActivity(String synchronizer, boolean edit) {
        Intent intent = new Intent(AccountListActivity.this, AccountActivity.class);
        intent.putExtra("synchronizer", synchronizer);
        intent.putExtra("edit", edit);
        AccountListActivity.this.startActivityForResult(intent,
                SyncManager.CONFIGURE_REQUEST + 1000);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SyncManager.CONFIGURE_REQUEST) {
            mSyncManager.onActivityResult(requestCode, resultCode, data);
        } else if (requestCode == SyncManager.CONFIGURE_REQUEST + 1000) {
            mSyncManager.clear();
            getSupportLoaderManager().restartLoader(0, null, this);
        }
    }
}
