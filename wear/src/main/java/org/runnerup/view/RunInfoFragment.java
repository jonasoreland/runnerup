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
import android.os.Handler;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.ValueModel;
import org.runnerup.service.StateService;

import java.util.ArrayList;
import java.util.List;


public class RunInfoFragment extends Fragment implements ValueModel.ChangeListener<TrackerState> {

    private final List<Pair<String, TextView>> textViews = new ArrayList<>(3);
    private int screen;
    private int rowsOnScreen;
    private long dataUpdateTime;
    private long headersTimestamp;
    private final Handler handler = new Handler();
    private boolean handlerOutstanding = false;
    private final Runnable periodicTick = new Runnable() {
        @Override
        public void run() {
            update();
            handlerOutstanding = false;
            if (isResumed()) {
                startTimer();
            }
        }
    };
    private MainActivity mainActivity;

    private static final int[] card1ids = new int[]{
            R.id.textView11,
            R.id.textViewHeader11,
    };
    private static final int[] card2ids = new int[]{
            R.id.textView21, R.id.textView22,
            R.id.textViewHeader21, R.id.textViewHeader22
    };
    private static final int[] card3ids = new int[]{
            R.id.textView31, R.id.textView32, R.id.textView33,
            R.id.textViewHeader31, R.id.textViewHeader32, R.id.textViewHeader33
    };

    static public RunInfoFragment createForScreen(int screen, int rowsOnScreen) {
        RunInfoFragment frag = new RunInfoFragment();
        frag.screen = screen;
        frag.rowsOnScreen = rowsOnScreen;
        return frag;
    }

    public RunInfoFragment() {
        super();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        int[] ids = null;
        int card = 0;
        switch (rowsOnScreen) {
            case 3:
                ids = card3ids;
                card = R.layout.card3;
                break;
            case 2:
                ids = card2ids;
                card = R.layout.card2;
                break;
            case 1:
                ids = card1ids;
                card = R.layout.card1;
                break;
        }
        View view = inflater.inflate(card, container, false);
        for (int i = 0; i < rowsOnScreen; i++) {
            textViews.add(new Pair<>(Constants.Wear.RunInfo.DATA +
                    screen + "." + i,
                    (TextView) view.findViewById(ids[i])));
        }
        for (int i = 0; i < rowsOnScreen; i++) {
            textViews.add(new Pair<>(Constants.Wear.RunInfo.HEADER +
                    screen + "." + i,
                    (TextView) view.findViewById(ids[rowsOnScreen + i])));
        }
        return view;
    }

    private void startTimer() {
        if (handlerOutstanding)
            return;
        handlerOutstanding = true;
        handler.postDelayed(periodicTick, 1000);
    }

    private void reset() {
        dataUpdateTime = 0;
        headersTimestamp = 0;
    }

    private void update() {
        Bundle data = mainActivity.getData(dataUpdateTime);
        if (data != null) {
            dataUpdateTime = data.getLong(StateService.UPDATE_TIME);
            update(data);
        }

        Bundle headers = mainActivity.getHeaders(headersTimestamp);
        if (headers != null) {
            headersTimestamp = headers.getLong(StateService.UPDATE_TIME);
            update(headers);
        }
    }

    private void update(Bundle b) {
        for (Pair<String, TextView> tv : textViews) {
            if (b.containsKey(tv.first)) {
                tv.second.setText(b.getString(tv.first));
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startTimer();
        mainActivity.registerTrackerStateListener(this);
        reset();
        update();
        onValueChanged(null, null, mainActivity.getTrackerState());
    }

    @Override
    public void onPause() {
        mainActivity.unregisterTrackerStateListener(this);
        super.onPause();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mainActivity = (MainActivity) activity;
    }

    @Override
    public void onValueChanged(ValueModel<TrackerState> obj,
                               TrackerState oldValue, TrackerState newValue) {
        if (!isAdded())
            return;

        if (newValue == null)
            return;

        if (textViews.size() == 0)
            return;

        if (newValue == TrackerState.PAUSED || newValue == TrackerState.STOPPED) {
            Animation anim = new AlphaAnimation(0, 1);
            anim.setDuration(500);
            anim.setStartOffset(20);
            anim.setRepeatMode(Animation.REVERSE);
            anim.setRepeatCount(Animation.INFINITE);
            textViews.get(0).second.startAnimation(anim);
        } else {
            textViews.get(0).second.clearAnimation();
        }
    }
}
