/*
 * Copyright (C) 2012 - 2020 jonas.oreland@gmail.com
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * File name helper for exporting/uploading
 */
public class FileNameHelper {

    /**
     * Generate export file name
     * @param activityStartTime  activity start time in unix timestamp
     * @param activityType activity type
     * @return the generated filename
     */
    public static String getExportFileName(long activityStartTime, String activityType) {
        return String.format(Locale.getDefault(),
                "RunnerUp_%s_%s.",
                unixTimeToString(activityStartTime),
                activityType);
    }

    /**
     * Generate filename including the model name, to limit sorting in services like DropBox/WebDAV
     * @param activityStartTime activity start time in unix timestamp
     * @param activityType activity type
     * @return the generated filename for DropBox/WebDAV
     */
    public static String getExportFileNameWithModel(long activityStartTime, String activityType) {
        return String.format(Locale.getDefault(),
                "/RunnerUp_%s_%s_%s.",
                android.os.Build.MODEL.replaceAll("\\s","_"),
                unixTimeToString(activityStartTime),
                activityType);
    }

    /**
     * convert unix timestamp to string
     * @param timeStamp unix timestamp in seconds
     * @return converted string in 'DATE_TIME_FORMAT_STRING' format
     */
    private static String unixTimeToString(long timeStamp) {
        String DATE_TIME_FORMAT_STRING = "yyyy-MM-dd-HH-mm-ss";
        return new SimpleDateFormat(DATE_TIME_FORMAT_STRING,
                Locale.getDefault()).format(new Date(timeStamp * 1000L));
    }
}
