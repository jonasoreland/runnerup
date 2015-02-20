package org.runnerup.content.db.provider.account;

import android.net.Uri;
import android.provider.BaseColumns;

import org.runnerup.content.db.provider.RunnerUpDbProvider;

/**
 * A Fitness Service
 */
public class AccountColumns implements BaseColumns {
    public static final String TABLE_NAME = "account";
    public static final Uri CONTENT_URI = Uri.parse(RunnerUpDbProvider.CONTENT_URI_BASE + "/" + TABLE_NAME);

    /**
     * Primary key.
     */
    public static final String _ID = BaseColumns._ID;

    /**
     * Name of the service
     */
    public static final String NAME = "name";

    /**
     * The description of the service
     */
    public static final String DESCRIPTION = "description";

    /**
     * The URL of the service
     */
    public static final String URL = "url";

    /**
     * The accepted format of the activity
     */
    public static final String FORMAT = "format";

    public static final String FLAGS = "default_send";

    /**
     * Status of the account
     */
    public static final String ENABLED = "enabled";

    /**
     * The authorization method
     */
    public static final String AUTH_METHOD = "auth_method";

    /**
     * The authorization config data
     */
    public static final String AUTH_CONFIG = "auth_config";

    /**
     * The service icon
     */
    public static final String ICON = "icon";


    public static final String DEFAULT_ORDER = TABLE_NAME + "." +_ID;

    // @formatter:off
    public static final String[] ALL_COLUMNS = new String[] {
            _ID,
            NAME,
            DESCRIPTION,
            URL,
            FORMAT,
            FLAGS,
            ENABLED,
            AUTH_METHOD,
            AUTH_CONFIG,
            ICON
    };
    // @formatter:on

    public static boolean hasColumns(String[] projection) {
        if (projection == null) return true;
        for (String c : projection) {
            if (c == NAME || c.contains("." + NAME)) return true;
            if (c == DESCRIPTION || c.contains("." + DESCRIPTION)) return true;
            if (c == URL || c.contains("." + URL)) return true;
            if (c == FORMAT || c.contains("." + FORMAT)) return true;
            if (c == FLAGS || c.contains("." + FLAGS)) return true;
            if (c == ENABLED || c.contains("." + ENABLED)) return true;
            if (c == AUTH_METHOD || c.contains("." + AUTH_METHOD)) return true;
            if (c == AUTH_CONFIG || c.contains("." + AUTH_CONFIG)) return true;
            if (c == ICON || c.contains("." + ICON)) return true;
        }
        return false;
    }

}
