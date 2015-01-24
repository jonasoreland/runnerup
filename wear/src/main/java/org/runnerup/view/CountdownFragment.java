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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.service.StateService;

import java.util.ArrayList;
import java.util.List;

@TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
public class CountdownFragment extends Fragment {

    long dataUpdateTime;
    List<Pair<String, TextView>> textViews = new ArrayList<Pair<String, TextView>>(3);
    Handler handler = new Handler();
    boolean handlerOutstanding = false;
    Runnable periodicTick = new Runnable() {
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

    public CountdownFragment() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.countdown, container, false);
        textViews.add(new Pair<String, TextView>(Constants.Wear.RunInfo.COUNTDOWN,
                (TextView) view.findViewById(R.id.countdown_txt)));
        return view;
    }

    void startTimer() {
        if (handlerOutstanding)
            return;
        handlerOutstanding = true;
        handler.postDelayed(periodicTick, 1000);
    }

    private void reset() {
        dataUpdateTime = 0;
    }

    private void update() {
        Bundle data = mainActivity.getData(dataUpdateTime);
        if (data != null) {
            dataUpdateTime = data.getLong(StateService.UPDATE_TIME);
            update(data);
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
        reset();
        update();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mainActivity = (MainActivity) activity;
    }
}
