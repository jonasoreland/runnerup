package org.runnerup.content.db.provider.feed;

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
 * A Service Feed entry
 */
public class FeedColumns implements BaseColumns {
    public static final String TABLE_NAME = "feed";
    public static final Uri CONTENT_URI = Uri.parse(RunnerUpDbProvider.CONTENT_URI_BASE + "/" + TABLE_NAME);

    /**
     * Primary key.
     */
    public static final String _ID = BaseColumns._ID;

    /**
     * Id of the account that the feed entry belongs to
     */
    public static final String ACCOUNT_ID = "account_id";

    /**
     * External Id of the feed entry
     */
    public static final String EXT_ID = "ext_id";

    /**
     * Type of the feed entry
     */
    public static final String ENTRY_TYPE = "entry_type";

    /**
     * Sub-Type of the feed entry
     */
    public static final String TYPE = "type";

    /**
     * Time of the feed entry
     */
    public static final String START_TIME = "start_time";

    /**
     * Duration of the feed entry activity
     */
    public static final String DURATION = "duration";

    /**
     * Distance of the feed entry activity
     */
    public static final String DISTANCE = "distance";

    /**
     * User Id of the feed entry activity
     */
    public static final String USER_ID = "user_id";

    /**
     * First name of the user of the feed entry activity
     */
    public static final String USER_FIRST_NAME = "user_first_name";

    /**
     * Last name of the user of the feed entry activity
     */
    public static final String USER_LAST_NAME = "user_last_name";

    /**
     * Image URL of the user of the feed entry activity
     */
    public static final String USER_IMAGE_URL = "user_image_url";

    /**
     * Notes of the feed entry activity
     */
    public static final String NOTES = "notes";

    /**
     * Comments of the feed entry activity
     */
    public static final String COMMENTS = "comments";

    /**
     * URL the feed entry activity
     */
    public static final String URL = "url";

    /**
     * Flags of the feed entry activity
     */
    public static final String FLAGS = "flags";


    public static final String DEFAULT_ORDER = TABLE_NAME + "." +_ID;

    // @formatter:off
    public static final String[] ALL_COLUMNS = new String[] {
            _ID,
            ACCOUNT_ID,
            EXT_ID,
            ENTRY_TYPE,
            TYPE,
            START_TIME,
            DURATION,
            DISTANCE,
            USER_ID,
            USER_FIRST_NAME,
            USER_LAST_NAME,
            USER_IMAGE_URL,
            NOTES,
            COMMENTS,
            URL,
            FLAGS
    };
    // @formatter:on

    public static boolean hasColumns(String[] projection) {
        if (projection == null) return true;
        for (String c : projection) {
            if (c == ACCOUNT_ID || c.contains("." + ACCOUNT_ID)) return true;
            if (c == EXT_ID || c.contains("." + EXT_ID)) return true;
            if (c == ENTRY_TYPE || c.contains("." + ENTRY_TYPE)) return true;
            if (c == TYPE || c.contains("." + TYPE)) return true;
            if (c == START_TIME || c.contains("." + START_TIME)) return true;
            if (c == DURATION || c.contains("." + DURATION)) return true;
            if (c == DISTANCE || c.contains("." + DISTANCE)) return true;
            if (c == USER_ID || c.contains("." + USER_ID)) return true;
            if (c == USER_FIRST_NAME || c.contains("." + USER_FIRST_NAME)) return true;
            if (c == USER_LAST_NAME || c.contains("." + USER_LAST_NAME)) return true;
            if (c == USER_IMAGE_URL || c.contains("." + USER_IMAGE_URL)) return true;
            if (c == NOTES || c.contains("." + NOTES)) return true;
            if (c == COMMENTS || c.contains("." + COMMENTS)) return true;
            if (c == URL || c.contains("." + URL)) return true;
            if (c == FLAGS || c.contains("." + FLAGS)) return true;
        }
        return false;
    }

}
