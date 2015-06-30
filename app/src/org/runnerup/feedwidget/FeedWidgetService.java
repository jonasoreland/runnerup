package org.runnerup.feedwidget;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.export.SyncManager;
import org.runnerup.feed.FeedImageLoader;
import org.runnerup.feed.FeedList;
import org.runnerup.util.Formatter;
import org.runnerup.view.FeedActivity;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class FeedWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new RemoteViewsFactory() {
            private FeedList data = null;
            private Formatter formatter = null;
            private DBHelper mDBHelper = null;

            @Override
            public void onCreate() {
                formatter = new Formatter(getApplicationContext());
                mDBHelper = new DBHelper(getApplicationContext());
            }


            @Override
            public void onDestroy() {
                mDBHelper.close();
            }

            @Override
            public void onDataSetChanged() {
                data = new FeedList(mDBHelper);
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
                final RemoteViews rv = getViewFactorizedAt(position);
                return rv;
            }

            // code is mainly a copy/paste from FeedActivity.java:getViewAt - however it could/should be done properly byy refactoring
            private RemoteViews getViewFactorizedAt(int position) {
                RemoteViews rv = null;

                ContentValues tmp = data.getList().get(position);
                Intent fillInIntent= new Intent();
                if (FeedList.isActivity(tmp)) {
                    rv = new RemoteViews(getPackageName(), R.layout.feed_widget_item);
                    rv.setOnClickFillInIntent(R.id.feed_widget_item, fillInIntent);
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

                    // String time = formatter.formatTime(Formatter.TXT,
                    // tmp.getAsLong(DB.FEED.START_TIME) / 1000);
                    rv.setTextViewText(R.id.feed_widget_item_source, src); // + " (" + time + ")");

                    String name = formatter.formatName(tmp.getAsString(Constants.DB.FEED.USER_FIRST_NAME),
                            tmp.getAsString(Constants.DB.FEED.USER_LAST_NAME));
                    String sport = FeedActivity.GetSportActivity(tmp);
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
                } else {
                    Log.e(getClass().getSimpleName(), "Unexpected feed type");
                }
                return rv;
            }

            private String getSynchronizerName(long id) {
                String from[] = new String[]{"name", Constants.DB.ACCOUNT.NAME, Constants.DB.ACCOUNT.AUTH_CONFIG, Constants.DB.ACCOUNT.FLAGS
                };
                String args[] = {
                        "" + id
                };
                DBHelper db = new DBHelper(getApplicationContext());
                Cursor c = db.getReadableDatabase().query(Constants.DB.ACCOUNT.TABLE, from, "_id = ?",
                        args, null, null, null, null);
                String name = "?";
                c.moveToFirst();
                ContentValues config = DBHelper.get(c);
                name = config.getAsString("name");
                c.close();
                db.close();
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
