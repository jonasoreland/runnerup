package org.runnerup.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.DBHelper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ActivityProviderShareStreamTest {
  private static final long START_MILLIS = 1_729_000_000_000L;

  private Context context;
  private SQLiteDatabase database;

  @Before
  public void setUp() {
    context = RuntimeEnvironment.getApplication();
    database = DBHelper.getWritableDatabase(context);
  }

  @Test
  public void sharedGpxAndTcxContentUrisReturnReadableExportStreams() throws Exception {
    long activityId = insertActivity();

    String gpx = readSharedExport("gpx", activityId, "qa-run.gpx");
    String tcx = readSharedExport("tcx", activityId, "qa-run.tcx");

    var gpxPoints = parse(gpx).getElementsByTagNameNS("*", "trkpt");
    assertEquals(2, gpxPoints.getLength());
    assertEquals("59.3001", gpxPoints.item(0).getAttributes().getNamedItem("lat").getNodeValue());
    assertEquals(
        "application/gpx+xml",
        context.getContentResolver().getType(sharedUri("gpx", activityId, "qa-run.gpx")));
    assertEquals(
        "application/vnd.garmin.tcx+xml",
        context.getContentResolver().getType(sharedUri("tcx", activityId, "qa-run.tcx")));

    var tcxDocument = parse(tcx);
    assertEquals(2, tcxDocument.getElementsByTagNameNS("*", "Trackpoint").getLength());
    assertEquals(
        "120.0",
        tcxDocument.getElementsByTagNameNS("*", "DistanceMeters").item(0).getTextContent());
  }

  private String readSharedExport(String format, long activityId, String fileName)
      throws Exception {
    Uri uri = sharedUri(format, activityId, fileName);
    assertNotNull(context.getContentResolver().getType(uri));
    try (InputStream input = context.getContentResolver().openInputStream(uri)) {
      assertNotNull("Share recipient must be able to open the activity export", input);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private Uri sharedUri(String format, long activityId, String fileName) {
    return new Uri.Builder()
        .scheme("content")
        .authority(ActivityProvider.AUTHORITY)
        .appendPath(format)
        .appendPath(Long.toString(activityId))
        .appendPath(fileName)
        .build();
  }

  private long insertActivity() {
    ContentValues activity = new ContentValues();
    activity.put(DB.ACTIVITY.NAME, "Share stream test");
    activity.put(DB.ACTIVITY.START_TIME, START_MILLIS / 1000);
    activity.put(DB.ACTIVITY.SPORT, DB.ACTIVITY.SPORT_RUNNING);
    activity.put(DB.ACTIVITY.DISTANCE, 120.0);
    activity.put(DB.ACTIVITY.TIME, 60);
    long activityId = database.insertOrThrow(DB.ACTIVITY.TABLE, null, activity);

    ContentValues lap = new ContentValues();
    lap.put(DB.LAP.ACTIVITY, activityId);
    lap.put(DB.LAP.LAP, 1);
    lap.put(DB.LAP.TIME, 60);
    lap.put(DB.LAP.DISTANCE, 120.0);
    lap.put(DB.LAP.INTENSITY, DB.INTENSITY.ACTIVE);
    database.insertOrThrow(DB.LAP.TABLE, null, lap);

    insertGpsPoint(activityId, 1, 59.3001, 18.1001, 0.0);
    insertGpsPoint(activityId, 2, 59.3002, 18.1002, 120.0);
    return activityId;
  }

  private void insertGpsPoint(
      long activityId, long id, double latitude, double longitude, double distance) {
    ContentValues point = new ContentValues();
    point.put(DB.LOCATION.ACTIVITY, activityId);
    point.put(DB.LOCATION.LAP, 1);
    point.put(DB.LOCATION.TYPE, DB.LOCATION.TYPE_GPS);
    point.put(DB.LOCATION.TIME, START_MILLIS + (id - 1) * 60_000);
    point.put(DB.LOCATION.LATITUDE, latitude);
    point.put(DB.LOCATION.LONGITUDE, longitude);
    point.put(DB.LOCATION.DISTANCE, distance);
    database.insertOrThrow(DB.LOCATION.TABLE, null, point);
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
