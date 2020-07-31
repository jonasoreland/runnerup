/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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
import android.support.wearable.view.DelayedConfirmationView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.ValueModel;

/**
 * @todo make this fragment contact phone and start app
 */

public class ConnectToPhoneFragment extends Fragment implements ValueModel.ChangeListener<TrackerState> {

    private DelayedConfirmationView mButton;
    private MainActivity activity;
    public ConnectToPhoneFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.connect_to_phone, container, false);
        super.onViewCreated(view, savedInstanceState);

        mButton = view.findViewById(R.id.icon_open_on_phone);
        //TextView txt = (TextView) view.findViewById(R.id.txt_open_on_phone);

        mButton.setListener(mListener);

        return view;
    }

    private void updateView(TrackerState state) {
        if (state == null) {
            mButton.setStartTimeMs(0);
            mButton.setTotalTimeMs(5000);
            mButton.start();
        }
    }

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

    private final DelayedConfirmationView.DelayedConfirmationListener mListener = new DelayedConfirmationView.DelayedConfirmationListener() {
        @Override
        public void onTimerFinished(View view) {
            updateView(activity.getTrackerState());
        }

        @Override
        public void onTimerSelected(View view) {

        }
    };
}
