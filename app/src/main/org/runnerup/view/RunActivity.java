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

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.view.ViewCompat;

import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.tracker.Tracker;
import org.runnerup.tracker.component.TrackerHRM;
import org.runnerup.util.Formatter;
import org.runnerup.util.TickListener;
import org.runnerup.workout.Intensity;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Step;
import org.runnerup.workout.Workout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;


public class RunActivity extends AppCompatActivity implements TickListener {
    private Workout workout = null;
    private Tracker mTracker = null;
    private final Handler handler = new Handler();

    private Button pauseButton = null;
    private Button newLapButton = null;
    private TextView activityTime = null;
    private TextView activityDistance = null;
    private TextView activityPace = null;
    private TextView lapTime = null;
    private TextView lapDistance = null;
    private TextView lapPace = null;
    private TextView intervalTime = null;
    private TextView intervalDistance = null;
    private TextView intervalPace = null;
    private TextView currentPace = null;
    private TextView countdownView = null;
    private ListView workoutList = null;
    private View tableRowInterval = null;
    private org.runnerup.workout.Step currentStep = null;
    private Formatter formatter = null;
    private TextView activityHr;
    private TextView lapHr;
    private TextView intervalHr;
    private TextView currentHr;
    private TextView activityHeaderHr;
    // A circular buffer for tap events
    private final long[] mTapArray= {0, 0, 0, 0};
    private int mTapIndex = 0;

    class WorkoutRow {
        org.runnerup.workout.Step step = null;
        ContentValues lap = null;
        public int level;
    }

    private final ArrayList<WorkoutRow> workoutRows = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.run);
        formatter = new Formatter(this);
        //HRZones hrZones = new HRZones(this);
        TextView velocity = findViewById(R.id.velocity_label);
        velocity.setText(formatter.formatVelocityLabel());

        final Button stopButton = findViewById(R.id.stop_button);
        stopButton.setOnClickListener(stopButtonClick);
        pauseButton = findViewById(R.id.pause_button);
        pauseButton.setOnClickListener(pauseButtonClick);
        newLapButton = findViewById(R.id.new_lap_button);
        activityHeaderHr = findViewById(R.id.activity_header_hr);
        activityTime = findViewById(R.id.run_activity_time);
        activityDistance = findViewById(R.id.run_activity_distance);
        activityPace = findViewById(R.id.run_activity_pace);
        activityHr = findViewById(R.id.activity_hr);
        lapTime = findViewById(R.id.lap_time);
        lapDistance = findViewById(R.id.lap_distance);
        lapPace = findViewById(R.id.lap_pace);
        lapHr = findViewById(R.id.lap_hr);
        intervalTime = findViewById(R.id.run_interval_time);
        intervalDistance = findViewById(R.id.intervall_distance);
        tableRowInterval = findViewById(R.id.table_row_interval);
        intervalPace = findViewById(R.id.interval_pace);
        intervalHr = findViewById(R.id.interval_hr);
        currentPace = findViewById(R.id.current_pace);
        currentHr = findViewById(R.id.current_hr);
        countdownView = findViewById(R.id.countdown_text_view);
        workoutList = findViewById(R.id.workout_list);
        WorkoutAdapter adapter = new WorkoutAdapter(workoutRows);
        workoutList.setAdapter(adapter);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final Resources res = this.getResources();
        final boolean active = prefs.getBoolean(res.getString(R.string.pref_lock_run), false);

        TableLayout t = findViewById(R.id.table_layout1);
        t.setOnTouchListener((v, event) -> {
            // Detect tapping on the header
            int action = event.getAction();
            if (active && action == MotionEvent.ACTION_DOWN) {
                final int maxTapTime = 1000;
                long time = event.getEventTime();
                if (mTapArray[mTapIndex] != 0 && time - mTapArray[mTapIndex] < maxTapTime) {
                    boolean enabled = !pauseButton.isEnabled();
                    pauseButton.setEnabled(enabled);
                    stopButton.setEnabled(enabled);
                    Arrays.fill(mTapArray, 0);
                } else {
                    if (mTapIndex == 0) {
                        Toast.makeText(getApplicationContext(), res.getString(R.string.Lock_activity_buttons_message), Toast.LENGTH_SHORT).show();
                    }
                    mTapArray[mTapIndex] = time;
                    mTapIndex = (mTapIndex + 1) % mTapArray.length;
                }
            }
            return false;
        });
        bindGpsTracker();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.e(getClass().getName(), "onConfigurationChange => do NOTHING!!");
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindGpsTracker();
        stopTimer();

    }

    private void onGpsTrackerBound() {
        if (mTracker == null) {
            // should not happen
            return;
        }

        workout = mTracker.getWorkout();
        if (workout == null) {
            // should not happen
            return;
        }

        {
            /*
             * Countdown view can't be bound until RunActivity is started
             *   since it's not created until then
             */
            HashMap<String, Object> bindValues = new HashMap<>();
            bindValues.put(Workout.KEY_COUNTER_VIEW, countdownView);
            workout.onBind(workout, bindValues);
        }

        startTimer();

        populateWorkoutList();
        newLapButton.setOnClickListener(newLapButtonClick);
        newLapButton.setText(R.string.Lap);
        mTracker.displayNotificationState();
    }

    private void populateWorkoutList() {
        List<Workout.StepListEntry> list = workout.getStepList();
        for (Workout.StepListEntry aList : list) {
            WorkoutRow row = new WorkoutRow();
            row.level = aList.level;
            row.step = aList.step;
            row.lap = null;
            workoutRows.add(row);
        }
    }

    private Timer timer = null;

    private void startTimer() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                RunActivity.this.handler.post(RunActivity.this::onTick);
            }
        }, 0, 500);
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    private Location l = null;

    public void onTick() {
        if (workout != null) {
            workout.onTick();
            updateView();

            if (mTracker != null) {
                Location l2 = mTracker.getLastKnownLocation();
                if (l2 != null && !l2.equals(l)) {
                    l = l2;
                }
            }
        }
    }

    private void doStop() {
        if (timer != null) {
            workout.onStop(workout);
            stopTimer(); // set timer=null;
            mTracker.stopForeground(true); // remove notification
            Intent intent = new Intent(RunActivity.this, DetailActivity.class);
            /*
             * The same activity is used to show details and to save
             * activity they show almost the same information
             */
            intent.putExtra("mode", "save");
            intent.putExtra("ID", mTracker.getActivityId());
            RunActivity.this.startActivityForResult(intent, workout.isPaused() ? 1 : 0);
        }
    }

    private final OnClickListener stopButtonClick = v -> doStop();

    @Override
    public void onBackPressed() {
        //boolean ignore_back = true; // atleast magnus belives that this is better...
        //if (!ignore_back) {
        //    stopButtonClick.onClick(stopButton);
        //}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (workout == null) {
            // "should not happen"
            finish();
            return;
        }
        if (resultCode == AppCompatActivity.RESULT_OK) {
            /*
             * they saved
             */
            workout.onComplete(Scope.ACTIVITY, workout);
            workout.onSave();
            mTracker = null;
            finish();
        } else if (resultCode == AppCompatActivity.RESULT_CANCELED) {
            /*
             * they discarded
             */
            workout.onComplete(Scope.ACTIVITY, workout);
            workout.onDiscard();
            mTracker = null;
            finish();
        } else if (resultCode == AppCompatActivity.RESULT_FIRST_USER) {
            startTimer();
            if (requestCode == 0) {
                workout.onResume(workout);
                //else: we were paused before stopButtonClick...don't resume
            }
        } else {
            if (BuildConfig.DEBUG) { throw new AssertionError(); }
        }
    }

    private final OnClickListener pauseButtonClick = v -> {
        if (workout == null) {
            // "should not happen"
            return;
        }

        if (workout.isPaused()) {
            workout.onResume(workout);
        } else {
            workout.onPause(workout);
        }
        setPauseButtonEnabled(!workout.isPaused());
    };

    private void setPauseButtonEnabled(boolean enabled) {
        if (enabled) {
            pauseButton.setText(R.string.Pause);
            ViewCompat.setBackground(pauseButton, AppCompatResources.getDrawable(this, R.drawable.btn_blue));
            pauseButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_av_pause, 0);
        } else {
            pauseButton.setText(R.string.Resume);
            ViewCompat.setBackground(pauseButton, AppCompatResources.getDrawable(this, R.drawable.btn_green));
            pauseButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_av_play_arrow, 0);
        }
    }

    private final OnClickListener newLapButtonClick = v -> workout.onNewLapOrNextStep();

    private void updateView() {
        if (mTracker.getState() == TrackerState.STOPPED){
            doStop();
        } else {
            if (workout == null) {
                // "should not happen"
                return;
            }

            setPauseButtonEnabled(!workout.isPaused());
            double ad = workout.getDistance(Scope.ACTIVITY);
            double at = workout.getTime(Scope.ACTIVITY);
            double ap = workout.getSpeed(Scope.ACTIVITY);
            activityTime.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(at)));
            activityDistance.setText(formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(ad)));
            activityPace.setText(formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, ap));

            double ld = workout.getDistance(Scope.LAP);
            double lt = workout.getTime(Scope.LAP);
            double lp = workout.getSpeed(Scope.LAP);
            lapTime.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(lt)));
            lapDistance.setText(formatter.formatDistance(Formatter.Format.TXT_LONG, Math.round(ld)));
            lapPace.setText(formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, lp));

            if (tableRowInterval != null && this.currentStep != null &&
                    workout.getWorkoutType() != Constants.WORKOUT_TYPE.BASIC &&
                    this.currentStep.getIntensity() == Intensity.ACTIVE) {
                double id = workout.getDistance(Scope.STEP);
                double it = workout.getTime(Scope.STEP);
                double ip = workout.getSpeed(Scope.STEP);

                tableRowInterval.setVisibility(View.VISIBLE);
                intervalTime.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(it)));
                intervalDistance.setText(formatter.formatDistance(Formatter.Format.TXT_LONG, Math.round(id)));
                intervalPace.setText(formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, ip));
            } else {
                // Do not show Interval Step row if no reason
                tableRowInterval.setVisibility(View.GONE);
            }

            double cp = workout.getSpeed(Scope.CURRENT);
            currentPace.setText(formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, cp));

            if (mTracker.isComponentConnected(TrackerHRM.NAME)) {
                double ahr = workout.getHeartRate(Scope.ACTIVITY);
                double ihr = workout.getHeartRate(Scope.STEP);
                double lhr = workout.getHeartRate(Scope.LAP);
                double chr = workout.getHeartRate(Scope.CURRENT);
                lapHr.setText(formatter.formatHeartRate(Formatter.Format.TXT_SHORT, lhr));
                intervalHr.setText(formatter.formatHeartRate(Formatter.Format.TXT_SHORT, ihr));
                currentHr.setText(formatter.formatHeartRate(Formatter.Format.TXT_SHORT, chr));
                activityHr.setText(formatter.formatHeartRate(Formatter.Format.TXT_SHORT, ahr));
                activityHr.setVisibility(View.VISIBLE);
                lapHr.setVisibility(View.VISIBLE);
                intervalHr.setVisibility(View.VISIBLE);
                currentHr.setVisibility(View.VISIBLE);
                activityHeaderHr.setVisibility(View.VISIBLE);
            } else {
                activityHr.setVisibility(View.GONE);
                lapHr.setVisibility(View.GONE);
                intervalHr.setVisibility(View.GONE);
                currentHr.setVisibility(View.GONE);
                activityHeaderHr.setVisibility(View.GONE);
            }

            Step curr = workout.getCurrentStep();
            if (curr != currentStep) {
                ((WorkoutAdapter) workoutList.getAdapter()).notifyDataSetChanged();
                currentStep = curr;
                workoutList.setSelection(getPosition(workoutRows, currentStep));
            }
        }
    }

    private int getPosition(ArrayList<WorkoutRow> workoutRows,
            org.runnerup.workout.Step currentActivity) {
        for (int i = 0; i < workoutRows.size(); i++) {
            if (workoutRows.get(i).step == currentActivity)
                return i;
        }
        return 0;
    }

    private boolean mIsBound = false;

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            if (mTracker == null) {
                mTracker = ((Tracker.LocalBinder) service).getService();
                // Tell the user about this for our demo.
                RunActivity.this.onGpsTrackerBound();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mIsBound = false;
            mTracker = null;
        }
    };

    private void bindGpsTracker() {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        mIsBound = getApplicationContext().bindService(new Intent(this, Tracker.class),
                mConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindGpsTracker() {
        if (mIsBound) {
            // Detach our existing connection.
            getApplicationContext().unbindService(mConnection);
            mIsBound = false;
        }
    }

    class WorkoutAdapter extends BaseAdapter {

        final ArrayList<WorkoutRow> rows;

        WorkoutAdapter(ArrayList<WorkoutRow> workoutRows) {
            this.rows = workoutRows;
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            WorkoutRow tmp = rows.get(position);
            if (tmp.step != null)
            {
                return getWorkoutRow(tmp.step, tmp.level, convertView, parent);
            }
            else
            {
                return getLapRow(tmp.lap, convertView, parent);
            }
        }

        private View getWorkoutRow(org.runnerup.workout.Step step, int level, View convertView,
                ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(RunActivity.this);
            View view = inflater.inflate(R.layout.workout_row, parent, false);
            TextView intensity = view.findViewById(R.id.workout_step_intensity);
            TextView durationType = view.findViewById(R.id.workout_step_duration_type);
            TextView durationValue = view.findViewById(R.id.workout_step_duration_value);
            TextView targetPace = view.findViewById(R.id.workout_step_pace);
            intensity.setPadding(level * 10, 0, 0, 0);
            intensity.setText(getResources().getText(step.getIntensity().getTextId()));
            if (step.getDurationType() != null) {
                durationType.setText(getResources().getText(step.getDurationType().getTextId()));
                durationValue.setText(formatter.format(Formatter.Format.TXT_LONG, step.getDurationType(),
                        step.getDurationValue()));
            } else {
                durationType.setText("");
                durationValue.setText("");
            }
            if (currentStep == step) {
                //view.setBackgroundResource(android.R.color.background_light);
            } else {
                view.setBackgroundResource(android.R.color.black);
            }

            if (step.getTargetType() == null) {
                targetPace.setText("");
            } else {
                double minValue = step.getTargetValue().minValue;
                double maxValue = step.getTargetValue().maxValue;
                if (minValue == maxValue) {
                    targetPace.setText(formatter.format(Formatter.Format.TXT_SHORT, step.getTargetType(),
                            minValue));
                } else {
                    targetPace.setText(String.format(Locale.getDefault(), "%s-%s",
                            formatter.format(Formatter.Format.TXT_SHORT, step.getTargetType(), minValue),
                            formatter.format(Formatter.Format.TXT_SHORT, step.getTargetType(), maxValue)));
                }
            }
            if (step.getIntensity() == Intensity.REPEAT){
                if (step.getCurrentRepeat() >= step.getRepeatCount()) {
                    durationValue.setText(R.string.Finished);
                } else {
                    durationValue.setText(String.format(Locale.getDefault(), "%d/%d",
                            (step.getCurrentRepeat() + 1), step.getRepeatCount()));
                }
            }
            return view;
        }

        private View getLapRow(ContentValues tmp, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(RunActivity.this);
            return inflater.inflate(R.layout.laplist_row, parent, false);
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }
    }
}
