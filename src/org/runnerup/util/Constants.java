/*
 * Copyright (C) 2012 jonas.oreland@gmail.com
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
			public static final String TYPE = "type";
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

			public static final int TYPE_START = 1;
			public static final int TYPE_END = 2;
			public static final int TYPE_GPS = 3;
			public static final int TYPE_PAUSE = 4;
			public static final int TYPE_RESUME = 5;
		};

		public interface LAP {
			public static final String TABLE = "lap";
			public static final String ACTIVITY = "activity_id";
			public static final String LAP = "lap";
			public static final String TYPE = "type";
			public static final String TIME = "time";
			public static final String DISTANCE = "distance";
			public static final String PLANNED_TIME = "planned_time";
			public static final String PLANNED_DISTANCE = "planned_distance";
			public static final String PLANNED_PACE = "planned_pace";
		};

		public interface ACCOUNT {
			public static final String TABLE = "account";
			public static final String NAME = "name";
			public static final String URL = "url";
			public static final String DESCRIPTION = "description";
			public static final String FORMAT = "format";
			public static final String DEFAULT = "default_send";
			public static final String ENABLED = "enabled";
			public static final String AUTH_METHOD = "auth_method";
			public static final String AUTH_CONFIG = "auth_config";
			public static final String ICON = "icon";
		};

		public interface EXPORT {
			public static final String TABLE = "report";
			public static final String ACTIVITY = "activity_id";
			public static final String ACCOUNT = "account_id";
			public static final String STATUS = "status";
			public static final String EXTERNAL_ID = "ext_id";
			public static final String EXTRA = "extra";
		};
	};
};