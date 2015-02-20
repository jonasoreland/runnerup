package org.runnerup.content.db.provider.export;

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
 * Informatoin about the export state of an activity
 */
public class ExportColumns implements BaseColumns {
    public static final String TABLE_NAME = "export";
    public static final Uri CONTENT_URI = Uri.parse(RunnerUpDbProvider.CONTENT_URI_BASE + "/" + TABLE_NAME);

    /**
     * Primary key.
     */
    public static final String _ID = BaseColumns._ID;

    /**
     * Id of the activity that's beeing exported
     */
    public static final String ACTIVITY_ID = "activity_id";

    /**
     * The account to which the activity has been exported
     */
    public static final String ACCOUNT_ID = "account_id";

    /**
     * Status of the export
     */
    public static final String STATUS = "status";

    /**
     * External Id of the activity
     */
    public static final String EXT_ID = "ext_id";

    /**
     * Extra
     */
    public static final String EXTRA = "extra";


    public static final String DEFAULT_ORDER = TABLE_NAME + "." +_ID;

    // @formatter:off
    public static final String[] ALL_COLUMNS = new String[] {
            _ID,
            ACTIVITY_ID,
            ACCOUNT_ID,
            STATUS,
            EXT_ID,
            EXTRA
    };
    // @formatter:on

    public static boolean hasColumns(String[] projection) {
        if (projection == null) return true;
        for (String c : projection) {
            if (c == ACTIVITY_ID || c.contains("." + ACTIVITY_ID)) return true;
            if (c == ACCOUNT_ID || c.contains("." + ACCOUNT_ID)) return true;
            if (c == STATUS || c.contains("." + STATUS)) return true;
            if (c == EXT_ID || c.contains("." + EXT_ID)) return true;
            if (c == EXTRA || c.contains("." + EXTRA)) return true;
        }
        return false;
    }

}
