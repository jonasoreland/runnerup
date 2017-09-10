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
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.export.SyncManager;
import org.runnerup.export.Synchronizer.Status;
import org.runnerup.util.Formatter;
import org.runnerup.util.SyncActivityItem;
import org.runnerup.workout.Sport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class UploadActivity extends ListActivity implements Constants {

    private long synchronizerID = -1;
    private String synchronizer = null;
    private Integer synchronizerIcon = null;
    private SyncManager.SyncMode syncMode = SyncManager.SyncMode.UPLOAD;
    private SyncManager syncManager = null;

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
        synchronizer = intent.getStringExtra("synchronizer");
        synchronizerID = intent.getLongExtra("synchronizerID", -1);
        syncMode = SyncManager.SyncMode.valueOf(intent.getStringExtra("mode"));
        if (intent.hasExtra("synchronizerIcon"))
            synchronizerIcon = intent.getIntExtra("synchronizerIcon", 0);

        mDB = DBHelper.getReadableDatabase(this);
        formatter = new Formatter(this);
        syncManager = new SyncManager(this);

        this.getListView().setDividerHeight(1);
        setListAdapter(new UploadListAdapter(this));

        {
            Button btn = (Button) findViewById(R.id.account_upload_set_all);
            btn.setOnClickListener(setAllButtonClick);
        }

        {
            Button btn = (Button) findViewById(R.id.account_upload_clear_all);
            btn.setOnClickListener(clearAllButtonClick);
        }

        {
            Button dwbtn = (Button) findViewById(R.id.account_download_button);
            Button upbtn = (Button) findViewById(R.id.account_upload_button);
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

        {
            TextView tv = (TextView) findViewById(R.id.account_upload_list_name);
            ImageView im = (ImageView) findViewById(R.id.account_upload_list_icon);
            tv.setText(synchronizer);
            if (synchronizerIcon == null) {
                im.setVisibility(View.GONE);
                tv.setVisibility(View.VISIBLE);
            } else {
                im.setVisibility(View.VISIBLE);
                tv.setVisibility(View.GONE);
                im.setBackgroundResource(synchronizerIcon);
            }
        }

        fillData();
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
            syncManager.load(synchronizer);
            syncManager.loadActivityList(allSyncActivities, synchronizer, new SyncManager.Callback() {
                @Override
                public void run(String synchronizerName, Status status) {
                    filterAlreadyPresentActivities();
                    requery();
                }
            });
        } else {
            // Fields from the database (projection)
            // Must include the _id column for the adapter to work
            final String[] from = new String[]{
                    DB.PRIMARY_KEY, DB.ACTIVITY.START_TIME,
                    DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME, DB.ACTIVITY.SPORT
            };

            final String w = "NOT EXISTS (SELECT 1 FROM " + DB.EXPORT.TABLE + " r WHERE r."
                    + DB.EXPORT.ACTIVITY + " = " + DB.ACTIVITY.TABLE + "._id " +
                    " AND r." + DB.EXPORT.ACCOUNT + " = " + synchronizerID + ")";

            Cursor c = mDB.query(DB.ACTIVITY.TABLE, from,
                    " deleted == 0 AND " + w, null,
                    null, null, "_id desc", "100");
            allSyncActivities.clear();
            if (c.moveToFirst()) {
                do {
                    ActivityEntity ac = new ActivityEntity(c);
                    SyncActivityItem ai = new SyncActivityItem(ac);
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
        ((BaseAdapter) this.getListAdapter()).notifyDataSetChanged();
        if (syncCount > 0) {
            actionButton.setText(String.format(Locale.getDefault(), "%s (%d)", actionButtonText, syncCount));
            actionButton.setEnabled(true);
        } else {
            actionButton.setText(actionButtonText);
            actionButton.setEnabled(false);
        }
    }

    private final OnClickListener onActivityClick = new OnClickListener() {

        @Override
        public void onClick(View arg0) {
            long id = (Long) arg0.getTag();
            Intent intent = new Intent(UploadActivity.this, DetailActivity.class);
            intent.putExtra("ID", id);
            intent.putExtra("mode", "details");
            startActivityForResult(intent, 100);
        }
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
        }

        @SuppressLint("ObsoleteSdkInt")
        @Override
        public View getView(int arg0, View convertView, ViewGroup parent) {
            View view = convertView;
            ViewHolderUploadActivity viewHolder;

            if (view == null) {
                viewHolder = new ViewHolderUploadActivity();

                view = inflater.inflate(R.layout.upload_row, parent, false);
                viewHolder.tvStartTime = (TextView) view.findViewById(R.id.upload_list_start_time);
                viewHolder.tvDistance = (TextView) view.findViewById(R.id.upload_list_distance);
                viewHolder.tvTime = (TextView) view.findViewById(R.id.upload_list_time);
                viewHolder.tvPace = (TextView) view.findViewById(R.id.upload_list_pace);
                viewHolder.tvSport = (TextView) view.findViewById(R.id.upload_list_sport);
                viewHolder.cb = (CheckBox) view.findViewById(R.id.upload_list_check);

                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolderUploadActivity) view.getTag();
            }
            SyncActivityItem ai = allSyncActivities.get(arg0);

            Float d = ai.getDistance();
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

            if (d != null && t != null && d != 0 && t != 0) {
                viewHolder.tvPace.setText(formatter.formatPace(Formatter.Format.TXT_LONG, t / d));
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
            } else if (Build.VERSION.SDK_INT < 15 || view.hasOnClickListeners()) {
                view.setOnClickListener(null);
            }

            return view;
        }
    }

    private final OnCheckedChangeListener checkedChangeClick = new OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
            int pos = (Integer) arg0.getTag();
            SyncActivityItem tmp = allSyncActivities.get(pos);
            tmp.setSkipFlag(!arg1);
            updateSyncCount();
            requery();
        }

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
            syncManager.syncActivities(SyncManager.SyncMode.UPLOAD, syncCallback, synchronizer, upload, cancelSync);
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
            syncManager.syncActivities(SyncManager.SyncMode.DOWNLOAD, syncCallback, synchronizer, download, cancelSync);
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

    private final SyncManager.Callback syncCallback = new SyncManager.Callback() {

        @Override
        public void run(String synchronizerName, Status status) {
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
        }
    };

    private final OnClickListener clearAllButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            for (SyncActivityItem tmp : allSyncActivities) {
                if (tmp.isRelevantForSynch(syncMode)) {
                    tmp.setSkipFlag(Boolean.TRUE);
                }
            }
            updateSyncCount();
            requery();
        }
    };

    private final OnClickListener setAllButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            for (SyncActivityItem tmp : allSyncActivities) {
                if (tmp.isRelevantForSynch(syncMode)) {
                    tmp.setSkipFlag(Boolean.FALSE);
                }
            }
            updateSyncCount();
            requery();
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        fillData();
    }
}
