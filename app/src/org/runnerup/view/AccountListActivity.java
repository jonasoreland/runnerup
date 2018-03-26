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
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.export.SyncManager;
import org.runnerup.export.Synchronizer;
import org.runnerup.export.Synchronizer.Status;
import org.runnerup.util.Bitfield;
import org.runnerup.util.SimpleCursorLoader;

@TargetApi(Build.VERSION_CODES.FROYO)
public class AccountListActivity extends AppCompatActivity implements Constants,
        LoaderCallbacks<Cursor> {

    SQLiteDatabase mDB = null;
    SyncManager mSyncManager = null;
    private boolean mShowDisabled = false;
    ListView mListView;
    CursorAdapter mCursorAdapter;

    /**
     * Called when the activity is first created.
     */

    @Override
    public void onCreate(Bundle savedInstanceState) { //todo write up logo usage permissions
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_list);

        mDB = DBHelper.getReadableDatabase(this);
        mSyncManager = new SyncManager(this);
        mListView = (ListView) findViewById(R.id.account_list);

        // button footer
        Button showDisabledBtn = new Button(this);
        showDisabledBtn.setTextAppearance(this, R.style.TextAppearance_AppCompat_Button);
        showDisabledBtn.setText(R.string.Show_disabled_accounts);
        showDisabledBtn.setBackgroundResource(0);
        showDisabledBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mShowDisabled = !mShowDisabled;
                if (mShowDisabled) {
                    ((Button) view).setText(R.string.Hide_disabled_accounts);
                } else {
                    ((Button) view).setText(R.string.Show_disabled_accounts);
                }
                getSupportLoaderManager().restartLoader(0, null, (LoaderCallbacks<Object>) view.getContext());
            }
        });
        mListView.addFooterView(showDisabledBtn); //todo should look like a button on Froyo

        // adapter
        mCursorAdapter = new AccountListAdapter(this, null);
        mListView.setAdapter(mCursorAdapter);
        getSupportLoaderManager().initLoader(0, null, this);

        mListView.setOnItemClickListener(configureItemClick);
        mListView.setOnItemLongClickListener(itemLongClickListener);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        DBHelper.closeDB(mDB);
        mSyncManager.close();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                break;
        }
        return true;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        String[] from = new String[]{
                "_id", DB.ACCOUNT.NAME, DB.ACCOUNT.AUTH_CONFIG, DB.ACCOUNT.FLAGS
        };
        String showDisabled = null;
        if (!mShowDisabled) {
            showDisabled = DB.ACCOUNT.ENABLED + "==1";
        }

        return new SimpleCursorLoader(this, mDB, DB.ACCOUNT.TABLE, from, showDisabled, null,
                DB.ACCOUNT.AUTH_CONFIG + " is null, " + DB.ACCOUNT.ENABLED + " desc, " + DB.ACCOUNT.NAME);
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

            view.setTag(id);

            ImageView addOrEditIndicator = view.findViewById(R.id.add_or_edit_indicator);
            ImageView accountIcon = view.findViewById(R.id.account_list_icon);
            TextView accountNameText = view.findViewById(R.id.account_list_name);
            SwitchCompat accountUploadBox = view.findViewById(R.id.account_list_upload);
            SwitchCompat accountFeedBox = view.findViewById(R.id.account_list_feed);

            // upload box
            accountUploadBox.setTag(id);
            setCustomThumb(accountUploadBox, R.drawable.switch_upload, context);
            accountUploadBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
                    setFlag((String) arg0.getTag(), DB.ACCOUNT.FLAG_UPLOAD, arg1); //todo doesn't seem to work
                }
            });

            // feed box
            accountFeedBox.setTag(id);
            setCustomThumb(accountFeedBox, R.drawable.switch_feed, context);
            accountFeedBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
                    setFlag((String) arg0.getTag(), DB.ACCOUNT.FLAG_FEED, arg1);
                }
            });

            boolean configured = mSyncManager.isConfigured(id);
            int synchronizerIcon = synchronizer.getIconId();
            if (synchronizerIcon == 0) {
                accountIcon.setVisibility(View.GONE);
                accountNameText.setVisibility(View.VISIBLE);
                accountNameText.setText(id);
            } else {
                accountIcon.setVisibility(View.VISIBLE);
                accountNameText.setVisibility(View.GONE);
                accountIcon.setImageDrawable(ContextCompat.getDrawable(context, synchronizerIcon));
            }

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

            //edit indicator
            if (configured) {
                addOrEditIndicator.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_edit_white_24dp));
            } else {
                addOrEditIndicator.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_add_white_24dp));
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return inflater.inflate(R.layout.account_row, parent, false);
        }

        private void setCustomThumb(SwitchCompat switchCompat, int drawableId,  Context context) {
            switchCompat.setThumbDrawable(ContextCompat.getDrawable(context, drawableId));
            switchCompat.setThumbTintList(ContextCompat.getColorStateList(context, R.color.switch_thumb));
            switchCompat.setThumbTintMode(PorterDuff.Mode.MULTIPLY);
        }
    }

    final AdapterView.OnItemClickListener configureItemClick = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            final String synchronizerName = (String) view.getTag();
            if (mSyncManager.isConfigured(synchronizerName)) {
                startActivity(synchronizerName, true);
            } else {
                mSyncManager.connect(callback, synchronizerName, false);
            }
        }
    };

    final AdapterView.OnItemLongClickListener itemLongClickListener = new AdapterView.OnItemLongClickListener() {
        public boolean onItemLongClick(AdapterView<?> arg0, View v,
                                       int pos, long id) {
            ContentValues tmp = DBHelper.get((Cursor) arg0.getItemAtPosition(pos));
            final String synchronizerName = tmp.getAsString(DB.ACCOUNT.NAME);

            //Toggle value for ENABLED
            mDB.execSQL("update " + DB.ACCOUNT.TABLE + " set " + DB.ACCOUNT.ENABLED + " = 1 - " + DB.ACCOUNT.ENABLED +
                    " where " + DB.ACCOUNT.NAME + " = \'" + synchronizerName + "\'");
            getSupportLoaderManager().restartLoader(0, null, (AccountListActivity) v.getContext());

            return true;
        }
    };

    private void setFlag(String name, int flag, boolean val) {
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
