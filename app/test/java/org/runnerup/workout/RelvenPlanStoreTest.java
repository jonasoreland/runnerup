package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class RelvenPlanStoreTest {
  private static final String JSON =
      "{\"format\":\"relven.plan\",\"schemaVersion\":1,\"id\":\"store-test\","
          + "\"name\":\"Store test\",\"timezone\":\"Europe/Stockholm\","
          + "\"workouts\":[{\"schemaVersion\":3,\"id\":\"run-a\",\"name\":\"Easy run\","
          + "\"sport\":\"running\",\"steps\":[{\"id\":\"step-a\",\"kind\":\"segment\","
          + "\"phase\":\"work\",\"end\":{\"type\":\"time\",\"seconds\":1800}}]}],"
          + "\"days\":[{\"id\":\"session-a\",\"date\":\"2026-10-20\",\"kind\":\"workout\","
          + "\"workoutId\":\"run-a\"}]}";

  private Context context;
  private File directory;

  @Before
  public void setUp() {
    context = RuntimeEnvironment.getApplication();
    directory = context.getDir("relven-plans", Context.MODE_PRIVATE);
    File[] existing = directory.listFiles();
    if (existing != null) {
      for (File file : existing) assertTrue(file.delete());
    }
  }

  @Test
  public void savesValidatedPlanAndRejectsDuplicateId() throws Exception {
    RelvenPlanImporter.Plan plan = RelvenPlanImporter.parse(JSON);
    RelvenPlanStore.save(context, plan);

    File[] saved = directory.listFiles();
    assertTrue(saved != null);
    assertEquals(1, saved.length);
    assertEquals(JSON, Files.readString(saved[0].toPath()));
    assertThrows(java.io.IOException.class, () -> RelvenPlanStore.save(context, plan));
    assertEquals(1, directory.listFiles().length);
  }

  @Test
  public void invalidSourceWritesNothing() {
    RelvenPlanImporter.Plan forged =
        new RelvenPlanImporter.Plan(
            "forged", "Forged", "Europe/Stockholm", List.of(), Map.of(), "{}");
    assertThrows(RuntimeException.class, () -> RelvenPlanStore.save(context, forged));
    assertEquals(0, directory.listFiles().length);
  }
}
