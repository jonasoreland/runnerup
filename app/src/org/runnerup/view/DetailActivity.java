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
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphView.GraphViewData;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.LineGraphView;

import org.runnerup.R;
import org.runnerup.content.ActivityProvider;
import org.runnerup.content.WorkoutFileProvider;
import org.runnerup.db.ActivityCleaner;
import org.runnerup.db.DBHelper;
import org.runnerup.export.UploadManager;
import org.runnerup.export.Uploader;
import org.runnerup.export.Uploader.Feature;
import org.runnerup.util.Bitfield;
import org.runnerup.common.util.Constants;
import org.runnerup.util.Formatter;
import org.runnerup.util.HRZones;
import org.runnerup.widget.TitleSpinner;
import org.runnerup.widget.WidgetUtil;
import org.runnerup.workout.Intensity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

@TargetApi(Build.VERSION_CODES.FROYO)
public class DetailActivity extends FragmentActivity implements Constants {

    long mID = 0;
    DBHelper mDBHelper = null;
    SQLiteDatabase mDB = null;
    final HashSet<String> pendingUploaders = new HashSet<String>();
    final HashSet<String> alreadyUploadedUploaders = new HashSet<String>();

    boolean lapHrPresent = false;
    ContentValues laps[] = null;
    final ArrayList<ContentValues> reports = new ArrayList<ContentValues>();
    final ArrayList<BaseAdapter> adapters = new ArrayList<BaseAdapter>(2);

    int mode; // 0 == save 1 == details
    final static int MODE_SAVE = 0;
    final static int MODE_DETAILS = 1;
    boolean edit = false;
    boolean uploading = false;

    Button saveButton = null;
    Button discardButton = null;
    Button uploadButton = null;
    Button resumeButton = null;
    TextView activityTime = null;
    TextView activityPace = null;
    TextView activityDistance = null;

    TitleSpinner sport = null;
    EditText notes = null;
    MenuItem recomputeMenuItem = null;

    View mapViewLayout = null;
    GoogleMap map = null;
    View mapView = null;
    LatLngBounds mapBounds = null;
    AsyncTask<String, String, Route> loadRouteTask = null;
    LinearLayout graphTab = null;
    GraphView graphView;
    GraphView graphView2;

    LinearLayout hrzonesBarLayout;
    HRZonesBar hrzonesBar;

    UploadManager uploadManager = null;
    Formatter formatter = null;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.detail);
        WidgetUtil.addLegacyOverflowButton(getWindow());

        Intent intent = getIntent();
        mID = intent.getLongExtra("ID", -1);
        String mode = intent.getStringExtra("mode");

        mDBHelper = new DBHelper(this);
        mDB = mDBHelper.getReadableDatabase();
        uploadManager = new UploadManager(this);
        formatter = new Formatter(this);

        if (mode.contentEquals("save")) {
            this.mode = MODE_SAVE;
        } else if (mode.contentEquals("details")) {
            this.mode = MODE_DETAILS;
        } else {
            assert (false);
        }

        saveButton = (Button) findViewById(R.id.save_button);
        discardButton = (Button) findViewById(R.id.discard_button);
        resumeButton = (Button) findViewById(R.id.resume_button);
        uploadButton = (Button) findViewById(R.id.upload_button);
        activityTime = (TextView) findViewById(R.id.activity_time);
        activityDistance = (TextView) findViewById(R.id.activity_distance);
        activityPace = (TextView) findViewById(R.id.activity_pace);
        sport = (TitleSpinner) findViewById(R.id.summary_sport);
        notes = (EditText) findViewById(R.id.notes_text);
        notes.setHint(getString(R.string.notes_hint));
        map = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                .getMap();

        if (map != null) {
            map.setOnCameraChangeListener(new OnCameraChangeListener() {

                @Override
                public void onCameraChange(CameraPosition arg0) {
                    if (mapBounds != null) {
                        // Move camera.
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(mapBounds, 5));
                        // Remove listener to prevent position reset on camera
                        // move.
                        map.setOnCameraChangeListener(null);
                    }
                }
            });
        }
        saveButton.setOnClickListener(saveButtonClick);
        uploadButton.setOnClickListener(uploadButtonClick);
        if (this.mode == MODE_SAVE) {
            resumeButton.setOnClickListener(resumeButtonClick);
            discardButton.setOnClickListener(discardButtonClick);
            setEdit(true);
        } else if (this.mode == MODE_DETAILS) {
            resumeButton.setVisibility(View.GONE);
            discardButton.setVisibility(View.GONE);
            setEdit(false);
        }
        uploadButton.setVisibility(View.GONE);

        fillHeaderData();
        requery();

        loadRoute();

        TabHost th = (TabHost) findViewById(R.id.tabhost);
        th.setup();
        TabSpec tabSpec = th.newTabSpec("notes");
        tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, getString(R.string.tab_notes)));
        tabSpec.setContent(R.id.tab_main);
        th.addTab(tabSpec);

        tabSpec = th.newTabSpec("laps");
        tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, getString(R.string.tab_laps)));
        tabSpec.setContent(R.id.tab_lap);
        th.addTab(tabSpec);

        tabSpec = th.newTabSpec("map");
        tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, getString(R.string.tab_map)));
        tabSpec.setContent(R.id.tab_map);
        th.addTab(tabSpec);

        tabSpec = th.newTabSpec("graph");
        tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, getString(R.string.tab_graph)));
        tabSpec.setContent(R.id.tab_graph);
        th.addTab(tabSpec);

        tabSpec = th.newTabSpec("share");
        tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, getString(R.string.tab_upload)));
        tabSpec.setContent(R.id.tab_upload);
        th.addTab(tabSpec);

        th.getTabWidget().setBackgroundColor(Color.DKGRAY);

        {
            ListView lv = (ListView) findViewById(R.id.laplist);
            LapListAdapter adapter = new LapListAdapter();
            adapters.add(adapter);
            lv.setAdapter(adapter);
        }
        {
            ListView lv = (ListView) findViewById(R.id.report_list);
            ReportListAdapter adapter = new ReportListAdapter();
            adapters.add(adapter);
            lv.setAdapter(adapter);
        }
        graphTab = (LinearLayout) findViewById(R.id.tab_graph);
        {
            graphView = new LineGraphView(this, getString(R.string.pace_chart)) {
                @Override
                protected String formatLabel(double value, boolean isValueX) {
                    if (!isValueX) {
                        return formatter.formatPace(Formatter.TXT_SHORT, value);
                    } else
                        return formatter.formatDistance(Formatter.TXT_SHORT, (long) value);
                }
            };

            graphView2 = new LineGraphView(this, getString(R.string.heart_rate)) {
                @Override
                protected String formatLabel(double value, boolean isValueX) {
                    if (!isValueX) {
                        return Integer.toString((int) Math.round(value));
                    } else {
                        return formatter.formatDistance(Formatter.TXT_SHORT,
                                (long) value);
                    }
                }
            };
        }
        hrzonesBarLayout = (LinearLayout) findViewById(R.id.hrzonesBarLayout);
        hrzonesBar = new HRZonesBar(this);
    }

    private void setEdit(boolean value) {
        edit = value;
        saveButton.setEnabled(value);
        uploadButton.setEnabled(value);
        WidgetUtil.setEditable(notes, value);
        sport.setEnabled(value);
        if (recomputeMenuItem != null)
            recomputeMenuItem.setEnabled(value);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mode == MODE_DETAILS) {
            getMenuInflater().inflate(R.menu.detail_menu, menu);
            recomputeMenuItem = menu.findItem(R.id.menu_recompute_activity);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_delete_activity:
                deleteButtonClick.onClick(null);
                break;
            case R.id.menu_edit_activity:
                if (edit == false) {
                    setEdit(true);
                    notes.requestFocus();
                    requery();
                }
                break;
            case R.id.menu_recompute_activity:
                new ActivityCleaner().recompute(mDB, mID);
                requery();
                break;
            case R.id.menu_share_activity:
                shareActivity();
                break;
        }
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDB.close();
        mDBHelper.close();
        uploadManager.close();
    }

    void requery() {
        {
            /**
             * Laps
             */
            String[] from = new String[] {
                    "_id", DB.LAP.LAP, DB.LAP.INTENSITY,
                    DB.LAP.TIME, DB.LAP.DISTANCE, DB.LAP.PLANNED_TIME,
                    DB.LAP.PLANNED_DISTANCE, DB.LAP.PLANNED_PACE, DB.LAP.AVG_HR
            };

            Cursor c = mDB.query(DB.LAP.TABLE, from, DB.LAP.ACTIVITY + " == " + mID,
                    null, null, null, "_id", null);

            laps = DBHelper.toArray(c);
            c.close();
            lapHrPresent = false;
            for (ContentValues v : laps) {
                if (v.containsKey(DB.LAP.AVG_HR) && v.getAsInteger(DB.LAP.AVG_HR) > 0) {
                    lapHrPresent = true;
                    break;
                }
            }
        }

        {
            /**
             * Accounts/reports
             */
            String sql = new String(
                    "SELECT DISTINCT "
                            + "  acc._id, " // 0
                            + ("  acc." + DB.ACCOUNT.NAME + ", ")
                            + ("  acc." + DB.ACCOUNT.DESCRIPTION + ", ")
                            + ("  acc." + DB.ACCOUNT.FLAGS + ", ")
                            + ("  acc." + DB.ACCOUNT.AUTH_METHOD + ", ")
                            + ("  acc." + DB.ACCOUNT.AUTH_CONFIG + ", ")
                            + ("  acc." + DB.ACCOUNT.ENABLED + ", ")
                            + ("  rep._id as repid, ")
                            + ("  rep." + DB.EXPORT.ACCOUNT + ", ")
                            + ("  rep." + DB.EXPORT.ACTIVITY + ", ")
                            + ("  rep." + DB.EXPORT.STATUS)
                            + (" FROM " + DB.ACCOUNT.TABLE + " acc ")
                            + (" LEFT OUTER JOIN " + DB.EXPORT.TABLE + " rep ")
                            + (" ON ( acc._id = rep." + DB.EXPORT.ACCOUNT)
                            + ("     AND rep." + DB.EXPORT.ACTIVITY + " = "
                                    + mID + " )")
                            + (" WHERE acc." + DB.ACCOUNT.ENABLED + " != 0 ")
                            + ("   AND acc." + DB.ACCOUNT.AUTH_CONFIG + " is not null"));

            Cursor c = mDB.rawQuery(sql, null);
            alreadyUploadedUploaders.clear();
            pendingUploaders.clear();
            reports.clear();
            if (c.moveToFirst()) {
                do {
                    ContentValues tmp = DBHelper.get(c);
                    Uploader uploader = uploadManager.add(tmp);
                    if (!uploader.checkSupport(Feature.UPLOAD)) {
                        continue;
                    }

                    reports.add(tmp);
                    if (tmp.containsKey("repid")) {
                        alreadyUploadedUploaders.add(tmp.getAsString(DB.ACCOUNT.NAME));
                    } else if (tmp.containsKey(DB.ACCOUNT.FLAGS)
                            && Bitfield.test(tmp.getAsLong(DB.ACCOUNT.FLAGS),
                                    DB.ACCOUNT.FLAG_UPLOAD)) {
                        pendingUploaders.add(tmp.getAsString(DB.ACCOUNT.NAME));
                    }
                } while (c.moveToNext());
            }
            c.close();
        }

        if (mode == MODE_DETAILS) {
            if (pendingUploaders.isEmpty()) {
                uploadButton.setVisibility(View.GONE);
            } else {
                uploadButton.setVisibility(View.VISIBLE);
            }
        }

        for (BaseAdapter a : adapters) {
            a.notifyDataSetChanged();
        }
    }

    void fillHeaderData() {
        // Fields from the database (projection)
        // Must include the _id column for the adapter to work
        String[] from = new String[] {
                DB.ACTIVITY.START_TIME,
                DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME, DB.ACTIVITY.COMMENT,
                DB.ACTIVITY.SPORT
        };

        Cursor c = mDB.query(DB.ACTIVITY.TABLE, from, "_id == " + mID, null,
                null, null, null, null);
        c.moveToFirst();
        ContentValues tmp = DBHelper.get(c);
        c.close();

        long st = 0;
        if (tmp.containsKey(DB.ACTIVITY.START_TIME)) {
            st = tmp.getAsLong(DB.ACTIVITY.START_TIME);
            setTitle("RunnerUp - " + formatter.formatDateTime(Formatter.TXT_LONG, st));
        }
        float d = 0;
        if (tmp.containsKey(DB.ACTIVITY.DISTANCE)) {
            d = tmp.getAsFloat(DB.ACTIVITY.DISTANCE);
            activityDistance.setText(formatter.formatDistance(Formatter.TXT_LONG, (long) d));
        } else {
            activityDistance.setText("");
        }

        float t = 0;
        if (tmp.containsKey(DB.ACTIVITY.TIME)) {
            t = tmp.getAsFloat(DB.ACTIVITY.TIME);
            activityTime.setText(formatter.formatElapsedTime(Formatter.TXT_SHORT, (long) t));
        } else {
            activityTime.setText("");
        }

        if (d != 0 && t != 0) {
            activityPace.setText(formatter.formatPace(Formatter.TXT_LONG, t / d));
        } else {
            activityPace.setText("");
        }

        if (tmp.containsKey(DB.ACTIVITY.COMMENT)) {
            notes.setText(tmp.getAsString(DB.ACTIVITY.COMMENT));
        }

        if (tmp.containsKey(DB.ACTIVITY.SPORT)) {
            sport.setValue(tmp.getAsInteger(DB.ACTIVITY.SPORT));
        }
    }

    class LapListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return laps.length;
        }

        @Override
        public Object getItem(int position) {
            return laps[position];
        }

        @Override
        public long getItemId(int position) {
            return laps[position].getAsLong("_id");
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(DetailActivity.this);
            View view = inflater.inflate(R.layout.laplist_row, parent, false);
            TextView tv0 = (TextView) view.findViewById(R.id.lap_list_type);
            int i = laps[position].getAsInteger(DB.LAP.INTENSITY);
            Intensity intensity = Intensity.values()[i];
            switch (intensity) {
                case ACTIVE:
                    tv0.setText("");
                    break;
                case COOLDOWN:
                case RESTING:
                case RECOVERY:
                case WARMUP:
                case REPEAT:
                    tv0.setText("(" + getResources().getString(intensity.getTextId()) + ")");
                default:
                    break;

            }
            TextView tv1 = (TextView) view.findViewById(R.id.lap_list_id);
            tv1.setText(laps[position].getAsString("_id"));
            TextView tv2 = (TextView) view.findViewById(R.id.lap_list_distance);
            float d = laps[position].containsKey(DB.LAP.DISTANCE) ? laps[position]
                    .getAsFloat(DB.LAP.DISTANCE) : 0;
            tv2.setText(formatter.formatDistance(Formatter.TXT_LONG, (long) d));
            TextView tv3 = (TextView) view.findViewById(R.id.lap_list_time);
            long t = laps[position].containsKey(DB.LAP.TIME) ? laps[position]
                    .getAsLong(DB.LAP.TIME) : 0;
            tv3.setText(formatter.formatElapsedTime(Formatter.TXT_SHORT, t));
            TextView tv4 = (TextView) view.findViewById(R.id.lap_list_pace);
            if (t != 0 && d != 0)
            {
                tv4.setText(formatter.formatPace(Formatter.TXT_LONG, t / d));
            }
            else
            {
                tv4.setText("");
            }
            int hr = laps[position].containsKey(DB.LAP.AVG_HR) ? laps[position]
                    .getAsInteger(DB.LAP.AVG_HR) : 0;
            TextView tvHr = (TextView) view.findViewById(R.id.lap_list_hr);
            if (hr > 0) {
                tvHr.setVisibility(View.VISIBLE);
                tvHr.setText(formatter.formatHeartRate(Formatter.TXT_LONG, hr) + " bpm");
            } else if (lapHrPresent) {
                tvHr.setVisibility(View.INVISIBLE);
            } else {
                tvHr.setVisibility(View.GONE);
            }

            return view;
        }
    }

    class ReportListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return reports.size() + 1;
        }

        @Override
        public Object getItem(int position) {
            if (position < reports.size())
                return reports.get(position);
            return this;
        }

        @Override
        public long getItemId(int position) {
            if (position < reports.size())
                return reports.get(position).getAsLong("_id");

            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (position == reports.size()) {
                Button b = new Button(DetailActivity.this);
                b.setText(getString(R.string.configure_accounts));
                b.setBackgroundResource(R.drawable.btn_blue);
                b.setTextColor(getResources().getColorStateList(R.color.btn_text_color));
                b.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(DetailActivity.this,
                                AccountListActivity.class);
                        DetailActivity.this.startActivityForResult(i,
                                UploadManager.CONFIGURE_REQUEST + 1);
                    }
                });
                return b;
            }

            ContentValues tmp = reports.get(position);
            String name = tmp.getAsString("name");

            LayoutInflater inflater = LayoutInflater.from(DetailActivity.this);
            View view = inflater.inflate(R.layout.reportlist_row, parent, false);

            TextView tv0 = (TextView) view.findViewById(R.id.account_id);
            CheckBox cb = (CheckBox) view.findViewById(R.id.report_sent);
            TextView tv1 = (TextView) view.findViewById(R.id.account_name);
            cb.setChecked(false);
            cb.setEnabled(false);
            cb.setTag(name);
            if (alreadyUploadedUploaders.contains(name)) {
                cb.setChecked(true);
                cb.setText(getString(R.string.uploaded));
                if (edit)
                {
                    cb.setOnLongClickListener(clearUploadClick);
                }
                else
                {
                    cb.setEnabled(false);
                }
            } else if (pendingUploaders.contains(name)) {
                cb.setChecked(true);
            } else {
                cb.setChecked(false);
            }
            if (mode == MODE_DETAILS && !alreadyUploadedUploaders.contains(name)) {
                cb.setEnabled(edit);
            } else if (mode == MODE_SAVE) {
                cb.setEnabled(true);
            }
            cb.setOnCheckedChangeListener(onSendChecked);

            tv0.setText(tmp.getAsString("_id"));
            tv1.setText(name);
            return view;
        }
    }

    void saveActivity() {
        ContentValues tmp = new ContentValues();
        tmp.put(DB.ACTIVITY.COMMENT, notes.getText().toString());
        tmp.put(DB.ACTIVITY.SPORT, sport.getValueInt());
        String whereArgs[] = {
            Long.toString(mID)
        };
        mDB.update(DB.ACTIVITY.TABLE, tmp, "_id = ?", whereArgs);
    }

    final OnLongClickListener clearUploadClick = new OnLongClickListener() {

        @Override
        public boolean onLongClick(View arg0) {
            final String name = (String) arg0.getTag();
            AlertDialog.Builder builder = new AlertDialog.Builder(DetailActivity.this);
            builder.setTitle("Clear upload for " + name);
            builder.setMessage(getString(R.string.are_you_sure));
            builder.setPositiveButton(getString(R.string.yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            uploadManager.clearUpload(name, mID);
                            requery();
                        }
                    });
            builder.setNegativeButton(getString(R.string.no),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Do nothing but close the dialog
                            dialog.dismiss();
                        }

                    });
            builder.show();
            return false;
        }

    };

    final OnClickListener saveButtonClick = new OnClickListener() {
        public void onClick(View v) {
            saveActivity();
            if (mode == MODE_DETAILS) {
                setEdit(false);
                requery();
                return;
            }
            uploading = true;
            uploadManager.startUploading(new UploadManager.Callback() {
                @Override
                public void run(String uploader, Uploader.Status status) {
                    uploading = false;
                    DetailActivity.this.setResult(RESULT_OK);
                    DetailActivity.this.finish();
                }
            }, pendingUploaders, mID);
        }
    };

    final OnClickListener discardButtonClick = new OnClickListener() {
        public void onClick(View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(DetailActivity.this);
            builder.setTitle(getString(R.string.discard_activity));
            builder.setMessage(getString(R.string.are_you_sure));
            builder.setPositiveButton(getString(R.string.yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            DetailActivity.this.setResult(RESULT_CANCELED);
                            DetailActivity.this.finish();
                        }
                    });
            builder.setNegativeButton(getString(R.string.no),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Do nothing but close the dialog
                            dialog.dismiss();
                        }

                    });
            builder.show();
        }
    };

    @Override
    public void onBackPressed() {
        if (uploading == true) {
            /**
             * Ignore while uploading
             */
            return;
        }
        if (mode == MODE_SAVE) {
            resumeButtonClick.onClick(resumeButton);
        } else {
            super.onBackPressed();
        }
    }

    final OnClickListener resumeButtonClick = new OnClickListener() {
        public void onClick(View v) {
            DetailActivity.this.setResult(RESULT_FIRST_USER);
            DetailActivity.this.finish();
        }
    };

    final OnClickListener uploadButtonClick = new OnClickListener() {
        public void onClick(View v) {
            uploading = true;
            uploadManager.startUploading(new UploadManager.Callback() {
                @Override
                public void run(String uploader, Uploader.Status status) {
                    uploading = false;
                    requery();
                }
            }, pendingUploaders, mID);
        }
    };

    public final OnCheckedChangeListener onSendChecked = new OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
            final String name = (String) arg0.getTag();
            if (alreadyUploadedUploaders.contains(name)) {
                // Only accept long clicks
                arg0.setChecked(true);
            } else {
                if (arg1 == true) {
                    pendingUploaders.add((String) arg0.getTag());
                } else {
                    pendingUploaders.remove((String) arg0.getTag());
                }

                if (mode == MODE_DETAILS) {
                    if (pendingUploaders.isEmpty())
                        uploadButton.setVisibility(View.GONE);
                    else
                        uploadButton.setVisibility(View.VISIBLE);
                }
            }
        }
    };

    final OnClickListener deleteButtonClick = new OnClickListener() {
        public void onClick(View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    DetailActivity.this);
            builder.setTitle(getString(R.string.delete_activity));
            builder.setMessage(getString(R.string.are_you_sure));
            builder.setPositiveButton(getString(R.string.yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            DBHelper.deleteActivity(mDB, mID);
                            dialog.dismiss();
                            DetailActivity.this.setResult(RESULT_OK);
                            DetailActivity.this.finish();
                        }
                    });
            builder.setNegativeButton(getString(R.string.no),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Do nothing but close the dialog
                            dialog.dismiss();
                        }

                    });
            builder.show();
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UploadManager.CONFIGURE_REQUEST) {
            uploadManager.onActivityResult(requestCode, resultCode, data);
        }
        requery();
    }

    public static int position(String arr[], String key) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].contentEquals(key))
                return i;
        }
        return -1;
    }

    class Route {
        final PolylineOptions path = new PolylineOptions();
        final LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        final ArrayList<MarkerOptions> markers = new ArrayList<MarkerOptions>(10);

        Route() {
            path.color(Color.RED);
            path.width(3);
        }
    }

    class GraphProducer {
        static final int GRAPH_INTERVAL_SECONDS = 5; // 1 point every 5 sec
        static final int GRAPH_AVERAGE_SECONDS = 30; // moving average 30 sec

        final int interval;
        int pos = 0;
        double time[] = null;
        double distance[] = null;
        double sum_time = 0;
        double sum_distance = 0;
        double acc_time = 0;

        int[] hr = null;
        double[] hrzHist = null;

        double tot_avg_hr = 0;
        int min_hr = Integer.MAX_VALUE;
        int max_hr = 0;

        double avg_pace = 0;
        double min_pace = Double.MAX_VALUE;
        double max_pace = Double.MIN_VALUE;
        List<GraphViewData> paceList = null;
        List<GraphViewData> hrList = null;

        boolean showHR = false;
        boolean showHRZhist = false;
        HRZones hrCalc = null;

        GraphProducer() {
            this(GRAPH_INTERVAL_SECONDS, GRAPH_AVERAGE_SECONDS);
        }

        public GraphProducer(int graphIntervalSeconds, int graphAverageSeconds) {
            this.paceList = new ArrayList<GraphViewData>();
            this.interval = graphIntervalSeconds;
            this.time = new double[graphAverageSeconds];
            this.distance = new double[graphAverageSeconds];

            this.hrList = new ArrayList<GraphViewData>();
            this.hr = new int[graphAverageSeconds];

            Resources res = getResources();
            Context ctx = getApplicationContext();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
            this.hrCalc = new HRZones(res, prefs);
            if (hrCalc.isConfigured()) {
                this.hrzHist = new double[hrCalc.getCount() + 1];
                for (int i = 0; i < this.hrzHist.length; i++) {
                    this.hrzHist[i] = 0;
                }
                showHRZhist = true;
            }

            clear(0);
        }

        void clear(double tot_distance) {
            if (pos >= (this.time.length / 2) && (acc_time >= 1000 * (interval / 2))
                    && sum_distance > 0) {
                emit(tot_distance);
            }

            for (int i = 0; i < this.distance.length; i++) {
                time[i] = 0;
                distance[i] = 0;
                hr[i] = 0;
            }
            pos = 0;
            sum_time = 0;
            sum_distance = 0;
            acc_time = 0;
        }

        void addObservation(double delta_time, double delta_distance, double tot_distance, int hr) {
            if (delta_time < 500)
                return;
            if (hr > 0) {
                showHR = true;
            }

            int p = pos % this.time.length;
            sum_time -= this.time[p];
            sum_distance -= this.distance[p];
            sum_time += delta_time;
            sum_distance += delta_distance;

            this.time[p] = delta_time;
            this.distance[p] = delta_distance;
            this.hr[p] = hr;
            pos = (pos + 1);

            if (showHRZhist && hr > 0) {
                this.hrzHist[hrCalc.getZoneInt(hr)] += delta_time;
            }

            acc_time += delta_time;

            if (pos >= this.time.length && (acc_time >= 1000 * interval) && sum_distance > 0) {
                emit(tot_distance);
            }
        }

        void emit(double tot_distance) {
            double avg_time = sum_time;
            double avg_dist = sum_distance;
            double avg_hr = calculateAverage(hr);
            if (true) {
                // remove max/min pace to (maybe) get smoother graph
                double max_pace[] = {
                        0, 0, 0
                };
                double min_pace[] = {
                        Double.MAX_VALUE, 0, 0
                };
                for (int i = 0; i < this.time.length; i++) {
                    double pace = this.time[i] / this.distance[i];
                    if (pace > max_pace[0]) {
                        max_pace[0] = pace;
                        max_pace[1] = this.time[i];
                        max_pace[2] = this.distance[i];
                    }
                    if (pace < min_pace[0]) {
                        min_pace[0] = pace;
                        min_pace[1] = this.time[i];
                        min_pace[2] = this.distance[i];
                    }
                }
                avg_time -= (max_pace[1] + min_pace[1]);
                avg_dist -= (max_pace[2] + min_pace[2]);
            }
            if (avg_dist > 0) {
                double pace = avg_time / avg_dist / 1000.0;
                paceList.add(new GraphViewData(tot_distance, pace));
                hrList.add(new GraphViewData(tot_distance, Math.round(avg_hr)));
                acc_time = 0;

                tot_avg_hr += avg_hr;
                avg_pace += pace;
                min_pace = Math.min(min_pace, pace);
                max_pace = Math.max(max_pace, pace);
            }
        }

        class GraphFilter {

            double data[] = null;
            final List<GraphViewData> source;

            GraphFilter(List<GraphViewData> paceList) {
                source = paceList;
                data = new double[paceList.size()];
                for (int i = 0; i < paceList.size(); i++)
                    data[i] = paceList.get(i).valueY;
            }

            void complete() {
                for (int i = 0; i < source.size(); i++)
                    source.set(i, new GraphViewData(source.get(i).valueX, data[i]));
            }

            void init(double window[], double val) {
                for (int j = 0; j < window.length - 1; j++)
                    window[j] = val;
            }

            void shiftLeft(double window[], double newVal) {
                for (int j = 0; j < window.length - 1; j++)
                    window[j] = window[j + 1];
                window[window.length - 1] = newVal;
            }

            /**
             * Perform in place moving average
             */
            void movingAvergage(int windowLen) {
                double window[] = new double[windowLen];
                init(window, data[0]);

                final int mid = (window.length - 1) / 2;
                final int last = window.length - 1;
                for (int i = 0; i < data.length && i <= mid; i++) {
                    window[i + mid] = data[i];
                }

                double sum = 0;
                for (double aWindow : window) sum += aWindow;

                for (int i = 0; i < data.length; i++) {
                    double newY = sum / windowLen;
                    data[i] = newY;
                    sum -= window[0];
                    shiftLeft(window, (i + mid) < data.length ? data[i + mid] : avg_pace);
                    sum += window[last];
                }
            }

            /**
             * Perform in place moving average
             */
            void movingMedian(int windowLen) {
                double window[] = new double[windowLen];
                init(window, data[0]);

                final int mid = (window.length - 1) / 2;
                for (int i = 0; i < data.length && i <= mid; i++) {
                    window[i + mid] = data[i];
                }

                double sort[] = new double[windowLen];
                for (int i = 0; i < data.length; i++) {
                    System.arraycopy(window, 0, sort, 0, windowLen);
                    Arrays.sort(sort);
                    data[i] = sort[mid];
                    shiftLeft(window, (i + mid) < data.length ? data[i + mid] : avg_pace);
                }
            }

            /**
             * Perform in place SavitzkyGolay windowLen = 5
             */
            void SavitzkyGolay5() {
                final int len = 5;
                double window[] = new double[len];
                init(window, data[0]);

                final int mid = (window.length - 1) / 2;
                for (int i = 0; i < data.length && i <= mid; i++) {
                    window[i + mid] = data[i];
                }
                for (int i = 0; i < data.length; i++) {
                    double newY = (-3 * window[0] + 12 * window[1] + 17
                            * window[2] + 12 * window[3] - 3 * window[4]) / 35;
                    data[i] = newY;
                    shiftLeft(window,
                            (i + mid) < data.length ? data[i + mid] : avg_pace);
                }
            }

            /**
             * Perform in place SavitzkyGolay windowLen = 7
             */
            void SavitzkyGolay7() {
                final int len = 7;
                double window[] = new double[len];
                init(window, data[0]);

                final int mid = (window.length - 1) / 2;
                for (int i = 0; i < data.length && i <= mid; i++) {
                    window[i + mid] = data[i];
                }
                for (int i = 0; i < data.length; i++) {
                    double newY = (-2 * window[0] + 3 * window[1] + 6
                            * window[2] + 7 * window[3] + 6 * window[4] + 3
                            * window[5] - 2 * window[6]) / 21;
                    data[i] = newY;
                    shiftLeft(window,
                            (i + mid) < data.length ? data[i + mid] : avg_pace);
                }
            }

            void KolmogorovZurbenko(int n, int len) {
                for (int i = 0; i < n; i++)
                    movingAvergage(len);
            }
        }

        public void complete(GraphView graphView) {
            avg_pace /= paceList.size();
            System.err.println("graph: " + paceList.size() + " points");

            boolean smoothData = PreferenceManager.getDefaultSharedPreferences(DetailActivity.this)
                    .getBoolean(getResources().getString(R.string.pref_pace_graph_smoothing), true);
            if (paceList.size() > 0 && smoothData) {
                GraphFilter f = new GraphFilter(paceList);
                final String defaultFilterList = "mm(31);kz(5,13);sg(5)";
                final String filterList = PreferenceManager.getDefaultSharedPreferences(
                        DetailActivity.this).getString(
                        getResources().getString(R.string.pref_pace_graph_smoothing_filters),
                        defaultFilterList);
                final String filters[] = filterList.split(";");
                System.err.print("Applying filters(" + filters.length + ", >" + filterList + "<):");
                for (String filter : filters) {
                    int args[] = getArgs(filter);
                    if (filter.startsWith("mm")) {
                        if (args.length == 1) {
                            f.movingMedian(args[0]);
                            System.err.print(" mm(" + args[0] + ")");
                        }
                    } else if (filter.startsWith("ma")) {
                        if (args.length == 1) {
                            f.movingAvergage(args[0]);
                            System.err.print(" ma(" + args[0] + ")");
                        }
                    } else if (filter.startsWith("kz")) {
                        if (args.length == 2) {
                            f.KolmogorovZurbenko(args[0], args[1]);
                            System.err.print(" kz(" + args[0] + "," + args[1] + ")");
                        }
                    } else if (filter.startsWith("sg")) {
                        if (args.length == 1 && args[0] == 5) {
                            f.SavitzkyGolay5();
                            System.err.print(" sg(5)");
                        } else if (args.length == 1 && args[0] == 7) {
                            f.SavitzkyGolay7();
                            System.err.print(" sg(7)");
                        }
                    }
                }
                System.err.println("");
                f.complete();
            }
            GraphViewSeries graphViewData = new GraphViewSeries(
                    paceList.toArray(new GraphViewData[paceList.size()]));
            graphView.addSeries(graphViewData); // data
            graphView.redrawAll();
            if (showHR) {
                GraphViewSeries graphViewData2 = new GraphViewSeries(
                        hrList.toArray(new GraphViewData[hrList.size()]));
                graphView2.addSeries(graphViewData2); // data

                if (showHRZhist) {
                    System.err.print("HR Zones:");
                    double sum = 0;
                    for (double aHrzHist : hrzHist) {
                        sum += aHrzHist;
                    }
                    for (int i = 0; i < hrzHist.length; i++) {
                        hrzHist[i] = hrzHist[i] / sum;
                        System.err.print(" " + hrzHist[i]);
                    }
                    System.err.println("\n");
                    hrzonesBar.pushHrzData(hrzHist);
                }
            }
        }

        private int[] getArgs(String s) {
            try {
                s = s.substring(s.indexOf('(') + 1);
                s = s.substring(0, s.indexOf(')'));
                String sargs[] = s.split(",");
                int args[] = new int[sargs.length];
                for (int i = 0; i < args.length; i++) {
                    args[i] = Integer.parseInt(sargs[i]);
                }
                return args;
            } catch (Exception e) {
                e.printStackTrace();
                return new int[0];
            }
        }

        public boolean HasHRInfo() {
            return showHR;
        }

        public boolean HasHRZHist () { return showHR && showHRZhist; }
    }

    public double calculateAverage(int[] data) {
        int sum = 0;

        for (int aData : data) {
            sum = sum + aData;
        }
        double average = (double) sum / data.length;
        return average;
    }

    private void loadRoute() {
        final GraphProducer graphData = new GraphProducer();

        final String[] from = new String[] {
                DB.LOCATION.LATITUDE,
                DB.LOCATION.LONGITUDE,
                DB.LOCATION.TYPE,
                DB.LOCATION.TIME,
                DB.LOCATION.LAP,
                DB.LOCATION.HR
        };

        loadRouteTask = new AsyncTask<String, String, Route>() {

            @Override
            protected Route doInBackground(String... params) {

                int cnt = 0;
                Route route = null;
                Cursor c = mDB.query(DB.LOCATION.TABLE, from, "activity_id == " + mID,
                        null, null, null, "_id", null);
                if (c.moveToFirst()) {
                    route = new Route();
                    double acc_distance = 0;
                    double tot_distance = 0;
                    int cnt_distance = 0;
                    LatLng lastLocation = null;
                    long lastTime = 0;
                    int lastLap = -1;
                    int hr = 0;
                    do {
                        cnt++;
                        LatLng point = new LatLng(c.getDouble(0), c.getDouble(1));
                        route.path.add(point);
                        route.bounds.include(point);
                        int type = c.getInt(2);
                        long time = c.getLong(3);
                        int lap = c.getInt(4);
                        if (!c.isNull(5))
                            hr = c.getInt(5);
                        MarkerOptions m;
                        switch (type) {
                            case DB.LOCATION.TYPE_START:
                            case DB.LOCATION.TYPE_END:
                            case DB.LOCATION.TYPE_PAUSE:
                            case DB.LOCATION.TYPE_RESUME:
                                if (type == DB.LOCATION.TYPE_PAUSE)
                                {
                                    if (lap != lastLap) {
                                        graphData.clear(tot_distance);
                                    } else if (lastTime != 0 && lastLocation != null) {
                                        float res[] = {
                                            0
                                        };
                                        Location.distanceBetween(lastLocation.latitude,
                                                lastLocation.longitude, point.latitude,
                                                point.longitude, res);
                                        graphData.addObservation(time - lastTime, res[0],
                                                tot_distance, hr);
                                        // hrList.clear();
                                        graphData.clear(tot_distance);
                                    }
                                    lastLap = lap;
                                    lastTime = 0;
                                }
                                else if (type == DB.LOCATION.TYPE_RESUME)
                                {
                                    lastLap = lap;
                                    lastTime = time;
                                }
                                m = new MarkerOptions();
                                m.position((lastLocation = point));
                                switch (type) {
                                    case DB.LOCATION.TYPE_START:
                                        m.title(getResources().getString(R.string.start));
                                        break;
                                    case DB.LOCATION.TYPE_END:
                                        m.title(getResources().getString(R.string.stop));
                                        break;
                                    case DB.LOCATION.TYPE_PAUSE:
                                        m.title(getResources().getString(R.string.pause));
                                        break;
                                    case DB.LOCATION.TYPE_RESUME:
                                        m.title(getResources().getString(R.string.resume));
                                        break;
                                }
                                m.snippet(null);
                                m.draggable(false);
                                route.markers.add(m);
                                break;
                            case DB.LOCATION.TYPE_GPS:
                                float res[] = {
                                    0
                                };
                                Location.distanceBetween(lastLocation.latitude,
                                        lastLocation.longitude, point.latitude, point.longitude,
                                        res);
                                acc_distance += res[0];
                                tot_distance += res[0];

                                if (lap != lastLap) {
                                    graphData.clear(tot_distance);
                                } else if (lastTime != 0) {
                                    graphData.addObservation(time - lastTime, res[0], tot_distance,
                                            hr);
                                }
                                lastLap = lap;
                                lastTime = time;

                                if (acc_distance >= formatter.getUnitMeters()) {
                                    cnt_distance++;
                                    acc_distance = 0;
                                    m = new MarkerOptions();
                                    m.position(point);
                                    m.title("" + cnt_distance + " " + formatter.getUnitString());
                                    m.snippet(null);
                                    m.draggable(false);
                                    route.markers.add(m);
                                }
                                lastLocation = point;
                                break;
                        }
                    } while (c.moveToNext());
                    System.err.println("Finished loading " + cnt + " points");
                }
                c.close();
                return route;
            }

            @Override
            protected void onPostExecute(Route route) {

                if (route != null) {
                    graphData.complete(graphView);
                    if (!graphData.HasHRInfo()) {
                        graphTab.addView(graphView);
                    } else {
                        graphTab.addView(graphView,
                                new LayoutParams(
                                        LayoutParams.MATCH_PARENT, 0, 0.5f));

                        graphTab.addView(graphView2,
                                new LayoutParams(
                                        LayoutParams.MATCH_PARENT, 0, 0.5f));
                    }

                    if (graphData.HasHRZHist()) {
                        hrzonesBarLayout.setVisibility(View.VISIBLE);
                        hrzonesBarLayout.addView(hrzonesBar);
                    } else {
                        hrzonesBarLayout.setVisibility(View.GONE);
                    }

                    if (map != null) {
                        map.addPolyline(route.path);
                        mapBounds = route.bounds.build();
                        System.err.println("Added polyline");
                        int cnt = 0;
                        for (MarkerOptions m : route.markers) {
                            cnt++;
                            map.addMarker(m);
                        }
                        System.err.println("Added " + cnt + " markers");

                        route = new Route(); // release mem for old...
                    }
                }
            }
        }.execute("kalle");
    }

    private void shareActivity() {
        final int which[] = {
            -1
        };
        final CharSequence items[] = {
                "gpx", "tcx" /* "nike+xml" */
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.share_activity));
        builder.setPositiveButton(getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int w) {
                        if (which[0] == -1) {
                            dialog.dismiss();
                            return;
                        }

                        final Activity context = DetailActivity.this;
                        final CharSequence fmt = items[which[0]];
                        final Intent intent = new Intent(Intent.ACTION_SEND);

                        intent.setType(WorkoutFileProvider.MIME);
                        Uri uri = Uri.parse("content://" + ActivityProvider.AUTHORITY + "/" + fmt
                                + "/" + mID
                                + "/" + "activity." + fmt);
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                        context.startActivity(Intent.createChooser(intent, "Share workout..."));

                    }
                });
        builder.setNegativeButton(getString(R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing but close the dialog
                        dialog.dismiss();
                    }

                });
        builder.setSingleChoiceItems(items, which[0], new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int w) {
                which[0] = w;
            }
        });
        builder.show();
    }
}
