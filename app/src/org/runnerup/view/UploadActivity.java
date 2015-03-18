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
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
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
import org.runnerup.db.entities.ActivityValues;
import org.runnerup.export.UploadManager;
import org.runnerup.export.Uploader.Status;
import org.runnerup.export.format.ActivityItem;
import org.runnerup.util.Formatter;
import org.runnerup.workout.Sport;

import java.util.ArrayList;
import java.util.List;

@TargetApi(Build.VERSION_CODES.FROYO)
public class UploadActivity extends ListActivity implements Constants {

    long uploaderID = -1;
    String uploader = null;
    Integer uploaderIcon = null;
    Boolean downloading = Boolean.FALSE;
    UploadManager uploadManager = null;

    DBHelper mDBHelper = null;
    SQLiteDatabase mDB = null;
    Formatter formatter = null;
    final ArrayList<ActivityItem> uploadActivities = new ArrayList<ActivityItem>();

    int uploadCount = 0;
    Button actionButton = null;
    CharSequence actionButtonText = null;

    boolean fetching = false;
    final StringBuffer cancelUploading = new StringBuffer();

    /** Called when the activity is first created. */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.upload);

        Intent intent = getIntent();
        uploader = intent.getStringExtra("uploader");
        uploaderID = intent.getLongExtra("uploaderID", -1);
        downloading = intent.getBooleanExtra("download", Boolean.FALSE);
        if (intent.hasExtra("uploaderIcon"))
            uploaderIcon = intent.getIntExtra("uploaderIcon", 0);

        mDBHelper = new DBHelper(this);
        mDB = mDBHelper.getReadableDatabase();
        formatter = new Formatter(this);
        uploadManager = new UploadManager(this);

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
            if (downloading) {
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
            tv.setText(uploader);
            if (uploaderIcon == null) {
                im.setVisibility(View.GONE);
                tv.setVisibility(View.VISIBLE);
            } else {
                im.setVisibility(View.VISIBLE);
                tv.setVisibility(View.GONE);
                im.setBackgroundResource(uploaderIcon);
            }
        }

        fillData();
    }

    @Override
    public void onBackPressed() {
        if (fetching == true) {
            /**
             * Cancel
             */
            cancelUploading.append("1");
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDB.close();
        mDBHelper.close();
        uploadManager.close();
    }

    void fillData() {

        if (downloading) {
            uploadManager.load(uploader);
            uploadManager.loadActivityList(uploadActivities, uploader, new UploadManager.Callback() {
                @Override
                public void run(String uploader, Status status) {
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
                    " AND r." + DB.EXPORT.ACCOUNT + " = " + uploaderID + ")";

            Cursor c = mDB.query(DB.ACTIVITY.TABLE, from,
                    " deleted == 0 AND " + w, null,
                    null, null, "_id desc", "100");
            uploadActivities.clear();
            if (c.moveToFirst()) {
                do {
                    ActivityValues ac = new ActivityValues(c);
                    ActivityItem ai = new ActivityItem(ac);
                    uploadActivities.add(ai);
                } while (c.moveToNext());
            }
            c.close();
            uploadCount = uploadActivities.size();
            requery();
        }
    }

    private void filterAlreadyPresentActivities() {
        List<ActivityItem> presentActivities = new ArrayList<ActivityItem>();
        final String[] from = new String[]{
                DB.PRIMARY_KEY, DB.ACTIVITY.START_TIME,
                DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME, DB.ACTIVITY.SPORT
        };

        Cursor c = mDB.query(DB.ACTIVITY.TABLE, from,
                " deleted == 0", null,
                null, null, "_id desc", "100");

        if (c.moveToFirst()) {
            do {
                ActivityValues av = new ActivityValues(c);
                ActivityItem ai = new ActivityItem(av);
                presentActivities.add(ai);
            } while (c.moveToNext());
        }
        c.close();

        for (ActivityItem toDown : uploadActivities) {
            for (ActivityItem present : presentActivities) {
                if (toDown.equals(present)) {
                    toDown.setPresentFlag(Boolean.TRUE);
                    toDown.setSkipFlag(Boolean.FALSE);
                    break;
                }
            }
        }

    }

    void requery() {
        ((BaseAdapter) this.getListAdapter()).notifyDataSetChanged();
        if (uploadCount > 0) {
            actionButton.setText(actionButtonText + " (" + uploadCount + ")");
            actionButton.setEnabled(true);
        } else {
            actionButton.setText(actionButtonText);
            actionButton.setEnabled(false);
        }
    }

    final OnClickListener onActivityClick = new OnClickListener() {

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
            return uploadActivities.size();
        }

        @Override
        public Object getItem(int arg0) {
            return uploadActivities.get(arg0);
        }

        @Override
        public long getItemId(int arg0) {
            return uploadActivities.get(arg0).getId();
        }

        @Override
        public View getView(int arg0, View arg1, ViewGroup parent) {
            View view = inflater.inflate(R.layout.upload_row, parent, false);
            ActivityItem ai = uploadActivities.get(arg0);

            Float d = ai.getDistance();
            Long t = ai.getDuration();

            {
                TextView tv = (TextView) view.findViewById(R.id.upload_list_start_time);
                if (ai.getStartTime() != null) {
                    tv.setText(formatter.formatDateTime(Formatter.TXT_LONG,
                           ai.getStartTime()));
                } else {
                    tv.setText("");
                }
            }

            {
                TextView tv = (TextView) view.findViewById(R.id.upload_list_distance);
                if (d != null) {
                    tv.setText(formatter.formatDistance(Formatter.TXT_SHORT, d.longValue()));
                } else {
                    tv.setText("");
                }
            }

            {
                TextView tv = (TextView) view.findViewById(R.id.upload_list_time);
                if (t != null) {
                    tv.setText(formatter.formatElapsedTime(Formatter.TXT_SHORT, t));
                } else {
                    tv.setText("");
                }
            }

            {
                TextView tv = (TextView) view.findViewById(R.id.upload_list_pace);
                if (d != null && t != null && d != 0 && t != 0) {
                    tv.setText(formatter.formatPace(Formatter.TXT_LONG, t / d));
                } else {
                    tv.setText("");
                }
            }

            {
                TextView tv = (TextView) view.findViewById(R.id.upload_list_sport);
                if (ai.getSport() == null) {
                    tv.setText(getResources().getText(R.string.Running));
                } else {
                    tv.setText(getResources().getText(Sport.valueOf(ai.getSport()).getTextId()));
                }
            }

            {
                CheckBox cb = (CheckBox) view.findViewById(R.id.upload_list_check);
                cb.setTag(arg0);
                cb.setOnCheckedChangeListener(checkedChangeClick);
                cb.setChecked(!ai.getSkipFlag());

                if (ai.getPresentFlag()) {
                    cb.setEnabled(Boolean.FALSE);
                }
            }

            Long id = ai.getId();
            view.setTag(id);
            if (!downloading) {
                view.setOnClickListener(onActivityClick);
            }

            return view;
        }
    }

    final OnCheckedChangeListener checkedChangeClick = new OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
            int pos = (Integer) arg0.getTag();
            ActivityItem tmp = uploadActivities.get(pos);
            if (!tmp.getSkipFlag())
                uploadCount--;
            if (arg1) {
                tmp.setSkipFlag(Boolean.FALSE);
                uploadCount++;
            } else {
                tmp.setSkipFlag(Boolean.TRUE);
            }
            requery();
        }

    };

    final OnClickListener uploadButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            ArrayList<Long> activities = new ArrayList<Long>();
            for (ActivityItem tmp : uploadActivities) {
                if (!tmp.getSkipFlag())
                    activities.add(tmp.getId());
            }
            if (activities.isEmpty()) {
                return;
            }

            System.err.println("Start uploading " + activities.size());
            fetching = true;
            cancelUploading.delete(0, cancelUploading.length());
            uploadManager.uploadWorkouts(uploadCallback, uploader, activities, cancelUploading);
        }
    };

    final OnClickListener downloadButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {

        }
    };

    final UploadManager.Callback uploadCallback = new UploadManager.Callback() {

        @Override
        public void run(String uploader, Status status) {
            fetching = false;
            if (cancelUploading.length() > 0 || status == Status.CANCEL) {
                finish();
                return;
            }
            fillData();
        }
    };

    final OnClickListener clearAllButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            for (ActivityItem tmp : uploadActivities) {
                if (!tmp.getPresentFlag()) {
                    tmp.setSkipFlag(Boolean.TRUE);
                }
            }
            uploadCount = 0;
            requery();
        }
    };

    final OnClickListener setAllButtonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            for (ActivityItem tmp : uploadActivities) {
                if (!tmp.getPresentFlag()) {
                    tmp.setSkipFlag(Boolean.FALSE);
                }
            }
            uploadCount = uploadActivities.size();
            requery();
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        fillData();
    }
}
