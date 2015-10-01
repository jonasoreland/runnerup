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
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.db.ActivityCleaner;
import org.runnerup.db.DBHelper;
import org.runnerup.common.util.Constants;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.util.Formatter;
import org.runnerup.util.SimpleCursorLoader;
import org.runnerup.workout.Sport;

@TargetApi(Build.VERSION_CODES.FROYO)
public class HistoryActivity extends FragmentActivity implements Constants, OnItemClickListener,
        LoaderCallbacks<Cursor> {

    DBHelper mDBHelper = null;
    SQLiteDatabase mDB = null;
    Formatter formatter = null;

    ListView listView = null;
    CursorAdapter cursorAdapter = null;

    /** Called when the activity is first created. */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.history);
        listView = (ListView) findViewById(R.id.history_list);

        mDBHelper = new DBHelper(this);
        mDB = mDBHelper.getReadableDatabase();
        formatter = new Formatter(this);
        listView.setDividerHeight(2);
        listView.setOnItemClickListener(this);
        cursorAdapter = new HistoryListAdapter(this, null);
        listView.setAdapter(cursorAdapter);

        this.getSupportLoaderManager().initLoader(0, null, this);

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
        mDB.close();
        mDBHelper.close();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        String[] from = new String[] {
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

        public HistoryListAdapter(Context context, Cursor c) {
            super(context, c, true);
            inflater = LayoutInflater.from(context);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ActivityEntity ae = new ActivityEntity(cursor);
            int[] to = new int[] {
                    R.id.history_list_id,
                    R.id.history_list_start_time, R.id.history_list_distance,
                    R.id.history_list_time, R.id.history_list_pace, R.id.history_list_sport
            };

            Long id = ae.getId();
            Long st = ae.getStartTime();
            Float d = ae.getDistance();
            Long t = ae.getTime();
            Integer s = ae.getSport();

            {
                TextView tv = (TextView) view.findViewById(to[0]);
                tv.setText(Long.toString(id));
            }

            {
                TextView tv = (TextView) view.findViewById(to[1]);
                if (st != null) {
                    tv.setText(formatter.formatDateTime(Formatter.TXT_LONG, st));
                } else {
                    tv.setText("");
                }
            }

            {
                TextView tv = (TextView) view.findViewById(to[2]);
                if (d != null) {
                    tv.setText(formatter.formatDistance(Formatter.TXT_SHORT, d.longValue()));
                } else {
                    tv.setText("");
                }
            }

            {
                TextView tv = (TextView) view.findViewById(to[3]);
                if (t != null) {
                    tv.setText(formatter.formatElapsedTime(Formatter.TXT_SHORT, t));
                } else {
                    tv.setText("");
                }
            }

            {
                TextView tv = (TextView) view.findViewById(to[4]);
                if (d != null && t != null && d != 0 && t != 0) {
                    tv.setText(formatter.formatPace(Formatter.TXT_LONG, t / d));
                } else {
                    tv.setText("");
                }
            }

            {
                TextView tv = (TextView) view.findViewById(to[5]);

                if (s != null) {
                    tv.setText(Sport.textOf(getResources(), s));
                } else {
                    tv.setText(Sport.textOf(getResources(), DB.ACTIVITY.SPORT_RUNNING));
                }
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return inflater.inflate(R.layout.history_row, parent, false);
        }
    }
}
