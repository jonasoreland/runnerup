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
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.wearable.view.DotsPageIndicator;
import android.support.wearable.view.FragmentGridPagerAdapter;
import android.support.wearable.view.GridViewPager;
import android.widget.LinearLayout;

import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.ValueModel;
import org.runnerup.service.StateService;
import org.runnerup.widget.MyDotsPageIndicator;

import java.util.ArrayList;

public class MainActivity extends Activity implements Constants, ValueModel.ChangeListener<TrackerState> {
    private final Handler handler = new Handler();
    private GridViewPager pager;
    private StateService mStateService;
    final private ValueModel<TrackerState> trackerState = new ValueModel<>();
    final private ValueModel<Bundle> headers = new ValueModel<>();
    private boolean pauseStep = false;
    private int scroll = 0;
    private boolean postScrollRightRunning = false;
    private boolean mIsBound;

    private static final int RUN_INFO_ROW = 0;
    private static final int PAUSE_RESUME_ROW = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pager = findViewById(R.id.pager);
        FragmentGridPagerAdapter pageAdapter = new PagerAdapter(getFragmentManager());
        pager.setAdapter(pageAdapter);

        LinearLayout verticalDotsPageIndicator = findViewById(R.id.vert_page_indicator);
        MyDotsPageIndicator dot2 = new MyDotsPageIndicator(verticalDotsPageIndicator);

        DotsPageIndicator dotsPageIndicator = findViewById(R.id.page_indicator);
        dotsPageIndicator.setPager(pager);
        dotsPageIndicator.setDotFadeWhenIdle(false);
        dotsPageIndicator.setDotFadeOutDelay(1000 * 3600 * 24);
        dotsPageIndicator.setOnPageChangeListener(dot2);
        dotsPageIndicator.setOnAdapterChangeListener(dot2);
        dot2.setPager(pager);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsBound = getApplicationContext().bindService(new Intent(this, StateService.class),
                mStateServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mStateService != null) {
            mStateService.unregisterTrackerStateListener(this);
            mStateService.unregisterHeadersListener(this);
        }
        if (mIsBound) {
            getApplicationContext().unbindService(mStateServiceConnection);
            mIsBound = false;
        }
        mStateService = null;
    }

    private class PagerAdapter extends FragmentGridPagerAdapter
            implements ValueModel.ChangeListener<TrackerState> {
        int rows = 1;
        int cols = 1;

        PagerAdapter(FragmentManager fm) {
            super(fm);
            update(trackerState.get());
            trackerState.registerChangeListener(this);
        }

        @Override
        public Fragment getFragment(int row, int col) {
            if (trackerState.get() == null)
                return new ConnectToPhoneFragment();
            else if (mStateService == null)
                return new ConnectToPhoneFragment();

            switch (trackerState.get()) {
                case INIT:
                case INITIALIZING:
                case CLEANUP:
                case ERROR:
                    return new ConnectToPhoneFragment();
                case INITIALIZED:
                    return new StartFragment();
                case CONNECTING:
                    return new SearchingFragment();
                case CONNECTED:
                    return new StartFragment();
                case STARTED:
                case PAUSED:
                case STOPPED:
                    if (row == RUN_INFO_ROW) {
                        if (pauseStep) {
                            if (col == 0)
                                return new CountdownFragment();
                            col--; // during pause step col=0 is CountDown
                        }
                        return RunInfoFragment.createForScreen(col, getRowsForScreen(col));
                    } else if (row == PAUSE_RESUME_ROW) {
                        if (trackerState.get() == TrackerState.STOPPED)
                            return new StoppedFragment();
                        else {
                            return new PauseResumeFragment();
                        }
                    }
            }
            return new ConnectToPhoneFragment();
        }

        @Override
        public int getRowCount() {
            return rows;
        }

        @Override
        public int getColumnCount(int i) {
            if (pauseStep)
                return cols + 1;
            return cols;
        }

        @Override
        public void onValueChanged(ValueModel<TrackerState> obj,
                                   TrackerState oldValue, TrackerState newValue) {
            notifyDataSetChanged();
        }

        @Override
        public void notifyDataSetChanged() {
            update(trackerState.get());
            super.notifyDataSetChanged();
        }

        private void update(TrackerState newValue) {
            if (newValue == null || mStateService == null) {
                cols = rows = 1;
                return;
            }
            switch (newValue) {
                case INIT:
                case INITIALIZING:
                case CLEANUP:
                case ERROR:
                case INITIALIZED:
                case CONNECTING:
                case CONNECTED:
                    cols = rows = 1;
                    break;
                case STARTED:
                case PAUSED:
                case STOPPED:
                    cols = getScreensCount();
                    rows = 2;
                    break;
            }
        }
    }

    private int getRowsForScreen(int col) {
        Bundle b = headers.get();
        if (b == null) {
            System.err.println("getRowsForScreen(): headers == null");
            return 1;
        }
        ArrayList<Integer> screens = b.getIntegerArrayList(Wear.RunInfo.SCREENS);
        if (screens == null) {
            System.err.println("getRowsForScreen(): screens == null");
            return 1;
        }
        if (col > screens.size())
            return 1;
        return screens.get(col);
    }

    private int getScreensCount() {
        Bundle b = headers.get();
        if (b == null) {
            System.err.println("getScreensCount(): headers == null");
            return 1;
        }
        ArrayList<Integer> screens = b.getIntegerArrayList(Wear.RunInfo.SCREENS);
        if (screens == null) {
            System.err.println("getScreensCount(): screens == null");
            return 1;
        }
        return screens.size();
    }

    Bundle getData(long lastUpdateTime) {
        if (mStateService == null) {
            return null;
        }
        return mStateService.getData(lastUpdateTime);
    }

    Bundle getHeaders(long lastUpdateTime) {
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
        pager.setCurrentItem(RUN_INFO_ROW, curr.x, true);
    }

    private void scrollRight() {
        Point curr = pager.getCurrentItem();
        if (curr.y != RUN_INFO_ROW)
            return;
        if (getScreensCount() <= 1)
            return;
        int newx = (curr.x + 1) % getScreensCount();
        pager.setCurrentItem(curr.y, newx, true);
    }

    private final ServiceConnection mStateServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (mStateService == null) {
                mStateService = ((StateService.LocalBinder) service).getService();
                mStateService.registerTrackerStateListener(MainActivity.this);
                mStateService.registerHeadersListener(MainActivity.this);
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
    public void onValueChanged(ValueModel<TrackerState> obj,
                               final TrackerState oldState, final TrackerState newState) {
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

    private void postScrollRight() {
        if (postScrollRightRunning)
            return;
        if (scroll > 0 && getScreensCount() > 1) {
            postScrollRightRunning = true;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            postScrollRightRunning = false;
                            postScrollRight();
                            scrollRight();
                        }
                    });
                }
            }, scroll * 1000);
        }
    }

    public void onValueChanged(final Bundle newValue) {
        synchronized (trackerState) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    synchronized (trackerState) {
                        pauseStep = false;
                        if (newValue != null) {
                            pauseStep = newValue.getBoolean(Wear.RunInfo.PAUSE_STEP);
                            scroll = newValue.getInt(Wear.RunInfo.SCROLL);
                        }
                        headers.set(newValue);
                        pager.getAdapter().notifyDataSetChanged();
                        postScrollRight();
                    }
                }
            });
        }
    }
}
