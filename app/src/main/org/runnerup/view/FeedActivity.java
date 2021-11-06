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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB.FEED;
import org.runnerup.db.DBHelper;
import org.runnerup.export.SyncManager;
import org.runnerup.export.SyncManager.Callback;
import org.runnerup.export.Synchronizer;
import org.runnerup.feed.FeedImageLoader;
import org.runnerup.feed.FeedList;
import org.runnerup.util.Formatter;
import org.runnerup.workout.Sport;

import java.text.DateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;


public class FeedActivity extends AppCompatActivity implements Constants {

    private SQLiteDatabase mDB = null;
    private SyncManager syncManager = null;
    private Formatter formatter = null;

    private FeedList feed = null;
    private ListView feedList = null;
    private FeedListAdapter feedAdapter = null;

    private LinearLayout feedProgress = null;
    private TextView feedProgressLabel = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.feed);

        Toolbar actionbar = findViewById(R.id.feed_actionbar);
        actionbar.inflateMenu(R.menu.feed_menu);
        actionbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_configure_accounts) {
                configureAccounts();
                return true;
            }
            if (id == R.id.menu_refresh) {
                refresh();
                return true;
            }
            return false;
        });

        syncManager = new SyncManager(this);
        formatter = new Formatter(this);
        mDB = DBHelper.getWritableDatabase(this);
        feed = new FeedList(mDB);
        feed.load(); // load from DB

        feedAdapter = new FeedListAdapter(this, feed);
        ListView feedList = findViewById(R.id.feed_list);
        feedList.setAdapter(feedAdapter);
        feedList.setDividerHeight(2);

        feedProgress = findViewById(R.id.feed_progress);
        feedProgressLabel = findViewById(R.id.feed_progress_label);
        startSync();

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    private void refresh() {
        feed.reset();
        feed.getList().clear();
        feedAdapter.feed.clear();
        feedAdapter.notifyDataSetInvalidated();
        startSync();
    }

    private void configureAccounts() {
        Intent i = new Intent(FeedActivity.this,
                AccountListActivity.class);
        startActivityForResult(i, 0);
    }

    private void startSync() {
        syncManager.clear();
        Set<String> set = syncManager.feedSynchronizersSet(this);
        if (!set.isEmpty()) {
            feedProgress.setVisibility(View.VISIBLE);
            feedProgressLabel.setText(R.string.synchronizing_feed);
            syncManager.synchronizeFeed(syncDone, set, feed, null);
        } else {
            feedProgress.setVisibility(View.GONE);
        }
    }

    private final Callback syncDone = (synchronizerName, status) -> feedProgress.setVisibility(View.GONE);

    @Override
    public void onDestroy() {
        super.onDestroy();
        DBHelper.closeDB(mDB);
        syncManager.close();
        feedAdapter.close();
    }

    public void onBackPressed() {
        Intent intent = new Intent(this, MainLayout.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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

        void close() {
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
                TextView tv = v.findViewById(R.id.feed_date_header);
                DateFormat a = android.text.format.DateFormat.getLongDateFormat(context);
                tv.setText(a.format(tmp.getAsLong(DB.FEED.START_TIME)));
                return v;

            } else if (FeedList.isActivity(tmp)) {

                View v = layoutInflator.inflate(R.layout.feed_row_activity, parent, false);
                final ImageView ivAvatar = v.findViewById(R.id.feed_avatar);
                ImageView ivSport = v.findViewById(R.id.feed_sport_emblem);
                TextView tvPerson = v.findViewById(R.id.feed_person);
                TextView tvSource = v.findViewById(R.id.feed_source);
                TextView tvSport = v.findViewById(R.id.feed_sport);
                TextView tvDistance = v.findViewById(R.id.feed_distance);
                TextView tvDuration = v.findViewById(R.id.feed_duration);
                TextView tvPace = v.findViewById(R.id.feed_pace);
                TextView tvNotes = v.findViewById(R.id.feed_notes);

                // avatar
                if (tmp.containsKey(DB.FEED.USER_IMAGE_URL)) {
                    FeedImageLoader.LoadImageAsync(tmp.getAsString(DB.FEED.USER_IMAGE_URL), (url, b) -> ivAvatar.setImageBitmap(b));
                }

                // String time = formatter.formatTime(Formatter.TXT,
                // tmp.getAsLong(DB.FEED.START_TIME) / 1000);

                // person
                String name = formatter.formatName(tmp.getAsString(DB.FEED.USER_FIRST_NAME),
                        tmp.getAsString(DB.FEED.USER_LAST_NAME));
                tvPerson.setText(name);

                // source
                Synchronizer sync = syncManager.getSynchronizer(tmp.getAsLong(FEED.ACCOUNT_ID));
                if (sync != null){
                    String src = sync.getName();
                    tvSource.setText(src);
                }

                // sport
                int sportId = tmp.getAsInteger(DB.FEED.FEED_SUBTYPE);
                Drawable sportDrawable = AppCompatResources.getDrawable(context, Sport.drawableColored16Of(sportId));
                ivSport.setImageDrawable(sportDrawable);

                String sportName = Sport.textOf(getResources(), sportId);
                int sportColor = ContextCompat.getColor(FeedActivity.this, Sport.colorOf(sportId));
                tvSport.setText(sportName);
                tvSport.setTextColor(sportColor);

                // distance
                double distance = 0;
                if (tmp.containsKey(DB.FEED.DISTANCE)) {
                    distance = tmp.getAsDouble(DB.FEED.DISTANCE);
                    tvDistance.setText(formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(distance)));
                }

                // duration
                long duration = 0;
                if (tmp.containsKey(DB.FEED.DURATION)) {
                    duration = tmp.getAsLong(DB.FEED.DURATION);
                    tvDuration.setText(formatter.formatElapsedTime(Formatter.Format.TXT_LONG, duration));
                }

                // pace
                if (duration != 0) {
                    tvPace.setText(formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_LONG, distance/duration));
                }

                // notes
                if (tmp.containsKey(DB.FEED.NOTES)) {
                    tvNotes.setVisibility(View.VISIBLE);
                    tvNotes.setText(tmp.getAsString(DB.FEED.NOTES));
                } else {
                    tvNotes.setVisibility(View.GONE);
                }

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
            return position < feed.size() && !FeedList.isHeaderDate(feed.get(position));
        }

        @Override
        public void update(Observable observable, Object data) {
            if (data == null) {
                feed = FeedList.addHeaders(feedList.getList());
                feedAdapter.notifyDataSetInvalidated();
                feedAdapter.notifyDataSetChanged();
            } else {
                String synchronizerName = (String) data;
                feedProgressLabel.setText(String.format(Locale.getDefault(), "%s: %s", getString(R.string.Synchronizing), synchronizerName)); //TODO parameter
            }
        }
    }
}
