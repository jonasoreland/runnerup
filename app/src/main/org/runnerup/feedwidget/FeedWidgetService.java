package org.runnerup.feedwidget;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.feed.FeedImageLoader;
import org.runnerup.feed.FeedList;
import org.runnerup.util.Formatter;
import org.runnerup.workout.Sport;

import java.text.DateFormat;


public class FeedWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new RemoteViewsFactory() {
            private FeedList data = null;
            private Formatter formatter = null;
            private SQLiteDatabase mDB = null;

            @Override
            public void onCreate() {
                formatter = new Formatter(getApplicationContext());
                mDB = DBHelper.getReadableDatabase(getApplicationContext());
            }


            @Override
            public void onDestroy() {
                DBHelper.closeDB(mDB);
            }

            @Override
            public void onDataSetChanged() {
                data = new FeedList(mDB);
                data.load();
            }

            @Override
            public int getCount() {
                return data != null ? data.getList().size() : 0;
            }

            @Override
            public RemoteViews getViewAt(int position) {
                if (position == AdapterView.INVALID_POSITION ||
                        data == null || getCount() <= position) {
                    Log.w(getClass().getSimpleName(), "getViewAt: " + position + " is invalid position!");
                    return null;
                }
                return getViewFactorizedAt(position);
            }

            // code is mainly a copy/paste from FeedActivity.java:getViewAt - however it could/should be done properly byy refactoring
            private RemoteViews getViewFactorizedAt(int position) {
                RemoteViews rv = null;

                ContentValues tmp = data.getList().get(position);
                if (FeedList.isActivity(tmp)) {
                    Intent fillInIntent = new Intent();
                    rv = new RemoteViews(getPackageName(), R.layout.feed_widget_item);
                    rv.setOnClickFillInIntent(R.id.feed_widget_item_layout, fillInIntent);
                    String src = getSynchronizerName(tmp.getAsLong(Constants.DB.FEED.ACCOUNT_ID));
                    if (tmp.containsKey(Constants.DB.FEED.USER_IMAGE_URL)) {
                        Bitmap b = FeedImageLoader.LoadImageSync(tmp.getAsString(Constants.DB.FEED.USER_IMAGE_URL));
                        if (b!=null) {
                            rv.setImageViewBitmap(R.id.feed_widget_item_avatar, b);
                        }
                    }

                    long startTime = tmp.getAsLong(Constants.DB.FEED.START_TIME);
                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(getApplicationContext());
                    String timeString = timeFormat.format(startTime);
                    DateFormat dateFormat = android.text.format.DateFormat.getLongDateFormat(getApplicationContext());
                    String dateString = dateFormat.format(startTime);
                    rv.setTextViewText(R.id.feed_widget_item_start_time, String.format(getResources().getString(R.string.formatting_date_at_time), dateString, timeString));

                    // String time = formatter.formatTime(Formatter.Format.TXT,
                    // tmp.getAsLong(DB.FEED.START_TIME) / 1000);
                    rv.setTextViewText(R.id.feed_widget_item_source, src); // + " (" + time + ")");

                    String name = formatter.formatName(tmp.getAsString(Constants.DB.FEED.USER_FIRST_NAME),
                            tmp.getAsString(Constants.DB.FEED.USER_LAST_NAME));
                    String sport = Sport.textOf(getResources(), tmp.getAsInteger(Constants.DB.FEED.FEED_SUBTYPE));
                    rv.setTextViewText(R.id.feed_widget_item_header, name + " trained " + sport);
                    if (tmp.containsKey(Constants.DB.FEED.DISTANCE) || tmp.containsKey(Constants.DB.FEED.DURATION)) {
                        double distance = 0;
                        long duration = 0;
                        if (tmp.containsKey(Constants.DB.FEED.DISTANCE))
                            distance = tmp.getAsDouble(Constants.DB.FEED.DISTANCE);
                        if (tmp.containsKey(Constants.DB.FEED.DURATION))
                            duration = tmp.getAsLong(Constants.DB.FEED.DURATION);

                        StringBuilder p = new StringBuilder();
                        if (duration != 0) {
                            p.append(formatter.formatElapsedTime(Formatter.Format.TXT_LONG, duration));
                        }

                        if (distance != 0) {
                            if (p.length() > 0)
                                p.append(", ");
                            p.append(formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(distance)));
                        }

                        if (duration != 0) {
                            p.append(", ");
                            p.append(formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_LONG, distance / duration));
                        }
                        if (p.length() > 0)
                            rv.setTextViewText(R.id.feed_widget_item_summary, p.toString());
                        else
                            rv.setViewVisibility(R.id.feed_widget_item_summary, View.GONE);
                    } else {
                        rv.setViewVisibility(R.id.feed_widget_item_summary, View.GONE);
                    }

                    if (tmp.containsKey(Constants.DB.FEED.NOTES)) {
                        rv.setTextViewText(R.id.feed_widget_item_notes, tmp.getAsString(Constants.DB.FEED.NOTES));
                    } else {
                        rv.setViewVisibility(R.id.feed_widget_item_notes, View.GONE);
                    }
                } else {
                    Log.e(getClass().getSimpleName(), "Unexpected feed type");
                }
                return rv;
            }

            private String getSynchronizerName(long id) {
                String[] from = new String[]{ "name", Constants.DB.ACCOUNT.NAME };
                String[] args = { "" + id };
                Cursor c = mDB.query(Constants.DB.ACCOUNT.TABLE, from, "_id = ?",
                        args, null, null, null, null);
                c.moveToFirst();
                ContentValues config = DBHelper.get(c);
                String name = config.getAsString(Constants.DB.ACCOUNT.NAME);
                c.close();
                return name;
            }

            @Override
            public RemoteViews getLoadingView() {
                return null;
            }

            @Override
            public int getViewTypeCount() {
                return 2;
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public boolean hasStableIds() {
                return true;
            }
        };
    }
}
