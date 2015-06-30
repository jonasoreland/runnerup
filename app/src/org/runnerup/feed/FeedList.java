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

package org.runnerup.feed;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;

import org.runnerup.db.DBHelper;
import org.runnerup.common.util.Constants;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Observable;
import java.util.Set;
import java.util.TimeZone;

@TargetApi(Build.VERSION_CODES.FROYO)
public class FeedList extends Observable implements Constants {

    static final int MAX_ITEMS = 50;
    static final long TIME_MARGIN = 5 * 60; // 5 minutes

    final DBHelper mDBHelper;
    List<ContentValues> list = new ArrayList<ContentValues>();
    boolean filterDuplicates = true;

    public FeedList(DBHelper db) {
        mDBHelper = db;
    }

    public void setFilterDuplicates(boolean val) {
        filterDuplicates = val;
    }

    public boolean getFilterDuplicates() {
        return filterDuplicates;
    }

    public void load() {
        list.clear();
        SQLiteDatabase db = mDBHelper.getReadableDatabase();
        Cursor c = db.query(DB.FEED.TABLE, null, null, null, null, null, DB.FEED.START_TIME
                + " desc", Integer.toString(MAX_ITEMS));
        if (c.moveToFirst()) {
            do {
                list.add(DBHelper.get(c));
            } while (c.moveToNext());
        }
        c.close();
        db.close();
    }

    public void reset() {
        SQLiteDatabase db = mDBHelper.getWritableDatabase();
        db.execSQL("DELETE FROM " + DB.FEED.TABLE);
        db.close();
    }

    public void prune() {
        if (list.size() >= MAX_ITEMS + 1) {
            SQLiteDatabase db = mDBHelper.getReadableDatabase();
            ContentValues tmp = list.get(MAX_ITEMS);
            long start_time = tmp.getAsLong(DB.FEED.START_TIME);
            db.execSQL("DELETE FROM " + DB.FEED.TABLE + " WHERE " + DB.FEED.START_TIME + " < "
                    + start_time);
            db.close();
            List<ContentValues> swap = list.subList(0, MAX_ITEMS);
            list = swap;
        }
    }

    public List<ContentValues> getList() {
        return list;
    }

    public class FeedUpdater {
        SQLiteDatabase mDB = null;
        List<ContentValues> currList = null;
        List<ContentValues> addList = null;
        String synchronizer = null;
        int added = 0;
        int discarded = 0;

        FeedUpdater() {
            mDB = mDBHelper.getWritableDatabase();
            currList = new ArrayList<ContentValues>(list.size());
            for (ContentValues c : list) { // initialize list with already
                                           // present items
                if (!isHeaderDate(c))
                    currList.add(c);
            }
            addList = new ArrayList<ContentValues>(list.size());
        }

        public void start(String synchronizerName) {
            synchronizer = synchronizerName;
            added = discarded = 0;
            setChanged();
            notifyObservers(synchronizerName);
        }

        // this method is called by different thread (not UI thread)
        public void addAll(List<ContentValues> result) {
            for (ContentValues c : result) {
                add(c);
            }
        }

        // this method is called by different thread (not UI thread)
        public void add(ContentValues values) {
            long startTime = values.getAsLong(DB.FEED.START_TIME);
            long endTime = startTime;
            if (values.containsKey(DB.FEED.DURATION))
                endTime = startTime + values.getAsLong(DB.FEED.DURATION);

            int endIndex = findEndIndex(startTime - TIME_MARGIN);
            int startIndex = findStartIndex(endIndex, endTime + TIME_MARGIN);

            for (int i = startIndex; i <= endIndex; i++) {
                ContentValues c = currList.get(i);
                if (match(values, c, filterDuplicates) == true) {
                    // Set already contains matching row...skip this
                    discarded++;
                    return;
                }
            }
            added++;
            addList.add(values); // no match, add this row
            mDB.insert(DB.FEED.TABLE, null, values);
        }

        private int findEndIndex(long time) {
            return currList.size() - 1;
        }

        private int findStartIndex(int startIndex, long l) {
            return 0;
        }

        public void complete() {
            if (addList.size() > 0) {
                mergeLists(currList, addList);
                list = currList;
                setChanged();
                notifyObservers(null);
            }
            mDB.close();
            mDB = null;
            prune();
            Log.i(getClass().getSimpleName(), "FeedUpdater: " + synchronizer + ", added: " + added + ", discarded: "
                    + discarded);
        }

        private void mergeLists(List<ContentValues> l1, List<ContentValues> l2) {
            if (l2.isEmpty())
                return;
            l1.addAll(l2);
            FeedList.sort(l1);
            l2.clear();
        }
    }

    public FeedUpdater getUpdater() {
        return new FeedUpdater();
    }

    public static List<ContentValues> addHeaders(List<ContentValues> oldList) {
        List<ContentValues> newList = new ArrayList<ContentValues>(oldList.size());
        Calendar lastDate = Calendar.getInstance();
        lastDate.setTimeInMillis(0);
        Calendar tmp = Calendar.getInstance();
        for (ContentValues anOldList : oldList) {
            if (isHeaderDate(anOldList)) {
                setDate(lastDate, anOldList);
            } else {
                setDate(tmp, anOldList);
                if (compare(tmp, lastDate) != 0) {
                    newList.add(newHeaderDate(tmp.getTimeInMillis()));
                    lastDate.setTime(tmp.getTime());
                }
                newList.add(anOldList);
            }
        }
        return newList;
    }

    private static int compare(Calendar c1, Calendar c2) {
        int res = c1.get(Calendar.YEAR) - c2.get(Calendar.YEAR);
        if (res == 0) {
            res = c1.get(Calendar.DAY_OF_YEAR) - c2.get(Calendar.DAY_OF_YEAR);
        }
        return res;
    }

    private static ContentValues newHeaderDate(long time) {
        ContentValues tmp = new ContentValues();
        tmp.put(DB.FEED.START_TIME, time);
        tmp.put(DB.FEED.FEED_TYPE, DB.FEED.FEED_TYPE_EVENT);
        tmp.put(DB.FEED.FEED_SUBTYPE, DB.FEED.FEED_TYPE_EVENT_DATE_HEADER);
        return tmp;
    }

    private static void setDate(Calendar lastDate, ContentValues tmp) {
        lastDate.setTimeInMillis(tmp.getAsLong(DB.FEED.START_TIME));
        lastDate.set(Calendar.HOUR, 0);
        lastDate.set(Calendar.MINUTE, 0);
        lastDate.set(Calendar.SECOND, 0);
        lastDate.set(Calendar.MILLISECOND, 0);
        lastDate.setTimeInMillis(lastDate.getTimeInMillis());
    }

    public static boolean isHeaderDate(ContentValues tmp) {
        return tmp.getAsInteger(DB.FEED.FEED_TYPE) == DB.FEED.FEED_TYPE_EVENT &&
                tmp.getAsInteger(DB.FEED.FEED_SUBTYPE) == DB.FEED.FEED_TYPE_EVENT_DATE_HEADER;
    }

    public static boolean isActivity(ContentValues tmp) {
        return tmp.getAsInteger(DB.FEED.FEED_TYPE) == DB.FEED.FEED_TYPE_ACTIVITY;
    }

    public static boolean match(ContentValues c0, ContentValues c1, boolean filterDuplicates) {
        boolean same_account = c0.getAsLong(DB.FEED.ACCOUNT_ID) == c1.getAsLong(DB.FEED.ACCOUNT_ID);
        if (same_account) {
            /**
             * Always filter duplicates from same account
             */
            if (c0.containsKey(DB.FEED.EXTERNAL_ID) && c1.containsKey(DB.FEED.EXTERNAL_ID)) {
                if (c0.getAsString(DB.FEED.EXTERNAL_ID).contentEquals(
                        c1.getAsString(DB.FEED.EXTERNAL_ID)))
                    return true;
            }
            // Same account must be identical to match
            // (this is a theoretical problem with upgrade...)
            Set<Entry<String, Object>> v1 = c0.valueSet();
            for (Entry<String, Object> val : v1) {
                if ("_id".contentEquals(val.getKey()))
                    continue;
                if (!c1.containsKey(val.getKey())) {
                    return false;
                }
                if (!val.getValue().toString().contentEquals(c1.getAsString(val.getKey())))
                    return false;
            }
            int i1 = v1.contains("_id") ? 1 : 0;
            int i2 = c1.valueSet().contains("_id") ? 1 : 0;
            if ((v1.size() - i1) != (c1.valueSet().size() - i2)) {
                return false;
            }

            return true;
        }

        if (!filterDuplicates)
            return false;

        boolean print = false; // enable printout of match failure

        String keys[] = {
                DB.FEED.FEED_TYPE,
                DB.FEED.FEED_SUBTYPE,
                DB.FEED.FEED_TYPE_STRING,
                DB.FEED.USER_FIRST_NAME,
                DB.FEED.USER_LAST_NAME
        };

        for (String k : keys) {
            if (c0.containsKey(k) && c1.containsKey(k))
                if (!c0.getAsString(k).equalsIgnoreCase(c1.getAsString(k))) {
                    if (print)
                        Log.i("FeedList", "fail at " + k + " c0: " + c0.getAsString(k) + ", c1: "
                                + c1.getAsString(k));
                    return false;
                }
        }

        if (overlaps(c0, c1, print)) {
            return true;
        }

        return false;
    }

    private static boolean overlaps(ContentValues c0, ContentValues c1, boolean print) {
        long t0s = c0.getAsLong(DB.FEED.START_TIME) / 1000; // ms => s
        long t0e = t0s;
        if (c0.containsKey(DB.FEED.DURATION))
            t0e += c0.getAsLong(DB.FEED.DURATION);

        long t1s = c1.getAsLong(DB.FEED.START_TIME) / 1000; // ms => s
        long t1e = t1s;
        if (c1.containsKey(DB.FEED.DURATION))
            t1e += c1.getAsLong(DB.FEED.DURATION);

        if (t1s >= t0s && t1s <= t0e)
            return true;

        if (t1e >= t0s && t1e <= t0e)
            return true;

        if (t1s <= t0s && t1e >= t0e)
            return true;

        boolean broken0 = c0.containsKey(DB.FEED.FLAGS)
                && c0.getAsString(DB.FEED.FLAGS).contains("brokenStartTime");
        boolean broken1 = c1.containsKey(DB.FEED.FLAGS)
                && c1.getAsString(DB.FEED.FLAGS).contains("brokenStartTime");
        if (broken0 || broken1) {
            /**
             * check if same day, if so compare distance/duration instead
             */
            Calendar d0 = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            Calendar d1 = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            d0.setTimeInMillis(t0s * 1000);
            d1.setTimeInMillis(t1s * 1000);
            if ((d0.get(Calendar.YEAR) != d1.get(Calendar.YEAR)) ||
                    (d0.get(Calendar.DAY_OF_YEAR) != d1.get(Calendar.DAY_OF_YEAR))) {
                if (print)
                    Log.i("FeedList", "fail at d0: " + d0.toString() + ", d1: " + d1.toString());
                return false;
            }

            int dur_match = 0;
            if (c0.containsKey(DB.FEED.DURATION) && c1.containsKey(DB.FEED.DURATION)) {
                double dur0 = c0.getAsDouble(DB.FEED.DURATION);
                double dur1 = c1.getAsDouble(DB.FEED.DURATION);
                if (dur0 != 0 && dur1 != 0) {
                    double pct = Math.abs((dur0 / dur1) - 1);
                    if (pct > 0.1) {
                        if (print)
                            Log.i("FeedList", "fail at dur0: " + dur0 + "dur1: " + dur1
                                    + " => pct: " + pct);
                        return false;
                    }
                    dur_match = 2;
                }
            } else if (!c0.containsKey(DB.FEED.DURATION) && !c1.containsKey(DB.FEED.DURATION)) {
                dur_match = 1;
            }

            int dis_match = 0;
            if (c0.containsKey(DB.FEED.DISTANCE) && c1.containsKey(DB.FEED.DISTANCE)) {
                double dur0 = c0.getAsDouble(DB.FEED.DISTANCE);
                double dur1 = c1.getAsDouble(DB.FEED.DISTANCE);
                if (dur0 != 0 && dur1 != 0) {
                    double pct = Math.abs((dur0 / dur1) - 1);
                    if (pct > 0.1) {
                        if (print)
                            Log.i("FeedList", "fail at dis0: " + dur0 + "dis1: " + dur1
                                    + " => pct: " + pct);
                        return false;
                    }
                    dis_match = 2;
                }
            } else if (!c0.containsKey(DB.FEED.DISTANCE) && !c1.containsKey(DB.FEED.DISTANCE)) {
                dis_match = 1;
            }

            if (dis_match + dur_match >= 3) {
                return true;
            }

            Log.i("FeedList", "dur_match: " + dur_match + ", dis_match: " + dis_match + ", c0: "
                    + FeedList.toString(c0) + ", c1: " + FeedList.toString(c1));
        }

        return false;
    }

    public static String toString(ContentValues tmp) {
        StringBuilder buf = new StringBuilder();
        buf.append("[ ");
        boolean first = true;
        for (Entry<String, Object> e : tmp.valueSet()) {
            if (!first) {
                buf.append(", ");
            }
            first = false;
            buf.append(e.getKey());
            buf.append(":");
            buf.append(e.getValue());
        }
        if (!first)
            buf.append(" ");
        buf.append("]");
        return buf.toString();
    }

    public static void sort(List<ContentValues> list) {
        java.util.Collections.sort(list, new Comparator<ContentValues>() {
            @Override
            public int compare(ContentValues lhs, ContentValues rhs) {
                long t1 = lhs.getAsLong(DB.FEED.START_TIME);
                long t2 = rhs.getAsLong(DB.FEED.START_TIME);
                if (t1 < t2) {
                    return +1;
                } else if (t1 > t2) {
                    return -1;
                }
                return 0;
            }
        });

    }
}
