package org.runnerup.content.db.provider.lap;

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
public class LapColumns implements BaseColumns {
    public static final String TABLE_NAME = "lap";
    public static final Uri CONTENT_URI = Uri.parse(RunnerUpDbProvider.CONTENT_URI_BASE + "/" + TABLE_NAME);

    /**
     * Primary key.
     */
    public static final String _ID = BaseColumns._ID;

    /**
     * Id of the activity the lap belongs to
     */
    public static final String ACTIVITY_ID = "activity_id";

    /**
     * Number of the lap
     */
    public static final String LAP = "lap";

    /**
     * Type (intensity) of the lap
     */
    public static final String TYPE = "type";

    /**
     * Duration of the lap
     */
    public static final String TIME = "time";

    /**
     * Distance of the lap
     */
    public static final String DISTANCE = "distance";

    /**
     * Planned duration of the lap
     */
    public static final String PLANNED_TIME = "planned_time";

    /**
     * Planned distance of the lap
     */
    public static final String PLANNED_DISTANCE = "planned_distance";

    /**
     * Planned pace of the lap
     */
    public static final String PLANNED_PACE = "planned_pace";

    /**
     * Average HR of the lap
     */
    public static final String AVG_HR = "avg_hr";

    /**
     * Maximum HR of the lap
     */
    public static final String MAX_HR = "max_hr";

    /**
     * Avarage cadence of the lap
     */
    public static final String AVG_CADENCE = "avg_cadence";


    public static final String DEFAULT_ORDER = TABLE_NAME + "." +_ID;

    // @formatter:off
    public static final String[] ALL_COLUMNS = new String[] {
            _ID,
            ACTIVITY_ID,
            LAP,
            TYPE,
            TIME,
            DISTANCE,
            PLANNED_TIME,
            PLANNED_DISTANCE,
            PLANNED_PACE,
            AVG_HR,
            MAX_HR,
            AVG_CADENCE
    };
    // @formatter:on

    public static boolean hasColumns(String[] projection) {
        if (projection == null) return true;
        for (String c : projection) {
            if (c == ACTIVITY_ID || c.contains("." + ACTIVITY_ID)) return true;
            if (c == LAP || c.contains("." + LAP)) return true;
            if (c == TYPE || c.contains("." + TYPE)) return true;
            if (c == TIME || c.contains("." + TIME)) return true;
            if (c == DISTANCE || c.contains("." + DISTANCE)) return true;
            if (c == PLANNED_TIME || c.contains("." + PLANNED_TIME)) return true;
            if (c == PLANNED_DISTANCE || c.contains("." + PLANNED_DISTANCE)) return true;
            if (c == PLANNED_PACE || c.contains("." + PLANNED_PACE)) return true;
            if (c == AVG_HR || c.contains("." + AVG_HR)) return true;
            if (c == MAX_HR || c.contains("." + MAX_HR)) return true;
            if (c == AVG_CADENCE || c.contains("." + AVG_CADENCE)) return true;
        }
        return false;
    }

}
