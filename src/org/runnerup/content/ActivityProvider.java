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
package org.runnerup.content;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

import org.runnerup.db.DBHelper;
import org.runnerup.export.format.GPX;
import org.runnerup.export.format.NikeXML;
import org.runnerup.export.format.TCX;

import android.annotation.TargetApi;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Pair;

@TargetApi(Build.VERSION_CODES.FROYO)
public class ActivityProvider extends ContentProvider {
 
    // The authority is the symbolic name for the provider class
    public static final String AUTHORITY = "org.runnerup.activity.provider";
    public static final String GPX_MIME = "application/gpx+xml";
    public static final String TCX_MIME = "application/tcx+xml";
    public static final String NIKE_MIME = "application/nike+xml";
    
    // UriMatcher used to match against incoming requests
    static final int GPX = 1;
    static final int TCX = 2;
    static final int NIKE = 3;
    private UriMatcher uriMatcher;
 
    @Override
    public boolean onCreate() {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTHORITY, "gpx/#/*", GPX);
        uriMatcher.addURI(AUTHORITY, "tcx/#/*", TCX);
        uriMatcher.addURI(AUTHORITY, "nike+xml/#/*", NIKE);
        return true;
    }

    private Pair<File, OutputStream> openCacheFile(String name) {
    	for (int i = 0; i < 3; i++) {
    		try {
    			File path = null;
    			switch(i) {
    			case 0:
   				default:
    				path = getContext().getExternalCacheDir();
    				break;
    			case 1:
    				path = getContext().getExternalFilesDir("tcx");
    				break;
    			case 2:
    				path = getContext().getCacheDir();
    				break;
    			}
    			final File file = new File(path.getAbsolutePath() + File.separator + name);
    			final OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
    			System.err.println(Integer.toString(i) + ": putting cache file in: " + file.getAbsolutePath());
    			return new Pair<File, OutputStream>(file, out);
    		} catch (IOException ex) {
    		}
    	}
    	
		return null;
    }
    
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
 
    	final int res = uriMatcher.match(uri);
    	System.err.println("match(" + uri.toString() + "): " + res);
		switch(res) {
		case GPX:
		case TCX:
		case NIKE:
	    	final List<String> list = uri.getPathSegments();
	    	final String id = list.get(list.size() - 2);
			final long activityId = Long.parseLong(id);
			final Pair<File, OutputStream> out = openCacheFile("activity." + list.get(list.size() - 3));
			if (out == null) {
				System.err.println("Failed to open cacheFile(" + "activity." + list.get(list.size() - 3) + ")");
				return null;
			}
			
			System.err.println("activity: " + activityId + ", file: " + out.first.getAbsolutePath());
			DBHelper mDBHelper = new DBHelper(getContext());
			SQLiteDatabase mDB = mDBHelper.getReadableDatabase();
			try {
				if (res == TCX) {
					TCX tcx = new TCX(mDB);
					tcx.export(activityId, new OutputStreamWriter(out.second));
					System.err.println("export tcx");
				} else if (res == GPX) {
					GPX gpx = new GPX(mDB);
					gpx.export(activityId, new OutputStreamWriter(out.second));
					System.err.println("export gpx");
				} else if (res == NIKE) {
					NikeXML xml = new NikeXML(mDB);
					xml.export(activityId, new OutputStreamWriter(out.second));
				}
				out.second.flush();
				out.second.close();
				System.err.println("wrote " + out.first.length() + " bytes...");
			} catch (Exception e) {
				e.printStackTrace();
			}
			mDB.close();
			mDBHelper.close();
            
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(out.first, ParcelFileDescriptor.MODE_READ_ONLY);
            return pfd;
		}

    	throw new FileNotFoundException("Unsupported uri: " + uri.toString());
    }
 
	@Override
    public int update(Uri uri, ContentValues contentvalues, String s,
            String[] as) {
        return 0;
    }
 
    @Override
    public int delete(Uri uri, String s, String[] as) {
        return 0;
    }
 
    @Override
    public Uri insert(Uri uri, ContentValues contentvalues) {
        return null;
    }
 
    @Override
    public String getType(Uri uri) {
    	switch (uriMatcher.match(uri)) {
    	case GPX:
    		return GPX_MIME;
    	case TCX:
    		return TCX_MIME;
    	}
    	return null;
    }
 
    @Override
    public Cursor query(Uri uri, String[] projection, String s, String[] as1,
            String s1) {
        return null;
    }
}
