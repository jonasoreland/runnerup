/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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

package org.runnerup.export.format;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.PathCursor;
import org.runnerup.db.PathSimplifier;
import org.runnerup.util.KXmlSerializer;
import org.runnerup.workout.Sport;

public class GPX {

  private final SQLiteDatabase mDB;
  private final SimpleDateFormat simpleDateFormat;
  private final ExportOptions exportOptions;
  private final PathSimplifier simplifier;
  private KXmlSerializer mXML;

  public GPX(SQLiteDatabase mDB, ExportOptions exportOptions, PathSimplifier simplifier) {
    this.mDB = mDB;
    mXML = new KXmlSerializer();
    simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    this.simplifier = simplifier;
    this.exportOptions = exportOptions;
  }

  private String formatTime(long time) {
    return simpleDateFormat.format(new Date(time));
  }

  /**
   * @param activityId
   * @param writer
   * @throws IOException
   */
  public void export(long activityId, Writer writer) throws IOException {

    String[] aColumns = {
      DB.ACTIVITY.NAME,
      DB.ACTIVITY.COMMENT,
      DB.ACTIVITY.START_TIME,
      DB.ACTIVITY.SPORT,
      DB.ACTIVITY.META_DATA
    };
    Cursor cursor =
        mDB.query(DB.ACTIVITY.TABLE, aColumns, "_id = " + activityId, null, null, null, null);
    cursor.moveToFirst();

    long startTime = cursor.getLong(2); // epoch
    try {
      List<String> schemas = new ArrayList<>();
      mXML.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
      mXML.setOutput(writer);
      mXML.startDocument("UTF-8", true);
      mXML.startTag("", "gpx");
      mXML.attribute("", "version", "1.1");
      mXML.attribute("", "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
      schemas.add("http://www.topografix.com/GPX/1/1");
      schemas.add("https://www.topografix.com/GPX/1/1/gpx.xsd");
      mXML.attribute("", "xmlns", schemas.get(schemas.size() - 2));
      if (exportOptions.garminExtensions) {
        schemas.add("http://www.garmin.com/xmlschemas/GpxExtensions/v3");
        schemas.add("https://www8.garmin.com/xmlschemas/GpxExtensionsv3.xsd");
        mXML.attribute("", "xmlns:ns2", schemas.get(schemas.size() - 2));
        schemas.add("http://www.garmin.com/xmlschemas/TrackPointExtension/v2");
        schemas.add("https://www8.garmin.com/xmlschemas/TrackPointExtensionv2.xsd");
        mXML.attribute("", "xmlns:gpxtpx", schemas.get(schemas.size() - 2));
      } else {
        schemas.add("http://www.cluetrust.com/XML/GPXDATA/1/0");
        schemas.add("https://www.cluetrust.com/Schemas/gpxdata10.xsd");
        mXML.attribute("", "xmlns:gpxtpx", schemas.get(schemas.size() - 2));
      }
      mXML.attribute("", "xsi:schemaLocation", String.join(" ", schemas));
      String creator = "RunnerUp " + android.os.Build.MODEL;
      if (!cursor.isNull(4)) {
        String metaData = cursor.getString(4);
        if (metaData.contains(DB.ACTIVITY.WITH_BAROMETER)) {
          creator += " with barometer";
        }
      }
      mXML.attribute("", "creator", creator);

      mXML.startTag("", "metadata");
      mXML.startTag("", "time");
      final String time = formatTime(startTime * 1000);
      mXML.text(time);
      mXML.endTag("", "time");
      mXML.endTag("", "metadata");

      mXML.startTag("", "trk");
      mXML.startTag("", "name");
      Integer sportDbValue = null;
      String sportName;
      if (cursor.isNull(3)) {
        sportName = "Running";
      } else {
        // Resources are not available, use hardcoded strings
        sportDbValue = cursor.getInt(3);
        sportName = Sport.textOf(sportDbValue);
      }

      mXML.text("RunnerUp-" + sportName + "-" + time);
      mXML.endTag("", "name");
      if (!cursor.isNull(1)) {
        String notes = cursor.getString(1);
        mXML.startTag("", "desc");
        mXML.text(notes);
        mXML.endTag("", "desc");
      }
      mXML.startTag("", "type");
      // Note: Not aligned to specific apps like Strava
      mXML.text(sportName);
      mXML.endTag("", "type");

      exportLaps(activityId, sportDbValue);
      mXML.endTag("", "trk");
      mXML.endTag("", "gpx");
      mXML.flush();
      mXML.endDocument();
      mXML = null;
      cursor.close();
    } catch (IOException e) {
      cursor.close();
      mXML = null;
      throw e;
    }
  }

  private void exportLaps(long activityId, Integer sportDbValue) throws IOException {
    String[] lColumns = {DB.LAP.LAP, DB.LAP.DISTANCE, DB.LAP.TIME, DB.LAP.INTENSITY};
    Cursor cLap =
        mDB.query(
            DB.LAP.TABLE,
            lColumns,
            "( "
                + DB.LAP.DISTANCE
                + " > 0 or "
                + DB.LAP.TIME
                + " > 0) and "
                + DB.LAP.ACTIVITY
                + " = "
                + activityId,
            null,
            null,
            null,
            null);

    String[] pColumns = {
      DB.LOCATION.LAP,
      DB.LOCATION.TIME,
      DB.LOCATION.LATITUDE,
      DB.LOCATION.LONGITUDE,
      DB.LOCATION.ALTITUDE,
      DB.LOCATION.TYPE,
      DB.LOCATION.HR,
      DB.LOCATION.CADENCE,
      DB.LOCATION.TEMPERATURE,
      DB.LOCATION.PRESSURE,
      DB.LOCATION.ACCURANCY,
      DB.LOCATION.BEARING,
      DB.LOCATION.SPEED,
      DB.LOCATION.SATELLITES,
      DB.LOCATION.GPS_ALTITUDE,
      DB.PRIMARY_KEY
    };
    PathCursor cLocation = new PathCursor(mDB, activityId, pColumns, 15, simplifier);
    boolean lok = cLap.moveToFirst();
    boolean pok = cLocation.moveToFirst();

    // Tracks with time gaps may show as unconnected, mostly the same as this setting
    final boolean useLapTrkSeg = (simplifier == null);

    // number of GPS points in current track segment
    int segmentPoints = 0;
    while (lok) {
      if (exportOptions.shouldExportLap(
          sportDbValue, /* distance= */ cLap.getFloat(1), /* time= */ cLap.getLong(2))) {
        long lap = cLap.getLong(0);
        while (pok && cLocation.getLong(0) != lap) {
          pok = cLocation.moveToNext();
        }
        if (pok && cLocation.getLong(0) == lap) {
          long last_time = 0;
          while (pok && cLocation.getLong(0) == lap) {
            // Ignore all other than GPS, GPX cannot handle pauses
            int locType = cLocation.getInt(5);
            long time = cLocation.getLong(1);
            if (locType != DB.LOCATION.TYPE_GPS) {
              if (exportOptions.accuracyExtensions) {
                if (segmentPoints >= 2 && locType == DB.LOCATION.TYPE_RESUME) {
                  // GPX has no standard for pauses, but segments are occasionally used,
                  // sometimes separate activities
                  mXML.endTag("", "trkseg");
                  segmentPoints = 0;
                }
                mXML.comment(" State change: " + locType + " " + formatTime(time));
              }
            } else if (time > last_time) {
              if (segmentPoints == 0) {
                mXML.startTag("", "trkseg");
              }
              segmentPoints++;

              mXML.startTag("", "trkpt");

              float lat = cLocation.getFloat(2);
              float lon = cLocation.getFloat(3);
              mXML.attribute("", "lon", Float.toString(lon));
              mXML.attribute("", "lat", Float.toString(lat));

              Float ele = null;
              if (exportOptions.accuracyExtensions && !cLocation.isNull(14)) {
                // raw elevation
                ele = cLocation.getFloat(14);
              } else if (!cLocation.isNull(4)) {
                ele = cLocation.getFloat(4);
              }
              if (ele != null) {
                mXML.startTag("", "ele");
                mXML.text("" + ele);
                mXML.endTag("", "ele");
              }

              mXML.startTag("", "time");
              mXML.text(formatTime(time));
              mXML.endTag("", "time");

              boolean isSats = !cLocation.isNull(13) && exportOptions.accuracyExtensions;
              if (isSats) {
                mXML.startTag("", "sat");
                String val = Integer.toString(cLocation.getInt(13));
                mXML.text(val);
                mXML.endTag("", "sat");
              }

              {
                // Garmin's GPX extensions for non standard data (other variants exists too, like
                // Cluetrust)
                // Check app specific like Strava: https://strava.github.io/api/v3/uploads/
                // Private extensions are normally not used externally
                boolean isHr = !cLocation.isNull(6);
                boolean isCad = !cLocation.isNull(7);
                boolean isTemp = !cLocation.isNull(8);
                boolean isPres = !cLocation.isNull(9) && exportOptions.accuracyExtensions;
                boolean isAccuracy = !cLocation.isNull(10) && exportOptions.accuracyExtensions;
                boolean isBearing = !cLocation.isNull(11) && exportOptions.accuracyExtensions;
                boolean isSpeed = !cLocation.isNull(12) && exportOptions.accuracyExtensions;
                boolean isGpsAlt = !cLocation.isNull(14) && exportOptions.accuracyExtensions;
                boolean isAny =
                    isCad
                        || isTemp
                        || isPres
                        || isAccuracy
                        || isBearing
                        || isSpeed
                        || isHr
                        || isGpsAlt;

                if (isAny) {
                  mXML.startTag("", "extensions");
                  if (exportOptions.garminExtensions) {
                    mXML.startTag("", "gpxtpx:TrackPointExtension");
                  }
                }

                if (isTemp) {
                  String ns;
                  if (exportOptions.garminExtensions) {
                    ns = "gpxtpx:atemp";
                  } else {
                    ns = "gpxtpx:temp";
                  }
                  mXML.startTag("", ns);
                  String val = Float.toString(cLocation.getFloat(8));
                  mXML.text(val);
                  mXML.endTag("", ns);
                }

                if (isHr) {
                  // Same ns for Garmin/Cluetrust extensions
                  mXML.startTag("", "gpxtpx:hr");
                  String bpm = Integer.toString(cLocation.getInt(6));
                  mXML.text(bpm);
                  mXML.endTag("", "gpxtpx:hr");
                }

                if (isCad) {
                  String ns;
                  if (exportOptions.garminExtensions) {
                    // Seen in some examples, not officially supported by Strava
                    ns = "gpxtpx:cad";
                  } else {
                    ns = "gpxtpx:cadence";
                  }
                  mXML.startTag("", ns);
                  String val = Integer.toString(Math.round(cLocation.getFloat(7)));
                  mXML.text(val);
                  mXML.endTag("", ns);
                }

                if (isSpeed) {
                  mXML.startTag("", "gpxtpx:speed");
                  String val = Float.toString(cLocation.getFloat(12));
                  mXML.text(val);
                  mXML.endTag("", "gpxtpx:speed");
                }

                if (isBearing) {
                  mXML.startTag("", "gpxtpx:course");
                  String val = Float.toString(cLocation.getFloat(11));
                  mXML.text(val);
                  mXML.endTag("", "gpxtpx:course");
                }

                {
                  boolean isGpxtpxExt =
                      exportOptions.garminExtensions && (isAccuracy || isPres || isGpsAlt);
                  if (isGpxtpxExt) {
                    mXML.startTag("", "gpxtpx:Extensions");
                  }
                  if (isAccuracy) {
                    mXML.startTag("", "accuracy");
                    String val = Float.toString(cLocation.getFloat(10));
                    mXML.text(val);
                    mXML.endTag("", "accuracy");
                  }

                  if (isPres) {
                    // private extension, not recorded by default
                    mXML.startTag("", "pressure");
                    String val = Float.toString(cLocation.getFloat(9));
                    mXML.text(val);
                    mXML.endTag("", "pressure");
                  }
                  if (isPres) {
                    // private extension, not recorded by default
                    mXML.startTag("", "gpsaltitude");
                    String val = Float.toString(cLocation.getFloat(14));
                    mXML.text(val);
                    mXML.endTag("", "gpsaltitude");
                  }
                  if (isGpxtpxExt) {
                    mXML.endTag("", "gpxtpx:Extensions");
                  }
                }

                if (isAny) {
                  if (exportOptions.garminExtensions) {
                    mXML.endTag("", "gpxtpx:TrackPointExtension");
                  }
                  mXML.endTag("", "extensions");
                }
              }

              mXML.endTag("", "trkpt");
              last_time = time;
            }
            pok = cLocation.moveToNext();
          }
        }
        if (useLapTrkSeg && segmentPoints >= 2) {
          // "merge" segments with less than two points
          mXML.endTag("", "trkseg");
          segmentPoints = 0;
        }
      }

      lok = cLap.moveToNext();
    }
    if (segmentPoints > 0) {
      mXML.endTag("", "trkseg");
    }
    cLap.close();
    cLocation.close();
  }
}
