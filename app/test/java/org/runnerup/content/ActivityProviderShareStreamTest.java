package org.runnerup.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.json.JSONObject;
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

  @Test
  public void sharedJsonContainsCompactSummaryAndSplitsWithoutRawGps() throws Exception {
    long activityId = insertActivity();

    Uri uri = sharedUri("json", activityId, "qa-run.json");
    assertEquals("application/json", context.getContentResolver().getType(uri));
    JSONObject export = new JSONObject(readSharedExport("json", activityId, "qa-run.json"));

    assertEquals("relven.activity", export.getString("format"));
    assertEquals(1, export.getInt("schemaVersion"));
    assertEquals("activity:" + activityId, export.getJSONObject("activity").getString("id"));
    assertEquals(
        120.0,
        export.getJSONObject("activity").getJSONObject("summary").getDouble("distanceMeters"),
        0.001);
    assertEquals(
        500.0,
        export
            .getJSONObject("activity")
            .getJSONObject("summary")
            .getDouble("averagePaceSecondsPerKm"),
        0.001);
    assertEquals(1, export.getJSONObject("activity").getJSONArray("splits").length());
    JSONObject split = export.getJSONObject("activity").getJSONArray("splits").getJSONObject(0);
    assertEquals(120.0, split.getDouble("distanceMeters"), 0.001);
    assertEquals(150, split.getInt("averageHeartRateBpm"));
    assertEquals(100.0, split.getJSONObject("planned").getDouble("distanceMeters"), 0.001);
    assertEquals("Original notes", export.getJSONObject("activity").getString("notes"));
    assertFalse(
        "Compact export must not include private route coordinates",
        export.toString().contains("59.3001"));
  }

  @Test
  public void sharedJsonKeepsUnavailableMetricsUnknownForPartialActivity() throws Exception {
    ContentValues activity = new ContentValues();
    activity.put(DB.ACTIVITY.START_TIME, START_MILLIS / 1000);
    activity.put(DB.ACTIVITY.SPORT, DB.ACTIVITY.SPORT_RUNNING);
    long activityId = database.insertOrThrow(DB.ACTIVITY.TABLE, null, activity);

    JSONObject export = new JSONObject(readSharedExport("json", activityId, "partial-run.json"));
    JSONObject savedActivity = export.getJSONObject("activity");

    assertEquals("partial", savedActivity.getString("status"));
    assertFalse(savedActivity.has("summary"));
    assertEquals(0, savedActivity.getJSONArray("splits").length());
  }

  @Test
  public void sharedJsonOmitsNegativeStoredDurationsInsteadOfViolatingSchema() throws Exception {
    ContentValues activity = new ContentValues();
    activity.put(DB.ACTIVITY.START_TIME, START_MILLIS / 1000);
    activity.put(DB.ACTIVITY.TIME, -3);
    activity.put(DB.ACTIVITY.DISTANCE, 10.0);
    activity.put(DB.ACTIVITY.SPORT, DB.ACTIVITY.SPORT_RUNNING);
    long activityId = database.insertOrThrow(DB.ACTIVITY.TABLE, null, activity);
    ContentValues lap = new ContentValues();
    lap.put(DB.LAP.ACTIVITY, activityId);
    lap.put(DB.LAP.LAP, -2);
    lap.put(DB.LAP.TIME, -3);
    lap.put(DB.LAP.PLANNED_TIME, -1);
    database.insertOrThrow(DB.LAP.TABLE, null, lap);

    JSONObject savedActivity =
        new JSONObject(readSharedExport("json", activityId, "negative-duration.json"))
            .getJSONObject("activity");

    assertEquals("partial", savedActivity.getString("status"));
    assertFalse(savedActivity.getJSONObject("summary").has("durationSeconds"));
    assertFalse(savedActivity.getJSONObject("summary").has("averagePaceSecondsPerKm"));
    JSONObject split = savedActivity.getJSONArray("splits").getJSONObject(0);
    assertFalse(split.has("durationSeconds"));
    assertFalse(split.has("planned"));
    assertEquals(1, split.getInt("number"));
  }

  @Test
  public void sharedJsonLabelsCyclingCadenceInRevolutionsPerMinute() throws Exception {
    ContentValues activity = new ContentValues();
    activity.put(DB.ACTIVITY.START_TIME, START_MILLIS / 1000);
    activity.put(DB.ACTIVITY.TIME, 600);
    activity.put(DB.ACTIVITY.DISTANCE, 5000.0);
    activity.put(DB.ACTIVITY.SPORT, DB.ACTIVITY.SPORT_BIKING);
    activity.put(DB.ACTIVITY.AVG_CADENCE, 82.0);
    long activityId = database.insertOrThrow(DB.ACTIVITY.TABLE, null, activity);

    JSONObject summary =
        new JSONObject(readSharedExport("json", activityId, "bike.json"))
            .getJSONObject("activity")
            .getJSONObject("summary");

    assertEquals(82.0, summary.getDouble("averagePedalCadenceRpm"), 0.001);
    assertFalse(summary.has("averageCadenceStepsPerMinute"));
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
    activity.put(DB.ACTIVITY.COMMENT, "Original notes");
    activity.put(DB.ACTIVITY.AVG_HR, 150);
    activity.put(DB.ACTIVITY.MAX_HR, 171);
    activity.put(DB.ACTIVITY.AVG_CADENCE, 172.0);
    long activityId = database.insertOrThrow(DB.ACTIVITY.TABLE, null, activity);

    ContentValues lap = new ContentValues();
    lap.put(DB.LAP.ACTIVITY, activityId);
    lap.put(DB.LAP.LAP, 1);
    lap.put(DB.LAP.TIME, 60);
    lap.put(DB.LAP.DISTANCE, 120.0);
    lap.put(DB.LAP.INTENSITY, DB.INTENSITY.ACTIVE);
    lap.put(DB.LAP.AVG_HR, 150);
    lap.put(DB.LAP.PLANNED_DISTANCE, 100.0);
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
