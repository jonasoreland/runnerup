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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.export.FileSynchronizer;
import org.runnerup.export.SyncManager;
import org.runnerup.export.Synchronizer;
import org.runnerup.export.Synchronizer.Status;
import org.runnerup.util.Formatter;
import org.runnerup.util.SyncActivityItem;
import org.runnerup.workout.Sport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class UploadActivity extends AppCompatActivity implements Constants {

    private String mSynchronizerName = null;
    private SyncManager.SyncMode syncMode = SyncManager.SyncMode.UPLOAD;
    private SyncManager syncManager = null;
    private ListView listView = null;

    private SQLiteDatabase mDB = null;
    private Formatter formatter = null;
    private final List<SyncActivityItem> allSyncActivities = new ArrayList<>();

    private int syncCount = 0;
    private Button actionButton = null;
    private CharSequence actionButtonText = null;

    private boolean fetching = false;
    private final StringBuffer cancelSync = new StringBuffer();

    /** Called when the activity is first created. */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.upload);

        Intent intent = getIntent();
        mSynchronizerName = intent.getStringExtra("synchronizer");
        syncMode = SyncManager.SyncMode.valueOf(intent.getStringExtra("mode"));

        mDB = DBHelper.getReadableDatabase(this);
        formatter = new Formatter(this);
        syncManager = new SyncManager(this);

        listView = findViewById(R.id.upload_view);
        listView.setDividerHeight(1);
        listView.setAdapter(new UploadListAdapter(this));

        {
            Button btn = findViewById(R.id.upload_account_set_all);
            btn.setOnClickListener(setAllButtonClick);
        }

        {
            Button btn = findViewById(R.id.upload_account_clear_all);
            btn.setOnClickListener(clearAllButtonClick);
        }

        {
            Button dwbtn = findViewById(R.id.upload_account_download_button);
            Button upbtn = findViewById(R.id.upload_account_button);
            if (syncMode.equals(SyncManager.SyncMode.DOWNLOAD)) {
                dwbtn.setOnClickListener(downloadButtonClick);
                actionButton = dwbtn;
                actionButtonText = dwbtn.getText();
                upbtn.setVisibility(View.GONE);
            } else {
                upbtn.setOnClickListener(uploadButtonClick);
                actionButton = upbtn;
                actionButtonText = upbtn.getText();
                dwbtn.setVisibility(View.GONE);
            }
        }

        fillData();
        {
            // synchronizer initialized in fillData() for DOWNLOAD only
            Synchronizer synchronizer = syncManager.getSynchronizerByName(mSynchronizerName);

            TextView tv = findViewById(R.id.upload_account_list_name);
            ImageView im = findViewById(R.id.upload_account_list_icon);
            if (synchronizer == null || synchronizer.getIconId() == 0) {
                im.setVisibility(View.GONE);
                tv.setText(mSynchronizerName);
                tv.setVisibility(View.VISIBLE);
            } else {
                im.setVisibility(View.VISIBLE);
                tv.setVisibility(View.GONE);
                im.setImageDrawable(AppCompatResources.getDrawable(this, synchronizer.getIconId()));
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (fetching) {
            /*
             * Cancel
             */
            cancelSync.append("1");
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        DBHelper.closeDB(mDB);
        syncManager.close();
    }

    private void fillData() {
        if (syncMode.equals(SyncManager.SyncMode.DOWNLOAD)) {
            syncManager.load(mSynchronizerName);
            syncManager.loadActivityList(allSyncActivities, mSynchronizerName, (synchronizerName, status) -> {
                filterAlreadyPresentActivities();
                requery();
            });
        } else {
            // Fields from the database (projection)
            final String[] from = new String[]{
                    DB.PRIMARY_KEY, DB.ACTIVITY.START_TIME,
                    DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME, DB.ACTIVITY.SPORT
            };
            String[] args = {
                    mSynchronizerName
            };
            final String w = "NOT EXISTS (SELECT 1 FROM " + DB.EXPORT.TABLE + " r," +
                    DB.ACCOUNT.TABLE + " a WHERE " +
                    "r." + DB.EXPORT.ACTIVITY + " = " + DB.ACTIVITY.TABLE + "._id " +
                    " AND r." + DB.EXPORT.ACCOUNT + " = a." + "_id" +
                    " AND a." + DB.ACCOUNT.NAME + " = ?)";

            Cursor c = mDB.query(DB.ACTIVITY.TABLE, from,
                    " deleted == 0 AND " + w, args,
                    null, null, "_id desc", null);
            allSyncActivities.clear();
            int i = 0;
            final int maxUpload = 10;
            if (c.moveToFirst()) {
                do {
                    ActivityEntity ac = new ActivityEntity(c);
                    SyncActivityItem ai = new SyncActivityItem(ac);
                    // Limit default to upload, except for local
                    if (!mSynchronizerName.contentEquals(FileSynchronizer.NAME) && i++ >= maxUpload) {
                        ai.setSkipFlag(true);
                    }
                    allSyncActivities.add(ai);
                } while (c.moveToNext());
            }
            c.close();
            syncCount = allSyncActivities.size();
            requery();
        }
    }

    private void filterAlreadyPresentActivities() {
        List<SyncActivityItem> presentActivities = new ArrayList<>();
        final String[] from = new String[]{
                DB.PRIMARY_KEY, DB.ACTIVITY.START_TIME,
                DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME, DB.ACTIVITY.SPORT
        };

        Cursor c = mDB.query(DB.ACTIVITY.TABLE, from,
                " deleted = 0", null,
                null, null, "_id desc", null);

        if (c.moveToFirst()) {
            do {
                ActivityEntity av = new ActivityEntity(c);
                SyncActivityItem ai = new SyncActivityItem(av);
                presentActivities.add(ai);
            } while (c.moveToNext());
        }
        c.close();

        for (SyncActivityItem toDown : allSyncActivities) {
            for (SyncActivityItem present : presentActivities) {
                if (toDown.isSimilarTo(present)) {
                    toDown.setPresentFlag(Boolean.TRUE);
                    toDown.setSkipFlag(Boolean.FALSE);
                    break;
                }
            }
        }
        updateSyncCount();
    }

    private void updateSyncCount() {
        syncCount = 0;
        for (SyncActivityItem ai : allSyncActivities) {
            if (ai.synchronize(syncMode)) {
                syncCount++;
            }
        }
    }

    private void requery() {
        if (listView != null)
        ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
        if (syncCount > 0) {
            actionButton.setText(String.format(Locale.getDefault(), "%s (%d)", actionButtonText, syncCount));
            actionButton.setEnabled(true);
        } else {
            actionButton.setText(actionButtonText);
            actionButton.setEnabled(false);
        }
    }

    private final OnClickListener onActivityClick = arg0 -> {
        long id = ((UploadListAdapter.ViewHolderUploadActivity) arg0.getTag()).activityID;
        Intent intent = new Intent(UploadActivity.this, DetailActivity.class);
        intent.putExtra("ID", id);
        intent.putExtra("mode", "details");
        startActivityForResult(intent, 100);
    };

    class UploadListAdapter extends BaseAdapter {
        final LayoutInflater inflater;

        public UploadListAdapter(Context context) {
            super();
            inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return allSyncActivities.size();
        }

        @Override
        public Object getItem(int arg0) {
            return allSyncActivities.get(arg0);
        }

        @Override
        public long getItemId(int arg0) {
            return allSyncActivities.get(arg0).getId();
        }

        private class ViewHolderUploadActivity {
            private TextView tvStartTime;
            private TextView tvDistance;
            private TextView tvTime;
            private TextView tvPace;
            private TextView tvSport;
            private CheckBox cb;
            // metadata when clicking activities
            private long activityID;
        }

        @SuppressLint("ObsoleteSdkInt")
        @Override
        public View getView(int arg0, View convertView, ViewGroup parent) {
            View view = convertView;
            ViewHolderUploadActivity viewHolder;

            if (view == null) {
                viewHolder = new ViewHolderUploadActivity();

                view = inflater.inflate(R.layout.upload_row, parent, false);
                viewHolder.tvStartTime = view.findViewById(R.id.upload_list_start_time);
                viewHolder.tvDistance = view.findViewById(R.id.upload_list_distance);
                viewHolder.tvTime = view.findViewById(R.id.upload_list_time);
                viewHolder.tvPace = view.findViewById(R.id.upload_list_pace);
                viewHolder.tvSport = view.findViewById(R.id.upload_list_sport);
                viewHolder.cb = view.findViewById(R.id.upload_list_check);

                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolderUploadActivity) view.getTag();
            }
            viewHolder.activityID = getItemId(arg0);
            SyncActivityItem ai = allSyncActivities.get(arg0);

            Double d = ai.getDistance();
            Long t = ai.getDuration();

            if (ai.getStartTime() != null) {
                viewHolder.tvStartTime.setText(formatter.formatDateTime(ai.getStartTime()));
            } else {
                viewHolder.tvStartTime.setText("");
            }

            if (d != null) {
                viewHolder.tvDistance.setText(formatter.formatDistance(Formatter.Format.TXT_SHORT, d.longValue()));
            } else {
                viewHolder.tvDistance.setText("");
            }

            if (t != null) {
                viewHolder.tvTime.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, t));
            } else {
                viewHolder.tvTime.setText("");
            }

            if (d != null && t != null && t != 0) {
                viewHolder.tvPace.setText(formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_LONG, d / t));
            } else {
                viewHolder.tvPace.setText("");
            }

            if (ai.getSport() == null) {
                viewHolder.tvSport.setText(Sport.textOf(getResources(), DB.ACTIVITY.SPORT_RUNNING));
            } else {
                int sport = Sport.valueOf(ai.getSport()).getDbValue();
                viewHolder.tvSport.setText(Sport.textOf(getResources(), sport));
            }

            viewHolder.cb.setTag(arg0);
            viewHolder.cb.setOnCheckedChangeListener(checkedChangeClick);
            viewHolder.cb.setChecked(!ai.skipActivity());

            if (ai.isRelevantForSynch(syncMode)) {
                viewHolder.cb.setEnabled(Boolean.TRUE);
            } else {
                viewHolder.cb.setEnabled(Boolean.FALSE);
            }

            if (syncMode.equals(SyncManager.SyncMode.UPLOAD)) {
                view.setOnClickListener(onActivityClick);
            } else if (view.hasOnClickListeners()) {
                view.setOnClickListener(null);
            }

            return view;
        }
    }

    private final OnCheckedChangeListener checkedChangeClick = (arg0, arg1) -> {
        int pos = (Integer) arg0.getTag();
        SyncActivityItem tmp = allSyncActivities.get(pos);
        tmp.setSkipFlag(!arg1);
        updateSyncCount();
        requery();
    };

    private final OnClickListener uploadButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (allSyncActivities.isEmpty()) {
                return;
            }
            List<SyncActivityItem> upload = getSelectedActivities();
            Log.i(Constants.LOG, "Start uploading " + upload.size());
            fetching = true;
            cancelSync.delete(0, cancelSync.length());
            syncManager.syncActivities(SyncManager.SyncMode.UPLOAD, syncCallback, mSynchronizerName, upload, cancelSync);
        }
    };



    private final OnClickListener downloadButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (allSyncActivities.isEmpty()) {
                return;
            }
            List<SyncActivityItem> download = getSelectedActivities();
            Log.i(Constants.LOG, "Start downloading " + download.size());
            fetching = true;
            cancelSync.delete(0, cancelSync.length());
            syncManager.syncActivities(SyncManager.SyncMode.DOWNLOAD, syncCallback, mSynchronizerName, download, cancelSync);
        }
    };

    private List<SyncActivityItem> getSelectedActivities() {
        List<SyncActivityItem> selected = new ArrayList<>();
        for (SyncActivityItem tmp : allSyncActivities) {
            if (tmp.synchronize(syncMode))
                selected.add(tmp);
        }
        return selected;
    }

    private final SyncManager.Callback syncCallback = (synchronizerName, status) -> {
        fetching = false;
        if (cancelSync.length() > 0 || status == Status.CANCEL) {
            finish();
            return;
        }
        if (syncMode.equals(SyncManager.SyncMode.UPLOAD)) {
            fillData();
        } else {
            filterAlreadyPresentActivities();
            requery();
        }
    };

    private final OnClickListener clearAllButtonClick = v -> {
        for (SyncActivityItem tmp : allSyncActivities) {
            if (tmp.isRelevantForSynch(syncMode)) {
                tmp.setSkipFlag(Boolean.TRUE);
            }
        }
        updateSyncCount();
        requery();
    };

    private final OnClickListener setAllButtonClick = v -> {
        int i = 0;
        final int maxUpload = 30;
        for (SyncActivityItem ai : allSyncActivities) {
            if (ai.isRelevantForSynch(syncMode)) {
                // Limit uploads by default, to not overload services (even if action is not all)
                Boolean upload = mSynchronizerName.contentEquals(FileSynchronizer.NAME) ||
                        i++ < maxUpload;
                ai.setSkipFlag(!upload);
            }
        }
        updateSyncCount();
        requery();
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        fillData();
    }
}
