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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.wearable.view.DotsPageIndicator;
import android.support.wearable.view.FragmentGridPagerAdapter;
import android.support.wearable.view.GridViewPager;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import org.runnerup.R;
import org.runnerup.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

@TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
public class MainActivity extends Activity
        implements Constants, DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private FragmentGridPagerAdapter pageAdapter;
    private GoogleApiClient mGoogleApiClient;
    private Bundle data;
    private long dataTimestamp;

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

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        if (null != mGoogleApiClient && mGoogleApiClient.isConnected()) {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    FragmentGridPagerAdapter createPager(FragmentManager fm) {
        return new FragmentGridPagerAdapter(fm) {

            Fragment fragments[][] = new Fragment[1][1];

            @Override
            public Fragment getFragment(int i, int j) {
                if (fragments[i][j] == null) {
                    fragments[i][j] = new RunInformationCardFragment();
                }
                return fragments[i][j];
            }

            @Override
            public int getRowCount() {
                return 1;
            }

            @Override
            public int getColumnCount(int i) {
                return 1;
            }
        };
    }

    public static class RunInformationCardFragment extends Fragment {

        private MainActivity mainActivity;
        List<Pair<String, TextView>> textViews = new ArrayList<Pair<String, TextView>>(3);

        long lastUpdateTime;
        Handler handler = new Handler();
        boolean handlerOutstanding = false;

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

        void startTimer() {
            if (handlerOutstanding)
                return;
            handlerOutstanding = true;
            handler.postDelayed(periodicTick, 1000);
        }

        private void update() {
            if (mainActivity.dataTimestamp != lastUpdateTime &&
                    mainActivity.data != null) {
                lastUpdateTime = mainActivity.dataTimestamp;
                update(mainActivity.data);
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

    ;

    @Override
    public void onConnected(Bundle connectionHint) {
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        System.err.println("addListener");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        System.err.println("onConnectionFailed(" + connectionResult + ")");
    }

    @Override
    public void onConnectionSuspended(int i) {
        System.err.println("onConnectionSuspended(" + i + ")");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        System.err.println("onDataChanged");
        int found = 0;
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataItem dataItem = event.getDataItem();
                String path = dataItem.getUri().getPath();
                if (!path.startsWith(Wear.Path.PREFIX)) {
                    System.out.println("got data with path: " + path);
                    continue;
                }
                found++;
                this.data = DataMapItem.fromDataItem(dataItem).getDataMap().toBundle();
            }
        }
        if (found > 0) {
            this.dataTimestamp = System.currentTimeMillis();
        }
    }
}
