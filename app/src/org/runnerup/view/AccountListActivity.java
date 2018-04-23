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
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.app.NavUtils;
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
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

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
                DB.ACCOUNT.AUTH_CONFIG + " is null, " + DB.ACCOUNT.ENABLED + " desc, " + DB.ACCOUNT.NAME + " collate nocase"); //todo I'm guessing AUTH_CONFIG lets me know whether an account is set up
    }

    @Override
    public void onLoadFinished(Loader<Cursor> arg0, Cursor arg1) {
        mCursorAdapter.swapCursor(arg1); //todo why isn't the old cursor being closed???!!!
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
        mCursorAdapter.swapCursor(null);
    } //todo why isn't the old cursor being closed???!!!

    class AccountListAdapter extends CursorAdapter {
        final LayoutInflater inflater;
        int[] categories;
        static final int CATEGORY_UNSET = 0;
        static final int CATEGORY_SAME_AS_PREV = -1;

        public AccountListAdapter(Context context, Cursor cursor) {
            super(context, cursor, true);
            inflater = LayoutInflater.from(context);
            categories = (cursor == null) ? null : new int[cursor.getCount()];
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(cursor);
            categories = (cursor == null) ? null : new int[cursor.getCount()];
        }

        @Override
        public Cursor swapCursor(Cursor newCursor) {
            categories = (newCursor == null) ? null : new int[newCursor.getCount()];
            return super.swapCursor(newCursor);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ContentValues values = DBHelper.get(cursor);

            final String id = values.getAsString(DB.ACCOUNT.NAME);
            final Synchronizer synchronizer = mSyncManager.add(values);
            final long flags = values.getAsLong(DB.ACCOUNT.FLAGS);
            boolean configured = mSyncManager.isConfigured(id);

            view.setTag(id);

            TextView sectionTitle = view.findViewById(R.id.section_title);
            ImageView accountIcon = view.findViewById(R.id.account_list_icon);
            TextView accountIconText = view.findViewById(R.id.account_list_icon_text);
            TextView accountNameText = view.findViewById(R.id.account_list_name);
            SwitchCompat accountUploadBox = view.findViewById(R.id.account_list_upload);
            SwitchCompat accountFeedBox = view.findViewById(R.id.account_list_feed);

            // category name
            {
                int curPosition = cursor.getPosition();
                if (categories[curPosition] == CATEGORY_UNSET) {
                    String curConnected = values.getAsString(DB.ACCOUNT.AUTH_CONFIG);

                    if (curPosition == 0) {
                        if (curConnected == null) {
                            categories[curPosition] = R.string.accounts_category_unconnected;
                        } else {
                            categories[curPosition] = R.string.accounts_category_connected;
                        }
                    } else {
                        // get data for previous item
                        cursor.moveToPrevious();
                        String prevConnected = DBHelper.get(cursor).getAsString(DB.ACCOUNT.AUTH_CONFIG);
                        cursor.moveToNext();

                        // compare the two
                        if (prevConnected == null && curConnected != null
                                || ((prevConnected != null && !prevConnected.equals(curConnected)))) { // start of unconnected //todo test
                            categories[cursor.getPosition()] = R.string.accounts_category_unconnected;
                        } else { // same categories
                            categories[cursor.getPosition()] = CATEGORY_SAME_AS_PREV;
                        }
                    }
                }
            }

            if (categories[cursor.getPosition()] == CATEGORY_SAME_AS_PREV) {
                sectionTitle.setVisibility(View.GONE);
            } else {
                sectionTitle.setText(getString(categories[cursor.getPosition()]));
                sectionTitle.setVisibility(View.VISIBLE);
            }

            // service icon
            int synchronizerIcon = synchronizer.getIconId();
            if (synchronizerIcon == 0) {
                Drawable circle = ContextCompat.getDrawable(context, R.drawable.circle_40dp);
                circle.setColorFilter(getResources().getColor(synchronizer.getColorId()), PorterDuff.Mode.SRC_IN);
                accountIcon.setImageDrawable(circle);
                accountIconText.setText(id.substring(0,1));
            } else {
                accountIcon.setImageDrawable(ContextCompat.getDrawable(context, synchronizerIcon));
                accountIconText.setText(null);
            }

            // service title
            accountNameText.setText(id);

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

        private void setCustomThumb(SwitchCompat switchCompat, int drawableId, Context context) {
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
