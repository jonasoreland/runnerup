package org.runnerup.content.db.provider.activity;

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
 * A activity summary
 */
public class ActivityColumns implements BaseColumns {
    public static final String TABLE_NAME = "activity";
    public static final Uri CONTENT_URI = Uri.parse(RunnerUpDbProvider.CONTENT_URI_BASE + "/" + TABLE_NAME);

    /**
     * Primary key.
     */
    public static final String _ID = BaseColumns._ID;

    /**
     * Start time of the activity
     */
    public static final String START_TIME = "start_time";

    /**
     * Distance of the activity
     */
    public static final String DISTANCE = "distance";

    /**
     * Duration of the activity
     */
    public static final String TIME = "time";

    /**
     * Name of the activity
     */
    public static final String NAME = "name";

    /**
     * Comment for the activity
     */
    public static final String COMMENT = "comment";

    /**
     * Sport type of the activity
     */
    public static final String TYPE = "type";

    /**
     * Maximum HR of the activity
     */
    public static final String MAX_HR = "max_hr";

    /**
     * Avarage HR of the activity
     */
    public static final String AVG_HR = "avg_hr";

    /**
     * Avarage cadence of the activity
     */
    public static final String AVG_CADENCE = "avg_cadence";

    /**
     * Status of the activity
     */
    public static final String DELETED = "deleted";

    /**
     * Workaround column
     */
    public static final String NULLCOLUMNHACK = "nullColumnHack";


    public static final String DEFAULT_ORDER = TABLE_NAME + "." +_ID;

    // @formatter:off
    public static final String[] ALL_COLUMNS = new String[] {
            _ID,
            START_TIME,
            DISTANCE,
            TIME,
            NAME,
            COMMENT,
            TYPE,
            MAX_HR,
            AVG_HR,
            AVG_CADENCE,
            DELETED,
            NULLCOLUMNHACK
    };
    // @formatter:on

    public static boolean hasColumns(String[] projection) {
        if (projection == null) return true;
        for (String c : projection) {
            if (c == START_TIME || c.contains("." + START_TIME)) return true;
            if (c == DISTANCE || c.contains("." + DISTANCE)) return true;
            if (c == TIME || c.contains("." + TIME)) return true;
            if (c == NAME || c.contains("." + NAME)) return true;
            if (c == COMMENT || c.contains("." + COMMENT)) return true;
            if (c == TYPE || c.contains("." + TYPE)) return true;
            if (c == MAX_HR || c.contains("." + MAX_HR)) return true;
            if (c == AVG_HR || c.contains("." + AVG_HR)) return true;
            if (c == AVG_CADENCE || c.contains("." + AVG_CADENCE)) return true;
            if (c == DELETED || c.contains("." + DELETED)) return true;
            if (c == NULLCOLUMNHACK || c.contains("." + NULLCOLUMNHACK)) return true;
        }
        return false;
    }

}
