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
import android.graphics.Point;
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
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.ValueModel;
import org.runnerup.service.StateService;
import org.runnerup.widget.MyDotsPageIndicator;

import java.util.ArrayList;
import java.util.List;

@TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
public class MainActivity extends Activity
        implements Constants, ValueModel.ChangeListener<TrackerState> {

    private GridViewPager pager;
    private StateService mStateService;
    final private ValueModel<TrackerState> trackerState = new ValueModel<TrackerState>();

    private static final int RUN_INFO_ROW = 0;
    private static final int PAUSE_RESUME_ROW = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pager = (GridViewPager) findViewById(R.id.pager);
        FragmentGridPagerAdapter pageAdapter = createPager(getFragmentManager());
        pager.setAdapter(pageAdapter);

        LinearLayout verticalDotsPageIndicator = (LinearLayout) findViewById(R.id.vert_page_indicator);
        MyDotsPageIndicator dot2 = new MyDotsPageIndicator(verticalDotsPageIndicator);

        DotsPageIndicator dotsPageIndicator = (DotsPageIndicator) findViewById(R.id.page_indicator);
        dotsPageIndicator.setPager(pager);
        dotsPageIndicator.setDotFadeWhenIdle(false);
        dotsPageIndicator.setDotFadeOutDelay(1000 * 3600 * 24);
        dotsPageIndicator.setOnPageChangeListener(dot2);
        dotsPageIndicator.setOnAdapterChangeListener(dot2);
        dot2.setPager(pager);
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
        if (mStateService != null) {
            mStateService.unregisterTrackerStateListener(this);
        }
        getApplicationContext().unbindService(mStateServiceConnection);
        mStateService = null;
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private class PagerAdapter extends  FragmentGridPagerAdapter implements ValueModel.ChangeListener<TrackerState> {
        int rows = 1;
        int cols = 1;
        Fragment fragments[][] = new Fragment[1][2];

        public PagerAdapter(FragmentManager fm) {
            super(fm);
            trackerState.registerChangeListener(this);
            update(trackerState.get());
        }

        @Override
        public Fragment getFragment(int row, int col) {
            if (fragments[col][row] == null) {
                switch (row) {
                    case RUN_INFO_ROW:
                        fragments[col][row] = new RunInformationCardFragment();
                        break;
                    default:
                    case PAUSE_RESUME_ROW:
                        fragments[col][row] = new PauseResumeFragment();
                        break;
                }
            }
            return fragments[col][row];
        }

        @Override
        public int getRowCount() {
            return rows;
        }

        @Override
        public int getColumnCount(int i) {
            return cols;
        }

        @Override
        public void onValueChanged(TrackerState oldValue, TrackerState newValue) {
            if ((TrackerState.equals(TrackerState.PAUSED, oldValue) &&
                 TrackerState.equals(TrackerState.STARTED, newValue)) ||
                (TrackerState.equals(TrackerState.PAUSED, newValue) &&
                            TrackerState.equals(TrackerState.STARTED, oldValue))) {
                /* no need to various updates */
                return;
            }

            update(newValue);
            notifyDataSetChanged();
        }

        private void update(TrackerState newValue) {
            if (newValue != null) {
                switch (newValue) {
                    case INIT:
                    case INITIALIZING:
                    case CLEANUP:
                    case ERROR:
                        /* handle same way is newValue == null */
                        break;
                    case INITIALIZED:
                        rows = 1;
                        cols = 1;
                        fragments[0][0] = new StartFragment();
                        return;
                    case CONNECTING:
                        rows = 1;
                        cols = 1;
                        fragments[0][0] = new SearchingFragment();
                        return;
                    case CONNECTED:
                        rows = 1;
                        cols = 1;
                        fragments[0][0] = new StartFragment();
                        return;
                    case STARTED:
                    case PAUSED:
                        fragments[0][0] = null;
                        rows = 2;
                        cols = 1;
                        return;
                    case STOPPED:
                        fragments[0][1] = new StoppedFragment();
                        rows = 2;
                        cols = 1;
                        return;
                }
            }
            rows = 1;
            cols = 1;
            fragments[0][0] = new ConnectToPhoneFragment();
            return;
        }
    }

    FragmentGridPagerAdapter createPager(FragmentManager fm) {
        return new PagerAdapter(fm);
    }

    private Bundle getData(long lastUpdateTime) {
        if (mStateService == null) {
            return null;
        }
        return mStateService.getData(lastUpdateTime);
    }

    private Bundle getHeaders(long lastUpdateTime) {
        if (mStateService == null) {
            return null;
        }
        return mStateService.getHeaders(lastUpdateTime);
    }

    public StateService getStateService() {
        return mStateService;
    }

    public void scrollToRunInfo() {
        Point curr = pager.getCurrentItem();
        pager.scrollTo(RUN_INFO_ROW, curr.y);
    }

    public static class RunInformationCardFragment extends Fragment implements ValueModel.ChangeListener<TrackerState> {

        List<Pair<String, TextView>> textViews = new ArrayList<Pair<String, TextView>>(3);
        long dataUpdateTime;
        long headersTimestamp;
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
            textViews.add(new Pair<String, TextView>(Wear.RunInfo.DATA + "0",
                    (TextView) view.findViewById(R.id.textView1)));
            textViews.add(new Pair<String, TextView>(Wear.RunInfo.DATA + "1",
                    (TextView) view.findViewById(R.id.textView2)));
            textViews.add(new Pair<String, TextView>(Wear.RunInfo.DATA + "2",
                    (TextView) view.findViewById(R.id.textView3)));

            textViews.add(new Pair<String, TextView>(Wear.RunInfo.HEADER + "0",
                    (TextView) view.findViewById(R.id.textViewHeader1)));
            textViews.add(new Pair<String, TextView>(Wear.RunInfo.HEADER + "1",
                    (TextView) view.findViewById(R.id.textViewHeader2)));
            textViews.add(new Pair<String, TextView>(Wear.RunInfo.HEADER + "2",
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
            onValueChanged(null, mainActivity.getTrackerState());
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
        public void onValueChanged(TrackerState oldValue, TrackerState newValue) {
            if (newValue == null)
                return;

            if (textViews.size() == 0)
                return;

            if (newValue == TrackerState.PAUSED) {
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

    private ServiceConnection mStateServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (mStateService == null) {
                mStateService = ((StateService.LocalBinder) service).getService();
                mStateService.registerTrackerStateListener(MainActivity.this);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mStateService = null;
        }
    };

    public TrackerState getTrackerState() {
        if (mStateService == null)
            return null;
        synchronized (trackerState) {
            return mStateService.getTrackerState();
        }
    }

    @Override
    public void onValueChanged(final TrackerState oldState, final TrackerState newState) {
        synchronized (trackerState) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    synchronized (trackerState) {
                        trackerState.set(newState);
                    }
                }
            });
        }
    }

    public void registerTrackerStateListener(ValueModel.ChangeListener<TrackerState> listener) {
        synchronized (trackerState) {
            trackerState.registerChangeListener(listener);
        }
    }

    public void unregisterTrackerStateListener(ValueModel.ChangeListener<TrackerState> listener) {
        synchronized (trackerState) {
            trackerState.unregisterChangeListener(listener);
        }
    }
}
