package org.runnerup.db;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.preference.PreferenceManager;

import com.goebl.simplify.Simplify;
import com.goebl.simplify.PointExtractor;

import org.runnerup.R;
import org.runnerup.common.util.Constants;

import java.util.ArrayList;

/**
 * Wrapper for com.goebl.simplify.Simplify.
 */
public class PathSimplifier {
    /** Simplifies a polyline. */
    private Simplify<Location> simplifier;

    /** Indicates if simplification is enabled. */
    private boolean enabled;
    /** Indicates when the simplification is performed (e.g., on export). */
    private String when_allowed;
    /**
     * Tolerance in meters for path simplification.
     *
     * The higher the tolerance, the smoother the path.
     * Note, if too big, the total distance might be reduced and won't match the reality.
     */
    private double tolerance;
    /** High quality (true) or fast (false) simplification. */
    private boolean high_quality;

    /** Multiplier to avoid deltas < 1 between points. */
    private static final double multiplier = 1e6;
    /** Indicates which types of locations shall be used for path simplificaiton. */
    private boolean gps_type_only;

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
            return point.getLatitude() * PathSimplifier.multiplier;
        }

        @Override
        public double getY(Location point) {
            return point.getLongitude() * PathSimplifier.multiplier;
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
        this.enabled = prefs.getBoolean(
                res.getString(R.string.pref_path_simplification_enable), false);
        this.when_allowed = prefs.getString(
                res.getString(R.string.pref_path_simplification_when), "export");
        this.tolerance = Double.parseDouble(prefs.getString(
                res.getString(R.string.pref_path_simplification_tolerance), "3"));
        String algorithm = prefs.getString(
                res.getString(R.string.pref_path_simplification_algorithm), "ramer_douglas_peucker");

        // convert algorithm to parameter for simplify method
        this.high_quality = algorithm.matches("ramer_douglas_peucker");

        // simplify path given all locations
        this.gps_type_only = false;
    }

    /**
     * Initialization, given the type of location to simplify.
     * @param context Context used to extract parameters for path simplification.
     * @param gps_type_only If true, then other location types than GPS are ignored for simplification.
     */
    public PathSimplifier(Context context, boolean gps_type_only) {
        this(context);
        this.gps_type_only = gps_type_only;
    }

    /** Returns true, if simplification is enabled and can be performed at given 'when'. */
    public boolean isEnabledFor(String when) {
        return this.enabled && when.matches(this.when_allowed);
    }

    /**
     * Returns the IDs (list of strings) of the location entries
     * that would simplify the path of an activity,
     * i.e., reduce the path's resolution.
     *
     * We use only 2D because we cannot mix degrees (lat,long) with meters (altitude),
     * regarding the tolerance of simplify.
     * Conversion of lat and long to meters is not necessary to simplify the path.
     *
     * https://github.com/hgoebl/simplify-java
     *
     * @param db Database.
     * @param activityId ID of the activity to simplify.
     * @param when The current point in the application (e.g., "save" or "export").
     */
    public ArrayList<String> getNoisyLocationIDsAsStrings(SQLiteDatabase db, long activityId, String when) {
        // continue only if enabled and given 'when' matches the allowed one
        if (!this.isEnabledFor(when))
            return new ArrayList<>(); // empty list

        // columns to query from the database, table "LOCATION"
        String[] pColumns = {
                "_id", Constants.DB.LOCATION.LATITUDE, Constants.DB.LOCATION.LONGITUDE
        };

        // optional constraint to extract GPS type locations only
        String constraint = "";
        if (gps_type_only)
            constraint = " and " + Constants.DB.LOCATION.TYPE + " = " + Constants.DB.LOCATION.TYPE_GPS;

        // get table from database
        Cursor c = db.query(Constants.DB.LOCATION.TABLE, pColumns,
                Constants.DB.LOCATION.ACTIVITY + " = " + activityId + constraint,
                null, null, null, Constants.DB.LOCATION.TIME);

        // data base rows to lists
        ArrayList<Location> locations = new ArrayList<>();  /** List of the activity's locations (lat,long). */
        if (c.moveToFirst()) {
            do {
                // save ID of the location entry (row ID) as provider
                Location l = new Location(String.format("%d", c.getInt(0)));
                // get point data
                l.setLatitude(c.getDouble(1));
                l.setLongitude(c.getDouble(2));
                locations.add(l);
            } while (c.moveToNext());
        }
        c.close();

        // squared tolerance in meters has to be transformed to tolerance in degrees
        // get meters per 1° with android.location.Location.distanceTo
        Location zeroDegrees = new Location("null island"); // 0°N 0°E
        zeroDegrees.setLatitude(0);
        zeroDegrees.setLongitude(0);
        Location oneDegrees = new Location(zeroDegrees); // 1°N 0°E
        oneDegrees.setLatitude(1);
        // tolerance in meters / meters per degree
        double toleranceDeg = this.tolerance / zeroDegrees.distanceTo(oneDegrees);

        // create an instance of the simplifier (empty array needed by List.toArray)
        Location[] sampleArray = new Location[0];
        Simplify<Location> simplify = new Simplify<Location>(sampleArray, locationPointExtractor);
        // removes unnecessary intermediate points (note this does not change lat/long values!)
        Location[] simplifiedLocations = simplify.simplify(locations.toArray(sampleArray),
                toleranceDeg*this.multiplier, this.high_quality);

        // remove the locations (skipped by simplify) from the database
        ArrayList<String> ids = new ArrayList<>();  // IDs of all the activity's locations
        ArrayList<String> simplifiedIDs = new ArrayList<>();  // IDs to remove from location table
        for (Location l: locations) {
            ids.add(l.getProvider());
        }
        for (Location l: simplifiedLocations) {
            simplifiedIDs.add(l.getProvider());
        }
        ids.removeAll(simplifiedIDs); // IDs to delete or ignore

        return ids;
    }

    /**
     * Returns the IDs (list of integers) of the location entries
     * that would simplify the path of an activity,
     * i.e., reduce the path's resolution.
     *
     * @param db Database.
     * @param activityId ID of the activity to simplify.
     * @param when The current point in the application (e.g., "save" or "export").
     */
    public ArrayList<Integer> getNoisyLocationIDs(SQLiteDatabase db, long activityId, String when) {
        ArrayList<String> strIDs = getNoisyLocationIDsAsStrings(db, activityId, when);

        // convert (back) to integers
        ArrayList<Integer> ids = new ArrayList<>();
        for (String str: strIDs)
            ids.add(Integer.parseInt(str));

        return ids;
    }
}
