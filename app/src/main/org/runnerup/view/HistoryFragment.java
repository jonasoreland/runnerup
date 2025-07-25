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

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;
import java.util.Calendar;
import java.util.Date;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.ActivityCleaner;
import org.runnerup.db.DBHelper;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.util.Formatter;
import org.runnerup.util.SimpleCursorLoader;
import org.runnerup.workout.Sport;

public class HistoryFragment extends Fragment
    implements Constants, OnItemClickListener, LoaderCallbacks<Cursor> {

  private SQLiteDatabase mDB = null;
  private Formatter formatter = null;

  CursorAdapter cursorAdapter = null;
  View fab = null;

  public HistoryFragment() {
    super(R.layout.history);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    ListView listView = view.findViewById(R.id.history_list);
    fab = view.findViewById(R.id.history_add);

    Context context = requireContext();
    fab.setOnClickListener(
        v -> {
          Intent i = new Intent(context, ManualActivity.class);
          // TODO: Use the Activity Result API
          startActivityForResult(i, 0);
        });

    mDB = DBHelper.getReadableDatabase(context);
    formatter = new Formatter(context);
    listView.setDividerHeight(2);
    listView.setOnItemClickListener(this);
    cursorAdapter = new HistoryListAdapter(context, null);
    listView.setAdapter(cursorAdapter);

    LoaderManager.getInstance(this).initLoader(0, null, this);
    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

    new ActivityCleaner().conditionalRecompute(mDB);
  }

  @Override
  public void onResume() {
    super.onResume();
    LoaderManager.getInstance(this).restartLoader(0, null, this);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    DBHelper.closeDB(mDB);
  }

  @NonNull
  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    String[] from =
        new String[] {
          "_id", DB.ACTIVITY.START_TIME, DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME, DB.ACTIVITY.SPORT
        };

    return new SimpleCursorLoader(
        requireContext(),
        mDB,
        DB.ACTIVITY.TABLE,
        from,
        "deleted == 0",
        null,
        DB.ACTIVITY.START_TIME + " desc");
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Cursor> arg0, Cursor arg1) {
    cursorAdapter.swapCursor(arg1);
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Cursor> arg0) {
    cursorAdapter.swapCursor(null);
  }

  @Override
  public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
    Intent intent = new Intent(requireContext(), DetailActivity.class);
    intent.putExtra("ID", id);
    intent.putExtra("mode", "details");
    startActivityForResult(intent, 0);
  }

  // TODO: Use Activity Result API
  @Override
  public void onActivityResult(int arg0, int arg1, Intent arg2) {
    super.onActivityResult(arg0, arg1, arg2);
    LoaderManager.getInstance(this).restartLoader(0, null, this);
  }

  class HistoryListAdapter extends CursorAdapter {
    final LayoutInflater inflater;

    HistoryListAdapter(Context context, Cursor c) {
      super(context, c, true);
      inflater = LayoutInflater.from(context);
    }

    private boolean sameMonthAsPrevious(int curYear, int curMonth, Cursor cursor) {
      int curPosition = cursor.getPosition();
      if (curPosition == 0) return false;

      cursor.moveToPrevious();
      long prevTimeInSecs = new ActivityEntity(cursor).getStartTime();

      Calendar prevCal = Calendar.getInstance();
      prevCal.setTime(new Date(prevTimeInSecs * 1000));
      return prevCal.get(Calendar.YEAR) == curYear && prevCal.get(Calendar.MONTH) == curMonth;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      ActivityEntity ae = new ActivityEntity(cursor);

      // month + day
      Date curDate = new Date(ae.getStartTime() * 1000);
      Calendar cal = Calendar.getInstance();
      cal.setTime(curDate);

      TextView sectionTitle = view.findViewById(R.id.history_section_title);
      int year = cal.get(Calendar.YEAR);
      int month = cal.get(Calendar.MONTH);
      if (sameMonthAsPrevious(year, month, cursor)) {
        sectionTitle.setVisibility(View.GONE);
      } else {
        sectionTitle.setVisibility(View.VISIBLE);
        sectionTitle.setText(formatter.formatMonth(curDate));
      }

      TextView dateText = view.findViewById(R.id.history_list_date);
      dateText.setText(formatter.formatDateTime(ae.getStartTime()));

      // distance
      Double d = ae.getDistance();
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

      int sportColor = ContextCompat.getColor(context, Sport.colorOf(s));
      Drawable sportDrawable =
          AppCompatResources.getDrawable(context, Sport.drawableColored16Of(s));
      emblem.setImageDrawable(sportDrawable);
      distanceText.setTextColor(sportColor);
      additionalInfo.setTextColor(sportColor);
      Integer hr = ae.getAvgHr();
      if (hr != null) {
        additionalInfo.setText(formatter.formatHeartRate(Formatter.Format.TXT_SHORT, hr));
      } else {
        additionalInfo.setText(null);
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
      String paceTextContents = "";
      if (d != null && dur != null && dur != 0) {
        paceTextContents =
            formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_LONG, d / dur);
      }
      paceText.setText(paceTextContents);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      return inflater.inflate(R.layout.history_row, parent, false);
    }
  }
}
