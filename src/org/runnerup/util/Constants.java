/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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

package org.runnerup.util;

public interface Constants {

    public interface DB {

        public interface ACTIVITY {
            public static final String TABLE = "activity";
            public static final String START_TIME = "start_time";
            public static final String DISTANCE = "distance";
            public static final String TIME = "time";
            public static final String NAME = "name";
            public static final String COMMENT = "comment";
            public static final String SPORT = "type";
            public static final String MAX_HR = "avg_hr";
            public static final String AVG_HR = "max_hr";
            public static final String AVG_CADENCE = "avg_cadence";
            public static final int SPORT_RUNNING = 0;
            public static final int SPORT_BIKING = 1;
            public static final int SPORT_OTHER = 2; // unknown
        };

        public interface LOCATION {
            public static final String TABLE = "location";
            public static final String ACTIVITY = "activity_id";
            public static final String LAP = "lap";
            public static final String TYPE = "type";
            public static final String TIME = "time";
            public static final String LATITUDE = "latitude";
            public static final String LONGITUDE = "longitude";
            public static final String ACCURANCY = "accurancy";
            public static final String ALTITUDE = "altitude";
            public static final String SPEED = "speed";
            public static final String BEARING = "bearing";
            public static final String HR = "hr";
            public static final String CADENCE = "cadence";

            public static final int TYPE_START = 1;
            public static final int TYPE_END = 2;
            public static final int TYPE_GPS = 3;
            public static final int TYPE_PAUSE = 4;
            public static final int TYPE_RESUME = 5;
            public static final int TYPE_DISCARD = 6;
        };

        public interface LAP {
            public static final String TABLE = "lap";
            public static final String ACTIVITY = "activity_id";
            public static final String LAP = "lap";
            public static final String INTENSITY = "type";
            public static final String TIME = "time";
            public static final String DISTANCE = "distance";
            public static final String PLANNED_TIME = "planned_time";
            public static final String PLANNED_DISTANCE = "planned_distance";
            public static final String PLANNED_PACE = "planned_pace";
            public static final String AVG_HR = "avg_hr";
            public static final String MAX_HR = "max_hr";
            public static final String AVG_CADENCE = "avg_cadence";
        };

        public interface INTENSITY {
            public static final int ACTIVE = 0;
            public static final int RESTING = 1;
            public static final int WARMUP = 2;
            public static final int COOLDOWN = 3;
            public static final int REPEAT = 4;
            public static final int RECOVERY = 5;
        };

        public interface ACCOUNT {
            public static final String TABLE = "account";
            public static final String NAME = "name";
            public static final String URL = "url";
            public static final String DESCRIPTION = "description";
            public static final String FORMAT = "format";
            public static final String FLAGS = "default_send";
            public static final String ENABLED = "enabled";
            public static final String AUTH_METHOD = "auth_method";
            public static final String AUTH_CONFIG = "auth_config";
            public static final String ICON = "icon";

            public static final int FLAG_UPLOAD = 0;
            public static final int FLAG_FEED = 1;
            public static final int FLAG_LIVE = 2;
            public static final int FLAG_SKIP_MAP = 3;
            public static final long DEFAULT_FLAGS =
                    (1 << FLAG_UPLOAD) +
                            (1 << FLAG_FEED) +
                            (1 << FLAG_LIVE);
        };

        public interface EXPORT {
            public static final String TABLE = "report";
            public static final String ACTIVITY = "activity_id";
            public static final String ACCOUNT = "account_id";
            public static final String STATUS = "status";
            public static final String EXTERNAL_ID = "ext_id";
            public static final String EXTRA = "extra";
        }

        public interface AUDIO_SCHEMES {
            public static final String TABLE = "audio_schemes";
            public static final String NAME = "name";
            public static final String SORT_ORDER = "sort_order";
        }

        public interface FEED {
            public static final String TABLE = "feed";
            public static final String ACCOUNT_ID = "account_id";
            public static final String EXTERNAL_ID = "ext_id"; // ID per account
            public static final String FEED_TYPE = "entry_type";
            public static final String FEED_SUBTYPE = "type";
            public static final String FEED_TYPE_STRING = "type_string";
            public static final String START_TIME = "start_time";
            public static final String DURATION = "duration";
            public static final String DISTANCE = "distance";
            public static final String USER_ID = "user_id";
            public static final String USER_FIRST_NAME = "user_first_name";
            public static final String USER_LAST_NAME = "user_last_name";
            public static final String USER_IMAGE_URL = "user_image_url";
            public static final String NOTES = "notes";
            public static final String COMMENTS = "comments";
            public static final String URL = "url";
            public static final String FLAGS = "flags";

            public static final int FEED_TYPE_ACTIVITY = 0; // FEED_SUBTYPE
                                                            // contains
                                                            // activity.type
            public static final int FEED_TYPE_EVENT = 1;

            public static final int FEED_TYPE_EVENT_DATE_HEADER = 0;
        }
    };
};
