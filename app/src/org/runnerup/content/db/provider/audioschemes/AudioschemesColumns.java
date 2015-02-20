package org.runnerup.content.db.provider.audioschemes;

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
 * Definition of the audio cue scheme
 */
public class AudioschemesColumns implements BaseColumns {
    public static final String TABLE_NAME = "audioschemes";
    public static final Uri CONTENT_URI = Uri.parse(RunnerUpDbProvider.CONTENT_URI_BASE + "/" + TABLE_NAME);

    /**
     * Primary key.
     */
    public static final String _ID = BaseColumns._ID;

    /**
     * Name of the scheme
     */
    public static final String NAME = "name";

    /**
     * The order of the scheme
     */
    public static final String SORT_ORDER = "sort_order";


    public static final String DEFAULT_ORDER = TABLE_NAME + "." +_ID;

    // @formatter:off
    public static final String[] ALL_COLUMNS = new String[] {
            _ID,
            NAME,
            SORT_ORDER
    };
    // @formatter:on

    public static boolean hasColumns(String[] projection) {
        if (projection == null) return true;
        for (String c : projection) {
            if (c == NAME || c.contains("." + NAME)) return true;
            if (c == SORT_ORDER || c.contains("." + SORT_ORDER)) return true;
        }
        return false;
    }

}
