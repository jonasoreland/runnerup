package org.runnerup.db;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.preference.PreferenceManager;

import com.goebl.simplify.PointExtractor;
import com.goebl.simplify.Simplify;

import org.runnerup.R;
import org.runnerup.common.util.Constants;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Wrapper for com.goebl.simplify.Simplify.
 */
public class PathSimplifier {
    /**
     * Tolerance in degrees for path simplification.
     *
     * The higher the tolerance, the smoother the path.
     * Note, if too big, the total distance might be reduced and won't match the reality.
     */
    private double toleranceDeg;
    /** High quality (true) or fast (false) simplification. */
    private boolean high_quality;

    /** Distance in meters between two degrees of latitude at equator,
     * as computed by android.location.Location.distanceTo()  */
    private static final double ONE_DEGREE = 110574.390625;
    /** Multiplier to avoid deltas < 1 between points. */
    private static final double MULTIPLIER = 1e6;

    /**
     * Wrapper needed by simplifyPath to use Location as 2D point.
     * Extracts (lat,long) from the Location class needed by com.goebl.simplify.simplify for (x,y).
     *
     * The functions return a multiple of the lat/long values to avoid deltas < 1 between points.
     *
     * https://github.com/hgoebl/simplify-java
     */
    private static PointExtractor<Location> locationPointExtractor = new PointExtractor<Location>() {
        @Override
        public double getX(Location point) {
            return point.getLatitude() * PathSimplifier.MULTIPLIER;
        }

        @Override
        public double getY(Location point) {
            return point.getLongitude() * PathSimplifier.MULTIPLIER;
        }
    };

    /**
     * Initializes path simplification.
     *
     * @param context Context used to extract parameters for path simplification.
     */
    public PathSimplifier(Context context) {
        Resources res = context.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // get user settings

        // tolerance in meters (default to 3)
        double tolerance;
        try {
            tolerance = Double.parseDouble(prefs.getString(
                    res.getString(R.string.pref_path_simplification_tolerance), "3"));
        } catch (Exception ex) {
            tolerance = 3;
        }
        // squared tolerance in meters has to be transformed to tolerance in degrees
        this.toleranceDeg = tolerance / ONE_DEGREE;

        String high_quality_setting = res.getStringArray(R.array.path_simplification_algorithm_values)[1];
        String algorithm = prefs.getString(
                res.getString(R.string.pref_path_simplification_algorithm),
                high_quality_setting);
        // convert algorithm to parameter for simplify method
        this.high_quality = high_quality_setting.equals(algorithm);
    }

    /** Returns true if simplification should be applied when completion and saving an activity. */
    public static boolean isEnabledForSave(Context context) {
        Resources res = context.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        return prefs.getBoolean(
                res.getString(R.string.pref_path_simplification_save), false);
    }

    /** Returns true if simplification should be applied when exporting an activity in GPX format. */
    public static boolean isEnabledForExportGpx(Context context) {
        Resources res = context.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        return prefs.getBoolean(
                res.getString(R.string.pref_path_simplification_export_gpx), false);
    }

    /**
     * Returns the IDs (as a list of strings) of the location entries
     * that would simplify the path of an activity, i.e., reduce the path's resolution.
     * Simplification is applied within each activity segment. A segment is a set of locations
     * between START/RESUME and PAUSE/END locations. Only GPS locations are considered
     * for simplification, other locations are preserved.
     *
     * We use only 2D because we cannot mix degrees (lat,long) with meters (altitude),
     * regarding the tolerance of simplify.
     * Conversion of lat and long to meters is not necessary to simplify the path.
     *
     * https://github.com/hgoebl/simplify-java
     *
     * @param db Database.
     * @param activityId ID of the activity to simplify.
     */
    public ArrayList<String> getNoisyLocationIDsAsStrings(SQLiteDatabase db, long activityId) {

        // columns to query from the "LOCATION" table in database
        String[] pColumns = {
                "_id", Constants.DB.LOCATION.LATITUDE, Constants.DB.LOCATION.LONGITUDE,
                Constants.DB.LOCATION.TYPE
        };


        // get table from database
        Cursor c = db.query(Constants.DB.LOCATION.TABLE, pColumns,
                Constants.DB.LOCATION.ACTIVITY + " = " + activityId,
                null, null, null, Constants.DB.LOCATION.TIME);

        // List of a segment activity's locations (lat,long).
        ArrayList<Location> locations = new ArrayList<>();
        // IDs of the full activity's locations
        ArrayList<String> ids = new ArrayList<>();
        // Location IDs to remove from the activity
        ArrayList<String> simplifiedIDs = new ArrayList<>();

        if (c.moveToFirst()) {
            do {
                int lstate = c.getInt(3);

                // Only TYPE_GPS locations are considered for simplification
                if (lstate == Constants.DB.LOCATION.TYPE_GPS) {
                    // save ID of the location entry
                    Location l = new Location(String.format(Locale.US, "%d", c.getInt(0)));
                    // get location's coordinates
                    l.setLatitude(c.getDouble(1));
                    l.setLongitude(c.getDouble(2));
                    ids.add(l.getProvider());
                    locations.add(l);

                } else if ((lstate == Constants.DB.LOCATION.TYPE_PAUSE)
                        || (lstate == Constants.DB.LOCATION.TYPE_END)) {
                    // this is the end of a segment

                    // simplify current segment
                    Location[] simplifiedLocations = simplifySegment(locations);
                    // store locations to keep
                    for (Location sl : simplifiedLocations) {
                        simplifiedIDs.add(sl.getProvider());
                    }

                    // start new segment
                    locations = new ArrayList<>();
                }

            } while (c.moveToNext());
        }
        c.close();

        // remove the locations to keep in the simplified path
        ids.removeAll(simplifiedIDs);

        return ids;
    }

    /**
     * Returns the IDs (list of integers) of the location entries
     * that would simplify the path of an activity,
     * i.e., reduce the path's resolution.
     *
     * @param db Database.
     * @param activityId ID of the activity to simplify.
     */
    public ArrayList<Integer> getNoisyLocationIDs(SQLiteDatabase db, long activityId) {
        ArrayList<String> strIDs = getNoisyLocationIDsAsStrings(db, activityId);

        // convert (back) to integers
        ArrayList<Integer> ids = new ArrayList<>();
        for (String str: strIDs)
            ids.add(Integer.parseInt(str));

        return ids;
    }

    /**
     * Simplifies a segment of an activity
     *
     * @param locations a subset of the activity locations that ends with a location
     *                 of TYPE_PAUSE or TYPE_END type
     * @return the locations of the simplified path segment
     */
    private Location[] simplifySegment(ArrayList<Location> locations) {

        // create an instance of the simplifier (empty array needed by List.toArray)
        Location[] sampleArray = new Location[0];
        Simplify<Location> simplify = new Simplify<>(sampleArray, locationPointExtractor);
        // removes unnecessary intermediate points (note this does not change lat/long values!)
        return simplify.simplify(locations.toArray(sampleArray),
                toleranceDeg * MULTIPLIER, this.high_quality);
    }
}
