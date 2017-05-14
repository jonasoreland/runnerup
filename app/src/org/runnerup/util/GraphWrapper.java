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

package org.runnerup.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Toast;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.OnDataPointTapListener;
import com.jjoe64.graphview.series.Series;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.entities.LocationEntity;
import org.runnerup.view.HRZonesBar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GraphWrapper implements Constants {
    private GraphView graphView;
    private GraphView graphView2;

    private final LinearLayout graphTab;
    private final HRZonesBar hrzonesBar;
    private final Formatter formatter;
    private final LinearLayout hrzonesBarLayout;

    /**
     * Called when the activity is first created.
     */
    public GraphWrapper(Context context, LinearLayout graphTab, LinearLayout hrzonesBarLayout, final Formatter formatter, SQLiteDatabase mDB, long mID) {
        this.graphTab = graphTab;
        this.hrzonesBarLayout = hrzonesBarLayout;
        this.formatter = formatter;

        if (Build.VERSION.SDK_INT > 8) {
            new LoadGraph().execute(new LoadParam(context, mDB, mID));

            graphView = new GraphView(context);
            graphView.setTitle(context.getString(R.string.Pace));
            graphView.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
                @Override
                public String formatLabel(double value, boolean isValueX) {
                    if (isValueX) {
                        return formatter.formatDistance(Formatter.Format.TXT, (long) value);
                    } else {
                        return formatter.formatPace(Formatter.Format.TXT_SHORT, value);
                    }
                }
            });
            graphView.getGridLabelRenderer().setVerticalAxisTitle(formatter.getPaceUnit());
            graphView.getGridLabelRenderer().setHorizontalAxisTitle(formatter.getDistanceUnit(Formatter.Format.TXT));
            //enable zoom
            graphView.getViewport().setScalable(true);
            graphView.getViewport().setScrollable(true);

            graphView2 = new GraphView(context);
            graphView2.setTitle(context.getString(R.string.Heart_rate));
            graphView2.getGridLabelRenderer().setVerticalAxisTitle("bpm");
            graphView2.getGridLabelRenderer().setHorizontalAxisTitle(formatter.getDistanceUnit(Formatter.Format.TXT));
            graphView2.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
                @Override
                public String formatLabel(double value, boolean isValueX) {
                    if (isValueX) {
                        return formatter.formatDistance(Formatter.Format.TXT_SHORT, (long) value);
                    } else {
                        return formatter.formatHeartRate(Formatter.Format.TXT_SHORT, value);
                    }
                }
            });
            graphView2.getViewport().setScalable(true);
            graphView2.getViewport().setScrollable(true);
        }
         hrzonesBar = new HRZonesBar(context);
    }

     class GraphProducer {
        final int interval;
        boolean first = true;
        int pos = 0;
        double time[] = null;
        double distance[] = null;
        double sum_time = 0;
        double sum_distance = 0;
        double acc_time = 0;

        int[] hr = null;
        double[] hrzHist = null;

        double tot_avg_hr = 0;

        double avg_pace = 0;
        double min_pace = Double.MAX_VALUE;
        double max_pace = Double.MIN_VALUE;
        List<DataPoint> paceList = null;
        List<DataPoint> hrList = null;

        boolean showHR = false;
        boolean showHRZhist = false;
        HRZones hrCalc = null;

        public GraphProducer(Context context, int noPoints) {
            final int GRAPH_INTERVAL_SECONDS = 5; // 1 point every 5 sec
            final int GRAPH_AVERAGE_SECONDS = 30; // moving average 30 sec

            final int graphAverageSeconds;
            if (noPoints < 60) {
                //This is short, maybe when testing. Dont bother to check time between points
                graphAverageSeconds = 1;
                this.interval = 2;
            } else {
                graphAverageSeconds = GRAPH_AVERAGE_SECONDS;
                this.interval = GRAPH_INTERVAL_SECONDS;
            }
            this.paceList = new ArrayList<>();
            this.time = new double[graphAverageSeconds];
            this.distance = new double[graphAverageSeconds];

            this.hrList = new ArrayList<>();
            this.hr = new int[graphAverageSeconds];

            Resources res = context.getResources();
            Context ctx = context.getApplicationContext();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
            this.hrCalc = new HRZones(res, prefs);
            if (hrCalc.isConfigured()) {
                this.hrzHist = new double[hrCalc.getCount() + 1];
                for (int i = 0; i < this.hrzHist.length; i++) {
                    this.hrzHist[i] = 0;
                }
                showHRZhist = true;
            }

            clearSmooth(0);
        }

        void clearSmooth(double tot_distance) {
            if (pos >= (this.time.length / 2) && (acc_time >= 1000 * (interval / 2))
                    && sum_distance > 0) {
                emit(tot_distance);
            }

            for (int i = 0; i < this.distance.length; i++) {
                time[i] = 0;
                distance[i] = 0;
                hr[i] = 0;
            }
            pos = 0;
            sum_time = 0;
            sum_distance = 0;
            acc_time = 0;
        }

        void addObservation(double delta_time, double delta_distance, double tot_distance, LocationEntity loc) {
            if (delta_time < 500)
                return;

            //Update moving average
            int p = pos % this.time.length;
            sum_time -= this.time[p];
            sum_distance -= this.distance[p];
            sum_time += delta_time;
            sum_distance += delta_distance;
            this.time[p] = delta_time;
            this.distance[p] = delta_distance;

            if (loc.getHr() != null) {
                showHR = true;
                int hr = loc.getHr();
                this.hr[p] = hr;

                if (showHRZhist && hr > 0) {
                    this.hrzHist[hrCalc.getZoneInt(hr)] += delta_time;
                }
            } else {
                this.hr[p] = 0;
            }

            pos += 1;
            acc_time += delta_time;

            if (pos >= this.time.length && (acc_time >= 1000 * interval) && sum_distance > 0) {
                emit(tot_distance);
            }
        }

        void emit(double tot_distance) {
            double avg_time = sum_time;
            double avg_dist = sum_distance;
            double avg_hr = calculateAverageHr(hr);
            {
                double max_pace[] = {
                        0, 0, 0
                };
                double min_pace[] = {
                        Double.MAX_VALUE, 0, 0
                };
                for (int i = 0; i < this.time.length; i++) {
                    double pace = this.time[i] / this.distance[i];
                    if (pace > max_pace[0]) {
                        max_pace[0] = pace;
                        max_pace[1] = this.time[i];
                        max_pace[2] = this.distance[i];
                    }
                    if (pace < min_pace[0]) {
                        min_pace[0] = pace;
                        min_pace[1] = this.time[i];
                        min_pace[2] = this.distance[i];
                    }
                }
                //avg_time -= (max_pace[1] + min_pace[1]);
                //avg_dist -= (max_pace[2] + min_pace[2]);
            }

            if (avg_dist > 0) {
                double pace = avg_time / avg_dist / 1000.0;
                if (first) {
                    paceList.add(new DataPoint(0, pace));
                    hrList.add(new DataPoint(0, Math.round(avg_hr)));
                    first = false;
                }
                paceList.add(new DataPoint(tot_distance, pace));
                hrList.add(new DataPoint(tot_distance, Math.round(avg_hr)));
                acc_time = 0;

                tot_avg_hr += avg_hr;
                avg_pace += pace;
                min_pace = Math.min(min_pace, pace);
                max_pace = Math.max(max_pace, pace);
            }
        }

        class GraphFilter {

            double data[] = null;
            final List<DataPoint> source;

            GraphFilter(List<DataPoint> paceList) {
                source = paceList;
                data = new double[paceList.size()];
                for (int i = 0; i < paceList.size(); i++)
                    data[i] = paceList.get(i).getY();
            }

            void complete() {
                for (int i = 0; i < source.size(); i++)
                    source.set(i, new DataPoint(source.get(i).getX(), data[i]));
            }

            void init(double window[], double val) {
                for (int j = 0; j < window.length - 1; j++)
                    window[j] = val;
            }

            void shiftLeft(double window[], double newVal) {
                System.arraycopy(window, 1, window, 0, window.length - 1);
                window[window.length - 1] = newVal;
            }

            /**
             * Perform in place moving average
             */
            void movingAvergage(int windowLen) {
                double window[] = new double[windowLen];
                init(window, data[0]);

                final int mid = (window.length - 1) / 2;
                final int last = window.length - 1;
                for (int i = 0; i < data.length && i <= mid; i++) {
                    window[i + mid] = data[i];
                }

                double sum = 0;
                for (double aWindow : window) sum += aWindow;

                for (int i = 0; i < data.length; i++) {
                    double newY = sum / windowLen;
                    data[i] = newY;
                    sum -= window[0];
                    shiftLeft(window, (i + mid) < data.length ? data[i + mid] : avg_pace);
                    sum += window[last];
                }
            }

            /**
             * Perform in place moving average
             */
            void movingMedian(int windowLen) {
                double window[] = new double[windowLen];
                init(window, data[0]);

                final int mid = (window.length - 1) / 2;
                for (int i = 0; i < data.length && i <= mid; i++) {
                    window[i + mid] = data[i];
                }

                double sort[] = new double[windowLen];
                for (int i = 0; i < data.length; i++) {
                    System.arraycopy(window, 0, sort, 0, windowLen);
                    Arrays.sort(sort);
                    data[i] = sort[mid];
                    shiftLeft(window, (i + mid) < data.length ? data[i + mid] : avg_pace);
                }
            }

            /**
             * Perform in place SavitzkyGolay windowLen = 5
             */
            void SavitzkyGolay5() {
                final int len = 5;
                double window[] = new double[len];
                init(window, data[0]);

                final int mid = (window.length - 1) / 2;
                for (int i = 0; i < data.length && i <= mid; i++) {
                    window[i + mid] = data[i];
                }
                for (int i = 0; i < data.length; i++) {
                    double newY = (-3 * window[0] + 12 * window[1] + 17
                            * window[2] + 12 * window[3] - 3 * window[4]) / 35;
                    data[i] = newY;
                    shiftLeft(window,
                            (i + mid) < data.length ? data[i + mid] : avg_pace);
                }
            }

            /**
             * Perform in place SavitzkyGolay windowLen = 7
             */
            void SavitzkyGolay7() {
                final int len = 7;
                double window[] = new double[len];
                init(window, data[0]);

                final int mid = (window.length - 1) / 2;
                for (int i = 0; i < data.length && i <= mid; i++) {
                    window[i + mid] = data[i];
                }
                for (int i = 0; i < data.length; i++) {
                    double newY = (-2 * window[0] + 3 * window[1] + 6
                            * window[2] + 7 * window[3] + 6 * window[4] + 3
                            * window[5] - 2 * window[6]) / 21;
                    data[i] = newY;
                    shiftLeft(window,
                            (i + mid) < data.length ? data[i + mid] : avg_pace);
                }
            }

            void KolmogorovZurbenko(int n, int len) {
                for (int i = 0; i < n; i++)
                    movingAvergage(len);
            }
        }

        public void complete(final GraphView graphView) {
            avg_pace /= paceList.size();
            Log.e(getClass().getName(), "graph: " + paceList.size() + " points");

            boolean smoothData = PreferenceManager.getDefaultSharedPreferences(graphView.getContext())
                    .getBoolean(graphView.getContext().getResources().getString(R.string.pref_pace_graph_smoothing), true);
            if (paceList.size() > 0 && smoothData) {
                GraphFilter f = new GraphFilter(paceList);
                final String defaultFilterList = graphView.getContext().getResources().getString(R.string.mm31kz513sg5);
                final String filterList = PreferenceManager.getDefaultSharedPreferences(
                        graphView.getContext()).getString(
                        graphView.getContext().getResources().getString(R.string.pref_pace_graph_smoothing_filters),
                        defaultFilterList);
                final String filters[] = filterList.split(";");
                System.err.print("Applying filters(" + filters.length + ", >" + filterList + "<):");
                for (String filter : filters) {
                    int args[] = getArgs(filter);
                    if (filter.startsWith("mm")) {
                        if (args.length == 1) {
                            f.movingMedian(args[0]);
                            System.err.print(" mm(" + args[0] + ")");
                        }
                    } else if (filter.startsWith("ma")) {
                        if (args.length == 1) {
                            f.movingAvergage(args[0]);
                            System.err.print(" ma(" + args[0] + ")");
                        }
                    } else if (filter.startsWith("kz")) {
                        if (args.length == 2) {
                            f.KolmogorovZurbenko(args[0], args[1]);
                            System.err.print(" kz(" + args[0] + "," + args[1] + ")");
                        }
                    } else if (filter.startsWith("sg")) {
                        if (args.length == 1 && args[0] == 5) {
                            f.SavitzkyGolay5();
                            System.err.print(" sg(5)");
                        } else if (args.length == 1 && args[0] == 7) {
                            f.SavitzkyGolay7();
                            System.err.print(" sg(7)");
                        }
                    }
                }
                Log.e(getClass().getName(), "");
                f.complete();
            }
            LineGraphSeries<DataPoint> graphViewData = new LineGraphSeries<>(
                    paceList.toArray(new DataPoint[paceList.size()]));
            graphView.addSeries(graphViewData); // data
            graphView.getViewport().setMinX(graphView.getViewport().getMinX(true));
            graphView.getViewport().setMaxX(graphView.getViewport().getMaxX(true));
            graphViewData.setOnDataPointTapListener(new OnDataPointTapListener() {
                @Override
                public void onTap(Series series, DataPointInterface dataPoint) {
                    String msg = graphView.getContext().getString(R.string.Distance) + ": " + formatter.formatDistance(Formatter.Format.TXT_SHORT, (long) dataPoint.getX()) + "\n" +
                            graphView.getContext().getString(R.string.Pace) + ": " + formatter.formatPace(Formatter.Format.TXT_SHORT, dataPoint.getY());
                    Toast.makeText(graphView.getContext(), msg, Toast.LENGTH_SHORT).show();
                }
            });
            if (showHR) {
                LineGraphSeries<DataPoint> graphViewData2 = new LineGraphSeries<>(
                        hrList.toArray(new DataPoint[hrList.size()]));
                graphView2.addSeries(graphViewData2); // data
                graphView2.getViewport().setMinX(graphView2.getViewport().getMinX(true));
                graphView2.getViewport().setMaxX(graphView2.getViewport().getMaxX(true));
                graphViewData2.setOnDataPointTapListener(new OnDataPointTapListener() {
                    @Override
                    public void onTap(Series series, DataPointInterface dataPoint) {
                        String msg = graphView.getContext().getString(R.string.Distance) + ": " + formatter.formatDistance(Formatter.Format.TXT_SHORT, (long) dataPoint.getX()) + "\n" +
                                graphView.getContext().getString(R.string.Heart_rate) + ": " + formatter.formatHeartRate(Formatter.Format.TXT_SHORT, dataPoint.getY());
                        Toast.makeText(graphView.getContext(), msg, Toast.LENGTH_SHORT).show();
                    }
                });

                if (showHRZhist) {
                    System.err.print("HR Zones:");
                    double sum = 0;
                    for (double aHrzHist : hrzHist) {
                        sum += aHrzHist;
                    }
                    for (int i = 0; i < hrzHist.length; i++) {
                        hrzHist[i] = hrzHist[i] / sum;
                        System.err.print(" " + hrzHist[i]);
                    }
                    Log.e(getClass().getName(), "\n");
                    hrzonesBar.pushHrzData(hrzHist);
                }
            }
        }

        private int[] getArgs(String s) {
            try {
                s = s.substring(s.indexOf('(') + 1);
                s = s.substring(0, s.indexOf(')'));
                String sargs[] = s.split(",");
                int args[] = new int[sargs.length];
                for (int i = 0; i < args.length; i++) {
                    args[i] = Integer.parseInt(sargs[i]);
                }
                return args;
            } catch (Exception e) {
                e.printStackTrace();
                return new int[0];
            }
        }

        public boolean HasHRInfo() {
            return showHR;
        }

        public boolean HasHRZHist() {
            return showHR && showHRZhist;
        }
    }

    private double calculateAverageHr(int[] data) {
        int sum = 0;
        int no = 0;

        for (int aData : data) {
            if (aData> 0) {
            sum = sum + aData;
                no++;
        }
    }
        //TODO Average of pointe, not over time
        if(no==0){
            return 0;
        } else {
            return (double) sum / no;
        }
    }

    class LoadParam {
        public LoadParam(Context context, SQLiteDatabase mDB, long mID) {
            this.context = context;
            this.mDB = mDB;
            this.mID = mID;
        }
        final Context context;
        final SQLiteDatabase mDB;
        final long mID;
    }

    private class LoadGraph extends AsyncTask<LoadParam, Void, GraphProducer> {
        @Override
        protected GraphProducer doInBackground(LoadParam... params) {

            LocationEntity.LocationList<LocationEntity> ll = new LocationEntity.LocationList<>(params[0].mDB, params[0].mID);
            GraphProducer graphData = new GraphProducer(params[0].context, ll.getCount());
            double lastDistance = 0;
            long lastTime = 0;
            int lastLap = -1;
            Double tot_distance = 0.0;
            for (LocationEntity loc : ll) {
                Long time = loc.getTime();
                time = time != null ? time : lastTime;
                Integer lap = loc.getLap();
                lap = lap != null ? lap : 0;
                tot_distance = loc.getDistance();
                tot_distance = tot_distance != null ? tot_distance : lastDistance;

                if (lap != lastLap) {
                    graphData.clearSmooth(tot_distance);
                    lastLap = lap;
                }
                graphData.addObservation(time - lastTime, tot_distance - lastDistance,
                        tot_distance, loc);
                lastTime = time;
                lastDistance = tot_distance;

            }
            graphData.clearSmooth(tot_distance);

            //    Log.e(getClass().getName(), "Finished loading " + cnt + " points");
            //}
            ll.close();
            return graphData;
        }

        @Override
        protected void onPostExecute(GraphProducer graphData) {

            if (Build.VERSION.SDK_INT > 8) {
                graphData.complete(graphView);
                if (!graphData.HasHRInfo()) {
                    graphTab.addView(graphView);
                } else {
                    graphTab.addView(graphView,
                            new LayoutParams(
                                    LayoutParams.MATCH_PARENT, 0, 0.5f));

                    graphTab.addView(graphView2,
                            new LayoutParams(
                                    LayoutParams.MATCH_PARENT, 0, 0.5f));
                }

                if (graphData.HasHRZHist()) {
                    hrzonesBarLayout.setVisibility(View.VISIBLE);
                    hrzonesBarLayout.addView(hrzonesBar);
                } else {
                    hrzonesBarLayout.setVisibility(View.GONE);
                }
            }
        }
    }
}
