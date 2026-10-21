package org.runnerup.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.runnerup.workout.Sport;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ActivityFileImporterTest {
  @Test
  public void parsesGpxRouteAndTrackPointMetrics() throws Exception {
    try (InputStream input = getClass().getResourceAsStream("/activity-import/minimal.gpx")) {
      ImportedActivity activity = ActivityFileImporter.read("run.gpx", input);

      assertEquals(ImportedActivity.Format.GPX, activity.getFormat());
      assertEquals(Sport.RUNNING, activity.getSport());
      assertEquals(1735725600L, activity.getStartTimeSeconds());
      assertEquals(10L, activity.getDurationSeconds());
      assertEquals(2, activity.getTrackPoints().size());
      ActivityTrackPoint first = activity.getTrackPoints().get(0);
      assertEquals(59.0, first.getLatitude(), 0.000001);
      assertEquals(18.0, first.getLongitude(), 0.000001);
      assertEquals(10.5, first.getAltitudeMeters(), 0.001);
      assertEquals(Integer.valueOf(140), first.getHeartRate());
      assertEquals(Integer.valueOf(82), first.getCadence());
      assertEquals(0.0, first.getDistanceMeters(), 0.001);
      assertEquals(Integer.valueOf(145), activity.getAverageHeartRate());
      assertEquals(Integer.valueOf(150), activity.getMaximumHeartRate());
      assertEquals(11.1, activity.getDistanceMeters(), 1.0);
    }
  }

  @Test
  public void parsesTcxSummaryAndOrderedPoints() throws Exception {
    try (InputStream input = getClass().getResourceAsStream("/activity-import/minimal.tcx")) {
      ImportedActivity activity = ActivityFileImporter.read("run.tcx", input);

      assertEquals(ImportedActivity.Format.TCX, activity.getFormat());
      assertEquals(Sport.RUNNING, activity.getSport());
      assertEquals(1735725600L, activity.getStartTimeSeconds());
      assertEquals(60L, activity.getDurationSeconds());
      assertEquals(120.0, activity.getDistanceMeters(), 0.001);
      assertEquals(2, activity.getTrackPoints().size());
      assertEquals(0.0, activity.getTrackPoints().get(0).getDistanceMeters(), 0.001);
      assertEquals(120.0, activity.getTrackPoints().get(1).getDistanceMeters(), 0.001);
      assertEquals(Integer.valueOf(145), activity.getAverageHeartRate());
      assertEquals(Integer.valueOf(150), activity.getMaximumHeartRate());
      assertEquals(Integer.valueOf(84), activity.getTrackPoints().get(1).getCadence());
    }
  }

  @Test
  public void parsesGpxVersion10Namespace() throws Exception {
    String xml =
        "<gpx xmlns=\"http://www.topografix.com/GPX/1/0\" version=\"1.0\"><trk><trkseg>"
            + point("59", "18", "2025-01-01T10:00:00Z")
            + point("59.0001", "18", "2025-01-01T10:00:10Z")
            + "</trkseg></trk></gpx>";

    ImportedActivity activity = read("not-a-gpx-name.xml", xml);

    assertEquals(ImportedActivity.Format.GPX, activity.getFormat());
    assertEquals(Sport.RUNNING, activity.getSport());
    assertEquals(2, activity.getTrackPoints().size());
  }

  @Test
  public void rejectsGpxWithMultipleTrackSegmentsWithoutFlatteningRoute() throws Exception {
    String xml =
        "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\"><trk>"
            + "<trkseg>"
            + point("59", "18", "2025-01-01T10:00:00Z")
            + point("59.0001", "18", "2025-01-01T10:00:10Z")
            + "</trkseg><trkseg>"
            + point("60", "19", "2025-01-01T10:00:20Z")
            + point("60.0001", "19", "2025-01-01T10:00:30Z")
            + "</trkseg></trk></gpx>";

    ActivityImportException error =
        org.junit.Assert.assertThrows(
            ActivityImportException.class, () -> read("segments.gpx", xml));
    assertEquals(ActivityImportException.Reason.MULTIPLE_ROUTE_SEGMENTS, error.getReason());
  }

  @Test
  public void trackPointListIsImmutable() throws Exception {
    try (InputStream input = getClass().getResourceAsStream("/activity-import/minimal.gpx")) {
      List<ActivityTrackPoint> points =
          ActivityFileImporter.read("run.gpx", input).getTrackPoints();

      org.junit.Assert.assertThrows(UnsupportedOperationException.class, points::clear);
    }
  }

  @Test
  public void rejectsMultipleTcxActivities() throws Exception {
    String tcx;
    try (InputStream input = getClass().getResourceAsStream("/activity-import/minimal.tcx")) {
      tcx = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    String secondActivity =
        "<Activity Sport=\"Running\"><Id>2025-01-01T11:00:00Z</Id></Activity></Activities>";
    ActivityImportException error =
        assertThrows(
            ActivityImportException.class,
            () -> read("multi.tcx", tcx.replace("</Activities>", secondActivity)));

    assertEquals(ActivityImportException.Reason.MULTIPLE_ACTIVITIES, error.getReason());
  }

  @Test
  public void rejectsDoctypeAndExternalEntities() {
    String xml =
        "<!DOCTYPE gpx [<!ENTITY secret SYSTEM \"file:///private/secret\">]>"
            + "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\"><trk><trkseg><trkpt lat=\"59\" lon=\"18\">"
            + "<time>2025-01-01T10:00:00Z</time><name>&secret;</name></trkpt></trkseg></trk></gpx>";

    ActivityImportException error =
        assertThrows(ActivityImportException.class, () -> read("hostile.gpx", xml));

    assertEquals(ActivityImportException.Reason.MALFORMED_XML, error.getReason());
  }

  @Test
  public void rejectsOutOfRangeCoordinates() {
    String xml =
        "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg>"
            + point("91", "18", "2025-01-01T10:00:00Z")
            + point("91", "18", "2025-01-01T10:00:10Z")
            + "</trkseg></trk></gpx>";

    ActivityImportException error =
        assertThrows(ActivityImportException.class, () -> read("invalid.gpx", xml));

    assertEquals(ActivityImportException.Reason.INVALID_DATA, error.getReason());
  }

  @Test
  public void rejectsTcxActivityWithoutRoute() {
    String xml =
        "<TrainingCenterDatabase xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\">"
            + "<Activities><Activity Sport=\"Running\"><Id>2025-01-01T10:00:00Z</Id></Activity>"
            + "</Activities></TrainingCenterDatabase>";

    ActivityImportException error =
        assertThrows(ActivityImportException.class, () -> read("no-route.tcx", xml));

    assertEquals(ActivityImportException.Reason.MISSING_ROUTE, error.getReason());
  }

  @Test
  public void rejectsUnsupportedXmlRoot() {
    ActivityImportException error =
        assertThrows(
            ActivityImportException.class, () -> read("not-an-activity.xml", "<notActivity/>"));

    assertEquals(ActivityImportException.Reason.UNSUPPORTED_FORMAT, error.getReason());
  }

  @Test
  public void rejectsInputAboveTenMiB() {
    InputStream oversized = new ByteArrayInputStream(new byte[10 * 1024 * 1024 + 1]);

    ActivityImportException error =
        assertThrows(
            ActivityImportException.class, () -> ActivityFileImporter.read("large.gpx", oversized));

    assertEquals(ActivityImportException.Reason.INPUT_TOO_LARGE, error.getReason());
  }

  @Test
  public void rejectsNegativeTcxSummaryDistance() throws Exception {
    String tcx;
    try (InputStream input = getClass().getResourceAsStream("/activity-import/minimal.tcx")) {
      tcx = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    ActivityImportException error =
        assertThrows(
            ActivityImportException.class,
            () ->
                read(
                    "invalid-summary.tcx",
                    tcx.replace(
                        "<DistanceMeters>120</DistanceMeters>",
                        "<DistanceMeters>-1</DistanceMeters>")));

    assertEquals(ActivityImportException.Reason.INVALID_DATA, error.getReason());
  }

  @Test
  public void rejectsNonFiniteTcxSummaryDuration() throws Exception {
    String tcx;
    try (InputStream input = getClass().getResourceAsStream("/activity-import/minimal.tcx")) {
      tcx = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    ActivityImportException error =
        assertThrows(
            ActivityImportException.class,
            () ->
                read(
                    "invalid-summary.tcx",
                    tcx.replace(
                        "<TotalTimeSeconds>60</TotalTimeSeconds>",
                        "<TotalTimeSeconds>NaN</TotalTimeSeconds>")));

    assertEquals(ActivityImportException.Reason.INVALID_DATA, error.getReason());
  }

  @Test
  public void rejectsRouteWithMoreThanOneHundredThousandPoints() {
    StringBuilder xml = new StringBuilder(9_000_000);
    xml.append("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg>");
    for (int index = 0; index <= 100_000; index++) {
      xml.append(point("59", "18", "2025-01-01T10:00:00Z"));
    }
    xml.append("</trkseg></trk></gpx>");

    ActivityImportException error =
        assertThrows(ActivityImportException.class, () -> read("large-route.gpx", xml.toString()));

    assertEquals(ActivityImportException.Reason.TOO_MANY_POINTS, error.getReason());
  }

  @Test
  public void permitsRoutePointsWithoutOptionalSensorMetrics() throws Exception {
    String xml =
        "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg>"
            + point("59", "18", "2025-01-01T10:00:00Z")
            + point("59.0001", "18", "2025-01-01T10:00:10Z")
            + "</trkseg></trk></gpx>";

    ImportedActivity activity = read("minimal.gpx", xml);

    assertNull(activity.getAverageHeartRate());
    assertNull(activity.getMaximumHeartRate());
    assertNull(activity.getTrackPoints().get(0).getAltitudeMeters());
    assertNull(activity.getTrackPoints().get(0).getCadence());
  }

  private static ImportedActivity read(String name, String xml) throws ActivityImportException {
    return ActivityFileImporter.read(
        name, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  private static String point(String latitude, String longitude, String time) {
    return "<trkpt lat=\""
        + latitude
        + "\" lon=\""
        + longitude
        + "\"><time>"
        + time
        + "</time></trkpt>";
  }
}
