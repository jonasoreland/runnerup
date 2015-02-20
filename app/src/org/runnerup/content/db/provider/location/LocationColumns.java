package org.runnerup.content.db.provider.location;

import android.net.Uri;
import android.provider.BaseColumns;

import org.runnerup.content.db.provider.RunnerUpDbProvider;
import org.runnerup.content.db.provider.account.AccountColumns;
import org.runnerup.content.db.provider.activity.ActivityColumns;
import org.runnerup.content.db.provider.audioschemes.AudioschemesColumns;
import org.runnerup.content.db.provider.export.ExportColumns;
import org.runnerup.content.db.provider.feed.FeedColumns;
import org.runnerup.content.db.provider.lap.LapColumns;
import org.runnerup.content.db.provider.location.LocationColumns;

/**
 * A lap summary
 */
public class LocationColumns implements BaseColumns {
    public static final String TABLE_NAME = "location";
    public static final Uri CONTENT_URI = Uri.parse(RunnerUpDbProvider.CONTENT_URI_BASE + "/" + TABLE_NAME);

    /**
     * Primary key.
     */
    public static final String _ID = BaseColumns._ID;

    /**
     * Id of the activity the location point belongs to
     */
    public static final String ACTIVITY_ID = "activity_id";

    /**
     * Lap number of the activity the location point belongs to
     */
    public static final String LAP = "lap";

    /**
     * Type of the location point
     */
    public static final String TYPE = "type";

    /**
     * The moment in time when the location point was recorded
     */
    public static final String TIME = "time";

    /**
     * Longitude of the location
     */
    public static final String LONGITUDE = "longitude";

    /**
     * Latitude of the location
     */
    public static final String LATITUDE = "latitude";

    /**
     * Accuracy of the location
     */
    public static final String ACCURANCY = "accurancy";

    /**
     * Altitude of the location
     */
    public static final String ALTITUDE = "altitude";

    /**
     * Speed of the location
     */
    public static final String SPEED = "speed";

    /**
     * Bearing of the location
     */
    public static final String BEARING = "bearing";

    /**
     * HR at the location
     */
    public static final String HR = "hr";

    /**
     * Cadence at the location
     */
    public static final String CADENCE = "cadence";


    public static final String DEFAULT_ORDER = TABLE_NAME + "." +_ID;

    // @formatter:off
    public static final String[] ALL_COLUMNS = new String[] {
            _ID,
            ACTIVITY_ID,
            LAP,
            TYPE,
            TIME,
            LONGITUDE,
            LATITUDE,
            ACCURANCY,
            ALTITUDE,
            SPEED,
            BEARING,
            HR,
            CADENCE
    };
    // @formatter:on

    public static boolean hasColumns(String[] projection) {
        if (projection == null) return true;
        for (String c : projection) {
            if (c == ACTIVITY_ID || c.contains("." + ACTIVITY_ID)) return true;
            if (c == LAP || c.contains("." + LAP)) return true;
            if (c == TYPE || c.contains("." + TYPE)) return true;
            if (c == TIME || c.contains("." + TIME)) return true;
            if (c == LONGITUDE || c.contains("." + LONGITUDE)) return true;
            if (c == LATITUDE || c.contains("." + LATITUDE)) return true;
            if (c == ACCURANCY || c.contains("." + ACCURANCY)) return true;
            if (c == ALTITUDE || c.contains("." + ALTITUDE)) return true;
            if (c == SPEED || c.contains("." + SPEED)) return true;
            if (c == BEARING || c.contains("." + BEARING)) return true;
            if (c == HR || c.contains("." + HR)) return true;
            if (c == CADENCE || c.contains("." + CADENCE)) return true;
        }
        return false;
    }

}
