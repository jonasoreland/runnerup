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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.widget.ImageView;

import org.runnerup.db.DBHelper;
import org.runnerup.common.util.Constants;
import org.runnerup.export.SyncManager;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Observable;
import java.util.Set;
import java.util.TimeZone;
import java.util.WeakHashMap;

@TargetApi(Build.VERSION_CODES.FROYO)
public class FeedImageLoader {
    static private final Map<String, Bitmap> imageCache = Collections
            .synchronizedMap(new WeakHashMap<String, Bitmap>());

    public interface Callback {
        void run(final String url, final Bitmap b);
    }

    static public Bitmap LoadImageSync(final String url) {
        final String fixedUrl = FixUrl(url);
        Bitmap b = imageCache.get(fixedUrl);
        if (b != null) {
            return b;
        }
        try {
            InputStream is = (InputStream) new URL(fixedUrl).getContent();
            b = BitmapFactory.decodeStream(is);
            if (b != null) {
                imageCache.put(fixedUrl, b);
            }
            return b;
        } catch (Exception e) {
            Log.e("FeedImageLoader", "url exception for " + fixedUrl + ": " + e.getMessage());
        }
        return null;
    }

    static public void LoadImageAsync(final String url, final Callback onLoadingDone) {
        final String fixedUrl = FixUrl(url);
        Bitmap b = imageCache.get(url);
        if (b != null) {
            Log.i("FeedImageLoader", "Found cached image for " + url);
            onLoadingDone.run(url, b);
        } else {
            Log.i("FeedImageLoader", "Downloading image for " + url);
            new AsyncTask<String, String, Bitmap>() {
                @Override
                protected Bitmap doInBackground(String... params) {
                    Bitmap b = imageCache.get(fixedUrl);
                    if (b != null) {
                        return b;
                    }
                    try {
                        InputStream is = (InputStream) new URL(fixedUrl).getContent();
                        b = BitmapFactory.decodeStream(is);
                        if (b != null) {
                            imageCache.put(fixedUrl, b);
                        }
                        return b;
                    } catch (Exception e) {
                        Log.e("FeedImageLoader", "url exception for " + fixedUrl + ": " + e.getMessage());
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Bitmap result) {
                    if (result == null)
                        return;

                    onLoadingDone.run(url, result);
                }
            }.execute(url);
        }
    }

    /**
     * fb redirects from http to https which is not handled automatically by HttpUrlConnection
     */
    static private String FixUrl(String in) {
        return in.replace("http://graph.facebook.com/", "https://graph.facebook.com/");
    }

}
