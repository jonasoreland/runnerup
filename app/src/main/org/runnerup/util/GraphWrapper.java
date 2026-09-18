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
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.preference.PreferenceManager;
import info.appdev.charting.charts.LineChart;
import info.appdev.charting.components.AxisBase;
import info.appdev.charting.components.YAxis;
import info.appdev.charting.data.EntryFloat;
import info.appdev.charting.data.LineData;
import info.appdev.charting.data.LineDataSet;
import info.appdev.charting.formatter.IAxisValueFormatter;
import info.appdev.charting.highlight.Highlight;
import info.appdev.charting.interfaces.datasets.ILineDataSet;
import info.appdev.charting.listener.OnChartValueSelectedListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.entities.LocationEntity;
import org.runnerup.view.HRZonesBar;
import org.runnerup.workout.SpeedUnit;

public class GraphWrapper implements Constants {
  private final LineChart chart;
  private final LinearLayout graphTab;
  private final HRZonesBar hrzonesBar;
  private final Formatter formatter;
  private final LinearLayout hrzonesBarLayout;
  private final LoadParam loadParam;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final XAxis distanceXAxis;
  private final XAxis timeXAxis;
  boolean firstLoad = true;
  boolean useDistanceAsX = true;
  private XAxis xAxis;

  /** Called when the activity is first created. */
  public GraphWrapper(
      Context context,
      LinearLayout graphTab,
      LinearLayout hrzonesBarLayout,
      final Formatter formatter,
      SQLiteDatabase mDB,
      long mID,
      boolean use_distance_as_x) {
    this.graphTab = graphTab;
    this.hrzonesBarLayout = hrzonesBarLayout;
    this.formatter = formatter;

    this.distanceXAxis =
        new XAxis() {
          @Override
          public String label() {
            return context.getString(org.runnerup.common.R.string.Distance);
          }

          public String labelLong() {
            return label() + " " + formatter.getDistanceUnit();
          }

          @Override
          public String formatLongValue(double value) {
            return formatter.getDistanceDisplay(value) + " " + formatter.getDistanceUnit();
          }

          @Override
          public String formatValue(double value) {
            return formatter.getDistanceDisplay(value);
          }

          @Override
          public double getX(double distance, double time_ms) {
            return distance;
          }

          @Override
          public String unit() {
            return formatter.getDistanceUnit();
          }
        };

    this.timeXAxis =
        new XAxis() {
          @Override
          public String label() {
            return context.getString(org.runnerup.common.R.string.Time);
          }

          public String labelLong() {
            return label();
          }

          @Override
          public String formatLongValue(double value) {
            return formatValue(value);
          }

          @Override
          public String formatValue(double value) {
            return formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, (long) value);
          }

          @Override
          public double getX(double distance, double time_ms) {
            return time_ms / 1000;
          }

          @Override
          public String unit() {
            return "";
          }
        };

    this.useDistanceAsX = use_distance_as_x;
    if (use_distance_as_x) {
      this.xAxis = distanceXAxis;
    } else {
      this.xAxis = timeXAxis;
    }

    this.loadParam = new LoadParam(context, mDB, mID);

    chart = new LineChart(context);
    hrzonesBar = new HRZonesBar(context);
    loadGraph();
  }

  public void setUseDistanceAsX(boolean val) {
    if (val == useDistanceAsX) {
      return;
    }
    useDistanceAsX = val;
    if (val) {
      xAxis = distanceXAxis;
    } else {
      xAxis = timeXAxis;
    }
    chart.clear();
    loadGraph();
  }

  private void loadGraph() {
    executor.execute(
        () -> {
          // Background work
          GraphProducer producer = doLoadGraphInBackground(loadParam);
          // Post result to UI thread
          handler.post(() -> onPostExecute(producer));
        });
  }

  private GraphProducer doLoadGraphInBackground(LoadParam params) {
    LocationEntity.LocationList<LocationEntity> ll =
        new LocationEntity.LocationList<>(params.mDB, params.mID);
    GraphProducer graphData = new GraphProducer(params.context, ll.getCount());
    double lastDistance = 0;
    long lastTime = 0;
    int lastLap = -1;
    Double tot_distance = 0.0;
    double tot_time = 0.0;
    for (LocationEntity loc : ll) {
      Long time = loc.getElapsed();
      time = time != null ? time : lastTime;
      tot_time = time.doubleValue();
      Integer lap = loc.getLap();
      lap = lap != null ? lap : 0;
      tot_distance = tot_distance != null ? loc.getDistance() : lastDistance;

      double tot_X = xAxis.getX(tot_distance, time);
      if (lap != lastLap) {
        graphData.clearSmooth(tot_X);
        lastLap = lap;
      }

      graphData.addObservation(time - lastTime, tot_distance - lastDistance, tot_X, loc);
      lastTime = time;
      lastDistance = tot_distance;
    }
    graphData.clearSmooth(xAxis.getX(tot_distance, tot_time));

    ll.close();
    return graphData;
  }

  private void onPostExecute(GraphProducer graphData) {
    if (graphData == null) return;

    graphData.complete(chart);
    chart.invalidate();
    chart.setVisibility(View.VISIBLE);
    graphTab.removeView(chart);

    LayoutParams layout = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f);

    graphTab.addView(chart, layout);
    hrzonesBarLayout.removeView(hrzonesBar);
    if (graphData.HasHRZHist()) {
      hrzonesBarLayout.setVisibility(View.VISIBLE);
      hrzonesBarLayout.addView(hrzonesBar);
    } else {
      hrzonesBarLayout.setVisibility(View.GONE);
    }

    // Add legend container to bottom of the view
    LinearLayout legend = new LinearLayout(graphTab.getContext());
    legend.setOrientation(LinearLayout.HORIZONTAL);
    android.widget.HorizontalScrollView scroll =
        new android.widget.HorizontalScrollView(graphTab.getContext());
    scroll.addView(legend);
    graphTab.addView(scroll, 0);
    for (CheckBox cb : graphData.legendBoxes) {
      legend.addView(cb);
    }

    if (!firstLoad) {
      graphTab.invalidate();
    } else {
      firstLoad = false;
    }
  }

  private double calculateAverageHr(int[] data) {
    int sum = 0;
    int no = 0;

    for (int aData : data) {
      if (aData > 0) {
        sum = sum + aData;
        no++;
      }
    }
    // TODO Average of pointe, not over time
    if (no == 0) {
      return 0;
    } else {
      return (double) sum / no;
    }
  }

  interface XAxis {
    String label();

    String labelLong();

    String formatLongValue(double val);

    String formatValue(double val);

    double getX(double distance, double time_ms);

    String unit();
  }

  record LoadParam(Context context, SQLiteDatabase mDB, long mID) {}

  class GraphProducer {
    final int interval;
    final double[] time;
    final double[] distance;
    final double[] elevation;
    final int[] hr;
    final List<DataPoint> velocityList;
    final List<DataPoint> hrList;
    final List<DataPoint> elevationList;
    final HRZones hrCalc;
    final SpeedUnit preferred_speedunit;
    final List<CheckBox> legendBoxes = new ArrayList<>();
    boolean first = true;
    int pos = 0;
    double sum_time = 0;
    double sum_distance = 0;
    double acc_time = 0;
    double[] hrzHist = null; // no chart displayed
    double tot_avg_hr = 0;
    double avg_velocity = 0;
    double min_velocity = Double.MAX_VALUE;
    double max_velocity = Double.MIN_VALUE;
    boolean showPace = false;
    boolean showElevation = false;
    boolean showHR = false;
    boolean showHRZhist = false;

    public GraphProducer(Context context, int noPoints) {
      final int GRAPH_INTERVAL_SECONDS = 5; // 1 point every 5 sec
      final int GRAPH_AVERAGE_SECONDS = 30; // moving average 30 sec

      final int graphAverageSeconds;
      if (noPoints < 60) {
        // This is short, maybe when testing. Dont bother to check time between points
        graphAverageSeconds = 1;
        this.interval = 2;
      } else {
        graphAverageSeconds = GRAPH_AVERAGE_SECONDS;
        this.interval = GRAPH_INTERVAL_SECONDS;
      }
      this.velocityList = new ArrayList<>();
      this.time = new double[graphAverageSeconds];
      this.distance = new double[graphAverageSeconds];

      this.hrList = new ArrayList<>();
      this.hr = new int[graphAverageSeconds];

      this.elevationList = new ArrayList<>();
      this.elevation = new double[graphAverageSeconds];

      Resources res = context.getResources();
      Context ctx = context.getApplicationContext();
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
      this.hrCalc = new HRZones(res, prefs);
      if (hrCalc.isConfigured()) {
        this.hrzHist = new double[hrCalc.getCount() + 1];
        Arrays.fill(this.hrzHist, 0);
        showHRZhist = true;
      }
      this.preferred_speedunit = formatter.getPreferredSpeedUnit();

      clearSmooth(0);
    }

    void clearSmooth(double tot_X) {
      if (pos >= (this.time.length / 2)
          && (acc_time >= 1000 * (interval / 2.0))
          && xAxis.getX(sum_distance, sum_time) > 0) {
        emit(tot_X);
      }

      for (int i = 0; i < this.distance.length; i++) {
        time[i] = 0;
        distance[i] = 0;
        hr[i] = 0;
        elevation[i] = 0;
      }
      pos = 0;
      sum_time = 0;
      sum_distance = 0;
      acc_time = 0;
    }

    void addObservation(
        double delta_time, double delta_distance, double tot_X, LocationEntity loc) {
      // delta_time is in ms, dist in m
      if (delta_time < 500) return;

      // Update moving average
      int p = pos % this.time.length;
      sum_time -= this.time[p];
      sum_time += delta_time;
      sum_distance -= this.distance[p];
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

      Double altitude = loc.getAltitude();
      this.elevation[p] = altitude != null ? altitude : 0.0;

      if (delta_distance > 0 && delta_time > 0) {
        showPace = true;
        if (altitude != null) showElevation = true;
      }

      pos += 1;
      acc_time += delta_time;

      if (pos >= this.time.length
          && (acc_time >= 1000 * interval)
          && xAxis.getX(sum_distance, sum_time) > 0) {
        emit(tot_X);
      }
    }

    void emit(double tot_X) {
      double avg_time = sum_time;
      double avg_dist = sum_distance;
      double avg_hr = calculateAverageHr(hr);

      double velocity = avg_time == 0 ? 0.0 : avg_dist * 1000.0 / avg_time;
      final double paceLimit = 1000.0 / 60.0 / 15.0;
      if (this.preferred_speedunit == SpeedUnit.PACE) {
        // Avoid presenting very slow pace (easier than to handle manual y axis scaling)
        velocity = Math.max(velocity, paceLimit);
      }

      double elevation = this.elevation[pos % this.time.length];

      if (first) {
        if (tot_X > 0) {
          velocityList.add(new DataPoint(0, velocity));
          elevationList.add(new DataPoint(0, elevation));
          if (avg_hr > 0) {
            hrList.add(new DataPoint(0, Math.round(avg_hr)));
          }
        }
        first = false;
      }
      velocityList.add(new DataPoint(tot_X, velocity));
      elevationList.add(new DataPoint(tot_X, elevation));
      if (avg_hr > 0) {
        hrList.add(new DataPoint(tot_X, Math.round(avg_hr)));
      }
      acc_time = 0;

      tot_avg_hr += avg_hr;
      avg_velocity += velocity;
      min_velocity = Math.min(min_velocity, velocity);
      max_velocity = Math.max(max_velocity, velocity);

      if (sum_distance > 0 && sum_time > 0) {
        showPace = true;
      }
    }

    private void setAxisAndLegend(
        LineDataSet<EntryFloat> dataSet, int color, IAxisValueFormatter valueFormatter) {
      dataSet.setColor(color);
      dataSet.setLineWidth(1.5f);
      dataSet.setDrawValues(false);
      dataSet.setDrawCircles(false);

      YAxis axis = null;
      if (!chart.getAxisLeft().isEnabled()) {
        axis = chart.getAxisLeft();
        dataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
      } else {
        dataSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        if (!chart.getAxisRight().isEnabled()) {
          axis = chart.getAxisRight();
        }
      }
      if (axis != null) {
        axis.setEnabled(true);
        axis.setTextColor(color);
        axis.setAxisLineColor(color);
        axis.setTextSize(14f);
        axis.setValueFormatter(valueFormatter);
      }

      CheckBox cB = new CheckBox(graphTab.getContext());
      cB.setText(dataSet.getLabel());
      cB.setTextColor(color);
      cB.setTextSize(12f);
      cB.setChecked(true);
      cB.setOnCheckedChangeListener(
          (btn, checked) -> {
            if (chart.getData() != null) {
              ILineDataSet<EntryFloat> ds =
                  chart.getData().getDataSetByLabel(dataSet.getLabel(), false);
              if (ds != null) {
                ds.setVisible(checked);
                chart.invalidate();
              }
            }
          });
      legendBoxes.add(cB);
    }

    public void complete(LineChart chart) {
      if (velocityList.isEmpty()) {
        avg_velocity = 0;
      } else {
        avg_velocity /= velocityList.size();
      }
      Log.d(getClass().getName(), "graph: " + velocityList.size() + " points");

      boolean smoothData =
          PreferenceManager.getDefaultSharedPreferences(graphTab.getContext())
              .getBoolean(
                  graphTab
                      .getContext()
                      .getResources()
                      .getString(R.string.pref_pace_graph_smoothing),
                  true);
      if (!velocityList.isEmpty() && smoothData) {
        GraphFilter f = new GraphFilter(velocityList);
        final String defaultFilterList =
            graphTab.getContext().getResources().getString(R.string.mm31kz513sg5);
        final String filterList =
            PreferenceManager.getDefaultSharedPreferences(graphTab.getContext())
                .getString(
                    graphTab
                        .getContext()
                        .getResources()
                        .getString(R.string.pref_pace_graph_smoothing_filters),
                    defaultFilterList);
        final String[] filters = filterList.split(";");
        StringBuilder s =
            new StringBuilder("Applying filters(" + filters.length + ", >" + filterList + "<):");
        for (String filter : filters) {
          int[] args = getArgs(filter);
          if (filter.startsWith("mm")) {
            if (args.length == 1) {
              f.movingMedian(args[0]);
              s.append(" mm(").append(args[0]).append(")");
            }
          } else if (filter.startsWith("ma")) {
            if (args.length == 1) {
              f.movingAvergage(args[0]);
              s.append(" ma(").append(args[0]).append(")");
            }
          } else if (filter.startsWith("kz")) {
            if (args.length == 2) {
              f.KolmogorovZurbenko(args[0], args[1]);
              s.append(" kz(").append(args[0]).append(",").append(args[1]).append(")");
            }
          } else if (filter.startsWith("sg")) {
            if (args.length == 1 && args[0] == 5) {
              f.SavitzkyGolay5();
              s.append(" sg(5)");
            } else if (args.length == 1 && args[0] == 7) {
              f.SavitzkyGolay7();
              s.append(" sg(7)");
            }
          }
        }
        Log.d(getClass().getName(), s.toString());
        f.complete();
      }

      if (showHRZhist && hrzHist != null) {
        StringBuilder s = new StringBuilder("HR Zones:");
        double sum = 0;
        for (double aHrzHist : hrzHist) {
          sum += aHrzHist;
        }
        if (sum > 0) {
          for (int i = 0; i < hrzHist.length; i++) {
            hrzHist[i] = hrzHist[i] / sum;
            s.append(" ").append(hrzHist[i]);
          }
        }
        Log.d(getClass().getName(), s.toString());
        hrzonesBar.pushHrzData(hrzHist);
        hrzonesBar.invalidate();
      }

      int chartBackColor = Color.BLACK;
      if (chart.getBackground() instanceof android.graphics.drawable.ColorDrawable) {
        chartBackColor =
            ((android.graphics.drawable.ColorDrawable) chart.getBackground()).getColor();
      }
      int chartForeColor = Color.WHITE;
      if (chart.getForeground() instanceof android.graphics.drawable.ColorDrawable) {
        chartForeColor =
            ((android.graphics.drawable.ColorDrawable) chart.getForeground()).getColor();
      }
      // Data mapping to MPAndroidChart

      // dataSets and axisFormatters are added in pairs
      List<ILineDataSet<EntryFloat>> dataSets = new ArrayList<>();
      List<IAxisValueFormatter> axisFormatters = new ArrayList<>();
      chart.getAxisLeft().setEnabled(false);
      chart.getAxisRight().setEnabled(false);

      if (showPace && !velocityList.isEmpty()) {
        List<EntryFloat> entries = new ArrayList<>();
        for (DataPoint p : velocityList) {
          entries.add(new EntryFloat((float) p.getX(), (float) p.getY()));
        }
        LineDataSet<EntryFloat> velocityDataSet =
            new LineDataSet<>(
                entries,
                formatter.formatVelocityLabel(preferred_speedunit)
                    + " ("
                    + formatter.getVelocityUnit()
                    + ")");
        axisFormatters.add(
            0,
            new IAxisValueFormatter() {
              @NonNull
              @Override
              public String getFormattedValue(float value, AxisBase axis) {
                return formatter.formatVelocity(
                    Formatter.Format.TXT_SHORT, value, preferred_speedunit);
              }
            });
        setAxisAndLegend(
            velocityDataSet,
            ColorUtils.blendARGB(Color.BLUE, chartForeColor, 0.5f),
            axisFormatters.get(axisFormatters.size() - 1));
        dataSets.add(velocityDataSet);
      }

      if (showHR && !hrList.isEmpty()) {
        List<EntryFloat> hrEntries = new ArrayList<>();
        for (DataPoint p : hrList) {
          hrEntries.add(new EntryFloat((float) p.getX(), (float) p.getY()));
        }
        LineDataSet<EntryFloat> hrDataSet =
            new LineDataSet<>(
                hrEntries,
                graphTab.getContext().getString(org.runnerup.common.R.string.Heart_rate)
                    + " (bpm)");
        axisFormatters.add(
            new IAxisValueFormatter() {
              @NonNull
              @Override
              public String getFormattedValue(float value, AxisBase axis) {
                return formatter.formatHeartRate(Formatter.Format.TXT_SHORT, value);
              }
            });
        dataSets.add(hrDataSet);
        setAxisAndLegend(
            hrDataSet,
            ColorUtils.blendARGB(Color.RED, chartForeColor, 0.5f),
            axisFormatters.get(axisFormatters.size() - 1));
      }

      if (showElevation && !elevationList.isEmpty()) {
        List<DataPoint> plottedElevation = elevationList;
        double[] inverseScaleParams = null;
        if (dataSets.size() >= 2) {
          // only one right axis supported, fake it by scaling the formatter series to previous
          ILineDataSet<EntryFloat> targetSeries = dataSets.get(1);
          double[] sourceMinMax = getMinMax(elevationList);
          plottedElevation =
              scaleToRange(
                  elevationList,
                  sourceMinMax[0],
                  sourceMinMax[1],
                  targetSeries.getYMin(),
                  targetSeries.getYMax());
          // Keep inverse mapping local so formatter can convert scaled values back.
          inverseScaleParams =
              new double[] {
                targetSeries.getYMin(), targetSeries.getYMax(), sourceMinMax[0], sourceMinMax[1]
              };
        }
        List<EntryFloat> elevationEntries = new ArrayList<>();
        for (DataPoint p : plottedElevation) {
          elevationEntries.add(new EntryFloat((float) p.getX(), (float) p.getY()));
        }
        LineDataSet<EntryFloat> elevationDataSet =
            new LineDataSet<>(
                elevationEntries,
                graphTab.getContext().getString(org.runnerup.common.R.string.Elevation)
                    + " ("
                    + formatter.getElevationUnit()
                    + ")");
        elevationDataSet.setDrawFilled(true);
        elevationDataSet.setFillDrawable(
            new android.graphics.drawable.ColorDrawable(
                ColorUtils.blendARGB(elevationDataSet.getColor(), chartBackColor, 0.5f)));
        final double[] finalInverseScaleParams = inverseScaleParams;
        axisFormatters.add(
            new IAxisValueFormatter() {
              @NonNull
              @Override
              public String getFormattedValue(float value, AxisBase axis) {
                double displayValue = value;
                if (finalInverseScaleParams != null) {
                  displayValue =
                      scaleValueToRange(
                          value,
                          finalInverseScaleParams[0],
                          finalInverseScaleParams[1],
                          finalInverseScaleParams[2],
                          finalInverseScaleParams[3]);
                }
                return formatter.formatElevation(Formatter.Format.TXT_SHORT, displayValue);
              }
            });
        setAxisAndLegend(
            elevationDataSet,
            ColorUtils.setAlphaComponent(Color.GREEN, 64),
            axisFormatters.get(axisFormatters.size() - 1));
        dataSets.add(elevationDataSet);
      }

      chart.getLegend().setEnabled(false);
      chart.getDescription().setEnabled(false);
      chart.getXAxis().setTextColor(chartForeColor);
      chart.getXAxis().setTextSize(14f);
      chart.getXAxis().setPosition(info.appdev.charting.components.XAxis.XAxisPosition.BOTTOM);
      IAxisValueFormatter xAxisFormatter =
          new IAxisValueFormatter() {
            @NonNull
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
              return xAxis.formatValue(value);
            }
          };
      chart.getXAxis().setValueFormatter(xAxisFormatter);

      chart.setOnChartValueSelectedListener(
          new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(@NonNull EntryFloat e, @NonNull Highlight h) {
              // Find out the dataset line that was tapped
              int datasetIndex = h.getDataSetIndex();
              int axisIndex = axisFormatters.size() - 1 - datasetIndex;
              if (datasetIndex >= axisFormatters.size()
                  || axisIndex >= axisFormatters.size()
                  || axisIndex < 0) {
                return;
              }

              // Display the results
              assert chart.getData() != null;
              String message =
                  chart.getData().getDataSetByIndex(datasetIndex).getLabel()
                      + ": "
                      + axisFormatters.get(axisIndex).getFormattedValue(e.getY(), null)
                      + "\n"
                      + xAxis.formatLongValue(e.getX());

              Toast.makeText(chart.getContext(), message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected() {}
          });

      // reverse the order so first chart is rendered on top
      List<ILineDataSet<EntryFloat>> chartSets = new ArrayList<>();
      for (int i = dataSets.size() - 1; i >= 0; i--) {
        chartSets.add(dataSets.get(i));
      }
      chart.setData(new LineData(chartSets));
    }

    private double[] getMinMax(List<DataPoint> series) {
      double min = Double.POSITIVE_INFINITY;
      double max = Double.NEGATIVE_INFINITY;
      for (DataPoint point : series) {
        double y = point.getY();
        if (y < min) {
          min = y;
        }
        if (y > max) {
          max = y;
        }
      }
      if (!Double.isFinite(min) || !Double.isFinite(max)) {
        return new double[] {0.0, 0.0};
      }
      return new double[] {min, max};
    }

    private List<DataPoint> scaleToRange(
        List<DataPoint> source,
        double sourceMin,
        double sourceMax,
        double targetMin,
        double targetMax) {
      List<DataPoint> scaled = new ArrayList<>(source.size());
      if (sourceMax - sourceMin <= 0) {
        double mapped = targetMin + ((targetMax - targetMin) / 2.0);
        for (DataPoint point : source) {
          scaled.add(new DataPoint(point.getX(), mapped));
        }
        return scaled;
      }

      for (DataPoint point : source) {
        double mapped = scaleValueToRange(point.getY(), sourceMin, sourceMax, targetMin, targetMax);
        scaled.add(new DataPoint(point.getX(), mapped));
      }
      return scaled;
    }

    private double scaleValueToRange(
        double value, double sourceMin, double sourceMax, double targetMin, double targetMax) {
      double sourceRange = sourceMax - sourceMin;
      if (sourceRange <= 0) {
        return targetMin + ((targetMax - targetMin) / 2.0);
      }
      return targetMin + ((value - sourceMin) * (targetMax - targetMin) / sourceRange);
    }

    private int[] getArgs(String s) {
      try {
        s = s.substring(s.indexOf('(') + 1);
        s = s.substring(0, s.indexOf(')'));
        String[] sargs = s.split(",");
        int[] args = new int[sargs.length];
        for (int i = 0; i < args.length; i++) {
          args[i] = Integer.parseInt(sargs[i]);
        }
        return args;
      } catch (Exception e) {
        e.printStackTrace();
        return new int[0];
      }
    }

    public boolean HasHRZHist() {
      return showHR && showHRZhist;
    }

    class GraphFilter {

      final double[] data;
      final List<DataPoint> source;

      GraphFilter(List<DataPoint> velocityList) {
        source = velocityList;
        data = new double[velocityList.size()];
        for (int i = 0; i < velocityList.size(); i++) data[i] = velocityList.get(i).getY();
      }

      void complete() {
        for (int i = 0; i < source.size(); i++)
          source.set(i, new DataPoint(source.get(i).getX(), data[i]));
      }

      void init(double[] window, double val) {
        for (int j = 0; j < window.length - 1; j++) window[j] = val;
      }

      void shiftLeft(double[] window, double newVal) {
        System.arraycopy(window, 1, window, 0, window.length - 1);
        window[window.length - 1] = newVal;
      }

      /** Perform in place moving average */
      void movingAvergage(int windowLen) {
        double[] window = new double[windowLen];
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
          shiftLeft(window, (i + mid) < data.length ? data[i + mid] : avg_velocity);
          sum += window[last];
        }
      }

      /** Perform in place moving average */
      void movingMedian(int windowLen) {
        double[] window = new double[windowLen];
        init(window, data[0]);

        final int mid = (window.length - 1) / 2;
        for (int i = 0; i < data.length && i <= mid; i++) {
          window[i + mid] = data[i];
        }

        double[] sort = new double[windowLen];
        for (int i = 0; i < data.length; i++) {
          System.arraycopy(window, 0, sort, 0, windowLen);
          Arrays.sort(sort);
          data[i] = sort[mid];
          shiftLeft(window, (i + mid) < data.length ? data[i + mid] : avg_velocity);
        }
      }

      /** Perform in place SavitzkyGolay windowLen = 5 */
      void SavitzkyGolay5() {
        final int len = 5;
        double[] window = new double[len];
        init(window, data[0]);

        final int mid = (window.length - 1) / 2;
        for (int i = 0; i < data.length && i <= mid; i++) {
          window[i + mid] = data[i];
        }
        for (int i = 0; i < data.length; i++) {
          double newY =
              (-3 * window[0] + 12 * window[1] + 17 * window[2] + 12 * window[3] - 3 * window[4])
                  / 35;
          data[i] = newY;
          shiftLeft(window, (i + mid) < data.length ? data[i + mid] : avg_velocity);
        }
      }

      /** Perform in place SavitzkyGolay windowLen = 7 */
      void SavitzkyGolay7() {
        final int len = 7;
        double[] window = new double[len];
        init(window, data[0]);

        final int mid = (window.length - 1) / 2;
        for (int i = 0; i < data.length && i <= mid; i++) {
          window[i + mid] = data[i];
        }
        for (int i = 0; i < data.length; i++) {
          double newY =
              (-2 * window[0]
                      + 3 * window[1]
                      + 6 * window[2]
                      + 7 * window[3]
                      + 6 * window[4]
                      + 3 * window[5]
                      - 2 * window[6])
                  / 21;
          data[i] = newY;
          shiftLeft(window, (i + mid) < data.length ? data[i + mid] : avg_velocity);
        }
      }

      void KolmogorovZurbenko(int n, int len) {
        for (int i = 0; i < n; i++) movingAvergage(len);
      }
    }
  }
}
