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
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.ActivityCleaner;
import org.runnerup.db.DBHelper;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.util.Formatter;
import org.runnerup.util.SimpleCursorLoader;

import java.util.Calendar;
import java.util.Date;

@TargetApi(Build.VERSION_CODES.FROYO)
public class HistoryActivity extends AppCompatActivity implements Constants, OnItemClickListener,
        LoaderCallbacks<Cursor> {

    SQLiteDatabase mDB = null;
    Formatter formatter = null;

    ListView listView = null;
    CursorAdapter cursorAdapter = null;
    View fab = null;

    /**
     * Called when the activity is first created.
     */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.history);
        listView = (ListView) findViewById(R.id.history_list);
        fab = findViewById(R.id.history_add);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(HistoryActivity.this,
                        ManualActivity.class);
                startActivityForResult(i, 0);
            }
        });

        mDB = DBHelper.getReadableDatabase(this);
        formatter = new Formatter(this);
        listView.setDividerHeight(2);
        listView.setOnItemClickListener(this);
        cursorAdapter = new HistoryListAdapter(this, null);
        listView.setAdapter(cursorAdapter);

        this.getSupportLoaderManager().initLoader(0, null, this);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        new ActivityCleaner().conditionalRecompute(mDB);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getSupportLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        DBHelper.closeDB(mDB);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        String[] from = new String[]{
                "_id", DB.ACTIVITY.START_TIME,
                DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME, DB.ACTIVITY.SPORT
        };

        return new SimpleCursorLoader(this, mDB, DB.ACTIVITY.TABLE, from, "deleted == 0", null,
                DB.ACTIVITY.START_TIME + " desc");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> arg0, Cursor arg1) {
        cursorAdapter.swapCursor(arg1);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
        cursorAdapter.swapCursor(null);
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("ID", id);
        intent.putExtra("mode", "details");
        startActivityForResult(intent, 0);
    }

    @Override
    protected void onActivityResult(int arg0, int arg1, Intent arg2) {
        super.onActivityResult(arg0, arg1, arg2);
        this.getSupportLoaderManager().restartLoader(0, null, this);
    }

    class HistoryListAdapter extends CursorAdapter {
        final LayoutInflater inflater;

        HistoryListAdapter(Context context, Cursor c) {
            super(context, c, true);
            inflater = LayoutInflater.from(context);
        }

        private boolean sameMonthAsPrevious(int curYear, int curMonth, Cursor cursor) {
            int curPosition = cursor.getPosition();
            if (curPosition == 0)
                return false;

            cursor.moveToPrevious();
            long prevTimeInSecs = new ActivityEntity(cursor).getStartTime();

            Calendar prevCal = Calendar.getInstance();
            prevCal.setTime(new Date(prevTimeInSecs * 1000));
            return prevCal.get(Calendar.YEAR) == curYear
                    && prevCal.get(Calendar.MONTH) == curMonth;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ActivityEntity ae = new ActivityEntity(cursor);

            // month + day
            Date curDate = new Date(ae.getStartTime() * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(curDate);

            TextView sectionTitle = view.findViewById(R.id.section_title);
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH);
            if (sameMonthAsPrevious(year, month, cursor)) {
                sectionTitle.setVisibility(View.GONE);
            } else {
                sectionTitle.setVisibility(View.VISIBLE);
                sectionTitle.setText(formatter.formatMonth(curDate));
            }

            TextView dateText = view.findViewById(R.id.history_list_date);
            dateText.setText(formatter.formatDayOfMonth(curDate));

            // distance
            Float d = ae.getDistance();
            TextView distanceText = view.findViewById(R.id.history_list_distance);
            if (d != null) {
                distanceText.setText(formatter.formatDistance(Formatter.Format.TXT_SHORT, d.longValue()));
            } else {
                distanceText.setText("");
            }

            // sport + additional info
            Integer s = ae.getSport();
            ImageView emblem = view.findViewById(R.id.history_list_emblem);
            TextView additionalInfo = view.findViewById(R.id.history_list_additional);

            switch (s) {
                case DB.ACTIVITY.SPORT_RUNNING: {
                    int sportColor = getResources().getColor(R.color.sportRunning);
                    emblem.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.sport_running));
                    distanceText.setTextColor(sportColor);

                    Integer hr = ae.getAvgHr();
                    if (hr != null) {
                        additionalInfo.setTextColor(sportColor);
                        additionalInfo.setText(formatter.formatHeartRate(Formatter.Format.TXT_SHORT, hr));
                    } else {
                        additionalInfo.setText(null);
                    }
                    break;
                }
                case DB.ACTIVITY.SPORT_BIKING: {
                    int sportColor = getResources().getColor(R.color.sportBiking);
                    emblem.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.sport_biking));
                    distanceText.setTextColor(sportColor);
                    additionalInfo.setTextColor(sportColor);
                    Float cad = ae.getAvgCadence();
                    if (cad != null) {
                        additionalInfo.setTextColor(sportColor);
                        additionalInfo.setText(formatter.formatCadence(Formatter.Format.TXT_SHORT, cad));
                    } else {
                        additionalInfo.setText(null);
                    }
                    break;
                }
                case DB.ACTIVITY.SPORT_OTHER: {
                    int sportColor = getResources().getColor(R.color.sportOther);
                    emblem.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.sport_other));
                    distanceText.setTextColor(sportColor);
//                    additionalInfo.setTextColor(sportColor);
                    additionalInfo.setText(null);
                    break;
                }
                case DB.ACTIVITY.SPORT_ORIENTEERING: {
                    int sportColor = getResources().getColor(R.color.sportOrienteering);
                    emblem.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.sport_orienteering));
                    distanceText.setTextColor(sportColor);
                    Integer hr = ae.getAvgHr();
                    if (hr != null) {
                        additionalInfo.setTextColor(sportColor);
                        additionalInfo.setText(formatter.formatHeartRate(Formatter.Format.TXT_SHORT, hr));
                    } else {
                        additionalInfo.setText(null);
                    }
                    break;
                }
                case DB.ACTIVITY.SPORT_WALKING: {
                    int sportColor = getResources().getColor(R.color.sportWalking);
                    emblem.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.sport_walking));
                    distanceText.setTextColor(sportColor);
//                    additionalInfo.setTextColor(sportColor);
                    additionalInfo.setText(null);
                    break;
                }
                default:
                    emblem.setImageResource(0);
            }

            // duration
            Long dur = ae.getTime();
            TextView durationText = view.findViewById(R.id.history_list_duration);
            if (dur != null) {
                durationText.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, dur));
            } else {
                durationText.setText("");
            }

            // pace
            TextView paceText = view.findViewById(R.id.history_list_pace);
            if (d != null && dur != null && d != 0 && dur != 0) {
                paceText.setText(formatter.formatPace(Formatter.Format.TXT_LONG, dur / d));
            } else {
                paceText.setText("");
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return inflater.inflate(R.layout.history_row, parent, false);
        }
    }
}
