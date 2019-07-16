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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
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


public class AccountListActivity extends AppCompatActivity implements Constants,
        LoaderCallbacks<Cursor> {

    private SQLiteDatabase mDB = null;
    private SyncManager mSyncManager = null;
    private boolean mShowDisabled = false;
    private CursorAdapter mCursorAdapter;

    /**
     * Called when the activity is first created.
     */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        setContentView(R.layout.account_list);

        mDB = DBHelper.getReadableDatabase(this);
        mSyncManager = new SyncManager(this);
        ListView listView = (ListView) findViewById(R.id.account_list);

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
                getSupportLoaderManager().restartLoader(0, null, AccountListActivity.this);
            }
        });
        listView.addFooterView(showDisabledBtn);

        // adapter
        mCursorAdapter = new AccountListAdapter(this, null);
        listView.setAdapter(mCursorAdapter);
        getSupportLoaderManager().initLoader(0, null, this);

        listView.setOnItemClickListener(configureItemClick);

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
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        String[] from = new String[]{
                "_id", DB.ACCOUNT.NAME, DB.ACCOUNT.AUTH_CONFIG, DB.ACCOUNT.FLAGS
        };
        String showDisabled = null;
        if (!mShowDisabled) {
            showDisabled = DB.ACCOUNT.ENABLED + "==1 or " + DB.ACCOUNT.AUTH_CONFIG + " is not null";
        }

        return new SimpleCursorLoader(this, mDB, DB.ACCOUNT.TABLE, from, showDisabled, null,
                DB.ACCOUNT.AUTH_CONFIG + " is null, " + DB.ACCOUNT.NAME + " collate nocase," + DB.ACCOUNT.ENABLED + " desc ");
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> arg0, Cursor arg1) {
        mCursorAdapter.swapCursor(arg1);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> arg0) {
        mCursorAdapter.swapCursor(null);
    }

    class AccountListAdapter extends CursorAdapter {
        final LayoutInflater inflater;

        public AccountListAdapter(Context context, Cursor cursor) {
            super(context, cursor, true);
            inflater = LayoutInflater.from(context);
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(cursor);
        }

        @Override
        public Cursor swapCursor(Cursor newCursor) {
            return super.swapCursor(newCursor);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ContentValues values = DBHelper.get(cursor);

            final Synchronizer synchronizer = mSyncManager.add(values);
            final long flags = values.getAsLong(DB.ACCOUNT.FLAGS);
            final String name = values.getAsString(DB.ACCOUNT.NAME);
            boolean configured = synchronizer != null && synchronizer.isConfigured();

            view.setTag(synchronizer);

            TextView sectionTitle = (TextView)view.findViewById(R.id.section_title);
            ImageView accountIcon = (ImageView)view.findViewById(R.id.account_list_icon);
            TextView accountIconText = (TextView)view.findViewById(R.id.account_list_icon_text);
            TextView accountNameText = (TextView)view.findViewById(R.id.account_list_name);
            SwitchCompat accountUploadBox = (SwitchCompat)view.findViewById(R.id.account_list_upload);
            SwitchCompat accountFeedBox = (SwitchCompat)view.findViewById(R.id.account_list_feed);

            // category name
            int curPosition = cursor.getPosition();
            boolean prevConfigured = false;
            if (curPosition > 0) {
                // get data for previous item
                cursor.moveToPrevious();
                ContentValues values2 = DBHelper.get(cursor);

                final Synchronizer synchronizer2 = mSyncManager.add(values2);
                prevConfigured = synchronizer2 != null && synchronizer2.isConfigured();
                cursor.moveToNext();
            }

            if (curPosition > 0 && configured == prevConfigured) {
                sectionTitle.setVisibility(View.GONE);
            } else {
                int str = configured ?
                        R.string.accounts_category_connected :
                        R.string.accounts_category_unconnected;
                sectionTitle.setText(getString(str));
                sectionTitle.setVisibility(View.VISIBLE);
            }

            if (synchronizer == null) {
                accountUploadBox.setVisibility(View.GONE);
                accountFeedBox.setVisibility(View.GONE);
                accountIcon.setVisibility(View.GONE);
                accountIconText.setVisibility(View.GONE);
                accountNameText.setText(name);
                return;
            }

            // service icon
            int synchronizerIcon = synchronizer.getIconId();
            if (synchronizerIcon == 0) {
                Drawable circle = ContextCompat.getDrawable(context, R.drawable.circle_40dp);
                circle.setColorFilter(getResources().getColor(synchronizer.getColorId()), PorterDuff.Mode.SRC_IN);
                accountIcon.setImageDrawable(circle);
                accountIconText.setText(name.substring(0, 1));
            } else {
                accountIcon.setImageDrawable(ContextCompat.getDrawable(context, synchronizerIcon));
                accountIconText.setText(null);
            }
            
            // service title
            accountNameText.setText(name);

            // upload box
            accountUploadBox.setTag(synchronizer);
            setCustomThumb(accountUploadBox, R.drawable.switch_upload, context);
            accountUploadBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
                    setFlag(((Synchronizer) arg0.getTag()).getName(), DB.ACCOUNT.FLAG_UPLOAD, arg1);
                }
            });

            // feed box
            accountFeedBox.setTag(synchronizer);
            setCustomThumb(accountFeedBox, R.drawable.switch_feed, context);
            accountFeedBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
                    setFlag(((Synchronizer) arg0.getTag()).getName(), DB.ACCOUNT.FLAG_FEED, arg1);
                }
            });

            if (configured && synchronizer.checkSupport(Synchronizer.Feature.UPLOAD)) {
                accountUploadBox.setEnabled(true);
                accountUploadBox.setChecked(Bitfield.test(flags, DB.ACCOUNT.FLAG_UPLOAD));
                accountUploadBox.setVisibility(View.VISIBLE);
            } else {
                accountUploadBox.setVisibility(View.GONE);
            }
            if (configured && synchronizer.checkSupport(Synchronizer.Feature.FEED)) {
                accountFeedBox.setEnabled(true);
                accountFeedBox.setChecked(Bitfield.test(flags, DB.ACCOUNT.FLAG_FEED));
                accountFeedBox.setVisibility(View.VISIBLE);
            } else {
                accountFeedBox.setVisibility(View.GONE);
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return inflater.inflate(R.layout.account_row, parent, false);
        }
    }

    private void setCustomThumb(SwitchCompat switchCompat, int drawableId, Context context) {
        switchCompat.setThumbDrawable(ContextCompat.getDrawable(context, drawableId));
        switchCompat.setThumbTintList(ContextCompat.getColorStateList(context, R.color.switch_thumb));
        switchCompat.setThumbTintMode(PorterDuff.Mode.MULTIPLY);
    }

    private final AdapterView.OnItemClickListener configureItemClick = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            final Synchronizer synchronizer = ((Synchronizer)view.getTag());
            if (synchronizer == null) {
                return;
            }
            if (synchronizer.isConfigured()) {
                startActivity(synchronizer.getName(), true);
            } else {
                mSyncManager.connect(callback, synchronizer.getName());
            }
        }
    };

    private void setFlag(String synchronizerName, int flag, boolean val) {
        if (val) {
            long bitval = (1 << flag);
            mDB.execSQL("update " + DB.ACCOUNT.TABLE + " set " + DB.ACCOUNT.FLAGS + " = ( " +
                    DB.ACCOUNT.FLAGS + "|" + bitval + ") where " + DB.ACCOUNT.NAME + " = \'" + synchronizerName
                    + "\'");
        } else {
            long mask = ~(long) (1 << flag);
            mDB.execSQL("update " + DB.ACCOUNT.TABLE + " set " + DB.ACCOUNT.FLAGS + " = ( " +
                    DB.ACCOUNT.FLAGS + "&" + mask + ") where " + DB.ACCOUNT.NAME + " = \'" + synchronizerName
                    + "\'");
        }
    }

    private final SyncManager.Callback callback = new SyncManager.Callback() {
        @Override
        public void run(String synchronizerName, Status status) {
            if (status == Synchronizer.Status.OK) {
                startActivity(synchronizerName, false);
            }
        }
    };

    private void startActivity(String synchronizerName, boolean edit) {
        Intent intent = new Intent(AccountListActivity.this, AccountActivity.class);
        intent.putExtra("synchronizer", synchronizerName);
        //intent.putExtra("edit", edit);
        AccountListActivity.this.startActivityForResult(intent,
                SyncManager.EDIT_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SyncManager.CONFIGURE_REQUEST) {
            mSyncManager.onActivityResult(requestCode, resultCode, data);
            this.mCursorAdapter.notifyDataSetChanged();
        } else if (requestCode == SyncManager.EDIT_REQUEST) {
            mSyncManager.clear();
            getSupportLoaderManager().restartLoader(0, null, this);
        }
    }
}
