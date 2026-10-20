package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class RelvenPlanImportTest {
  private static final String JSON =
      "{\"format\":\"relven.plan\",\"schemaVersion\":1,\"id\":\"preview-1\","
          + "\"name\":\"First weeks\",\"timezone\":\"Europe/Stockholm\",\"workouts\":["
          + "{\"schemaVersion\":3,\"id\":\"run-a\",\"name\":\"Easy run\","
          + "\"sport\":\"running\",\"steps\":[{\"id\":\"step-a\","
          + "\"kind\":\"segment\",\"phase\":\"work\",\"end\":{"
          + "\"type\":\"time\",\"seconds\":1800}}]}],\"days\":["
          + "{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"workout\","
          + "\"workoutId\":\"run-a\"},{\"id\":\"s2\",\"date\":\"2026-10-21\","
          + "\"kind\":\"rest\"}]}";

  @Test
  public void readsUtf8PackageWithinLimit() throws Exception {
    String text = "{\"name\":\"Löpning\"}";
    assertEquals(
        text,
        RelvenPlanImport.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))));
  }

  @Test
  public void rejectsStreamLargerThanPackageLimit() {
    byte[] bytes = new byte[4_194_305];
    assertThrows(IOException.class, () -> RelvenPlanImport.read(new ByteArrayInputStream(bytes)));
  }

  @Test
  public void previewExplainsOneTimeScheduleAndSeparateActivation() {
    RelvenPlanImporter.Plan plan = RelvenPlanImporter.parse(JSON);
    String preview = RelvenPlanImport.preview(plan);

    assertTrue(preview.contains("First weeks"));
    assertTrue(preview.contains("2026-10-20–2026-10-21"));
    assertTrue(preview.contains("1 workout day · 1 rest day"));
    assertTrue(preview.contains("2026-10-20 · Workout (run-a)"));
    assertTrue(preview.contains("2026-10-21 · Rest day"));
    assertTrue(preview.contains("one-time"));
    assertTrue(preview.contains("does not activate"));
  }
}
