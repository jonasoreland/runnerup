/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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

package org.runnerup.export.format;

import android.annotation.TargetApi;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;

import org.runnerup.common.util.Constants.DB;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

@TargetApi(Build.VERSION_CODES.FROYO)
public class GoogleStaticMap {

    long mID = 0;
    SQLiteDatabase mDB = null;

    public GoogleStaticMap(SQLiteDatabase mDB) {
        this.mDB = mDB;
    }

    public static void main(final String args[]) {
        long lat0 = 0, lot0 = 0;
        StringBuffer dst = new StringBuffer();
        for (int i = 0; i + 1 < args.length; i += 2) {
            long lat = Long.parseLong(args[i + 0]);
            long lot = Long.parseLong(args[i + 1]);
            encode(dst, lat, lot, lat0, lot0);
            System.err.print("[ " + lat + ", " + lot + "] ");
            lat0 = lat;
            lot0 = lot;
        }
        Log.e("GoogleStaticMap", " => " + dst.toString());
    }

    public static void encode(StringBuffer buf, long val) {
        val <<= 1;
        if (val < 0) {
            val = ~val;
        }
        do {
            char tmp = (char) (val & 31);
            val >>= 5;
            if (val != 0)
                tmp |= (char) 0x20;
            tmp += 63;
            buf.append(tmp);
        } while (val != 0);
    }

    public static void encode(StringBuffer dst, long latitude1, long longitude1, long latitude0,
            long longitude0) {
        encode(dst, latitude1 - latitude0);
        encode(dst, longitude1 - longitude0);
    }

    public long countLocations(long activityId) {
        long count = 0;
        String[] args = {
            Long.toString(activityId)
        };
        Cursor c = mDB.rawQuery("SELECT COUNT (DISTINCT round(" + DB.LOCATION.LATITUDE
                + "* 10000000000 + " + DB.LOCATION.LONGITUDE + "*100000)) FROM "
                + DB.LOCATION.TABLE + " WHERE " + DB.LOCATION.ACTIVITY + " = ?", args);
        if (c.moveToFirst()) {
            count = c.getLong(0);
        }
        c.close();
        return count;
    }

    /**
     * @param activityId
     */
    public String export(long activityId, final int maxLen) {

        long count = countLocations(activityId);
        int avgLen = 6; // in this encoding 1 location "normally" takes 9 chars
        StringBuffer dst = null;
        do {
            dst = new StringBuffer();
            /**
			 * 
			 */
            final int points = maxLen / avgLen;
            final int skip = (int) (1 + count / points);

            String[] args = {
                Long.toString(activityId)
            };
            Cursor c = mDB.rawQuery("SELECT DISTINCT cast(round(" + DB.LOCATION.LATITUDE
                    + "*100000) as integer), cast(round(" + DB.LOCATION.LONGITUDE
                    + "*100000) as integer) FROM " + DB.LOCATION.TABLE + " WHERE "
                    + DB.LOCATION.ACTIVITY + " = ?", args);
            if (c.moveToFirst()) {
                long lat0 = 0;
                long long0 = 0;
                do {
                    long lat = c.getLong(0);
                    long longi = c.getLong(1);
                    encode(dst, lat, longi, lat0, long0);
                    lat0 = lat;
                    long0 = longi;
                } while (c.move(skip));
            }
            c.close();

            String res;
            try {
                res = URLEncoder.encode(dst.toString(), "UTF-8");
                Log.e("GoogleStaticMap", "count: " + count + ", avgLen: " + avgLen + ", points: "
                        + points + ", res.length(): " + res.length());
                if (res.length() < maxLen)
                    return res;

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return null;
            }

            avgLen++; // use less points...
        } while (true);
    }
}
