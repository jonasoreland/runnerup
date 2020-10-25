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
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.DBHelper;
import org.runnerup.util.Formatter;
import org.runnerup.util.SafeParse;
import org.runnerup.widget.SpinnerInterface.OnSetValueListener;
import org.runnerup.widget.TitleSpinner;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

public class ManualActivity extends AppCompatActivity {

    TitleSpinner manualSport = null;
    TitleSpinner manualDate = null;
    TitleSpinner manualTime = null;
    TitleSpinner manualDistance = null;
    TitleSpinner manualDuration = null;
    TitleSpinner manualPace = null;
    EditText manualNotes = null;

    SQLiteDatabase mDB = null;

    Formatter formatter = null;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDB = DBHelper.getWritableDatabase(this);
        formatter = new Formatter(this);

        setContentView(R.layout.manual);

        manualSport = findViewById(R.id.manual_sport);
        manualDate = findViewById(R.id.manual_date);
        manualTime = findViewById(R.id.manual_time);
        manualDistance = findViewById(R.id.manual_distance);
        manualDistance.setOnSetValueListener(onSetManualDistance);
        manualDuration = findViewById(R.id.manual_duration);
        manualDuration.setOnSetValueListener(onSetManualDuration);
        manualPace = findViewById(R.id.manual_pace);
        manualPace.setVisibility(View.GONE);
        manualNotes = findViewById(R.id.manual_notes);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.manual_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_save) {
            saveEntry();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        DBHelper.closeDB(mDB);
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {//todo is this log needed?
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null) {
            if (data.getStringExtra("url") != null)
                Log.e(getClass().getName(), "data.getStringExtra(\"url\") => " + data.getStringExtra("url"));
            if (data.getStringExtra("ex") != null)
                Log.e(getClass().getName(), "data.getStringExtra(\"ex\") => " + data.getStringExtra("ex"));
            if (data.getStringExtra("obj") != null)
                Log.e(getClass().getName(), "data.getStringExtra(\"obj\") => " + data.getStringExtra("obj"));
        }
    }

    void setManualPace(String distance, String duration) {
        Log.e(getClass().getName(), "distance: >" + distance + "< duration: >" + duration + "<");
        double dist = SafeParse.parseDouble(distance, 0); // convert to meters
        long seconds = SafeParse.parseSeconds(duration, 0);
        if (seconds == 0) {
            manualPace.setVisibility(View.GONE);
            return;
        }
        manualPace.setValue(formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, dist/seconds));
        manualPace.setVisibility(View.VISIBLE);
    }

    final OnSetValueListener onSetManualDistance = new OnSetValueListener() {

        @Override
        public String preSetValue(String newValue)
                throws IllegalArgumentException {
            setManualPace(newValue, manualDuration.getValue().toString());
            return newValue;
        }

        @Override
        public int preSetValue(int newValue) throws IllegalArgumentException {
            return newValue;
        }

    };

    final OnSetValueListener onSetManualDuration = new OnSetValueListener() {

        @Override
        public String preSetValue(String newValue)
                throws IllegalArgumentException {
            setManualPace(manualDistance.getValue().toString(), newValue);
            return newValue;
        }

        @Override
        public int preSetValue(int newValue) throws IllegalArgumentException {
            return newValue;
        }
    };

    final void saveEntry() {
        ContentValues save = new ContentValues();
        int sport = manualSport.getValueInt();
        CharSequence date = manualDate.getValue();
        CharSequence time = manualTime.getValue();
        CharSequence distance = manualDistance.getValue();
        CharSequence duration = manualDuration.getValue();
        String notes = manualNotes.getText().toString().trim();
        long start_time = 0;

        if (notes.length() > 0) {
            save.put(DB.ACTIVITY.COMMENT, notes);
        }
        double dist = 0;
        if (distance.length() > 0) {
            dist = Double.parseDouble(distance.toString()); // convert to
            // meters
            save.put(DB.ACTIVITY.DISTANCE, dist);
        }
        long secs = 0;
        if (duration.length() > 0) {
            secs = SafeParse.parseSeconds(duration.toString(), 0);
            save.put(DB.ACTIVITY.TIME, secs);
        }
        if (date.length() > 0) { //todo deal with parse exceptions
            DateFormat df = android.text.format.DateFormat.getDateFormat(ManualActivity.this);
            try {
                Date d = df.parse(date.toString());
                start_time += d.getTime() / 1000;
            } catch (ParseException e) {
            }
        }
        if (time.length() > 0) {
            DateFormat df = android.text.format.DateFormat.getTimeFormat(ManualActivity.this);
            try {
                Date d = df.parse(time.toString());
                // date has no timezine/dst info, must compensate
                Calendar c = Calendar.getInstance();
                c.setTime(d);
                c.add(Calendar.MILLISECOND, c.getTimeZone().getOffset((new Date()).getTime()));
                start_time += c.getTime().getTime() / 1000;
            } catch (ParseException e) {
            }
        }
        save.put(DB.ACTIVITY.START_TIME, start_time);

        save.put(DB.ACTIVITY.SPORT, sport);
        long id = mDB.insert(DB.ACTIVITY.TABLE, null, save);

        ContentValues lap = new ContentValues();
        lap.put(DB.LAP.ACTIVITY, id);
        lap.put(DB.LAP.LAP, 0);
        lap.put(DB.LAP.INTENSITY, DB.INTENSITY.ACTIVE);
        lap.put(DB.LAP.TIME, secs);
        lap.put(DB.LAP.DISTANCE, dist);
        mDB.insert(DB.LAP.TABLE, null, lap);

        finish();
    }
}
