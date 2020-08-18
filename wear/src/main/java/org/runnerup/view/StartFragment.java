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
import android.support.wearable.view.CircledImageView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.ValueModel;


public class StartFragment extends Fragment implements ValueModel.ChangeListener<TrackerState> {

    private TextView mTxt;
    private MainActivity activity;

    public StartFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.start, container, false);
        super.onViewCreated(view, savedInstanceState);

        CircledImageView button = view.findViewById(R.id.icon_start);
        button.setOnClickListener(startButtonClick);
        mTxt = view.findViewById(R.id.txt_start);

        return view;
    }

    private void updateView(TrackerState state) {
        if (state == null)
            return;
        switch (state) {
            case INIT:
            case INITIALIZING:
            case INITIALIZED:
                mTxt.setText(R.string.Start_GPS);
                break;
            case CONNECTING:
                break;
            case CONNECTED:
                mTxt.setText(R.string.Start_activity);
                break;
            case STARTED:
                break;
            case PAUSED:
                break;
            case CLEANUP:
                break;
            case ERROR:
                break;
        }
    }

    private final View.OnClickListener startButtonClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            activity.getStateService().sendStart();
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
                               TrackerState oldValue, TrackerState newValue) {
        if (isAdded())
            updateView(newValue);
    }
}
