package org.runnerup.export.format;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.runnerup.common.util.Constants.DB;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ActivityExportRoundTripTest {
  private static final long START_MILLIS = 1_729_000_000_000L;

  private SQLiteDatabase database;

  @Before
  public void setUp() {
    database = SQLiteDatabase.create(null);
    database.execSQL(
        "CREATE TABLE activity (_id INTEGER PRIMARY KEY, name TEXT, comment TEXT, start_time INTEGER, type INTEGER, meta_data TEXT)");
    database.execSQL(
        "CREATE TABLE lap (_id INTEGER PRIMARY KEY, activity_id INTEGER, lap INTEGER, time INTEGER, distance REAL, type INTEGER)");
    database.execSQL(
        "CREATE TABLE location (_id INTEGER PRIMARY KEY, activity_id INTEGER NOT NULL, lap INTEGER NOT NULL, type INTEGER NOT NULL, time INTEGER NOT NULL, longitude REAL NOT NULL, latitude REAL NOT NULL, altitude REAL, hr INTEGER, cadence REAL, temperature REAL, pressure REAL, elapsed REAL, distance REAL, gps_altitude REAL, accurancy REAL, speed REAL, bearing REAL, satellites INTEGER)");
  }

  @After
  public void tearDown() {
    database.close();
  }

  @Test
  public void gpxAndTcxPreserveTheSavedSummaryAndGpsSamples() throws Exception {
    long activityId = insertActivity();
    String gpx = exportGpx(activityId);
    String tcx = exportTcx(activityId);

    var gpxDocument = parse(gpx);
    var gpxPoints = gpxDocument.getElementsByTagNameNS("*", "trkpt");
    assertEquals(3, gpxPoints.getLength());
    String[] expectedTimes = {
      "2024-10-15T13:46:40Z", "2024-10-15T13:47:40Z", "2024-10-15T13:48:40Z"
    };
    for (int i = 0; i < expectedTimes.length; i++) {
      var gpxPoint = (org.w3c.dom.Element) gpxPoints.item(i);
      assertEquals(
          expectedTimes[i], gpxPoint.getElementsByTagNameNS("*", "time").item(0).getTextContent());
    }
    assertEquals("59.3001", gpxPoints.item(0).getAttributes().getNamedItem("lat").getNodeValue());
    assertEquals("18.1001", gpxPoints.item(0).getAttributes().getNamedItem("lon").getNodeValue());
    assertEquals("160", gpxDocument.getElementsByTagNameNS("*", "hr").item(2).getTextContent());
    assertEquals(
        "steady <test> run",
        gpxDocument.getElementsByTagNameNS("*", "desc").item(0).getTextContent());

    var tcxDocument = parse(tcx);
    assertEquals(
        "180",
        tcxDocument.getElementsByTagNameNS("*", "TotalTimeSeconds").item(0).getTextContent());
    assertEquals(
        "1000.0",
        tcxDocument.getElementsByTagNameNS("*", "DistanceMeters").item(0).getTextContent());
    assertEquals(
        "150",
        tcxDocument
            .getElementsByTagNameNS("*", "AverageHeartRateBpm")
            .item(0)
            .getTextContent()
            .trim());
    assertEquals(
        "160",
        tcxDocument
            .getElementsByTagNameNS("*", "MaximumHeartRateBpm")
            .item(0)
            .getTextContent()
            .trim());
    var tcxPoints = tcxDocument.getElementsByTagNameNS("*", "Trackpoint");
    assertEquals(3, tcxPoints.getLength());
    for (int i = 0; i < expectedTimes.length; i++) {
      var tcxPoint = (org.w3c.dom.Element) tcxPoints.item(i);
      assertEquals(
          expectedTimes[i], tcxPoint.getElementsByTagNameNS("*", "Time").item(0).getTextContent());
    }
    var firstTcxPosition =
        (org.w3c.dom.Element)
            ((org.w3c.dom.Element) tcxPoints.item(0))
                .getElementsByTagNameNS("*", "Position")
                .item(0);
    assertEquals(
        "59.3001",
        firstTcxPosition.getElementsByTagNameNS("*", "LatitudeDegrees").item(0).getTextContent());
    assertEquals(
        "18.1001",
        firstTcxPosition.getElementsByTagNameNS("*", "LongitudeDegrees").item(0).getTextContent());
    assertTrue(
        tcxDocument
            .getElementsByTagNameNS("*", "Id")
            .item(0)
            .getTextContent()
            .startsWith("2024-10-"));
    assertEquals(
        "steady <test> run",
        tcxDocument.getElementsByTagNameNS("*", "Notes").item(0).getTextContent());
  }

  private long insertActivity() {
    ContentValues activity = new ContentValues();
    activity.put(DB.ACTIVITY.NAME, "Intervals & hills");
    activity.put(DB.ACTIVITY.COMMENT, "steady <test> run");
    activity.put(DB.ACTIVITY.START_TIME, START_MILLIS / 1000);
    activity.put(DB.ACTIVITY.SPORT, DB.ACTIVITY.SPORT_RUNNING);
    long activityId = database.insertOrThrow(DB.ACTIVITY.TABLE, null, activity);

    ContentValues lap = new ContentValues();
    lap.put(DB.LAP.ACTIVITY, activityId);
    lap.put(DB.LAP.LAP, 1);
    lap.put(DB.LAP.TIME, 180);
    lap.put(DB.LAP.DISTANCE, 1000.0);
    lap.put(DB.LAP.INTENSITY, DB.INTENSITY.ACTIVE);
    database.insertOrThrow(DB.LAP.TABLE, null, lap);

    insertGpsPoint(activityId, 1, 59.3001, 18.1001, 140, 0.0);
    insertGpsPoint(activityId, 2, 59.3002, 18.1002, 150, 500.0);
    insertGpsPoint(activityId, 3, 59.3003, 18.1003, 160, 1000.0);
    return activityId;
  }

  private void insertGpsPoint(
      long activityId, long id, double latitude, double longitude, int hr, double distance) {
    ContentValues point = new ContentValues();
    point.put(DB.LOCATION.ACTIVITY, activityId);
    point.put(DB.LOCATION.LAP, 1);
    point.put(DB.LOCATION.TYPE, DB.LOCATION.TYPE_GPS);
    point.put(DB.LOCATION.TIME, START_MILLIS + (id - 1) * 60_000);
    point.put(DB.LOCATION.LATITUDE, latitude);
    point.put(DB.LOCATION.LONGITUDE, longitude);
    point.put(DB.LOCATION.HR, hr);
    point.put(DB.LOCATION.DISTANCE, distance);
    database.insertOrThrow(DB.LOCATION.TABLE, null, point);
  }

  private String exportGpx(long activityId) throws Exception {
    Writer writer = new StringWriter();
    new GPX(database, ExportOptions.getDefault(), null).export(activityId, writer);
    return writer.toString();
  }

  private String exportTcx(long activityId) throws Exception {
    Writer writer = new StringWriter();
    new TCX(database, ExportOptions.getDefault(), null).export(activityId, writer);
    return writer.toString();
  }

  private org.w3c.dom.Document parse(String xml) throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    return factory
        .newDocumentBuilder()
        .parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }
}
