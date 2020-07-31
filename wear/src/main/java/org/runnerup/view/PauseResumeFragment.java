/*
 * Copyright (C) 2014 weides@gmail.com
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

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.support.wearable.view.CircledImageView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.ValueModel;


public class PauseResumeFragment extends Fragment implements ValueModel.ChangeListener<TrackerState> {

    private static final long SCROLL_DELAY = 1500; // 1.5s
    private final Handler handler = new Handler();
    private TextView mButtonPauseResumeTxt;
    private CircledImageView mButtonPauseResume;
    private CircledImageView mButtonNewLap;
    private MainActivity activity;
    private long clickCount = 0;

    public PauseResumeFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.newlap_pause_resume, container, false);
        super.onViewCreated(view, savedInstanceState);

        mButtonPauseResume = view.findViewById(R.id.icon_resume);
        mButtonPauseResume.setOnClickListener(pauseButtonClick);
        mButtonPauseResumeTxt = view.findViewById(R.id.txt_resume);
        mButtonNewLap = view.findViewById(R.id.icon_newlap);
        mButtonNewLap.setOnClickListener(newLapButtonClick);
        //TextView buttonNewLapTxt = (TextView) view.findViewById(R.id.txt_newlap);

        return view;
    }

    private void updateView(TrackerState state) {
        if (state != null) {
            switch (state) {
                case INIT:
                case INITIALIZING:
                case INITIALIZED:
                case CLEANUP:
                case ERROR:
                case CONNECTING:
                case CONNECTED:
                    break;
                case STARTED:
                    mButtonNewLap.setEnabled(true);
                    mButtonPauseResume.setEnabled(true);
                    mButtonPauseResume.setImageResource(R.drawable.ic_av_pause);
                    mButtonPauseResumeTxt.setText(getText(R.string.Pause));
                    return;
                case PAUSED:
                    mButtonNewLap.setEnabled(true);
                    mButtonPauseResume.setEnabled(true);
                    mButtonPauseResume.setImageResource(R.drawable.ic_av_play_arrow);
                    mButtonPauseResumeTxt.setText(getText(R.string.Resume));
                    return;
            }
        }
        mButtonNewLap.setEnabled(false);
        mButtonPauseResume.setEnabled(false);
    }

    private final View.OnClickListener pauseButtonClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            clickCount++;
            activity.getStateService().sendPauseResume();
            TrackerState state = activity.getTrackerState();
            if (state == TrackerState.STARTED)
                updateView(TrackerState.PAUSED);
            else if (state == TrackerState.PAUSED) {
                updateView(TrackerState.STARTED);
                // When resuming...scroll to RunInfo screen in 1.5 seconds
                final long saveClickCount = clickCount;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (saveClickCount != clickCount)
                            return;
                        PauseResumeFragment.this.activity.scrollToRunInfo();
                    }
                }, SCROLL_DELAY);
            }
        }
    };

    private final View.OnClickListener newLapButtonClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            clickCount++;
            activity.getStateService().sendNewLap();
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        activity.registerTrackerStateListener(this);
        updateView(this.activity.getTrackerState());
    }

    @Override
    public void onPause() {
        activity.unregisterTrackerStateListener(this);
        super.onPause();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = (MainActivity) activity;
    }

    @Override
    public void onValueChanged(ValueModel<TrackerState> obj,
                               TrackerState oldState, TrackerState newState) {
        if (isAdded())
            updateView(newState);
    }
}
