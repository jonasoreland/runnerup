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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.wearable.view.DotsPageIndicator;
import android.support.wearable.view.FragmentGridPagerAdapter;
import android.support.wearable.view.GridViewPager;
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
public class MainActivity extends Activity
        implements Constants {

    private FragmentGridPagerAdapter pageAdapter;
    private Bundle data;
    private long dataTimestamp;
    private StateService mStateService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final GridViewPager pager = (GridViewPager) findViewById(R.id.pager);
        pageAdapter = createPager(getFragmentManager());
        pager.setAdapter(pageAdapter);

        DotsPageIndicator dotsPageIndicator = (DotsPageIndicator) findViewById(R.id.page_indicator);
        dotsPageIndicator.setPager(pager);
        dotsPageIndicator.setDotFadeWhenIdle(false);
        dotsPageIndicator.setDotFadeOutDelay(1000 * 3600 * 24);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getApplicationContext().bindService(new Intent(this, StateService.class),
                mStateServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onResume();
        getApplicationContext().unbindService(mStateServiceConnection);
        mStateService = null;
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    FragmentGridPagerAdapter createPager(FragmentManager fm) {
        return new FragmentGridPagerAdapter(fm) {

            Fragment fragments[][] = new Fragment[1][2];

            @Override
            public Fragment getFragment(int row, int col) {
                if (fragments[col][row] == null) {
                    switch (row) {
                        case 0:
                            fragments[col][row] = new RunInformationCardFragment();
                            break;
                        default:
                        case 1:
                            fragments[col][row] = new PauseResumeFragment();
                            break;
                    }
                }
                return fragments[col][row];
            }

            @Override
            public int getRowCount() {
                return 2;
            }

            @Override
            public int getColumnCount(int i) {
                return 1;
            }
        };
    }

    private Bundle getData(long lastUpdateTime) {
        if (mStateService == null) {
            return null;
        }
        return mStateService.getData(lastUpdateTime);
    }

    public static class RunInformationCardFragment extends Fragment {

        List<Pair<String, TextView>> textViews = new ArrayList<Pair<String, TextView>>(3);
        long lastUpdateTime;
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

        public RunInformationCardFragment() {
            super();
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.card3, container, false);
            textViews.add(new Pair<String, TextView>(Wear.RunInfo.DATA + "1",
                    (TextView) view.findViewById(R.id.textView1)));
            textViews.add(new Pair<String, TextView>(Wear.RunInfo.DATA + "2",
                    (TextView) view.findViewById(R.id.textView2)));
            textViews.add(new Pair<String, TextView>(Wear.RunInfo.DATA + "3",
                    (TextView) view.findViewById(R.id.textView3)));

            textViews.add(new Pair<String, TextView>(Wear.RunInfo.HEADER + "1",
                    (TextView) view.findViewById(R.id.textViewHeader1)));
            textViews.add(new Pair<String, TextView>(Wear.RunInfo.HEADER + "2",
                    (TextView) view.findViewById(R.id.textViewHeader2)));
            textViews.add(new Pair<String, TextView>(Wear.RunInfo.HEADER + "3",
                    (TextView) view.findViewById(R.id.textViewHeader3)));
            return view;
        }

        void startTimer() {
            if (handlerOutstanding)
                return;
            handlerOutstanding = true;
            handler.postDelayed(periodicTick, 1000);
        }

        private void update() {
            Bundle data = mainActivity.getData(lastUpdateTime);
            if (data == null)
                return;

            lastUpdateTime = data.getLong(StateService.UPDATE_TIME);
            update(data);
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

    private ServiceConnection mStateServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (mStateService == null) {
                mStateService = ((StateService.LocalBinder) service).getService();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mStateService = null;
        }
    };
}
