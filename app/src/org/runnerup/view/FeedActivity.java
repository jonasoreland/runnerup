/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RemoteViews;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.export.SyncManager;
import org.runnerup.export.SyncManager.Callback;
import org.runnerup.export.Synchronizer.Status;
import org.runnerup.feed.FeedImageLoader;
import org.runnerup.feed.FeedList;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB.FEED;
import org.runnerup.feedwidget.FeedWidgetProvider;
import org.runnerup.util.Formatter;

import java.io.InputStream;
import java.net.URL;
import java.text.DateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.WeakHashMap;

@TargetApi(Build.VERSION_CODES.FROYO)
public class FeedActivity extends Activity implements Constants {

    DBHelper mDBHelper = null;
    SyncManager syncManager = null;
    Formatter formatter = null;

    FeedList feed = null;
    ListView feedList = null;
    FeedListAdapter feedAdapter = null;

    Button refreshButton = null;
    LinearLayout feedHeader = null;
    TextView feedStatus = null;
    ProgressBar feedProgressBar = null;

    Button feedAccountButton = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.feed);

        mDBHelper = new DBHelper(this);
        syncManager = new SyncManager(this);
        formatter = new Formatter(this);
        feed = new FeedList(mDBHelper);
        feed.load(); // load from DB

        feedAdapter = new FeedListAdapter(this, feed);
        feedList = (ListView) findViewById(R.id.feed_list);
        feedList.setAdapter(feedAdapter);
        feedList.setDividerHeight(2);

        refreshButton = (Button) findViewById(R.id.refresh_feed_button);
        refreshButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                feed.reset();
                feed.getList().clear();
                feedAdapter.feed.clear();
                feedAdapter.notifyDataSetInvalidated();
                startSync();
            }
        });

        feedAccountButton = (Button) findViewById(R.id.feed_account_button);
        feedAccountButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(FeedActivity.this,
                        AccountListActivity.class);
                startActivityForResult(i, 0);
            }
        });

        feedHeader = (LinearLayout) findViewById(R.id.feed_header);
        feedStatus = (TextView) findViewById(R.id.feed_status);
        startSync();
    }

    void startSync() {
        syncManager.clear();
        Set<String> set = syncManager.feedSynchronizersSet();
        if (!set.isEmpty()) {
            feedAccountButton.setVisibility(View.GONE);
            refreshButton.setVisibility(View.VISIBLE);
            feedHeader.setVisibility(View.VISIBLE);
            refreshButton.setEnabled(false);
            feedStatus.setText(getString(R.string.synchronizing_feed));
            syncManager.synchronizeFeed(syncDone, set, feed, null);
        } else {
            feedHeader.setVisibility(View.GONE);
            refreshButton.setVisibility(View.GONE);
            feedAccountButton.setVisibility(View.VISIBLE);
        }
    }

    final Callback syncDone = new Callback() {
        @Override
        public void run(String synchronizerName, Status status) {
            refreshButton.setEnabled(true);
            feedHeader.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDBHelper.close();
        syncManager.close();
        feedAdapter.close();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        startSync();
    }

    class FeedListAdapter extends BaseAdapter implements Observer {

        final Context context;
        List<ContentValues> feed;
        final FeedList feedList;
        final LayoutInflater layoutInflator;

        FeedListAdapter(Context context, FeedList feedList) {
            this.context = context;
            this.feedList = feedList;
            this.feed = FeedList.addHeaders(feedList.getList());
            feedList.addObserver(this);
            this.layoutInflator = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        public void close() {
            feedList.deleteObserver(this);
        }

        @Override
        public int getCount() {
            return feed.size();
        }

        @Override
        public Object getItem(int arg0) {
            if (arg0 <= feed.size())
                return feed.get(arg0);
            return null;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public long getItemId(int arg0) {
            return feed.get(arg0).hashCode();
        }

        @Override
        public View getView(int arg0, View arg1, ViewGroup parent) {
            ContentValues tmp = feed.get(arg0);

            if (FeedList.isHeaderDate(tmp)) {
                View v = layoutInflator.inflate(R.layout.feed_row_date_header, parent, false);
                TextView tv = (TextView) v.findViewById(R.id.feed_activity_date_header);
                DateFormat a = android.text.format.DateFormat.getLongDateFormat(context);
                tv.setText(a.format(tmp.getAsLong(DB.FEED.START_TIME)));
                return v;
            } else if (FeedList.isActivity(tmp)) {
                View v = layoutInflator.inflate(R.layout.feed_row_activity, parent, false);
                final ImageView iv = (ImageView) v.findViewById(R.id.feed_image);
                TextView tv0 = (TextView) v.findViewById(R.id.feed_activity_source);
                TextView tv1 = (TextView) v.findViewById(R.id.feed_activity_header);
                TextView tv2 = (TextView) v.findViewById(R.id.feed_activity_summary);
                TextView tv3 = (TextView) v.findViewById(R.id.feed_activity_notes);

                String src = syncManager.getSynchronizer(tmp.getAsLong(FEED.ACCOUNT_ID)).getName();
                if (tmp.containsKey(DB.FEED.USER_IMAGE_URL)) {
                    FeedImageLoader.LoadImageAsync(tmp.getAsString(DB.FEED.USER_IMAGE_URL), new FeedImageLoader.Callback() {
                        @Override
                        public void run(String url, Bitmap b) {
                            iv.setImageBitmap(b);
                        }
                    });
                }

                // String time = formatter.formatTime(Formatter.TXT,
                // tmp.getAsLong(DB.FEED.START_TIME) / 1000);
                tv0.setText(src); // + " (" + time + ")");

                String name = formatter.formatName(tmp.getAsString(DB.FEED.USER_FIRST_NAME),
                        tmp.getAsString(DB.FEED.USER_LAST_NAME));
                String sport = FeedActivity.GetSportActivity(tmp);
                tv1.setText(name + " trained " + sport);
                if (tmp.containsKey(DB.FEED.DISTANCE) || tmp.containsKey(DB.FEED.DURATION)) {
                    double distance = 0;
                    long duration = 0;
                    if (tmp.containsKey(DB.FEED.DISTANCE))
                        distance = tmp.getAsDouble(DB.FEED.DISTANCE);
                    if (tmp.containsKey(DB.FEED.DURATION))
                        duration = tmp.getAsLong(DB.FEED.DURATION);

                    StringBuilder p = new StringBuilder();
                    if (duration != 0) {
                        p.append(formatter.formatElapsedTime(Formatter.TXT_LONG, duration));
                    }

                    if (distance != 0) {
                        if (p.length() > 0)
                            p.append(", ");
                        p.append(formatter.formatDistance(Formatter.TXT_SHORT, Math.round(distance)));
                    }

                    if (distance != 0 && duration != 0) {
                        double pace = duration / distance;
                        p.append(", ");
                        p.append(formatter.formatPace(Formatter.TXT_LONG, pace));
                    }
                    if (p.length() > 0)
                        tv2.setText(p.toString());
                    else
                        tv2.setVisibility(View.GONE);
                } else {
                    tv2.setVisibility(View.GONE);
                }

                if (tmp.containsKey(DB.FEED.NOTES)) {
                    tv3.setText(tmp.getAsString(DB.FEED.NOTES));
                } else {
                    tv3.setVisibility(View.GONE);
                }

                // c.put(FEED.FEED_TYPE, FEED.FEED_TYPE_ACTIVITY);
                // c.put(FEED.FEED_SUBTYPE,
                // getTrainingType(o.getInt("TrainingTypeID"),
                // o.getString("TrainingTypeName")));
                // c.put(FEED.FEED_TYPE_STRING,
                // o.getString("TrainingTypeName"));
                // c.put(FEED.START_TIME,
                // parseDateTime(o.getString("DateTime")));
                // if (!o.isNull("Distance"))
                // c.put(FEED.DISTANCE, 1000 * o.getDouble("Distance"));
                // if (!o.isNull("Duration"))
                // c.put(FEED.DURATION,
                // getDuration(o.getJSONObject("Duration")));
                // if (!o.isNull("PersonID"))
                // c.put(FEED.USER_ID, o.getInt("PersonID"));
                // if (!o.isNull("Firstname"))
                // c.put(FEED.USER_FIRST_NAME, o.getString("Firstname"));
                // if (!o.isNull("Lastname"))
                // c.put(FEED.USER_LAST_NAME, o.getString("Lastname"));
                // if (!o.isNull("PictureURL"))
                // c.put(FEED.USER_IMAGE_URL,
                // o.getString("PictureURL").replace("~/",
                // "http://www.funbeat.se/"));
                // if (!o.isNull("Description"))
                // c.put(FEED.NOTES, o.getString("Description"));
                // c.put(FEED.URL,
                // "http://www.funbeat.se/training/show.aspx?TrainingID="
                // + Long.toString(o.getLong("ID")));
                return v;
            } else {
                TextView tv = new TextView(context);
                tv.setText(FeedList.toString(tmp));
                return tv;
            }
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            if (position < feed.size())
                return !FeedList.isHeaderDate(feed.get(position));
            return false;
        }

        @Override
        public void update(Observable observable, Object data) {
            if (data == null) {
                feed = FeedList.addHeaders(feedList.getList());
                feedAdapter.notifyDataSetInvalidated();
                feedAdapter.notifyDataSetChanged();
            } else {
                String synchronizerName = (String) data;
                feedStatus.setText(getString(R.string.Synchronizing) + " " + synchronizerName);
            }
        }
    }

    public static String GetSportActivity(ContentValues tmp) {
        switch (tmp.getAsInteger(DB.FEED.FEED_SUBTYPE)) {
            case DB.ACTIVITY.SPORT_RUNNING:
                return "running";
            case DB.ACTIVITY.SPORT_BIKING:
                return "biking";
            case DB.ACTIVITY.SPORT_ORIENTEERING:
                return "orienteering";
            case DB.ACTIVITY.SPORT_WALKING:
                return "walking";
            case DB.ACTIVITY.SPORT_OTHER:
            default:
                if (tmp.containsKey(DB.FEED.FEED_TYPE_STRING))
                    return tmp.getAsString(DB.FEED.FEED_TYPE_STRING);

                return "something";
        }
    }
}
