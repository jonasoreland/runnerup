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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * File name helper for exporting/uploading
 */
public class FileNameHelper {

    private static String DATE_TIME_FORMAT_STRING = "yyyyMMddHHmmss";

    /**
     * Generate export file name
     * @param activityId activity index
     * @param activityType activity type
     * @param activityStartTime  activity start time with String type
     * @return the generated filename
     */
    public static String getExportFileName(long activityId, String activityType, String activityStartTime) {
        String fileBase =
                String.format(Locale.getDefault(), "RunnerUp_%04d_%s_%s.",
                        activityId, activityType, activityStartTime);

        return fileBase;
    }

    /**
     * Generate export file name
     * @param activityId activity index
     * @param activityType activity type
     * @param activityStartTime  activity start time in unix timestamp
     * @return the generated filename
     */
    public static String getExportFileName(long activityId, String activityType, long activityStartTime) {
        return getExportFileName(activityId, activityType, unixTimeToString(activityStartTime));
    }

    /**
     * Generate filename for DropBox/WebDav
     * @param activityId activity index
     * @param activityType activity type
     * @param activityStartTime activity start time in unix timestamp
     * @param fileExt file extension (tcx/gpx)
     * @return the generated filename for DropBox/WebDav
     */
    public static String getDropBoxUploadFileName(long activityId, String activityType, long activityStartTime, String fileExt) {
        return String.format(Locale.getDefault(), "/RunnerUp_%s_%04d_%s_%s.%s",
                android.os.Build.MODEL.replaceAll("\\s","_"), activityId, activityType,
                unixTimeToString(activityStartTime),
                fileExt);
    }

    /**
     * convert unix timestamp to string
     * @param timeStamp unix timestamp in seconds
     * @return converted string in 'DATE_TIME_FORMAT_STRING' format
     */
    public static String unixTimeToString(long timeStamp) {
        return new SimpleDateFormat(DATE_TIME_FORMAT_STRING,
                Locale.getDefault()).format(new Date(timeStamp * 1000L));
    }
}
